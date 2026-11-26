import {test} from 'node:test';
import assert from 'node:assert/strict';
import orders from '../../cloudflare/smartstock-orders/src/index.mjs';
import inventory from '../../cloudflare/smartstock-inventory/src/index.mjs';
import customers from '../../cloudflare/smartstock-customers/src/index.mjs';
import balance from '../../cloudflare/smartstock-balance-sheet/src/index.mjs';
import overseas from '../../cloudflare/smartstock-overseas/src/index.mjs';

const gateways = [
  ['ordersmanagement', orders], ['inventory', inventory], ['customermanagement', customers],
  ['balancesheet', balance], ['overseasshipping', overseas]
];
const env = {EMPLOYEE_TUNNEL_HOST:'store.example', EMPLOYEE_ORIGIN:'https://timeclock.deckers.gy'};
for (const [host, worker] of gateways) {
  const origin = `https://${host}.deckers.gy`;
  test(`${host}: management handoffs keep the checked destination origin and secure cookie`, async () => {
    const original = globalThis.fetch;
    globalThis.fetch = async request => {
      assert.equal(request.url, 'https://store.example/employees/mobile-management/redeem');
      assert.equal(request.headers.get('Origin'), origin);
      assert.equal(request.headers.get('Cookie'), 'ss_mobile_device=opaque');
      assert.equal(request.headers.get('Forwarded'), null);
      assert.deepEqual(await request.json(), {token:'one-time-handoff'});
      return new Response('{"ok":true}', {headers:{'Set-Cookie':'ss_mobile_device=opaque; Path=/; Secure; HttpOnly; SameSite=Lax'}});
    };
    try {
      const response = await worker.fetch(new Request(origin+'/employees/mobile-management/redeem', {
        method:'POST', headers:{Origin:origin, Cookie:'ss_mobile_device=opaque', Forwarded:'host=evil'}, body:JSON.stringify({token:'one-time-handoff'})
      }), env);
      assert.equal(response.status, 200);
      assert.equal(response.headers.get('Cache-Control'), 'no-store');
      assert.match(response.headers.get('Set-Cookie'), /HttpOnly/);
    } finally { globalThis.fetch = original; }
  });
  test(`${host}: management routes reject cross-site posts and unknown endpoints`, async () => {
    for (const caller of [undefined, 'null', 'https://evil.example', origin+'.evil.example']) {
      const response = await worker.fetch(new Request(origin+'/employees/mobile-management/pair', {
        method:'POST', headers:caller ? {Origin:caller} : {}, body:'{}'
      }), env);
      assert.equal(response.status, 403);
    }
    assert.equal((await worker.fetch(new Request(origin+'/employees/mobile-management/admin', {method:'POST', body:'{}'}),env)).status,404);
    assert.equal((await worker.fetch(new Request(origin+'/employees/mobile-management/redeem'),env)).status,405);
  });
  test(`${host}: launcher assets use the store service without issuing credentials on GET`, async () => {
    const original = globalThis.fetch;
    globalThis.fetch = async request => {
      assert.equal(request.method, 'GET');
      assert.equal(new URL(request.url).hostname, 'store.example');
      return new Response('launcher', {headers:{'Content-Type':'text/html'}});
    };
    try {
      for(const resource of ['', 'screens.js','device-info.js','screen-modules/ORDERS.js','screen-assets/mobile-web/orders.css', 'reconnect.js','repair-qr.js','repair-qr-LICENSE.txt','app.js','app.css','signin.css','manifest.webmanifest','logo.png','icon-180.png','icon-192.png','icon-512.png','icons/ORDERS.png','icons/SCHEDULER.svg','icons.ttf','icons-LICENSE.txt']) {
        const response = await worker.fetch(new Request(origin+'/employees/mobile-management/'+resource),env);
        assert.equal(response.status,200);assert.equal(response.headers.get('Set-Cookie'),null);
      }
    } finally { globalThis.fetch = original; }
  });
  test(`${host}: network challenges stay authenticated and the public gateway cannot verify LAN presence`, async () => {
    const original=globalThis.fetch;
    globalThis.fetch=async request=>{
      assert.equal(new URL(request.url).pathname,'/employees/mobile-management/network-challenge');
      assert.equal(request.headers.get('Origin'),origin);
      assert.equal(request.headers.get('Cookie'),'ss_mobile_device=approved; ss_employee=employee');
      return Response.json({ok:true,data:{challenge:'temporary'}},{headers:{'Content-Security-Policy':"default-src 'self'; connect-src 'self' https://store.local:8444"}});
    };
    try {
      assert.equal((await worker.fetch(new Request(origin+'/employees/mobile-management/network-challenge',{method:'POST',headers:{Origin:'https://evil.example'},body:'{}'}),env)).status,403);
      const response=await worker.fetch(new Request(origin+'/employees/mobile-management/network-challenge',{method:'POST',headers:{Origin:origin,Cookie:'ss_mobile_device=approved; ss_employee=employee'},body:'{"app":"RECEIVING"}'}),env);
      assert.equal(response.status,200);assert.match(response.headers.get('Content-Security-Policy'),/https:\/\/store\.local:8444/);
      assert.equal((await worker.fetch(new Request(origin+'/employees/mobile-management/network-probe',{method:'POST',headers:{Origin:origin},body:'{}'}),env)).status,404);
    } finally {globalThis.fetch=original;}
  });
  test(`${host}: installed-app linking stays scoped to the checked origin`, async () => {
    const original=globalThis.fetch;
    globalThis.fetch=async request=>{
      assert.equal(request.headers.get('Origin'),origin);
      assert.equal(request.headers.get('Cookie'),'ss_mobile_link=claim');
      const headers=new Headers();headers.append('Set-Cookie','ss_mobile_device=opaque; Path=/; Secure; HttpOnly');headers.append('Set-Cookie','ss_mobile_link=; Path=/employees/mobile-management/; Max-Age=0; Secure; HttpOnly');
      return new Response('{"ok":true}',{headers});
    };
    try {
      for(const route of ['link-request','link-status','link-info','approve-link']) {
        assert.equal((await worker.fetch(new Request(origin+'/employees/mobile-management/'+route,{method:'POST',headers:{Origin:'https://evil.example'},body:'{}'}),env)).status,403);
        const response=await worker.fetch(new Request(origin+'/employees/mobile-management/'+route,{method:'POST',headers:{Origin:origin,Cookie:'ss_mobile_link=claim'},body:'{}'}),env);
        assert.equal(response.status,200);assert.equal(response.headers.getSetCookie().length,2);
      }
    } finally {globalThis.fetch=original;}
  });
}
