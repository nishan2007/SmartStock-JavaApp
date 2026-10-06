import {instanceId,originSecrets} from './origin-fixture.mjs';
import test from 'node:test';
import assert from 'node:assert/strict';
import worker,{readBoundedBody,originHealth,selectedStoreId} from '../src/index.js';

test('clean project paths select their own store at the gateway',()=>{
  const url=new URL('https://shop.test/shop/projects/2/00000000-0000-0000-0000-000000000001');
  assert.equal(selectedStoreId({},url,[{storeId:1},{storeId:2}]),2);
});

test('body limits count UTF-8 bytes rather than characters',async()=>{
  await assert.rejects(readBoundedBody(new Request('https://shop.test',{method:'POST',body:'日'.repeat(4)}),10),e=>e.status===413);
  assert.equal(await readBoundedBody(new Request('https://shop.test',{method:'POST',body:'日'.repeat(3)}),9),'日日日');
});
test('chunked uploads are cancelled immediately when their byte budget is exceeded',async()=>{
  let cancelled=false,reads=0;
  const body=new ReadableStream({pull(controller){reads++;controller.enqueue(new Uint8Array(8));},cancel(){cancelled=true;}});
  await assert.rejects(readBoundedBody(new Request('https://shop.test',{method:'POST',body,duplex:'half'}),10),e=>e.status===413);
  assert.equal(cancelled,true);assert.ok(reads<=3);
});
test('malformed JSON and invalid encodings fail as client errors before contacting stores',async()=>{
  const env={ORIGINS_JSON:JSON.stringify([{storeId:1,instanceId:instanceId(1),url:'https://one.test'}])};
  for(const body of ['{','null','[]',new Uint8Array([0xff])]){
    const response=await worker.fetch(new Request('https://shop.test/shop/api/v1/catalog',{method:'POST',headers:{Origin:'https://shop.test','X-Storefront-Request':'same-origin'},body}),env);
    assert.equal(response.status,400);
  }
});
test('backup eligibility requires the selected store snapshot, not another store sync time',()=>{
  const origin={storeId:2,instanceId:instanceId(2)},base={ok:true,version:1,instanceId:instanceId(2),locationId:2};
  assert.equal(originHealth(origin,1,{...base,snapshots:{2:100}},5,200).ok,false);
  assert.equal(originHealth(origin,1,{...base,snapshots:{1:100}},5,200).ok,true);
  assert.equal(originHealth(origin,1,{...base,snapshots:{1:90000}},5,200).ok,false);
  assert.equal(originHealth(origin,1,{...base,version:2,snapshots:{1:100}},5,200).ok,false);
  assert.equal(originHealth(origin,1,{...base,ok:false,snapshots:{1:100}},5,200).ok,false);
  assert.equal(originHealth({storeId:1,instanceId:instanceId(1)},1,{ok:true,version:1,instanceId:instanceId(1),locationId:1},5,200).ok,true);
});

test('apex navigation redirects to www without requiring an available store',async()=>{
 const env={REDIRECT_HOST:'deckers.gy',CANONICAL_HOST:'www.deckers.gy',ORIGINS_JSON:'[]'};
 const response=await worker.fetch(new Request('https://deckers.gy/shop/?category=Home'),env);
 assert.equal(response.status,308);
 assert.equal(response.headers.get('Location'),'https://www.deckers.gy/shop/?category=Home');
 const post=await worker.fetch(new Request('https://deckers.gy/shop/api/v1/checkout',{method:'POST',body:'{}'}),env);
 assert.equal(post.status,421);
 assert.equal(post.headers.get('Location'),null);
});

test('disabled ordering leaves the store available for browsing and branding',async()=>{
 const env={ORIGINS_JSON:JSON.stringify([{storeId:1,instanceId:instanceId(1),url:'https://one.test'}]),STORES:{idFromName:id=>id,get:()=>({fetch:async()=>Response.json({store:{id:1,name:'Store one'},enabled:false,capturedAt:'2026-09-27T00:00:00Z'})})}};
 const response=await worker.fetch(new Request('https://shop.test/shop/api/v1/stores',{method:'POST',body:'{}'}),env);
 assert.equal(response.status,200);
 assert.deepEqual((await response.json()).stores,[{id:1,name:'Store one',capturedAt:'2026-09-27T00:00:00Z'}]);
});

test('plain HTTP navigation upgrades to HTTPS while preserving its destination',async()=>{
 const response=await worker.fetch(new Request('http://www.deckers.gy/shop/?category=Home'),{CANONICAL_HOST:'www.deckers.gy'});
 assert.equal(response.status,308);
 assert.equal(response.headers.get('Location'),'https://www.deckers.gy/shop/?category=Home');
});

