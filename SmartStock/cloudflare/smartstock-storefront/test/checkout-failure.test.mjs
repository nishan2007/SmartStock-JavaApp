import {instanceId,originSecrets} from './origin-fixture.mjs';
import test from 'node:test';
import assert from 'node:assert/strict';
import {StoreCoordinator} from '../src/index.js';

class Storage {
  data=new Map();
  async get(key){return structuredClone(this.data.get(key));}
  async put(key,value){if(typeof key==='object'){for(const [k,v] of Object.entries(key))this.data.set(k,structuredClone(v));}else this.data.set(key,structuredClone(value));}
  async delete(key){this.data.delete(key);}
  async list({prefix}){return new Map([...this.data].filter(([k])=>k.startsWith(prefix)));}
  async setAlarm(){}
}
test('lost checkout response never causes a second acceptance attempt, including after coordinator restart',async()=>{
  const storage=new Storage(),revocations=new StoreCoordinator({storage:new Storage()},{});
  const env={BROWSE_ONLY:'false',ORIGIN_KEYS_JSON:originSecrets,ORIGINS_JSON:JSON.stringify([{storeId:1,instanceId:instanceId(1),url:'https://one.test'},{storeId:2,instanceId:instanceId(2),url:'https://two.test'}]),STORES:{idFromName:x=>x,get:()=>({fetch:(url,options)=>revocations.fetch(new Request(url,options))})}};
  let coordinator=new StoreCoordinator({storage},env);
  await coordinator.fetch(new Request('https://coordinator/snapshot',{method:'POST',body:JSON.stringify({locationId:1,capturedAt:new Date().toISOString(),settings:{enabled:true},orders:[],receipts:[]})}));
  const orderId='11111111-1111-4111-8111-111111111111',attempts=[];
  const request=()=>new Request('https://shop.test/shop/api/v1/checkout',{method:'POST',headers:{Cookie:'__Host-deckers=test','X-CSRF-Token':'csrf'},body:JSON.stringify({storeId:1,orderId,lines:[{id:1,quantity:1}],expectedTotal:10})});
  const original=globalThis.fetch;
  globalThis.fetch=async(url)=>{
    if(url.endsWith('/health'))return Response.json({ok:true,version:1,instanceId:instanceId(url.startsWith('https://one')?1:2),locationId:url.startsWith('https://one')?1:2,snapshots:{1:Date.now()}});
    if(url.endsWith('/auth/session'))return Response.json({id:'customer',csrf:'csrf',issuedAt:Date.now()});
    if(url.endsWith('/checkout')){attempts.push(url);throw Error('response lost after possible commit');}
    if(url.endsWith('/resolve-command'))return Response.json({order:{order_id:orderId,location_id:1,status:'CONFIRMED',total:10}});
    throw Error('Unexpected request '+url);
  };
  try{
    assert.equal((await coordinator.fetch(request())).status,503);
    assert.deepEqual(attempts,['https://one.test/shop/api/v1/checkout']);
    assert.ok(await storage.get('jobSnapshot:'+orderId+':parts'),'Unresolved checkout retains its exact snapshot.');
    // Persisted attempt alone must protect a crash before the uncertainty flag was saved.
    const job=await storage.get('job:'+orderId);delete job.uncertain;await storage.put('job:'+orderId,job);
    coordinator=new StoreCoordinator({storage},env);
    assert.equal((await coordinator.fetch(request())).status,503);
    assert.equal(attempts.length,1);
    await coordinator.alarm();
    const recovered=await coordinator.fetch(request());assert.equal(recovered.status,200);
    assert.equal((await recovered.json()).order_id,orderId);assert.equal(attempts.length,1);
    assert.equal((await storage.list({prefix:'jobSnapshot:'})).size,0);
  }finally{globalThis.fetch=original;}
});

test('concurrent backup checkouts share commitments and retries cannot cross customer identities',async()=>{
  const storage=new Storage(),revocations=new StoreCoordinator({storage:new Storage()},{});
  const env={BROWSE_ONLY:'false',ORIGIN_KEYS_JSON:originSecrets,ORIGINS_JSON:JSON.stringify([{storeId:1,instanceId:instanceId(1),url:'https://one.test'},{storeId:2,instanceId:instanceId(2),url:'https://two.test'}]),STORES:{idFromName:x=>x,get:()=>({fetch:(url,options)=>revocations.fetch(new Request(url,options))})}};
  const coordinator=new StoreCoordinator({storage},env),accepted=[],attempts=[];
  await coordinator.fetch(new Request('https://coordinator/snapshot',{method:'POST',body:JSON.stringify({locationId:1,capturedAt:new Date().toISOString(),settings:{enabled:true},orders:[],receipts:[],products:[{id:1,available:1,price:10}]})}));
  const first='11111111-1111-4111-8111-111111111111',second='22222222-2222-4222-8222-222222222222';
  const request=(id,customer='a',quantity=1)=>new Request('https://shop.test/shop/api/v1/checkout',{method:'POST',headers:{Cookie:'__Host-deckers='+customer,'X-CSRF-Token':'csrf'},body:JSON.stringify({storeId:1,orderId:id,lines:[{id:1,quantity}],expectedTotal:10})});
  const original=globalThis.fetch;
  globalThis.fetch=async(url,options)=>{
    if(url.startsWith('https://one'))throw Error('primary offline');
    if(url.endsWith('/health'))return Response.json({ok:true,version:1,instanceId:instanceId(2),locationId:2,snapshots:{1:Date.now()}});
    if(url.endsWith('/auth/session'))return Response.json({id:options.headers.get('Cookie'),csrf:'csrf',issuedAt:Date.now()});
    if(url.endsWith('/checkout')){
      const command=JSON.parse(options.body);attempts.push(command);
      assert.equal(command.storeId,1);assert.equal(command._snapshot.locationId,1);
      if((command._pending['1']||0)+command.lines[0].quantity>1)return Response.json({message:'Insufficient stock'},{status:400});
      const order={order_id:command.orderId,location_id:1,source_server:2,status:'CONFIRMED',revision:1,total:10,quote:{lines:command.lines}};
      accepted.push(order);return Response.json(order);
    }
    throw Error('Unexpected request '+url);
  };
  try{
    const responses=await Promise.all([coordinator.fetch(request(first)),coordinator.fetch(request(second,'b'))]);
    assert.deepEqual(responses.map(x=>x.status),[200,400]);assert.equal(accepted.length,1);
    assert.equal(attempts[1]._pending['1'],1);
    const retry=await coordinator.fetch(request(first));assert.equal(retry.status,200);assert.equal((await retry.json()).order_id,first);
    assert.equal((await coordinator.fetch(request(first,'b'))).status,409);
    assert.equal((await coordinator.fetch(request(first,'a',2))).status,409);
    assert.equal(attempts.length,2,'Retries and conflicting identifiers must not reach a store checkout twice.');
    assert.equal((await storage.list({prefix:'jobSnapshot:'})).size,0,'Accepted and definitively rejected checkouts release their private snapshot copies.');
  }finally{globalThis.fetch=original;}
});
