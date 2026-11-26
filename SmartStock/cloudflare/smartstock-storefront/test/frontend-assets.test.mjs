import test from 'node:test';
import assert from 'node:assert/strict';
import {withFrontendAssets} from '../src/frontend-assets.js';
const url='https://www.deckers.gy',script='/shop/assets/index-current.js';
test('published assets bypass an offline store',async()=>{
 const worker=withFrontendAssets({fetch(){throw Error('Store must not be called');}},script);
 const response=await worker.fetch(new Request(url+script),{FRONTEND_ASSETS:{fetch:()=>new Response('current')}});
 assert.equal(await response.text(),'current');
});
test('unknown assets retain the existing gateway fallback',async()=>{
 const worker=withFrontendAssets({fetch:()=>new Response('origin asset')},script);
 const response=await worker.fetch(new Request(url+'/shop/assets/older.js'),{FRONTEND_ASSETS:{fetch:()=>new Response('',{status:404})}});
 assert.equal(await response.text(),'origin asset');
});
test('HTML keeps server content and references the current script',async()=>{
 const worker=withFrontendAssets({fetch:()=>new Response('<title>Shirts</title><script src="/shop/assets/index-old.js"></script>',{headers:{'Content-Type':'text/html',ETag:'old'}})},script);
 const response=await worker.fetch(new Request(url+'/shop/services/custom-t-shirts'),{});
 assert.ok((await response.text()).includes(script));assert.equal(response.headers.get('etag'),null);assert.equal(response.headers.get('cache-control'),'no-store');
});
test('offline HTML loads the published shell',async()=>{
 const worker=withFrontendAssets({fetch:()=>new Response('offline',{status:503})},script);
 const response=await worker.fetch(new Request(url+'/shop/services/custom-t-shirts',{headers:{Accept:'text/html'}}),{FRONTEND_ASSETS:{fetch:request=>{assert.equal(new URL(request.url).pathname,'/shop/');return new Response('shell',{headers:{'Content-Type':'text/html'}});}}});
 assert.equal(response.status,200);assert.equal(await response.text(),'shell');
});
test('API and private endpoints preserve status even when requesting HTML',async()=>{
 const worker=withFrontendAssets({fetch:()=>new Response('offline',{status:503})},script);
 for(const path of ['/shop/api/v1/catalog','/shop/internal/sync','/shop/studio-photo-queue']){
  const response=await worker.fetch(new Request(url+path,{headers:{Accept:'text/html'}}),{FRONTEND_ASSETS:{fetch(){throw Error('Must not replace API/private response');}}});
  assert.equal(response.status,503);
 }
});
test('script paths must be published storefront assets',()=>{
 assert.throws(()=>withFrontendAssets({},'https://other.example/script.js'));
});
