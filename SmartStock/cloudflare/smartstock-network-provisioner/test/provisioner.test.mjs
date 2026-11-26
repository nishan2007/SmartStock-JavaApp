import {test} from 'node:test';
import assert from 'node:assert/strict';
import {mkdtempSync,readFileSync,rmSync} from 'node:fs';
import {tmpdir} from 'node:os';
import {join,resolve,sep} from 'node:path';
import {execFileSync} from 'node:child_process';
import {sign,randomUUID,X509Certificate} from 'node:crypto';
import worker,{StoreNetwork,authenticate,canonical,hash,names,privateAddress} from '../src/index.mjs';

const dir=mkdtempSync(join(tmpdir(),'smartstock-network-test-'));
let key,certificate;
try {
  const openssl=process.env.OPENSSL_BINARY||(process.platform==='win32'?'C:/Program Files/Git/usr/bin/openssl.exe':'openssl');
  execFileSync(openssl,['req','-x509','-newkey','rsa:2048','-nodes','-keyout',join(dir,'key.pem'),'-out',join(dir,'cert.pem'),
    '-days','1','-subj','/CN=test-server','-addext','subjectAltName=DNS:test-server'],{stdio:'ignore'});
  key=readFileSync(join(dir,'key.pem'));certificate=new X509Certificate(readFileSync(join(dir,'cert.pem')));
} finally {
  if(!resolve(dir).startsWith(resolve(tmpdir())+sep))throw new Error('Unexpected test directory.');
  rmSync(dir,{recursive:true,force:true});
}
const instance=randomUUID(),fingerprint=hash(certificate.raw);
const server={serverInstanceId:instance,locationId:7,generation:3,role:'PRIMARY',certificateFingerprint:fingerprint};
const env={PROVISION_LAN:'true',BASE_DOMAIN:'deckers.gy',CF_ZONE_ID:'zone',CF_ACCOUNT_ID:'account',SUPABASE_URL:'https://registry.example',SUPABASE_SERVER_KEY:'fixture'};
function signed(path,payload,time=Date.now(),nonce=randomUUID()){
  const body=JSON.stringify(payload),stamp=String(time);
  return new Request('https://networking.deckers.gy'+path,{method:'POST',body,headers:{
    'X-SmartStock-Time':stamp,'X-SmartStock-Nonce':nonce,'X-SmartStock-Certificate':certificate.raw.toString('base64'),
    'X-SmartStock-Signature':sign('sha256',Buffer.from(canonical(path,stamp,nonce,body)),key).toString('base64')}});
}
function object(record){
  const values=new Map(record?[['network',structuredClone(record)]]:[]);
  const state={storage:{async get(k){return structuredClone(values.get(k));},async put(k,v){values.set(k,structuredClone(v));}}};
  return {object:new StoreNetwork(state,env),values};
}
async function call(obj,path,fields,nonce=randomUUID()){
  return obj.fetch(new Request('https://internal'+path,{method:'POST',body:JSON.stringify({payload:{locationId:7,serverInstanceId:instance,...fields},proof:{fingerprint,nonce,time:Date.now()}})}));
}
async function registry(record,run){
  const original=globalThis.fetch;
  globalThis.fetch=async(url,options)=>{
    assert.equal(new URL(url).hostname,'registry.example');
    assert.equal(JSON.parse(options.body).p_action,'LIST');
    return Response.json({servers:record?[record]:[]});
  };
  try {await run();} finally {globalThis.fetch=original;}
}
test('server proof binds body and operation to a valid existing certificate',async()=>{
  const payload={locationId:7,serverInstanceId:instance,lanAddress:'192.168.5.10',upstreamTlsName:'test-server'};
  const request=signed('/v1/provision',payload),body=await request.clone().text();
  assert.equal(authenticate(request,body).fingerprint,fingerprint);
  assert.throws(()=>authenticate(request,body+' '));
  assert.throws(()=>authenticate(signed('/v1/provision',payload,Date.now()-180000),body));
  const other=signed('/v1/dns/present',payload);
  assert.throws(()=>authenticate(new Request('https://networking.deckers.gy/v1/provision',{method:'POST',body,headers:other.headers}),body));
  const bad=signed('/v1/provision',{...payload,upstreamTlsName:'another-server'});
  assert.throws(()=>authenticate(bad,JSON.stringify({...payload,upstreamTlsName:'another-server'})));
});
test('hostnames are deterministic and every private IPv4 range is supported',()=>{
  assert.equal(names(7).lanHost,'store-7-lan.deckers.gy');
  for(const address of ['10.1.1.3','172.16.4.5','172.31.255.10','192.168.5.10'])assert.ok(privateAddress(address));
  for(const address of ['127.0.0.1','8.8.8.8','172.32.0.2','10.1.1.300','010.1.1.3','::1'])assert.equal(privateAddress(address),false);
  assert.throws(()=>names(-1));assert.throws(()=>names(7,'attacker.example'));
});
test('unregistered, replaced, and fingerprint-mismatched servers cannot mutate DNS',async()=>{
  for(const invalid of [null,{...server,role:'STANDBY'},{...server,role:'FENCED'},{...server,certificateFingerprint:'wrong'}]){
    await registry(invalid,async()=>{
      const {object:obj}=object();obj.api=()=>assert.fail('Unauthorized provider mutation');
      assert.equal((await call(obj,'/v1/provision',{lanAddress:'10.1.1.3'})).status,403);
    });
  }
});
test('replayed requests are rejected and repeat provisioning does not overwrite foreign DNS',async()=>{
  await registry(server,async()=>{
    const {object:obj}=object();let writes=0;const records=[];
    obj.api=async(path,method,body)=>{
      if(!method||method==='GET')return records;
      writes++;records.push({id:'record',...body});return records[0];
    };
    const nonce=randomUUID();
    assert.equal((await call(obj,'/v1/provision',{lanAddress:'10.1.1.3'},nonce)).status,200);
    assert.equal((await call(obj,'/v1/provision',{lanAddress:'10.1.1.3'},nonce)).status,409);
    assert.equal((await call(obj,'/v1/provision',{lanAddress:'10.1.1.3'})).status,200);
    assert.equal(writes,1);
    records[0].content='10.9.9.9';
    assert.equal((await call(obj,'/v1/provision',{lanAddress:'10.1.1.4'})).status,409);
    assert.equal(writes,1);
  });
});
test('DNS challenge scope is exact and cleanup preserves other concurrent values',async()=>{
  await registry(server,async()=>{
    const {object:obj}=object(),deleted=[];
    obj.api=async(path,method)=>{
      if(method==='DELETE'){deleted.push(path);return {};}
      return [{id:'mine',type:'TXT',content:'x'.repeat(43)},{id:'other',type:'TXT',content:'y'.repeat(43)}];
    };
    assert.equal((await call(obj,'/v1/dns/present',{recordName:'_acme-challenge.store-8-lan.deckers.gy',value:'x'.repeat(43)})).status,403);
    assert.equal((await call(obj,'/v1/dns/cleanup',{recordName:'_acme-challenge.store-7-lan.deckers.gy',value:'x'.repeat(43)})).status,200);
    assert.deepEqual(deleted,['/zones/zone/dns_records/mine']);
  });
});
test('remote configuration requires a pre-approved MFA policy and never tunnels LAN/API ports',async()=>{
  const {object:obj}=object();
  await assert.rejects(()=>obj.remote({},names(7),server));
  obj.env={...env,ADMIN_ACCESS_POLICY_ID:'policy',ADMIN_ACCESS_TEAM:'deckers'};
  let configuration;
  obj.api=async(path,method,body)=>{
    if(path.endsWith('/access/policies/policy'))return {decision:'allow',include:[{group:{id:'admins'}}],require:[{auth_method:{auth_method:'mfa'}}]};
    if(path.endsWith('/configurations')){configuration=body;return {};}
    if(path.includes('/dns_records'))return method?{}:[];
    if(path.endsWith('/token'))return 'fixture-connector-token';
    assert.fail('Unexpected provider operation: '+path);
  };
  await obj.remote({tunnelId:'tunnel',accessApp:'app',accessAud:'aud',caPath:'C:/private/register.pem',upstreamTlsName:'test-server'},names(7),server);
  assert.equal(configuration.config.ingress[0].service,'https://127.0.0.1:8450');
  assert.equal(configuration.config.ingress[0].originRequest.noTLSVerify,undefined);
  assert.equal(configuration.config.ingress[1].originRequest.access.required,true);
  assert.deepEqual(configuration.config.ingress.at(-1),{service:'http_status:404'});
  assert.ok(!JSON.stringify(configuration).includes('8444'));
  assert.ok(!JSON.stringify(configuration).includes('8443'));
  assert.ok(!JSON.stringify(configuration).includes('network-probe'));
});
test('the worker is disabled until centrally enabled and rejects unsigned requests',async()=>{
  assert.equal((await worker.fetch(new Request('https://networking.deckers.gy/v1/provision',{method:'POST',body:'{}'}),{})).status,503);
  assert.equal((await worker.fetch(new Request('https://networking.deckers.gy/v1/provision',{method:'POST',body:'{}'}),env)).status,401);
});
test('old installed servers cannot publish remote routes before the application update',async()=>{
  await registry(server,async()=>{
    const {object:obj}=object({instanceId:instance,generation:3,caPath:'C:/private/register.pem'});
    obj.env={...env,PROVISION_REMOTE_ACCESS:'true'};
    obj.api=async()=>[];obj.remote=()=>assert.fail('Old application exposed remote routes');
    const response=await call(obj,'/v1/provision',{lanAddress:'10.1.1.3',managedRouting:false});
    assert.equal(response.status,200);
    const body=await response.json();assert.equal(body.remoteReady,false);assert.equal(body.serverUpdateRequired,true);
  });
});
test('handover fences the previous connector before changing its LAN address',async()=>{
  await registry({...server,generation:4},async()=>{
    const {object:obj,values}=object({instanceId:randomUUID(),generation:3,tunnelId:'old-tunnel',lanAddress:'10.1.1.3',sshHostKeys:['ssh-ed25519 '+'x'.repeat(44)]});
    const operations=[];
    obj.api=async(path,method,body)=>{
      operations.push({path,method,body});
      if(!method)return [{id:'dns',type:'A',content:'10.1.1.3',proxied:false}];
      return {};
    };
    assert.equal((await call(obj,'/v1/provision',{lanAddress:'10.1.1.4'})).status,200);
    assert.equal(operations[0].path,'/accounts/account/cfd_tunnel/old-tunnel/configurations');
    assert.deepEqual(operations[0].body,{config:{ingress:[{service:'http_status:404'}]}});
    assert.equal(values.get('network').instanceId,instance);
    assert.equal(values.get('network').generation,4);
    assert.deepEqual(values.get('network').sshHostKeys,[]);
  });
});
test('public SSH host-key lookup rechecks ownership and never serves fenced keys',async()=>{
  const publicKey='ssh-ed25519 '+'x'.repeat(44);
  const saved={instanceId:instance,locationId:7,generation:3,fingerprint,sshHostKeys:[publicKey]};
  await registry(server,async()=>{
    const {object:obj}=object(saved);
    const result=await obj.fetch(new Request('https://internal/host-keys'));
    assert.equal(result.status,200);assert.deepEqual(await result.json(),{locationId:7,host:'store-7-admin.deckers.gy',hostKeys:[publicKey]});
  });
  await registry({...server,role:'FENCED'},async()=>{
    const {object:obj}=object(saved);
    assert.equal((await obj.fetch(new Request('https://internal/host-keys'))).status,403);
  });
});
test('an administrator policy without MFA cannot create or modify a connector',async()=>{
  const {object:obj}=object();obj.env={...env,ADMIN_ACCESS_POLICY_ID:'policy',ADMIN_ACCESS_TEAM:'deckers'};
  obj.api=async(path,method)=>{assert.equal(method,undefined);return {decision:'allow',include:[{email:{email:'fixture@example.test'}}],require:[]};};
  await assert.rejects(()=>obj.remote({},names(7),server));
});
