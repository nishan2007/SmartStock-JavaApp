import {instanceId,originSecrets} from './origin-fixture.mjs';
import test from 'node:test';
import assert from 'node:assert/strict';
import {rankOrigins,pendingQuantities,StoreCoordinator,sessionCookie} from '../src/index.js';
test('unrelated cookies cannot bypass session revocation',()=>{assert.equal(sessionCookie('analytics=changed; __Host-deckers=abc; preference=dark'),'__Host-deckers=abc');assert.equal(sessionCookie('__Host-deckers=abc'),'__Host-deckers=abc');});
test('selected store wins; unavailable origins excluded; backups prefer fresh data then latency',()=>{
 const stores=[{storeId:1},{storeId:2},{storeId:3},{storeId:4}],health={1:{ok:false},2:{ok:true,freshness:50,latency:5},3:{ok:true,freshness:60,latency:10},4:{ok:true,freshness:60,latency:1}};
 assert.deepEqual(rankOrigins(stores,1,health).map(x=>x.storeId),[4,3,2]);assert.equal(rankOrigins(stores,2,health)[0].storeId,2);
});
test('cached stock subtracts unmirrored accepted and collected orders, never double subtracts mirrored orders',()=>{
 const order=(id,status)=>({order_id:id,status,quote:{lines:[{id:10,quantity:2}]}});
 assert.deepEqual(pendingQuantities([order('a','CONFIRMED'),order('b','CONFIRMED'),order('c','COLLECTED'),order('d','CANCELLED')],{orders:[{id:'a'}]}),{10:4});
});
class Storage{data=new Map();async setAlarm(at){this.alarmAt=at;}async get(k){return structuredClone(this.data.get(k));}async put(k,v){if(typeof k==='object')for(const [key,value] of Object.entries(k))this.data.set(key,structuredClone(value));else this.data.set(k,structuredClone(v));}async delete(k){this.data.delete(k);}async list({prefix}){return new Map([...this.data].filter(([k])=>k.startsWith(prefix)));}}
test('logout revokes a session and password reset invalidates earlier sessions',async()=>{
 const c=new StoreCoordinator({storage:new Storage()},{}),call=async(path,body)=>(await c.fetch(new Request('https://coordinator/'+path,{method:'POST',body:JSON.stringify(body)}))).json();
 await call('revoke',{cookieHash:'old-cookie'});assert.equal((await call('revoked',{cookieHash:'old-cookie',id:'user',issuedAt:100})).revoked,true);
 await call('revoke',{id:'user',before:200});assert.equal((await call('revoked',{cookieHash:'another-cookie',id:'user',issuedAt:100})).revoked,true);
 assert.equal((await call('revoked',{cookieHash:'fresh-cookie',id:'user',issuedAt:200})).revoked,false);
});
test('large unicode snapshots are chunked below the KV value limit and reassemble exactly',async()=>{
 const storage=new Storage(),coordinator=new StoreCoordinator({storage},{}),snapshot={capturedAt:'2026-09-25T10:00:00Z',description:'日用品 🛒 '.repeat(30000)};
 await coordinator.fetch(new Request('https://coordinator/snapshot',{method:'POST',body:JSON.stringify(snapshot)}));
 for(const value of storage.data.values())assert.ok(new TextEncoder().encode(JSON.stringify(value)).length<128000);
 assert.deepEqual((await(await coordinator.fetch(new Request('https://coordinator/state'))).json()).snapshot,snapshot);
});
test('accepted order event resolves a checkout whose HTTP response was lost',async()=>{
 const storage=new Storage(),c=new StoreCoordinator({storage},{}),order={order_id:'retry',location_id:1,source_server:2,revision:1,status:'CONFIRMED'};
 await storage.put('job:retry',{hash:'original',body:{orderId:'retry'}});
 await c.fetch(new Request('https://coordinator/event',{method:'POST',body:JSON.stringify({source:2,order})}));
 assert.deepEqual((await storage.get('job:retry')).result,order);
});
test('older snapshots cannot replace a newer catalog',async()=>{const storage=new Storage(),coordinator=new StoreCoordinator({storage},{});for(const capturedAt of ['2026-09-25T10:00:00Z','2026-09-24T10:00:00Z'])await coordinator.fetch(new Request('https://coordinator/snapshot',{method:'POST',body:JSON.stringify({capturedAt})}));assert.equal((await (await coordinator.fetch(new Request('https://coordinator/state'))).json()).snapshot.capturedAt,'2026-09-25T10:00:00Z');});
test('only fulfillment store advances replicated lifecycle; duplicate replay is idempotent',async()=>{const storage=new Storage(),c=new StoreCoordinator({storage},{}),order={order_id:'order',location_id:1,source_server:2,revision:1,status:'CONFIRMED'};const send=(source,order)=>c.fetch(new Request('https://coordinator/event',{method:'POST',body:JSON.stringify({source,order})}));await send(2,order);await send(2,{...order,revision:3,status:'COLLECTED'});assert.equal((await storage.get('order:order')).status,'CONFIRMED');await send(1,{...order,revision:2,status:'READY'});await send(2,order);assert.equal((await storage.get('order:order')).status,'READY');});

