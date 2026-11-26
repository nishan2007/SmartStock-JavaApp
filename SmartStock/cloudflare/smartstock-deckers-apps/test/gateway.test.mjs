import {test} from 'node:test';
import assert from 'node:assert/strict';
import worker from '../src/index.mjs';
const origin='https://deckersapps.deckers.gy',env={EMPLOYEE_TUNNEL_HOST:'store.example'};
test('the dedicated entry opens the shared Deckers shell and blocks unrelated paths',async()=>{
  const root=await worker.fetch(new Request(origin+'/'),env);
  assert.equal(root.status,302);assert.equal(root.headers.get('Location'),origin+'/employees/mobile-management/');
  assert.equal((await worker.fetch(new Request(origin+'/mobile-management/network-probe',{method:'POST'}),env)).status,404);
  assert.equal((await worker.fetch(new Request('https://other.example/employees/'),env)).status,404);
  assert.equal((await worker.fetch(new Request(origin+'/employees/api/v1/auth/login',{method:'POST',headers:{Origin:'https://evil.example'}}),env)).status,403);
});
test('screens and employee authentication retain the dedicated browser origin and server policy',async()=>{
  const original=globalThis.fetch;let forwarded;
  globalThis.fetch=async request=>{forwarded=request;return new Response('{"ok":true}',{headers:{'Content-Type':'application/json','Content-Security-Policy':"script-src 'self'",'Set-Cookie':'ss_employee=fixture; Path=/employees; Secure; HttpOnly; SameSite=Lax'}});};
  try {
    const response=await worker.fetch(new Request(origin+'/employees/mobile-management/screen-api',{method:'POST',headers:{Origin:origin,Cookie:'ss_employee=fixture','Content-Type':'application/json','X-CSRF-Token':'fixture',Forwarded:'for=evil','X-Forwarded-For':'127.0.0.1'},body:'{}'}),env);
    assert.equal(response.status,200);assert.equal(new URL(forwarded.url).hostname,'store.example');
    assert.equal(forwarded.headers.get('Origin'),origin);assert.equal(forwarded.headers.get('X-CSRF-Token'),'fixture');
    assert.equal(forwarded.headers.get('Forwarded'),null);assert.equal(forwarded.headers.get('X-Forwarded-For'),null);
    assert.equal(response.headers.get('Content-Security-Policy'),"script-src 'self'");assert.match(response.headers.get('Set-Cookie'),/HttpOnly/);
    assert.equal(response.headers.get('Cache-Control'),'no-store');
  }finally{globalThis.fetch=original;}
});
test('an older server receives a clear update-required message without bypassing its origin check',async()=>{
  const original=globalThis.fetch;
  globalThis.fetch=async()=>Response.json({ok:false,error:{code:'ORIGIN_DENIED'}},{status:403});
  try {
    const response=await worker.fetch(new Request(origin+'/employees/api/v1/auth/session',{method:'POST',headers:{Origin:origin},body:'{}'}),env);
    assert.equal(response.status,503);assert.equal((await response.json()).error.code,'SERVER_UPDATE_REQUIRED');
  }finally{globalThis.fetch=original;}
});
