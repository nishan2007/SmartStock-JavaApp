import test from 'node:test';
import assert from 'node:assert/strict';
import worker from '../src/index.js';
import {instanceId,originSecrets} from './origin-fixture.mjs';
const env={BROWSE_ONLY:'true',ORIGINS_JSON:JSON.stringify([{storeId:1,instanceId:instanceId(1),url:'https://one.test'},{storeId:2,instanceId:instanceId(2),url:'https://two.test'}]),ORIGIN_KEYS_JSON:originSecrets};
const id='00000000-0000-4000-8000-000000000099',token='A'.repeat(43),photo=new Uint8Array([255,216,0,255,217]);
function upload({instance=instanceId(2),origin='https://shop.test',authorization='Bearer '+token,body=photo,name='Person 2'}={}){
 return new Request('https://shop.test/shop/studio-photo-queue/upload?'+new URLSearchParams({instance,uploadId:id,name}),{method:'POST',headers:{Origin:origin,Authorization:authorization,'Content-Type':'image/jpeg'},body});
}
test('queue upload goes only to the selected store server and carries no browser cookies',async t=>{
 let calls=0;
 t.mock.method(globalThis,'fetch',async(url,options)=>{
  calls++;assert.equal(url.origin,'https://two.test');assert.equal(url.pathname,'/shop/internal/studio-photo-queue');assert.equal(url.searchParams.get('name'),'Person 2');assert.equal(url.searchParams.get('uploadId'),id);
  assert.equal(options.headers['X-Storefront-Key'],JSON.parse(originSecrets)[instanceId(2)]);assert.equal(options.headers['X-Storefront-Instance'],instanceId(2));assert.equal(options.headers.Authorization,'Bearer '+token);assert.equal(options.headers.Cookie,undefined);assert.equal(options.redirect,'manual');assert.deepEqual(options.body,photo);return Response.json({id,queued:true});
 });
 const response=await worker.fetch(upload(),env);assert.equal(response.status,200);assert.equal(response.headers.get('Cache-Control'),'no-store');assert.equal(calls,1);assert.equal((await response.json()).queued,true);
});
test('offline store fails without forwarding a personal photo to a backup',async t=>{
 let calls=0;t.mock.method(globalThis,'fetch',async()=>{calls++;throw Error('offline');});
 assert.equal((await worker.fetch(upload(),env)).status,503);assert.equal(calls,1);
});
test('origin redirects are rejected without forwarding photos or credentials elsewhere',async t=>{
 let calls=0;t.mock.method(globalThis,'fetch',async(url,options)=>{
  calls++;assert.equal(url.origin,'https://two.test');assert.equal(options.redirect,'manual');
  return new Response(null,{status:307,headers:{Location:'https://other.test/upload'}});
 });
 const response=await worker.fetch(upload(),env);assert.equal(response.status,503);assert.equal(calls,1);assert.equal(response.headers.get('Location'),null);
});
test('invalid links, cross-origin requests and oversized photos never reach the origin',async t=>{
 let calls=0;t.mock.method(globalThis,'fetch',async()=>{calls++;throw Error('should not run');});
 assert.equal((await worker.fetch(upload({origin:'https://evil.test'}),env)).status,403);
 assert.equal((await worker.fetch(upload({authorization:'wrong'}),env)).status,400);
 assert.equal((await worker.fetch(upload({instance:instanceId(9)}),env)).status,400);
 assert.equal((await worker.fetch(upload({body:new Uint8Array(2*1024*1024+1)}),env)).status,413);
 assert.equal((await worker.fetch(upload({name:'x'.repeat(81)}),env)).status,400);assert.equal(calls,0);
});
test('queue page supports repeated uploads without a template and never lists personal photos publicly',async()=>{
 const response=await worker.fetch(new Request('https://shop.test/shop/studio-photo-queue'),env);assert.equal(response.status,200);assert.equal(response.headers.get('Referrer-Policy'),'no-referrer');
 const html=await response.text();assert.match(html,/24 hours/);assert.match(html,/12 hours/);assert.match(html,/crypto.randomUUID/);assert.match(html,/location.hash/);assert.match(html,/history.replaceState/);
 const internal=await worker.fetch(new Request('https://shop.test/shop/internal/studio-photo-queue'),env);assert.equal(internal.status,404);
});