test('browse-only mode blocks every customer API route before contacting an origin',async()=>{
 const env={BROWSE_ONLY:'true',ORIGINS_JSON:JSON.stringify([{storeId:1,instanceId:instanceId(1),url:'https://one.test'}])};
 for(const route of ['auth/session','auth/activate','account','favorite','favorite-state','quote','checkout','custom-quote','custom-quote-file','quote-template','proof-file','proof-decision']){
  const response=await worker.fetch(new Request('https://shop.test/shop/api/v1/'+route,{method:'POST',headers:{Origin:'https://shop.test','X-Storefront-Request':'same-origin'},body:JSON.stringify({storeId:1})}),env);
  assert.equal(response.status,503,route);
 }
});

test('missing browse-only setting fails closed for customer routes',async()=>{
 const env={ORIGINS_JSON:JSON.stringify([{storeId:1,instanceId:instanceId(1),url:'https://one.test'}])};
 const response=await worker.fetch(new Request('https://shop.test/shop/api/v1/auth/session',{method:'POST',headers:{Origin:'https://shop.test','X-Storefront-Request':'same-origin'},body:'{"storeId":1}'}),env);
 assert.equal(response.status,503);
});

test('browse-only catalog keeps discovery data but removes ordering eligibility',async()=>{
 const previous=globalThis.fetch,calls=[];
 const env={BROWSE_ONLY:'true',ORIGINS_JSON:JSON.stringify([{storeId:1,instanceId:instanceId(1),url:'https://one.test'}]),ORIGIN_KEYS_JSON:originSecrets};
 globalThis.fetch=async input=>{const url=String(input);calls.push(url);
  if(url.endsWith('/shop/health'))return Response.json({ok:true,version:1,instanceId:instanceId(1),locationId:1,snapshots:{}});
  if(url.endsWith('/shop/api/v1/catalog'))return Response.json({store:{id:1,name:'Skeldon'},settings:{enabled:true},campaign:{topic:'3D Printing',steps:'Upload your design'},products:[{id:7,name:'Paper',canOrder:true}],projects:[{id:'project'}]});
  throw Error('Unexpected origin request');
 };
 try{
  const response=await worker.fetch(new Request('https://shop.test/shop/api/v1/catalog',{method:'POST',headers:{Origin:'https://shop.test','X-Storefront-Request':'same-origin'},body:'{"storeId":1}'}),env);
  assert.equal(response.status,200);
  const catalog=await response.json();
  assert.equal(catalog.browseOnly,true);
  assert.equal(catalog.settings.enabled,false);
  assert.equal(catalog.products[0].canOrder,false);
  assert.equal(catalog.campaign.primary,'Find your store');
  assert.equal(catalog.campaign.steps.includes('Upload'),false);
  assert.deepEqual(catalog.projects,[{id:'project'}]);
  assert.equal(calls.length,2);
 }finally{globalThis.fetch=previous;}
});
test('custom requests and proofs go only to their selected store, never a backup',async()=>{
 const old=globalThis.fetch,calls=[];
 const env={BROWSE_ONLY:'false',ORIGINS_JSON:JSON.stringify([{storeId:1,instanceId:instanceId(1),url:'https://one.test'},{storeId:2,instanceId:instanceId(2),url:'https://two.test'}]),ORIGIN_KEYS_JSON:originSecrets};
 globalThis.fetch=async(input)=>{const url=String(input);calls.push(url);
   if(url==='https://one.test/shop/health')return Response.json({ok:false,version:1,locationId:1,instanceId:instanceId(1),snapshots:{}});
   if(url==='https://two.test/shop/health')return Response.json({ok:true,version:1,locationId:2,instanceId:instanceId(2),snapshots:{1:Date.now()}});
   throw Error('Custom request was forwarded to an origin');
 };
 try{
   for(const route of ['custom-quote','custom-quote-file','proof-file','proof-decision','favorite','favorite-state']){
     const response=await worker.fetch(new Request('https://shop.test/shop/api/v1/'+route,{method:'POST',headers:{Origin:'https://shop.test','X-Storefront-Request':'same-origin'},body:JSON.stringify({storeId:1,requestId:'test'})}),env);
     assert.equal(response.status,503);
   }
   assert.equal(calls.length,12);
 }finally{globalThis.fetch=old;}
});
test('oversized custom artwork is rejected before contacting a store',async()=>{
 const env={BROWSE_ONLY:'false',ORIGINS_JSON:JSON.stringify([{storeId:1,instanceId:instanceId(1),url:'https://one.test'}])};
 const response=await worker.fetch(new Request('https://shop.test/shop/api/v1/custom-quote-file',{method:'POST',headers:{Origin:'https://shop.test','X-Storefront-Request':'same-origin','Content-Length':'6100001'},body:'{}'}),env);
 assert.equal(response.status,413);
});
