// Read-only verification of the deployed public app. No credentials or store data.
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {createHash} from 'node:crypto';
const origin='https://balancesheet.deckers.gy',source=new URL('../src/employee-web/',import.meta.url);
const get=async(path,options={})=>fetch(origin+path,{redirect:'manual',signal:AbortSignal.timeout(20000),...options});
const root=await get('/');assert.equal(root.status,302);assert.equal(root.headers.get('Location'),origin+'/employees/balance-sheet.html');
for(const file of ['balance-sheet.html','balance-sheet.css','balance-sheet.js','balance-sheet.webmanifest',
  'icons/balance-sheet.svg','icons/balance-sheet-180.png','icons/balance-sheet-192.png','icons/balance-sheet-512.png']) {
  const local=await readFile(new URL(file,source)),response=await get('/employees/'+file);
  assert.equal(response.status,200,file);assert.equal(response.headers.get('Cache-Control'),'no-store');
  const hosted=Buffer.from(await response.arrayBuffer()),hash=bytes=>createHash('sha256').update(bytes).digest('hex');
  assert.equal(hosted.length,local.length,file+' download size');assert.equal(hash(hosted),hash(local),file+' SHA-256');
  console.log(JSON.stringify({asset:file,bytes:hosted.length,sha256:hash(hosted)}));
}
const health=await get('/employees/health');assert.equal(health.status,200,'Store gateway health');assert.equal((await health.json()).ok,true);
for(const path of ['/employees/api/v1/history','/employees/api/v1/orders/dashboard','/v1/accounting/balance-sheet/update'])
  assert.equal((await get(path,{method:'POST',headers:{Origin:origin,'Content-Type':'application/json'},body:'{}'})).status,404,path);
assert.equal((await get('/employees/api/v1/balance-sheet',{method:'POST',headers:{Origin:'https://evil.example','Content-Type':'application/json'},body:'{}'})).status,403,'Foreign origin is denied');
// No cookies: verify the store requests approval without attempting a real login or activation.
const session=await get('/employees/api/v1/auth/session',{method:'POST',headers:{Origin:origin,'Content-Type':'application/json'},body:'{}'});
assert.equal(session.status,403);assert.equal((await session.json()).error.code,'DEVICE_REQUIRED');
console.log('Verified dedicated hostname, all asset sizes/SHA-256, healthy store, API isolation and activation requirement.');
