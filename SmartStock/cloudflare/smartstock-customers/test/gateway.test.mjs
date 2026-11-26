import test from 'node:test';import assert from 'node:assert/strict';import worker from '../src/index.mjs';
const origin='https://customermanagement.deckers.gy',env={EMPLOYEE_TUNNEL_HOST:'scheduler.deckers.gy',EMPLOYEE_ORIGIN:'https://timeclock.deckers.gy'};
test('gateway isolates routes, host, methods and origin',async()=>{
 for(const path of ['/employees/api/v1/employees','/employees/api/v1/customers/adjust','/v1/customer-accounts/list','/employees/inventory.html'])assert.equal((await worker.fetch(new Request(origin+path),env)).status,404);
 assert.equal((await worker.fetch(new Request('https://other.example/'),env)).status,404);
 assert.equal((await worker.fetch(new Request(origin+'/employees/api/v1/customers/list'),env)).status,405);
 assert.equal((await worker.fetch(new Request(origin+'/employees/api/v1/customers/save',{method:'POST',headers:{Origin:'https://evil.example'}}),env)).status,403);
});
test('forwards cookies and csrf only through approved ingress without caching',async()=>{const previous=globalThis.fetch;try{globalThis.fetch=async request=>{assert.equal(new URL(request.url).hostname,env.EMPLOYEE_TUNNEL_HOST);assert.equal(request.headers.get('Origin'),env.EMPLOYEE_ORIGIN);assert.equal(request.headers.get('Cookie'),'ss_employee=test');assert.equal(request.headers.get('X-CSRF-Token'),'csrf');return Response.json({ok:true,data:{accounts:[]}})};const r=await worker.fetch(new Request(origin+'/employees/api/v1/customers/list',{method:'POST',headers:{Origin:origin,Cookie:'ss_employee=test','X-CSRF-Token':'csrf'},body:'{}'}),env);assert.equal(r.status,200);assert.equal(r.headers.get('Cache-Control'),'no-store');}finally{globalThis.fetch=previous}});

test('customer history, receipts and adjustments use the approved gateway',async()=>{
 const previous=globalThis.fetch;
 try{
  for(const action of ['history','transaction','order-file','payment-options','record-adjustment']){
   const path='/employees/api/v1/customers/'+action;
   assert.equal((await worker.fetch(new Request(origin+path),env)).status,405);
   assert.equal((await worker.fetch(new Request(origin+path,{method:'POST',headers:{Origin:'https://evil.example'}}),env)).status,403);
   globalThis.fetch=async request=>{
    assert.equal(new URL(request.url).pathname,path);
    assert.equal(request.headers.get('X-CSRF-Token'),'fixture-csrf');
    return Response.json({ok:true});
   };
   const response=await worker.fetch(new Request(origin+path,{method:'POST',
    headers:{Origin:origin,'X-CSRF-Token':'fixture-csrf'},body:'{}'}),env);
   assert.equal(response.status,200);
   assert.equal(response.headers.get('Cache-Control'),'no-store');
  }
 }finally{globalThis.fetch=previous;}
});

test('minutes routes retain origin and retry boundaries',async()=>{
 const previous=globalThis.fetch;try{
  for(const action of ['bootstrap','history','create','reverse']){
   const path='/employees/api/v1/minutes/'+action;
   assert.equal((await worker.fetch(new Request(origin+path),env)).status,405);
   assert.equal((await worker.fetch(new Request(origin+path,{method:'POST',headers:{Origin:'https://evil.example'}}),env)).status,403);
   globalThis.fetch=async request=>{assert.equal(new URL(request.url).pathname,path);assert.equal(request.headers.get('X-CSRF-Token'),'csrf');assert.equal(request.headers.get('Cookie'),'ss_employee=test');assert.equal(await request.text(),'{"idempotencyKey":"fixture-key"}');return Response.json({ok:true});};
   assert.equal((await worker.fetch(new Request(origin+path,{method:'POST',headers:{Origin:origin,Cookie:'ss_employee=test','X-CSRF-Token':'csrf'},body:'{"idempotencyKey":"fixture-key"}'}),env)).status,200);
  }
  assert.equal((await worker.fetch(new Request(origin+'/employees/api/v1/minutes/delete',{method:'POST'}),env)).status,404);
 }finally{globalThis.fetch=previous;}
});
