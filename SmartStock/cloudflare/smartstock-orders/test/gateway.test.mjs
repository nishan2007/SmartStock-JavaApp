import {test} from 'node:test';
import assert from 'node:assert/strict';
import worker from '../src/index.mjs';
const origin='https://ordersmanagement.deckers.gy';
const env={EMPLOYEE_TUNNEL_HOST:'scheduler.deckers.gy',EMPLOYEE_ORIGIN:'https://timeclock.deckers.gy'};
test('only Orders, activation and employee authentication are exposed',async()=>{
  for(const path of ['/v1/custom-orders/dashboard','/employees/api/v1/overseas','/employees/api/v1/history','/employees/api/v1/employees','/employees/app.js','/employees/index.html','/employees/background-remover.html']){
    const r=await worker.fetch(new Request(origin+path,{method:'POST',body:'{}'}),env);assert.equal(r.status,404,path);
  }
  assert.equal((await worker.fetch(new Request('https://evil.example/employees/orders.html'),env)).status,404);
});
test('root opens the Orders workspace',async()=>{
  const r=await worker.fetch(new Request(origin),env);assert.equal(r.status,302);assert.equal(r.headers.get('Location'),origin+'/employees/orders.html');
});
test('absent, null, deceptive and foreign origins cannot reach the store',async()=>{
  for(const value of [undefined,'null','https://evil.example',origin+'.evil.example']){
    const r=await worker.fetch(new Request(origin+'/employees/api/v1/orders/assign',{method:'POST',headers:value?{Origin:value}:{},body:'{}'}),env);assert.equal(r.status,403);
  }
});
test('forwards CSRF, browser cookie and body only after checking the exact origin',async()=>{
  const previous=globalThis.fetch;
  globalThis.fetch=async request=>{
    assert.equal(request.url,'https://scheduler.deckers.gy/employees/api/v1/orders/assign');
    assert.equal(request.headers.get('Origin'),env.EMPLOYEE_ORIGIN);
    assert.equal(request.headers.get('Cookie'),'ss_employee=opaque');
    assert.equal(request.headers.get('X-CSRF-Token'),'csrf');
    assert.equal(request.headers.get('Forwarded'),null);
    assert.equal(await request.text(),'{"orderId":42}');
    return new Response('{"ok":true}',{headers:{'Set-Cookie':'session=opaque; Path=/employees; Secure; HttpOnly; SameSite=Strict'}});
  };
  try{
    const r=await worker.fetch(new Request(origin+'/employees/api/v1/orders/assign',{method:'POST',headers:{Origin:origin,Cookie:'ss_employee=opaque','X-CSRF-Token':'csrf',Forwarded:'host=evil'},body:'{"orderId":42}'}),env);
    assert.equal(r.status,200);assert.equal(r.headers.get('Cache-Control'),'no-store');assert.match(r.headers.get('Set-Cookie'),/HttpOnly/);
  }finally{globalThis.fetch=previous;}
});
test('only allowlisted Orders assets are served',async()=>{
  let requested;
  const assets={...env,ASSETS:{fetch:async request=>{requested=request.url;return new Response('Orders',{headers:{'Content-Type':'text/html'}})}}};
  const r=await worker.fetch(new Request(origin+'/employees/orders.html'),assets);
  assert.equal(requested,origin+'/orders.html');assert.equal(r.status,200);assert.equal(r.headers.get('X-Frame-Options'),'DENY');
});
test('reports offline and outdated backends honestly',async()=>{
  const previous=globalThis.fetch;
  try{
    globalThis.fetch=async()=>new Response('',{status:404});let r=await worker.fetch(new Request(origin+'/employees/health'),env);assert.equal(r.status,503);assert.equal((await r.json()).error.code,'SERVER_UPDATE_REQUIRED');
    globalThis.fetch=async()=>{throw Error('offline')};r=await worker.fetch(new Request(origin+'/employees/health'),env);assert.equal(r.status,503);assert.equal((await r.json()).error.code,'STORE_UNAVAILABLE');
    globalThis.fetch=async()=>Response.json({ok:true});r=await worker.fetch(new Request(origin+'/employees/health'),env);assert.equal(r.status,200);
  }finally{globalThis.fetch=previous;}
});
test('pre-login company branding exposes only already-public company identity',async()=>{
  const previous=globalThis.fetch;const calls=[];
  globalThis.fetch=async(url,options)=>{
    calls.push({url,options});assert.equal(options.redirect,'manual');
    if(String(url).includes('/shop/image?'))return new Response(new Uint8Array([1,2,3]),{headers:{'Content-Type':'image/png'}});
    return Response.json({branding:{name:'DECKERS',mottoLine1:'Service',addressLine1:'Store address',logo:'a'.repeat(64)},products:[{private:'excluded'}],employee:'excluded'});
  };
  try{
    const response=await worker.fetch(new Request(origin+'/employees/branding',{headers:{Cookie:'ss_employee=private'}}),env);
    assert.equal(response.status,200);const data=(await response.json()).data;
    assert.equal(data.name,'DECKERS');assert.match(data.logo,/^data:image\/png;base64,/);
    assert.equal(data.products,undefined);assert.equal(data.employee,undefined);
    assert.equal(calls[0].url,'https://www.deckers.gy/shop/api/v1/catalog');
    assert.equal(calls[0].options.headers.Cookie,undefined);assert.equal(calls[1].options.headers,undefined);
  }finally{globalThis.fetch=previous;}
});
