import test from 'node:test';
import assert from 'node:assert/strict';
import {templateSubmissionRoute} from '../src/template-submit.js';
import {templateSubmissionPage} from '../src/template-submit-page.js';
const stores=[{instanceId:'instance-one',url:'https://configured-origin.example:8443'}];
const env={ORIGIN_KEYS_JSON:JSON.stringify({'instance-one':'k'.repeat(40)})};
const request=(options={})=>new Request('https://deckers.example/shop/template-submit/api?instance=instance-one',{method:'POST',headers:{Origin:'https://deckers.example','Content-Type':'application/json',...options.headers},body:options.body||JSON.stringify({action:'INFO',token:'a'.repeat(43)}),...options});
test('page has a private first-use/code form, dynamic fields, upload/camera and no persistent credentials',async()=>{
 const response=await templateSubmissionRoute(new Request('https://deckers.example/shop/template-submit'),env,stores);
 assert.equal(response.status,200);assert.equal(response.headers.get('Cache-Control'),'no-store');assert.equal(response.headers.get('Referrer-Policy'),'no-referrer');
 assert.match(response.headers.get('Content-Security-Policy'),/frame-ancestors 'none'/);
 assert.match(templateSubmissionPage,/camera.capture='user'/);assert.ok(templateSubmissionPage.includes("api(setup?'SETUP':'LOGIN'"));assert.match(templateSubmissionPage,/schema:formSchema.schema/);
 assert.doesNotMatch(templateSubmissionPage,/localStorage|sessionStorage|innerHTML|document.cookie/);
 // Parse the inline script as JavaScript without executing browser APIs.
 new Function(templateSubmissionPage.match(/<script>([\s\S]*)<\/script>/)[1]);
});
test('rejects cross-origin callers, unknown instances and oversized payloads before forwarding',async()=>{
 let calls=0;const old=globalThis.fetch;globalThis.fetch=async()=>{calls++;throw Error('not expected');};
 try{
  assert.equal((await templateSubmissionRoute(request({headers:{Origin:'https://other.example','Content-Type':'application/json'}}),env,stores)).status,403);
  assert.equal((await templateSubmissionRoute(request(),env,[])).status,400);
  assert.equal((await templateSubmissionRoute(request({body:'x'.repeat(12*1024*1024+1)}),env,stores)).status,413);
  assert.equal(calls,0);
 }finally{globalThis.fetch=old;}
});
test('gateway pins the configured origin, replaces spoofed credentials, and rejects redirects',async()=>{
 const old=globalThis.fetch;let called=false;
 globalThis.fetch=async(url,options)=>{
  called=true;assert.equal(url.origin,'https://configured-origin.example:8443');assert.equal(url.pathname,'/shop/internal/template-submit');assert.equal(options.redirect,'manual');
  assert.equal(options.headers['X-Storefront-Key'],'k'.repeat(40));assert.equal(options.headers['X-Storefront-Instance'],'instance-one');
  return new Response(null,{status:302,headers:{Location:'https://untrusted.example'}});
 };
 try{assert.equal((await templateSubmissionRoute(request(),env,stores)).status,503);assert.equal(called,true);}finally{globalThis.fetch=old;}
});
test('public responses cannot expose protected origin headers',async()=>{
 const old=globalThis.fetch;globalThis.fetch=async()=>Response.json({received:true,reference:'synthetic'},{headers:{'X-Storefront-Key':'secret','Set-Cookie':'private'}});
 try{
  const response=await templateSubmissionRoute(request(),env,stores);assert.equal(response.status,200);
  assert.equal(response.headers.get('X-Storefront-Key'),null);assert.equal(response.headers.get('Set-Cookie'),null);assert.deepEqual(await response.json(),{received:true,reference:'synthetic'});
 }finally{globalThis.fetch=old;}
});