test('handoff cannot change customer or price even with a higher revision',async()=>{
 const storage=new Storage(),c=new StoreCoordinator({storage},{}),order={order_id:'immutable',location_id:1,source_server:2,revision:1,status:'CONFIRMED',auth_id:'customer-a',total:120,quote:{lines:[{id:1,quantity:1,price:120}]}};
 const send=(source,value)=>c.fetch(new Request('https://coordinator/event',{method:'POST',body:JSON.stringify({source,order:value})}));
 assert.equal((await send(3,order)).status,403);
 assert.equal((await send(2,order)).status,200);
 assert.equal((await send(1,{...order,revision:2,auth_id:'customer-b'})).status,409);
 assert.equal((await send(1,{...order,revision:2,total:1})).status,409);
 assert.deepEqual(await storage.get('order:immutable'),order);
});

test('an abandoned checkout closes only after every attempted server durably rejects it',async()=>{
 const storage=new Storage(),env={BROWSE_ONLY:'false',ORIGINS_JSON:JSON.stringify([{storeId:1,instanceId:instanceId(1),url:'https://one.test'},{storeId:2,instanceId:instanceId(2),url:'https://two.test'}]),ORIGIN_KEYS_JSON:originSecrets},c=new StoreCoordinator({storage},env);
 await storage.put('job:lost',{hash:'hash',attempted:[{storeId:1,instanceId:instanceId(1)},{storeId:2,instanceId:instanceId(2)}],body:{orderId:'lost',storeId:1}});
 const original=globalThis.fetch;let offline=true;
 globalThis.fetch=async url=>{if(url.startsWith('https://two')&&offline)throw new Error('offline');return Response.json({rejected:true});};
 try{
   await c.alarm();assert.equal((await storage.get('job:lost')).failure,undefined);assert.ok(storage.alarmAt>Date.now());
   offline=false;await c.alarm();assert.equal((await storage.get('job:lost')).failure.checkoutClosed,true);
 }finally{globalThis.fetch=original;}
});
test('browse-only mode pauses existing checkout reconciliation without contacting origins',async()=>{
 const storage=new Storage(),c=new StoreCoordinator({storage},{BROWSE_ONLY:'true'});
 await storage.put('job:pending',{attempted:[{storeId:1}],body:{orderId:'pending',storeId:1}});
 const original=globalThis.fetch;let calls=0;globalThis.fetch=async()=>{calls++;throw Error('Unexpected origin request');};
 try{await c.alarm();assert.equal(calls,0);assert.equal((await storage.get('job:pending')).result,undefined);assert.ok(storage.alarmAt>Date.now());}
 finally{globalThis.fetch=original;}
});
test('background reconciliation recovers durable acceptance without the shopper returning',async()=>{
 const storage=new Storage(),env={BROWSE_ONLY:'false',ORIGINS_JSON:JSON.stringify([{storeId:1,instanceId:instanceId(1),url:'https://one.test'}]),ORIGIN_KEYS_JSON:originSecrets},c=new StoreCoordinator({storage},env),order={order_id:'accepted',location_id:1,status:'CONFIRMED'};
 await storage.put('job:accepted',{attempted:[{storeId:1,instanceId:instanceId(1)}],body:{orderId:'accepted',storeId:1}});
 const original=globalThis.fetch;globalThis.fetch=async()=>Response.json({order});
 try{await c.alarm();assert.deepEqual((await storage.get('job:accepted')).result,order);assert.deepEqual(await storage.get('order:accepted'),order);}
 finally{globalThis.fetch=original;}
});

test('verified enrollment handoff preserves identity and only its store resolves it',async()=>{
 const storage=new Storage(),c=new StoreCoordinator({storage},{}),enrollment={enrollment_id:'signup',auth_id:'customer',location_id:1,source_server:2,email:'customer@example.test',revision:1,status:'PENDING'};
 const send=(source,value)=>c.fetch(new Request('https://coordinator/enrollment',{method:'POST',body:JSON.stringify({source,enrollment:value})}));
 assert.equal((await send(2,enrollment)).status,200);
 await send(2,{...enrollment,revision:2,status:'LINKED'});assert.equal((await storage.get('enrollment:signup')).status,'PENDING');
 assert.equal((await send(1,{...enrollment,auth_id:'different',revision:2})).status,409);
 await send(1,{...enrollment,revision:2,status:'LINKED'});await send(2,enrollment);
 assert.equal((await storage.get('enrollment:signup')).status,'LINKED');
});
