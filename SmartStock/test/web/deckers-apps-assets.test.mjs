import {test} from 'node:test';
import assert from 'node:assert/strict';
import worker from '../../cloudflare/smartstock-deckers-apps/src/index.mjs';
test('Deckers serves the current network-check script without caching',async()=>{
 const response=await worker.fetch(new Request('https://deckersapps.deckers.gy/employees/mobile-management/app.js'),{WORKSPACE_ASSETS:{fetch:async request=>{assert.equal(new URL(request.url).pathname,'/app.js');return new Response('current script',{headers:{'Content-Type':'application/javascript'}});}}});
 assert.equal(await response.text(),'current script');assert.equal(response.headers.get('Cache-Control'),'no-store');
});
test('the asset binding does not expose other assets or a public LAN probe',async()=>{
 const original=globalThis.fetch;let forwarded=0;
 globalThis.fetch=async request=>{forwarded++;assert.equal(new URL(request.url).hostname,'store.example');return new Response('server policy');};
 try{
  const env={EMPLOYEE_TUNNEL_HOST:'store.example',WORKSPACE_ASSETS:{fetch:()=>assert.fail('Only app.js may use assets')}};
  assert.equal((await worker.fetch(new Request('https://deckersapps.deckers.gy/app.js'),env)).status,404);
  assert.equal((await worker.fetch(new Request('https://deckersapps.deckers.gy/mobile-management/network-probe',{method:'POST'}),env)).status,404);
  assert.equal((await worker.fetch(new Request('https://deckersapps.deckers.gy/employees/mobile-management/screen-api',{method:'POST',headers:{Origin:'https://evil.example'}}),env)).status,403);
  await worker.fetch(new Request('https://deckersapps.deckers.gy/employees/mobile-management/screen-api',{method:'POST',headers:{Origin:'https://deckersapps.deckers.gy'},body:'{}'}),env);
  assert.equal(forwarded,1);
 }finally{globalThis.fetch=original;}
});
