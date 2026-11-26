import {X509Certificate,createHash,verify} from 'node:crypto';

const paths=new Set(['/v1/provision','/v1/dns/present','/v1/dns/cleanup']);
const json=(body,status=200)=>Response.json(body,{status,headers:{'Cache-Control':'no-store','X-Content-Type-Options':'nosniff'}});
class Denied extends Error { constructor(status,message){super(message);this.status=status;} }
const reject=(status,message)=>{throw new Denied(status,message);};
export const hash=value=>createHash('sha256').update(value).digest('hex');
export const canonical=(path,time,nonce,body)=>`POST\n${path}\n${time}\n${nonce}\n${hash(body)}`;
export function privateAddress(value){
  if(typeof value!=='string'||!/^\d{1,3}(\.\d{1,3}){3}$/.test(value))return false;
  const b=value.split('.').map(Number);
  return b.every((n,i)=>n>=0&&n<=255&&String(n)===value.split('.')[i])&&
    (b[0]===10||(b[0]===172&&b[1]>=16&&b[1]<=31)||(b[0]===192&&b[1]===168));
}
export function names(locationId,domain='deckers.gy'){
  if(!Number.isSafeInteger(locationId)||locationId<=0||domain!=='deckers.gy')reject(400,'Invalid store identity.');
  return {lanHost:`store-${locationId}-lan.${domain}`,websiteHost:`store-${locationId}-web.${domain}`,adminHost:`store-${locationId}-admin.${domain}`};
}
export function hostKeys(values){
  if(!Array.isArray(values)||values.length>6||values.some(key=>typeof key!=='string'||
    ! /^(ssh-ed25519|ssh-rsa|ecdsa-sha2-nistp(?:256|384|521)) [A-Za-z0-9+/=]{40,8192}$/.test(key)))reject(400,'Invalid SSH host public keys.');
  return values;
}
export function authenticate(request,body,now=Date.now()){
  const time=request.headers.get('X-SmartStock-Time')||'',nonce=request.headers.get('X-SmartStock-Nonce')||'';
  if(!/^\d{13}$/.test(time)||Math.abs(now-Number(time))>120000||! /^[a-f0-9-]{36}$/.test(nonce))reject(401,'Expired or invalid server proof.');
  try {
    const encoded=request.headers.get('X-SmartStock-Certificate')||'';
    if(encoded.length>12000)reject(401,'Invalid server proof.');
    const cert=new X509Certificate(Buffer.from(encoded,'base64'));
    if(cert.publicKey.asymmetricKeyType!=='rsa'||cert.publicKey.asymmetricKeyDetails.modulusLength<2048)reject(401,'Invalid server key.');
    if(now<Date.parse(cert.validFrom)||now>Date.parse(cert.validTo))reject(401,'Expired server identity.');
    if(!verify('sha256',Buffer.from(canonical(new URL(request.url).pathname,time,nonce,body)),cert.publicKey,
      Buffer.from(request.headers.get('X-SmartStock-Signature')||'','base64')))reject(401,'Invalid server proof.');
    const payload=JSON.parse(body);
    if(payload.upstreamTlsName && !cert.checkHost(payload.upstreamTlsName))reject(401,'Upstream name does not match server identity.');
    return {fingerprint:hash(cert.raw),nonce,time:Number(time)};
  }catch(error){if(error instanceof Denied)throw error;reject(401,'Invalid server proof.');}
}
async function checked(response){
  if(!response.ok)throw new Error('Provisioning dependency unavailable.');
  return response.json();
}
export async function activeServer(env,payload,fingerprint){
  if(!/^[a-f0-9-]{36}$/.test(payload.serverInstanceId||''))reject(400,'Invalid server identity.');
  const origin=new URL(env.SUPABASE_URL);
  if(origin.protocol!=='https:'||origin.username||origin.password||origin.pathname!=='/')throw new Error('Invalid registry configuration.');
  const result=await checked(await fetch(new URL('/rest/v1/rpc/smartstock_server_registry',origin),{
    method:'POST',headers:{apikey:env.SUPABASE_SERVER_KEY,'Content-Type':'application/json',
      ...(env.SUPABASE_SERVER_KEY?.startsWith('eyJ')?{Authorization:`Bearer ${env.SUPABASE_SERVER_KEY}`}:{})},
    body:JSON.stringify({p_action:'LIST',p_payload:{location_id:payload.locationId}}),signal:AbortSignal.timeout(10000)}));
  const server=result.servers?.find(s=>s.serverInstanceId===payload.serverInstanceId);
  if(!server||server.locationId!==payload.locationId||server.role!=='PRIMARY'||
    server.certificateFingerprint?.toLowerCase()!==fingerprint||!Number.isSafeInteger(server.generation)||server.generation<=0)
    reject(403,'Only the registered active store server can provision network access.');
  return server;
}
export default {async fetch(request,env){
  try {
    const path=new URL(request.url).pathname;
    if(env.PROVISION_LAN!=='true')return json({error:'Automatic provisioning is not enabled.'},503);
    const directory=/^\/v1\/stores\/([1-9][0-9]{0,8})\/ssh-host-keys$/.exec(path);
    if(request.method==='GET'&&directory){
      const location=Number(directory[1]);names(location,env.BASE_DOMAIN);
      return await env.STORES.get(env.STORES.idFromName(String(location))).fetch(new Request('https://store.internal/host-keys'));
    }
    if(request.method!=='POST'||!paths.has(path))return json({error:'Not found.'},404);
    if(Number(request.headers.get('Content-Length')||0)>16384)return json({error:'Request too large.'},413);
    const reader=request.body?.getReader();let body='',length=0;
    if(!reader)reject(400,'A signed request body is required.');
    const decoder=new TextDecoder();
    for(;;){const part=await reader.read();if(part.done)break;length+=part.value.byteLength;if(length>16384){await reader.cancel();reject(413,'Request too large.');}body+=decoder.decode(part.value,{stream:true});}
    body+=decoder.decode();
    const proof=authenticate(request,body),payload=JSON.parse(body);
    names(payload.locationId,env.BASE_DOMAIN);
    // Only the Worker binding can call this object. Never forward user-supplied
    // identity headers to the object; reconstruct its trusted envelope here.
    return await env.STORES.get(env.STORES.idFromName(String(payload.locationId))).fetch(new Request('https://store.internal'+path,{
      method:'POST',body:JSON.stringify({payload,proof})}));
  }catch(error){return json({error:error instanceof Denied?error.message:'Provisioning unavailable.'},error.status||503);}
}};

