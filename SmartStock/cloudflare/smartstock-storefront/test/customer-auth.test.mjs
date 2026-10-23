import test from 'node:test';
import assert from 'node:assert/strict';
import {identityOperation,rankIdentityOrigins,identityRoute} from '../src/customer-auth.js';
import worker from '../src/index.js';
import {instanceId,originSecrets} from './origin-fixture.mjs';

const email='a'.repeat(64),other='b'.repeat(64),phone='c'.repeat(64),payload='encrypted-envelope'.repeat(4);
function storage(){const values=new Map();return {async list({prefix}){return new Map([...values].filter(([k])=>k.startsWith(prefix)));},async get(key){return values.get(key);},async put(key,value){if(typeof key==='object')for(const [k,v] of Object.entries(key))values.set(k,structuredClone(v));else values.set(key,structuredClone(value));},values};}
const body=(emailKey,revision,phoneKey=phone)=>({action:'WRITE',emailKey,revision,phoneKey,payload});
test('concurrent email or phone claims cannot both commit',async()=>{
  const s=storage();assert.equal((await identityOperation(s,body(email,0))).status,200);assert.equal((await identityOperation(s,body(email,0))).status,409);assert.equal((await identityOperation(s,body(other,0))).status,409);
  const result=await(await identityOperation(s,{action:'READ',emailKey:email})).json();assert.equal(result.revision,1);assert.equal(result.payload,payload);assert.equal(JSON.stringify([...s.values]).includes('passwordHash'),false);
});
test('pending email challenges do not reserve a phone',async()=>{
  const s=storage();assert.equal((await identityOperation(s,body(email,0,null))).status,200);assert.equal((await identityOperation(s,body(other,0,null))).status,200);assert.equal((await identityOperation(s,body(email,1))).status,200);assert.equal((await identityOperation(s,body(other,1))).status,409);
});
test('only explicit staff transfer can revoke a previous owner and change phone ownership atomically',async()=>{
  const s=storage();await identityOperation(s,body(email,0));await identityOperation(s,body(other,0,null));
  assert.equal((await identityOperation(s,{...body(other,1),staffRelink:true})).status,409);
  assert.equal((await identityOperation(s,{...body(other,1),staffRelink:true,transfer:{emailKey:email,revision:9,payload}})).status,409);
  assert.equal((await identityOperation(s,{...body(other,1),staffRelink:true,transfer:{emailKey:email,revision:1,payload}})).status,200);
  assert.equal(s.values.get('phone:'+phone),other);assert.equal(s.values.get('identity:'+email).revision,2);assert.equal(s.values.get('identity:'+other).revision,2);
});
test('cross-server rate limits share an email and IP budget',async()=>{
  const s=storage();for(let i=0;i<20;i++)assert.equal((await(await identityOperation(s,{action:'RATE',emailKey:email,ipKey:other},1)).json()).allowed,true);
  assert.equal((await(await identityOperation(s,{action:'RATE',emailKey:email,ipKey:other},1)).json()).allowed,false);
  assert.equal((await(await identityOperation(s,{action:'RATE',emailKey:email,ipKey:other},600001)).json()).allowed,true);
});
test('identity routing chooses lowest latency among healthy servers',()=>{
  assert.deepEqual(rankIdentityOrigins([{storeId:1},{storeId:2},{storeId:3}],{1:{ok:true,latency:300},2:{ok:true,latency:20},3:{ok:false,latency:1}}).map(o=>o.storeId),[2,1]);
});
test('authentication never falls back to a server that cannot enforce launch rejection',async()=>{
  const previous=globalThis.fetch,posts=[];
  globalThis.fetch=async(url,options)=>{
    const id=url.startsWith('https://one.test')?1:2;
    if(url.endsWith('/health'))return new Response(JSON.stringify({ok:true,version:1,customerAuthVersion:id,instanceId:instanceId(id),locationId:id,snapshots:{1:Date.now()}}));
    posts.push(id);return new Response(JSON.stringify({message:'Sign in again'}),{status:401});
  };
  try{
    const env={BROWSE_ONLY:'false',ORIGIN_KEYS_JSON:originSecrets,ORIGINS_JSON:JSON.stringify([{storeId:1,instanceId:instanceId(1),url:'https://one.test'},{storeId:2,instanceId:instanceId(2),url:'https://two.test'}])};
    const response=await worker.fetch(new Request('https://shop.test/shop/api/v1/auth/login',{method:'POST',headers:{Origin:'https://shop.test','X-Storefront-Request':'same-origin'},body:JSON.stringify({storeId:1,email:'person@example.test',password:'test'})}),env);
    assert.equal(response.status,401);assert.deepEqual(posts,[2]);
  }finally{globalThis.fetch=previous;}
});
test('identity synchronization rejects unauthenticated requests',async()=>{
  const response=await identityRoute(new Request('https://shop.test/shop/internal/customer-auth?storeId=1',{method:'POST',body:'{}'}),{ORIGIN_KEYS_JSON:'{}'},[{storeId:1,instanceId:'one'}],()=>{throw Error('must not dispatch');},async()=>{throw Error('must not read');});assert.equal(response.status,403);
});
test('launch closure fences in-flight claims and settings use shared revisions',async()=>{
  const s=storage();assert.equal((await(await identityOperation(s,{action:'POLICY_READ'})).json()).allowUnreviewedLinks,true);
  await identityOperation(s,{...body(email,0,null),reviewPending:true});
  assert.equal((await identityOperation(s,{action:'POLICY_WRITE',revision:0,allowUnreviewedLinks:false})).status,200);
  assert.equal((await identityOperation(s,{action:'POLICY_WRITE',revision:0,allowUnreviewedLinks:true})).status,409);
  assert.equal((await identityOperation(s,{...body(email,1),reviewPending:true,launchClaim:true})).status,409);
  assert.equal(s.values.has('phone:'+phone),false);
});
test('review flags survive coordinator restart and clear atomically with the identity decision',async()=>{
  const s=storage();await identityOperation(s,{...body(email,0),reviewPending:true});
  assert.equal((await(await identityOperation(s,{action:'REVIEW_LIST'})).json()).records.length,1);
  await identityOperation(s,{action:'POLICY_WRITE',revision:0,allowUnreviewedLinks:false});
  // Ordinary session/password writes on existing claims still work after launch closes.
  assert.equal((await identityOperation(s,{...body(email,1),reviewPending:true})).status,200);
  assert.equal((await identityOperation(s,{...body(email,2),reviewPending:false})).status,200);
  assert.equal((await(await identityOperation(s,{action:'REVIEW_LIST'})).json()).records.length,0);
  assert.equal(s.values.get('phone:'+phone),email);
});
test('activation directory fails closed when any configured store is unavailable',async()=>{
  const previous=globalThis.fetch;
  globalThis.fetch=async url=>url.startsWith('https://one.test')?new Response(JSON.stringify({customers:[]})):new Response('',{status:503});
  try{
    const origins=[{storeId:1,instanceId:'one',url:'https://one.test'},{storeId:2,instanceId:'two',url:'https://two.test'}];
    const request=new Request('https://shop.test/shop/internal/customer-auth?storeId=1',{method:'POST',headers:{'X-Storefront-Key':'secret-one','X-Storefront-Instance':'one'}});
    const response=await identityRoute(request,{ORIGIN_KEYS_JSON:JSON.stringify({one:'secret-one',two:'secret-two'})},origins,()=>{throw Error('must not claim');},async()=>JSON.stringify({action:'LOOKUP',phone:'+5926123456',email:'person@example.test'}));
    assert.equal(response.status,503);
  }finally{globalThis.fetch=previous;}
});
test('one origin cannot impersonate another origin for identity synchronization',async()=>{
  const response=await identityRoute(new Request('https://shop.test/shop/internal/customer-auth?storeId=1',{method:'POST',headers:{'X-Storefront-Key':'other-secret','X-Storefront-Instance':'two'}}),{ORIGIN_KEYS_JSON:JSON.stringify({one:'secret',two:'other-secret'})},[{storeId:1,instanceId:'one'}],()=>{throw Error('must not dispatch');},async()=>{throw Error('must not read');});
  assert.equal(response.status,403);
});
