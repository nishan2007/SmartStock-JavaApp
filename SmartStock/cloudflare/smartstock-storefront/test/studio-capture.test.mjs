import test from 'node:test';
import assert from 'node:assert/strict';
import worker from '../src/index.js';
import {StudioCapture,sha256} from '../src/studio-capture.js';
import {instanceId,originSecrets} from './origin-fixture.mjs';
function fixture(){
  const objects=new Map();
  return {objects,env:{ORIGINS_JSON:JSON.stringify([{storeId:1,instanceId:instanceId(1),url:'https://one.test'},{storeId:2,instanceId:instanceId(2),url:'https://two.test'}]),ORIGIN_KEYS_JSON:originSecrets,
    CAPTURES:{idFromName:name=>name,get(name){if(!objects.has(name)){
      const values=new Map();const storage={async get(key){return structuredClone(values.get(key));},async put(key,value){values.set(key,structuredClone(value));},async setAlarm(time){this.alarm=time;},async deleteAll(){values.clear();},async transaction(fn){return fn(this);}};
      const object=new StudioCapture({storage});objects.set(name,{object,storage,values});
    }return objects.get(name).object;}}}};
}
const id='00000000-0000-4000-8000-000000000099',token='A'.repeat(43);
async function internal(env,action,owner='device:session:1',store=1,extra={}){
 return worker.fetch(new Request('https://shop.test/shop/internal/studio-capture',{method:'POST',headers:{'X-Storefront-Instance':instanceId(store),'X-Storefront-Key':JSON.parse(originSecrets)[instanceId(store)]},body:JSON.stringify({id,action,owner,...extra})}),env);
}
function upload(env,bytes=new Uint8Array([255,216,0,255,217]),credential=token){
 return worker.fetch(new Request('https://shop.test/shop/studio-capture/upload?id='+id+'&instance='+instanceId(1),{method:'POST',headers:{Origin:'https://shop.test',Authorization:'Bearer '+credential,'Content-Type':'image/jpeg'},body:bytes}),env);
}
test('authenticated store creates capture; phone can upload once; only owning desktop session reads and acknowledges',async()=>{
 const {env,objects}=fixture();const created=await internal(env,'CREATE',undefined,1,{tokenHash:await sha256(token)});assert.equal(created.status,200);
 assert.equal((await internal(env,'POLL','another-session')).status,403);
 assert.equal((await internal(env,'POLL',undefined,2)).status,410);
 assert.equal((await upload(env,undefined,'B'.repeat(43))).status,403);
 assert.equal((await internal(env,'POLL')).status,200);
 assert.equal((await upload(env)).status,200);assert.equal((await upload(env)).status,409);
 const photo=await(await internal(env,'POLL')).json();assert.equal(photo.uploaded,true);assert.equal(photo.bytesBase64,Buffer.from([255,216,0,255,217]).toString('base64'));
 assert.equal((await internal(env,'ACK')).status,200);assert.equal((await internal(env,'POLL')).status,410);
 assert.equal(objects.get(instanceId(1)+':'+id).values.size,0);
});
test('relay rejects unsigned creation, cross-origin uploads, malformed and oversized photos',async()=>{
 const {env}=fixture();
 assert.equal((await worker.fetch(new Request('https://shop.test/shop/internal/studio-capture',{method:'POST',body:'{}'}),env)).status,403);
 await internal(env,'CREATE',undefined,1,{tokenHash:await sha256(token)});
 assert.equal((await upload(env,new Uint8Array([1,2,3,4]))).status,400);
 assert.equal((await upload(env,new Uint8Array(2*1024*1024+1))).status,413);
 const badOrigin=new Request('https://shop.test/shop/studio-capture/upload?id='+id+'&instance='+instanceId(1),{method:'POST',headers:{Origin:'https://evil.test'},body:'x'});
 assert.equal((await worker.fetch(badOrigin,env)).status,403);
 assert.equal((await upload(env)).status,200);
});
test('expiry, cancellation and alarm cleanup remove photos and revoke links',async()=>{
 for(const mode of ['expiry','cancel','alarm']){
  const {env,objects}=fixture();await internal(env,'CREATE',undefined,1,{tokenHash:await sha256(token)});await upload(env);
  const entry=objects.get(instanceId(1)+':'+id);assert.ok(entry.storage.alarm>Date.now());
  if(mode==='expiry'){const session=entry.values.get('session');session.expires=Date.now()-1;entry.values.set('session',session);}
  if(mode==='cancel')await internal(env,'CANCEL');if(mode==='alarm')await entry.object.alarm();
  assert.equal((await internal(env,'POLL')).status,410);assert.equal(entry.values.size,0);assert.equal((await upload(env)).status,410);
 }
});
test('capture page remains available in browse-only mode with private-link and frame protections',async()=>{
 const {env}=fixture();env.BROWSE_ONLY='true';
 const response=await worker.fetch(new Request('https://shop.test/shop/studio-capture'),env);
 assert.equal(response.status,200);assert.equal(response.headers.get('Referrer-Policy'),'no-referrer');
 assert.match(response.headers.get('Content-Security-Policy'),/frame-ancestors 'none'/);
 const html=await response.text();assert.match(html,/location.hash/);assert.match(html,/history.replaceState/);assert.match(html,/capture="environment"/);
});