test('dedicated photo hostname serves uploader at root and exposes no shop or private routes',async()=>{
 const photoEnv={...env,PHOTO_HOST:'pic.deckers.gy'};
 const response=await worker.fetch(new Request('https://pic.deckers.gy/'),photoEnv);assert.equal(response.status,200);assert.match(await response.text(),/localStorage/);assert.equal(response.headers.get('Referrer-Policy'),'no-referrer');
 assert.equal((await worker.fetch(new Request('https://pic.deckers.gy/shop/internal/studio-photo-queue'),photoEnv)).status,404);
 assert.equal((await worker.fetch(new Request('https://pic.deckers.gy/shop/api/catalog'),photoEnv)).status,404);
 const redirect=await worker.fetch(new Request('http://pic.deckers.gy/'),photoEnv);assert.equal(redirect.headers.get('Location'),'https://pic.deckers.gy/');
});
test('paired-phone browser remembers upload-only grants and can forget them',async()=>{
 const html=await(await worker.fetch(new Request('https://shop.test/shop/studio-photo-queue'),env)).text();
 assert.match(html,/smartstudio.photoPhones.v1/);assert.match(html,/paired staff phones stay connected/i);assert.match(html,/Forget this phone pairing/);assert.match(html,/response.status===403/);
 assert.match(html,/textContent=pair.store/);assert.doesNotMatch(html,/innerHTML/);
});
import vm from 'node:vm';
import {queuePage} from '../src/studio-photo-queue-page.js';
function pageSession(storage,hash=''){
 const elements=new Map();const element=()=>({hidden:false,disabled:false,textContent:'',value:'',options:[],append(value){this.options.push(value);}});
 for(const match of queuePage.matchAll(/id="([^"]+)"/g))elements.set(match[1],element());
 const context={window:{addEventListener(){}},clearTimeout,setTimeout,URLSearchParams,location:{hash,pathname:'/',replace(){},reload(){}},history:{replaceState(){}},localStorage:{getItem:k=>storage.get(k)||null,setItem:(k,v)=>storage.set(k,v)},document:{addEventListener(){},getElementById:id=>{if(!elements.has(id))elements.set(id,element());return elements.get(id);},createElement:()=>element()},confirm:()=>true,URL,console};
 vm.runInNewContext(queuePage.match(/<script>([\s\S]*)<\/script>/)[1],context);return {context,elements};
}
test('staff phone pairing is remembered across visits and forgetting removes it',()=>{
 const storage=new Map();let page=pageSession(storage,'#instance='+instanceId(1)+'&token='+token+'&paired=1&store=1');
 assert.equal(page.elements.get('forget').hidden,false);assert.equal(JSON.parse(storage.get('smartstudio.photoPhones.v1')).length,1);
 page=pageSession(storage);assert.equal(page.elements.get('controls').hidden,false);assert.equal(page.elements.get('forget').hidden,false);
 page.elements.get('forget').onclick();assert.equal(JSON.parse(storage.get('smartstudio.photoPhones.v1')).length,0);
 const unpaired=pageSession(storage);assert.equal(unpaired.elements.get('controls').hidden,true);assert.match(unpaired.elements.get('status').textContent,/Pair this staff phone once/);
});
test('temporary links are not silently made persistent on the phone',()=>{
 const storage=new Map(),page=pageSession(storage,'#instance='+instanceId(1)+'&token='+token);
 assert.equal(storage.size,0);assert.equal(page.elements.get('forget').hidden,true);
});

