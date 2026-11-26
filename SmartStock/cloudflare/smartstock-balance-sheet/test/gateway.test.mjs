import {test} from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import worker from '../src/index.mjs';
const origin='https://balancesheet.deckers.gy';
const env={EMPLOYEE_TUNNEL_HOST:'scheduler.deckers.gy',EMPLOYEE_ORIGIN:'https://timeclock.deckers.gy'};
test('root opens the dedicated Balance Sheet workspace',async()=>{
  for(const path of ['/', '/employees','/employees/']){
    const r=await worker.fetch(new Request(origin+path),env);assert.equal(r.status,302);assert.equal(r.headers.get('Location'),origin+'/employees/balance-sheet.html');
  }
});
test('only Balance Sheet, activation and authentication routes are exposed',async()=>{
  for(const path of ['/v1/accounting/balance-sheet/update','/employees/api/v1/history','/employees/api/v1/employees','/employees/api/v1/orders/dashboard','/employees/api/v1/inventory/search','/employees/api/v1/overseas','/employees/app.js','/employees/index.html'])
    assert.equal((await worker.fetch(new Request(origin+path,{method:'POST',body:'{}'}),env)).status,404,path);
  assert.equal((await worker.fetch(new Request('https://evil.example/employees/balance-sheet.html'),env)).status,404);
});
test('missing, null, deceptive and foreign origins are rejected',async()=>{
  for(const value of [undefined,'null','https://evil.example',origin+'.evil.example']){
    const r=await worker.fetch(new Request(origin+'/employees/api/v1/balance-sheet',{method:'POST',headers:value?{Origin:value}:{},body:'{}'}),env);assert.equal(r.status,403);
  }
  assert.equal((await worker.fetch(new Request(origin+'/employees/api/v1/balance-sheet'),env)).status,405);
});
test('forwards cookies and CSRF only after checking the exact public origin',async()=>{
  const previous=globalThis.fetch;
  globalThis.fetch=async request=>{
    assert.equal(request.url,'https://scheduler.deckers.gy/employees/api/v1/balance-sheet');
    assert.equal(request.headers.get('Origin'),env.EMPLOYEE_ORIGIN);assert.equal(request.headers.get('Cookie'),'ss_employee=opaque');
    assert.equal(request.headers.get('X-CSRF-Token'),'csrf');assert.equal(request.headers.get('Forwarded'),null);
    assert.equal(request.headers.get('X-Forwarded-Host'),null);assert.equal(request.headers.get('X-Forwarded-For'),null);
    assert.equal(await request.text(),'{"action":"POLL"}');
    return Response.json({ok:true},{headers:{'Set-Cookie':'ss_employee=opaque; Path=/employees; Secure; HttpOnly; SameSite=Strict'}});
  };
  try{
    const r=await worker.fetch(new Request(origin+'/employees/api/v1/balance-sheet',{method:'POST',headers:{Origin:origin,Cookie:'ss_employee=opaque','X-CSRF-Token':'csrf',Forwarded:'host=evil','X-Forwarded-Host':'evil','X-Forwarded-For':'evil'},body:'{"action":"POLL"}'}),env);
    assert.equal(r.status,200);assert.equal(r.headers.get('Cache-Control'),'no-store');assert.match(r.headers.get('Set-Cookie'),/HttpOnly/);
  }finally{globalThis.fetch=previous;}
});
test('all dedicated assets and icons are served without Time Clock metadata',async()=>{
  const files=['balance-sheet.html','balance-sheet.css','balance-sheet.js','balance-sheet.webmanifest','icons/balance-sheet.svg','icons/balance-sheet-180.png','icons/balance-sheet-192.png','icons/balance-sheet-512.png'];
  for(const file of files){
    const r=await worker.fetch(new Request(origin+'/employees/'+file),{...env,ASSETS:{fetch:async request=>{assert.equal(request.url,origin+'/'+file);return new Response('asset');}}});
    assert.equal(r.status,200);assert.equal(r.headers.get('X-Frame-Options'),'DENY');if(file.endsWith('.webmanifest'))assert.equal(r.headers.get('Content-Type'),'application/manifest+json');
  }
  const html=await readFile(new URL('../../../src/employee-web/balance-sheet.html',import.meta.url),'utf8');
  assert.match(html,/balance-sheet.webmanifest/);assert.match(html,/balance-sheet-180.png/);assert.doesNotMatch(html,/Time Clock/);
  assert.equal((await worker.fetch(new Request(origin+'/employees/icons/timeclock-192.png'),env)).status,404);
});
test('health distinguishes an old server and an unavailable store',async()=>{
  const previous=globalThis.fetch;
  try{
    globalThis.fetch=async()=>new Response('',{status:404});let r=await worker.fetch(new Request(origin+'/employees/health'),env);assert.equal(r.status,503);assert.equal((await r.json()).error.code,'SERVER_UPDATE_REQUIRED');
    globalThis.fetch=async()=>{throw Error('offline');};r=await worker.fetch(new Request(origin+'/employees/health'),env);assert.equal((await r.json()).error.code,'STORE_UNAVAILABLE');
    globalThis.fetch=async url=>String(url).endsWith('.html')?new Response('<html></html>',{headers:{'Content-Type':'text/html'}}):Response.json({ok:true});r=await worker.fetch(new Request(origin+'/employees/health'),env);assert.equal(r.status,200);
  }finally{globalThis.fetch=previous;}
});
test('upstream auth denial is retained and unsupported balance sheet APIs report update required',async()=>{
  const previous=globalThis.fetch;
  try{
    globalThis.fetch=async()=>Response.json({ok:false,error:{code:'DEVICE_REQUIRED'}},{status:403});
    const request=()=>new Request(origin+'/employees/api/v1/balance-sheet',{method:'POST',headers:{Origin:origin},body:'{}'});
    let r=await worker.fetch(request(),env);assert.equal(r.status,403);assert.equal((await r.json()).error.code,'DEVICE_REQUIRED');
    globalThis.fetch=async()=>new Response('',{status:404});r=await worker.fetch(request(),env);assert.equal((await r.json()).error.code,'SERVER_UPDATE_REQUIRED');
  }finally{globalThis.fetch=previous;}
});
