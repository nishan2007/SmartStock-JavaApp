import {test} from 'node:test';
import assert from 'node:assert/strict';
import worker from '../src/index.mjs';
const origin='https://overseasshipping.deckers.gy';
const env={EMPLOYEE_TUNNEL_HOST:'scheduler.deckers.gy',EMPLOYEE_ORIGIN:'https://timeclock.deckers.gy'};
test('blocks unrelated hosts, register paths and payroll routes',async()=>{
  for(const url of ['https://evil.example/employees/','/v1/cash/drawer/close','/employees/api/v1/history','/employees/api/v1/employees']){
    const r=await worker.fetch(new Request(url.startsWith('https:')?url:origin+url),env);assert.equal(r.status,404);
  }
});
test('rejects foreign, absent and null origins before proxying',async()=>{
  for(const value of [undefined,'null','https://evil.example','https://overseasshipping.deckers.gy.evil.example']){
    const r=await worker.fetch(new Request(origin+'/employees/api/v1/overseas',{method:'POST',headers:value?{Origin:value}:{},body:'{}'}),env);assert.equal(r.status,403);
  }
});
test('preserves authentication and CSRF while translating only this verified site origin',async()=>{
  const original=globalThis.fetch;let calls=0;
  globalThis.fetch=async req=>{calls++;assert.equal(req.url,'https://scheduler.deckers.gy/employees/api/v1/activate');assert.equal(req.headers.get('Origin'),env.EMPLOYEE_ORIGIN);assert.equal(req.headers.get('Cookie'),'session=private');assert.equal(req.headers.get('X-CSRF-Token'),'csrf');assert.equal(req.headers.get('Forwarded'),null);assert.equal(await req.text(),'{"token":"one-use"}');return new Response('{}',{headers:{'Set-Cookie':'activated=opaque; Path=/employees; Secure; HttpOnly; SameSite=Strict'}});};
  try{const r=await worker.fetch(new Request(origin+'/employees/api/v1/activate',{method:'POST',headers:{Origin:origin,Cookie:'session=private','X-CSRF-Token':'csrf',Forwarded:'host=evil'},body:'{"token":"one-use"}'}),env);assert.equal(calls,1);assert.equal(r.headers.get('Cache-Control'),'no-store');assert.match(r.headers.get('Set-Cookie'),/HttpOnly/);}finally{globalThis.fetch=original;}
});
test('reports outdated or unavailable store backend without claiming readiness',async()=>{
  const original=globalThis.fetch;
  try{globalThis.fetch=async url=>new Response('',{status:String(url).endsWith('overseas.js')?404:200});let r=await worker.fetch(new Request(origin+'/employees/health'),env);assert.equal(r.status,503);assert.equal((await r.json()).error.code,'SERVER_UPDATE_REQUIRED');
    globalThis.fetch=async()=>new Response('{}');r=await worker.fetch(new Request(origin+'/employees/health'),env);assert.equal(r.status,200);
    globalThis.fetch=async()=>{throw Error('offline');};r=await worker.fetch(new Request(origin+'/employees/health'),env);assert.equal(r.status,503);
  }finally{globalThis.fetch=original;}
});