test('background removal choice reaches the store and invalid options are rejected',async t=>{
 let calls=0;t.mock.method(globalThis,'fetch',async url=>{calls++;assert.equal(url.searchParams.get('removeBackground'),'false');return Response.json({id});});
 const request=upload();const url=new URL(request.url);url.searchParams.set('removeBackground','false');
 assert.equal((await worker.fetch(new Request(url,request),env)).status,200);
 url.searchParams.set('removeBackground','invalid');assert.equal((await worker.fetch(new Request(url,upload()),env)).status,400);assert.equal(calls,1);
});


test('home-screen app can activate in its own storage using a persistent pairing link',()=>{
 const storage=new Map(),page=pageSession(storage);
 assert.equal(page.elements.get('activation').hidden,false);
 page.elements.get('pairLink').value='https://pic.deckers.gy/#instance='+instanceId(1)+'&token='+token+'&paired=1&store=1';
 page.elements.get('activate').onclick();
 assert.equal(page.elements.get('pairLink').value,'');
 const reopened=pageSession(storage);
 assert.equal(reopened.elements.get('controls').hidden,false);
 assert.equal(reopened.elements.get('forget').hidden,false);
});

test('activation rejects temporary and foreign links without saving credentials',()=>{
 for(const link of ['https://pic.deckers.gy/#instance='+instanceId(1)+'&token='+token,'https://evil.test/#instance='+instanceId(1)+'&token='+token+'&paired=1']){
  const storage=new Map(),page=pageSession(storage);page.elements.get('pairLink').value=link;
  page.elements.get('activate').onclick();assert.equal(storage.size,0);
  assert.match(page.elements.get('status').textContent,/Paste the full private link/);
 }
});


test('PICS install assets have standalone launch metadata and real PNG icons',async()=>{
 const photoEnv={...env,PHOTO_HOST:'pic.deckers.gy'};
 const get=path=>worker.fetch(new Request('https://pic.deckers.gy'+path),photoEnv);
 const manifest=await(await get('/photo.webmanifest')).json();assert.equal(manifest.display,'standalone');assert.equal(manifest.start_url,'/');
 for(const size of [180,192,512]){const response=await get('/photo-icon-'+size+'.png');assert.equal(response.headers.get('Content-Type'),'image/png');const bytes=new Uint8Array(await response.arrayBuffer());assert.deepEqual([...bytes.slice(0,8)],[137,80,78,71,13,10,26,10]);}
 const response=await get('/');assert.match(response.headers.get('Content-Security-Policy'),/manifest-src 'self'/);const html=await response.text();assert.match(html,/apple-touch-icon/);assert.match(html,/Scan pairing QR code/);
 assert.equal((await get('/photo-qr.js')).status,200);
});

test('QR camera denial leaves pairing storage untouched and offers manual activation',async()=>{
 const storage=new Map(),page=pageSession(storage);page.context.navigator={mediaDevices:{getUserMedia:async()=>{const e=Error();e.name='NotAllowedError';throw e;}}};
 await page.elements.get('scan').onclick();assert.equal(storage.size,0);assert.equal(page.elements.get('scan').disabled,false);assert.match(page.elements.get('status').textContent,/Camera access was denied/);
});


test('QR scan activates app storage and stops camera tracks',async()=>{
 const storage=new Map(),page=pageSession(storage);let stopped=0;
 page.context.navigator={mediaDevices:{getUserMedia:async()=>({getTracks:()=>[{stop(){stopped++;}}]})}};
 Object.assign(page.elements.get('scanVideo'),{play:async()=>{},readyState:2,videoWidth:640,videoHeight:480});
 page.context.document.createElement=()=>({getContext:()=>({drawImage(){},getImageData:()=>({data:new Uint8ClampedArray(4),width:1,height:1})})});
 page.context.jsQR=()=>({data:'https://pic.deckers.gy/#instance='+instanceId(1)+'&token='+token+'&paired=1'});
 await page.elements.get('scan').onclick();assert.equal(stopped,1);assert.equal(JSON.parse(storage.get('smartstudio.photoPhones.v1'))[0].token,token);
 assert.equal(page.elements.get('scanner').hidden,true);
});
