import test from 'node:test';
import assert from 'node:assert/strict';
import worker,{configuredOrigins,originHealth,StoreCoordinator} from '../src/index.js';
import {instanceId,originSecrets} from './origin-fixture.mjs';

const origins=[{storeId:1,instanceId:instanceId(1),url:'https://one.test'},{storeId:2,instanceId:instanceId(2),url:'https://two.test'}];
test('origin configuration rejects ambiguous stores, reused identities and unsafe URLs',()=>{
  for(const list of [[origins[0],origins[0]],[origins[0],{...origins[1],instanceId:instanceId(1)}],[{...origins[0],instanceId:undefined}],[{...origins[0],url:'https://user:password@one.test'}]])
    assert.throws(()=>configuredOrigins({ORIGINS_JSON:JSON.stringify(list)}));
  assert.deepEqual(configuredOrigins({ORIGINS_JSON:JSON.stringify(origins)}),origins);
});
test('a healthy server with the wrong registered identity is ineligible',()=>{
  const response={ok:true,version:1,locationId:1,instanceId:instanceId(2)};
  assert.equal(originHealth(origins[0],1,response,1).ok,false);
  assert.equal(originHealth(origins[0],1,{...response,instanceId:instanceId(1)},1).ok,true);
});
test('sync cannot impersonate another origin using its own secret or a retired identity',async()=>{
  let calls=0;const keys=JSON.parse(originSecrets);
  const env={ORIGINS_JSON:JSON.stringify(origins),ORIGIN_KEYS_JSON:originSecrets,STORES:{idFromName:x=>x,get:()=>({fetch:async()=>{calls++;return Response.json({orders:[],enrollments:[]});}})}};
  const request=(instance,key)=>new Request('https://shop.test/shop/internal/sync?storeId=1',{method:'POST',headers:{'X-Storefront-Instance':instance,'X-Storefront-Key':key},body:JSON.stringify({snapshot:{locationId:1},events:[],enrollments:[]})});
  for(const [id,key] of [[instanceId(2),keys[instanceId(2)]],[instanceId(1),keys[instanceId(2)]],[instanceId(3),keys[instanceId(1)]]]){
    assert.equal((await worker.fetch(request(id,key),env)).status,403);assert.equal(calls,0);
  }
  assert.equal((await worker.fetch(request(instanceId(1),keys[instanceId(1)]),env)).status,200);assert.ok(calls>0);
});
test('browse-only sync rejects customer data and returns public snapshots only',async()=>{
  const key=JSON.parse(originSecrets)[instanceId(1)];let calls=0,stored;
  const env={BROWSE_ONLY:'true',ORIGINS_JSON:JSON.stringify([origins[0]]),ORIGIN_KEYS_JSON:originSecrets,
    STORES:{idFromName:x=>x,get:()=>({fetch:async (request,init)=>{
      if(request instanceof Request&&new URL(request.url).pathname==='/shop/internal/sync')return new StoreCoordinator({storage:{}},env).fetch(request);
      calls++;
      if(new URL(request).pathname==='/snapshot'){stored=JSON.parse(init.body);return Response.json({ok:true});}
      return Response.json({snapshot:{locationId:1,settings:{enabled:true},store:{id:1},customers:[{email:'private@example.test'}],receipts:[{id:5}]},orders:[{id:6}],enrollments:[{id:7}]});
    }})}};
  const sync=body=>worker.fetch(new Request('https://shop.test/shop/internal/sync?storeId=1',{
    method:'POST',headers:{'X-Storefront-Instance':instanceId(1),'X-Storefront-Key':key},body:JSON.stringify(body)}),env);
  const snapshot={locationId:1,settings:{enabled:true},store:{id:1},products:[],projects:[]};
  assert.equal((await sync({snapshot:{...snapshot,customers:[]},events:[],enrollments:[]})).status,409);
  assert.equal((await sync({snapshot,events:[{payload:{location_id:1}}],enrollments:[]})).status,409);
  assert.equal(calls,0);
  const response=await sync({snapshot,events:[],enrollments:[]});
  assert.equal(response.status,200,await response.clone().text());
  assert.deepEqual(stored,{...snapshot,settings:{enabled:false}});
  const result=await response.json();
  assert.deepEqual(result.orders,[]);assert.deepEqual(result.enrollments,[]);
  assert.deepEqual(result.snapshots,[{locationId:1,settings:{enabled:false},store:{id:1}}]);
});
test('a replacement cannot declare an old instance checkout unaccepted',async()=>{
  const job={body:{orderId:'interrupted',storeId:1},attempted:[{storeId:1,instanceId:instanceId(3)}]};
  let alarm=0,calls=0;const data=new Map([['job:interrupted',job]]);
  const storage={get:async k=>data.get(k),put:async(k,v)=>data.set(k,v),list:async({prefix})=>new Map([...data].filter(([k])=>k.startsWith(prefix))),setAlarm:async t=>{alarm=t;}};
  const coordinator=new StoreCoordinator({storage},{BROWSE_ONLY:'false',ORIGINS_JSON:JSON.stringify(origins),ORIGIN_KEYS_JSON:originSecrets});
  const original=globalThis.fetch;globalThis.fetch=async()=>{calls++;return Response.json({rejected:true});};
  try{await coordinator.alarm();assert.equal(calls,0);assert.equal(job.failure,undefined);assert.ok(alarm>Date.now());}
  finally{globalThis.fetch=original;}
});