export class StoreNetwork {
  constructor(state,env){this.state=state;this.env=env;this.tail=Promise.resolve();}
  fetch(request){const run=this.tail.then(()=>this.handle(request));this.tail=run.catch(()=>{});return run;}
  async api(path,method='GET',body){
    const response=await checked(await fetch('https://api.cloudflare.com/client/v4'+path,{method,
      headers:{Authorization:`Bearer ${this.env.CF_API_TOKEN}`,'Content-Type':'application/json'},
      ...(body===undefined?{}:{body:JSON.stringify(body)}),signal:AbortSignal.timeout(10000)}));
    if(!response.success)throw new Error('Network provider rejected the operation.');
    return response.result;
  }
  async dns(name,type,content,proxied=false,previous){
    const base=`/zones/${this.env.CF_ZONE_ID}/dns_records`;
    const records=await this.api(base+'?name='+encodeURIComponent(name));
    if(records.length>1)reject(409,'Conflicting hostname records require administrator review.');
    const existing=records[0];
    if(existing&&(existing.type!==type||existing.content!==content)&&
      (!previous||existing.type!==previous.type||existing.content!==previous.content))
      reject(409,'An existing hostname belongs to another configuration.');
    if(existing&&existing.type===type&&existing.content===content&&existing.proxied===proxied)return existing;
    return this.api(base+(existing?'/'+existing.id:''),existing?'PUT':'POST',{name,type,content,proxied,ttl:proxied?1:60});
  }
  async remote(record,hosts,server){
    const account=`/accounts/${this.env.CF_ACCOUNT_ID}`;
    if(!this.env.ADMIN_ACCESS_POLICY_ID||!this.env.ADMIN_ACCESS_TEAM)reject(503,'Administrator access policy must be configured first.');
    const keys=JSON.parse(this.env.ADMIN_SSH_PUBLIC_KEYS_JSON||'[]');
    if(!Array.isArray(keys)||keys.length>4||keys.some(key=>typeof key!=='string'||! /^(ssh-ed25519|ssh-rsa) [A-Za-z0-9+/=]{40,8192}$/.test(key)))
      throw new Error('Invalid approved SSH public keys.');
    // Confirm the pre-approved reusable policy exists. Never create an Everyone,
    // Bypass or service-token policy on behalf of an enrolling store server.
    const policy=await this.api(account+'/access/policies/'+this.env.ADMIN_ACCESS_POLICY_ID);
    if(policy.decision!=='allow'||!policy.include?.length||!policy.include.every(r=>r.email?.email||r.group?.id)||
      !policy.require?.some(r=>r.auth_method?.auth_method==='mfa'))reject(503,'Administrator policy must require MFA and an explicit identity group.');
    if(!record.accessApp){
      const name='SmartStock store '+server.locationId+' administration';
      const apps=await this.api(account+'/access/apps?per_page=1000');
      const matches=apps.filter(app=>app.domain===hosts.adminHost);
      if(matches.length>1)reject(409,'Duplicate administrator applications need review.');
      const app=matches[0]||await this.api(account+'/access/apps','POST',{name,
        domain:hosts.adminHost,type:'self_hosted',session_duration:'1h',
        policies:[{id:this.env.ADMIN_ACCESS_POLICY_ID}],app_launcher_visible:false});
      if(app.name!==name||app.type!=='self_hosted'||app.domain!==hosts.adminHost||!app.aud||
        app.policies?.length!==1||app.policies[0].id!==this.env.ADMIN_ACCESS_POLICY_ID)
        reject(409,'Administrator application policy requires review.');
      record.accessApp=app.id;record.accessAud=app.aud;
      await this.state.storage.put('network',record);
    }
    if(!record.tunnelId){
      // Recover an earlier create after a provider timeout using the exact name.
      const name=`smartstock-store-${server.locationId}-${server.serverInstanceId}`;
      const list=await this.api(account+'/cfd_tunnel?is_deleted=false&name='+encodeURIComponent(name));
      const matches=list.filter(t=>t.name===name);
      if(matches.length>1)reject(409,'Duplicate store tunnels need administrator review.');
      const tunnel=matches[0]||await this.api(account+'/cfd_tunnel','POST',{name,config_src:'cloudflare'});
      record.tunnelId=tunnel.id;await this.state.storage.put('network',record);
    }
    const configuration={config:{ingress:[
      {hostname:hosts.websiteHost,path:'^/employees(/.*)?$',service:'https://127.0.0.1:8450',originRequest:{
        originServerName:record.upstreamTlsName,caPool:record.caPath}},
      {hostname:hosts.adminHost,service:'ssh://127.0.0.1:22',originRequest:{access:{required:true,
        teamName:this.env.ADMIN_ACCESS_TEAM,audTag:[record.accessAud]}}},
      {service:'http_status:404'}]}};
    const configurationHash=hash(JSON.stringify(configuration));
    if(record.configurationHash!==configurationHash){
      await this.api(account+`/cfd_tunnel/${record.tunnelId}/configurations`,'PUT',configuration);
      record.configurationHash=configurationHash;await this.state.storage.put('network',record);
    }
    const content=record.tunnelId+'.cfargotunnel.com';
    for(const name of [hosts.websiteHost,hosts.adminHost])await this.dns(name,'CNAME',content,true,
      record.previousTunnel?{type:'CNAME',content:record.previousTunnel+'.cfargotunnel.com'}:undefined);
    return {tunnelId:record.tunnelId,tunnelToken:await this.api(account+`/cfd_tunnel/${record.tunnelId}/token`),sshPublicKeys:keys};
  }
  async handle(request){
    try {
      if(request.method==='GET'&&new URL(request.url).pathname==='/host-keys'){
        const saved=await this.state.storage.get('network');
        if(!saved?.sshHostKeys?.length)reject(404,'Verified SSH host keys are not available yet.');
        await activeServer(this.env,{locationId:saved.locationId,serverInstanceId:saved.instanceId},saved.fingerprint);
        return json({locationId:saved.locationId,host:names(saved.locationId,this.env.BASE_DOMAIN).adminHost,hostKeys:hostKeys(saved.sshHostKeys)});
      }
      const {payload,proof}=await request.json(),hosts=names(payload.locationId,this.env.BASE_DOMAIN);
      if(Math.abs(Date.now()-proof.time)>120000)reject(401,'Expired server proof.');
      const server=await activeServer(this.env,payload,proof.fingerprint);
      const used=await this.state.storage.get('nonces')||{};
      for(const [nonce,time] of Object.entries(used))if(time<Date.now()-240000)delete used[nonce];
      if(used[proof.nonce])reject(409,'This request was already used.');
      if(Object.keys(used).length>=1000)reject(429,'Provisioning rate limit reached.');
      used[proof.nonce]=proof.time;await this.state.storage.put('nonces',used);
      let record=await this.state.storage.get('network')||{};
      if(record.generation>server.generation)reject(403,'This server generation has been replaced.');
      if(record.instanceId&&record.instanceId!==server.serverInstanceId){
        // Fence the old connector before exposing its replacement. Existing SSH
        // sessions may require separate termination during the server handover.
        if(record.tunnelId)await this.api(`/accounts/${this.env.CF_ACCOUNT_ID}/cfd_tunnel/${record.tunnelId}/configurations`,
          'PUT',{config:{ingress:[{service:'http_status:404'}]}});
        record={...record,previousTunnel:record.tunnelId,tunnelId:null,configurationHash:null,sshHostKeys:[],caPath:null,upstreamTlsName:null};
      }
      record.instanceId=server.serverInstanceId;record.generation=server.generation;
      record.locationId=payload.locationId;record.fingerprint=proof.fingerprint;
      const path=new URL(request.url).pathname;
      if(path==='/v1/provision'){
        if(!privateAddress(payload.lanAddress))reject(400,'Select a private store LAN address.');
        await this.dns(hosts.lanHost,'A',payload.lanAddress,false,record.lanAddress?{type:'A',content:record.lanAddress}:undefined);
        record.lanAddress=payload.lanAddress;
        if(payload.sshHostKeys)record.sshHostKeys=hostKeys(payload.sshHostKeys);
        // The path is a fixed per-user installation location supplied only to the
        // verified connector configuration, never used as a command or URL.
        if(payload.caPath&&!/^(?:[A-Za-z]:[\\/]|\/)[^\r\n"\x00]{1,400}\.pem$/.test(payload.caPath))reject(400,'Invalid public CA path.');
        if(payload.caPath){
          if(typeof payload.upstreamTlsName!=='string'||! /^[A-Za-z0-9.-]{1,253}$/.test(payload.upstreamTlsName))reject(400,'Invalid upstream TLS name.');
          record.caPath=payload.caPath;record.upstreamTlsName=payload.upstreamTlsName;
        }
        await this.state.storage.put('network',record);
        let remote={remoteReady:false,serverUpdateRequired:payload.managedRouting!==true};
        if(this.env.PROVISION_REMOTE_ACCESS==='true'&&record.caPath&&payload.managedRouting===true)remote={remoteReady:true,...await this.remote(record,hosts,server)};
        return json({...hosts,generation:server.generation,...remote});
      }
      if(payload.recordName!==`_acme-challenge.${hosts.lanHost}`||! /^[A-Za-z0-9_-]{43}$/.test(payload.value||''))
        reject(403,'DNS changes are limited to this store certificate challenge.');
      const base=`/zones/${this.env.CF_ZONE_ID}/dns_records`,records=await this.api(base+'?type=TXT&name='+encodeURIComponent(payload.recordName));
      const matching=records.filter(r=>r.type==='TXT'&&r.content===payload.value);
      if(path==='/v1/dns/present'&&!matching.length)await this.api(base,'POST',{type:'TXT',name:payload.recordName,content:payload.value,ttl:60});
      if(path==='/v1/dns/cleanup')for(const item of matching)await this.api(base+'/'+item.id,'DELETE');
      return json({ok:true});
    }catch(error){return json({error:error instanceof Denied?error.message:'Provisioning unavailable.'},error.status||503);}
  }
}
