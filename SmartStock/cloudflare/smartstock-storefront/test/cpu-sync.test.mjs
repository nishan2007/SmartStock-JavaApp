import test from 'node:test';
import assert from 'node:assert/strict';
import worker,{StoreCoordinator} from '../src/index.js';
import {instanceId,originSecrets} from './origin-fixture.mjs';

class Storage {
  data=new Map();writes=[];reads=[];
  async get(key){this.reads.push(key);return structuredClone(this.data.get(key));}
  async put(key,value){const entries=typeof key==='object'?Object.entries(key):[[key,value]];for(const [k,v] of entries){this.writes.push(k);this.data.set(k,structuredClone(v));}}
  async delete(key){this.data.delete(key);}
  async list({prefix}){return new Map([...this.data].filter(([key])=>key.startsWith(prefix)));}
  async transaction(callback){
    const tx=new Storage();tx.data=structuredClone(this.data);
    await callback(tx);this.data=tx.data;this.writes.push(...tx.writes);this.reads.push(...tx.reads);
  }
}
function fixture(){
  const objects=new Map(),stores=new Map();
  const env={BROWSE_ONLY:'true',ORIGINS_JSON:JSON.stringify([{storeId:1,instanceId:instanceId(1),url:'https://one.test'}]),ORIGIN_KEYS_JSON:originSecrets};
  env.STORES={idFromName:id=>id,get:id=>({fetch:(input,init)=>{
    if(!objects.has(id)){const storage=new Storage();stores.set(id,storage);objects.set(id,new StoreCoordinator({storage},env));}
    return objects.get(id).fetch(input instanceof Request?input:new Request(input,init));
  }})};
  const request=body=>new Request('https://shop.test/shop/internal/sync?storeId=1',{method:'POST',headers:{'X-Storefront-Instance':instanceId(1),'X-Storefront-Key':JSON.parse(originSecrets)[instanceId(1)]},body:JSON.stringify(body)});
  return {env,stores,request};
}
const snapshot=(capturedAt,name='Paper')=>({locationId:1,capturedAt,settings:{enabled:true},store:{id:1,name:'Store'},products:[{id:1,name}],projects:[]});
test('edge forwards the authenticated sync body without decoding it',async()=>{
  const {env,request}=fixture();const input=request({snapshot:snapshot('2026-10-04T10:00:00Z'),events:[],enrollments:[]});
  let called=false;
  env.STORES.get=id=>({fetch:async forwarded=>{called=true;assert.equal(id,'sync:1');assert.equal(forwarded,input);assert.equal(input.bodyUsed,false);return Response.json({snapshots:[],orders:[],enrollments:[]});}});
  assert.equal((await worker.fetch(input,env)).status,200);assert.equal(called,true);assert.equal(input.bodyUsed,false);
});
test('large sync survives a coordinator restart and unchanged catalogs only update freshness',async()=>{
  const {env,stores,request}=fixture();const first=snapshot('2026-10-04T10:00:00Z');first.products[0].description='日用品 🛒 '.repeat(50000);
  const sync=async value=>{const response=await worker.fetch(request({snapshot:value,events:[],enrollments:[]}),env);assert.equal(response.status,200);return response.json();};
  assert.equal((await sync(first)).snapshots[0].products[0].description,first.products[0].description);
  const storage=stores.get('1');assert.ok(storage.data.get('snapshot:parts')>1);storage.writes=[];
  const second={...first,capturedAt:'2026-10-04T10:00:15Z'};const result=await sync(second);
  assert.deepEqual(storage.writes,['snapshot:meta']);assert.equal(result.snapshots[0].capturedAt,second.capturedAt);
  const restarted=new StoreCoordinator({storage},env);
  const read=await (await restarted.fetch(new Request('https://coordinator/state?public=1'))).json();assert.equal(read.snapshot.capturedAt,second.capturedAt);
  storage.reads=[];const meta=await (await restarted.fetch(new Request('https://coordinator/meta'))).json();
  assert.equal(meta.capturedAt,second.capturedAt);assert.equal(meta.revision,undefined);assert.deepEqual(storage.reads,['snapshot:meta']);
  storage.writes=[];await sync({...first,capturedAt:'2026-10-04T09:59:00Z'});assert.deepEqual(storage.writes,[]);
  storage.writes=[];await sync({...second,products:[{id:1,name:'Changed'}],capturedAt:'2026-10-04T10:00:30Z'});assert.ok(storage.writes.includes('snapshot:part:0'));
});
test('legacy stored snapshots migrate lazily without losing catalog data',async()=>{
  const storage=new Storage();await storage.put('snapshot',snapshot('2026-10-04T10:00:00Z'));const coordinator=new StoreCoordinator({storage},{});
  assert.equal((await (await coordinator.fetch(new Request('https://coordinator/meta'))).json()).store.name,'Store');
  await coordinator.fetch(new Request('https://coordinator/snapshot',{method:'POST',body:JSON.stringify(snapshot('2026-10-04T10:00:15Z'))}));
  assert.ok(storage.data.has('snapshot:meta'));assert.equal(storage.data.has('snapshot'),false);
});
test('delegated sync retains body limits, JSON validation and instance authentication',async()=>{
  const {env,request}=fixture();
  for(const [body,status] of [['{',400],['null',400],['[]',400]]){const input=request({});input.headers.delete('Content-Length');const malformed=new Request(input,{body});assert.equal((await worker.fetch(malformed,env)).status,status);}
  const oversized=request({});oversized.headers.set('Content-Length','16000001');assert.equal((await worker.fetch(oversized,env)).status,413);
  const impostor=request({});impostor.headers.set('X-Storefront-Instance',instanceId(2));
  const direct=new StoreCoordinator({storage:new Storage()},env);assert.equal((await direct.fetch(impostor)).status,403);assert.equal(impostor.bodyUsed,false);
});
test('public state never lists or exposes private customer handoffs',async()=>{
  const storage=new Storage(),coordinator=new StoreCoordinator({storage},{});
  await coordinator.fetch(new Request('https://coordinator/snapshot',{method:'POST',body:JSON.stringify({...snapshot('2026-10-04T10:00:00Z'),customers:[{email:'private@example.test'}]})}));
  storage.list=async()=>{throw Error('Private handoffs must not be read');};
  const result=await (await coordinator.fetch(new Request('https://coordinator/state?public=1'))).json();
  assert.equal(result.snapshot.customers,undefined);assert.equal(result.snapshot.settings.enabled,false);assert.deepEqual(result.orders,[]);assert.deepEqual(result.enrollments,[]);
});
test('a failed catalog transaction retains the last acknowledged catalog and freshness',async()=>{
  const storage=new Storage(),coordinator=new StoreCoordinator({storage},{});
  const send=value=>coordinator.fetch(new Request('https://coordinator/snapshot',{method:'POST',body:JSON.stringify(value)}));
  const first=snapshot('2026-10-04T10:00:00Z');await send(first);
  const transaction=storage.transaction.bind(storage);storage.transaction=callback=>transaction(async tx=>{
    const put=tx.put.bind(tx);tx.put=async(key,value)=>{await put(key,value);if(typeof key==='object')throw Error('Simulated storage failure');};
    return callback(tx);
  });
  await assert.rejects(send(snapshot('2026-10-04T10:00:15Z','Changed')),/Simulated storage failure/);
  const state=await (await coordinator.fetch(new Request('https://coordinator/state'))).json();
  assert.deepEqual(state.snapshot,first);assert.equal(storage.data.get('snapshot:meta').capturedAt,first.capturedAt);
  storage.transaction=transaction;await send(snapshot('2026-10-04T10:00:15Z','Changed'));
  assert.equal((await (await coordinator.fetch(new Request('https://coordinator/state'))).json()).snapshot.products[0].name,'Changed');
});
