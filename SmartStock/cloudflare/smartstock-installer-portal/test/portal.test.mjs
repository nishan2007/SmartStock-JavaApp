import test from 'node:test';
import assert from 'node:assert/strict';
import {webcrypto} from 'node:crypto';
import portal,{configuration} from '../src/index.js';
globalThis.crypto??=webcrypto;
const env=()=>({
  INSTALLERS_JSON:JSON.stringify({windows:{key:'releases/SmartStock.exe',filename:'SmartStock.exe',size:3,sha256:'a'.repeat(64)}}),
  UPDATE_BUCKET:{get:async key=>key.startsWith('installers/latest/')?null:({size:3,customMetadata:{sha256:'a'.repeat(64)},body:'exe'})}
});
const request=(path='/',method='GET')=>new Request('https://downloads.deckers.gy'+path,{method});
test('public downloads require no account or online store',async()=>{
  const original=globalThis.fetch;globalThis.fetch=()=>assert.fail('Must not authenticate with a store');
  try {
    const e=env();e.STORES_JSON='invalid';e.INSTALLER_ORIGIN_KEYS_JSON='invalid';
    const page=await portal.fetch(request(),e);assert.equal(page.status,200);assert.match(await page.text(),/\/download\/windows/);
    const r=await portal.fetch(request('/download/windows'),e);assert.equal(r.status,200);assert.equal(await r.text(),'exe');
    assert.equal(r.headers.get('WWW-Authenticate'),null);assert.equal(r.headers.get('Content-Length'),'3');
    assert.equal(r.headers.get('Content-Disposition'),'attachment; filename="SmartStock.exe"');
  }finally{globalThis.fetch=original;}
});
test('artifact manifest rejects paths and injected filenames',()=>{
  for(const change of [{key:'../private'},{filename:'bad"name'},{size:0},{sha256:'bad'}]) {
    const e=env(),a=JSON.parse(e.INSTALLERS_JSON);Object.assign(a.windows,change);e.INSTALLERS_JSON=JSON.stringify(a);assert.throws(()=>configuration(e));
  }
});
test('downloads still enforce artifact size and hash metadata',async()=>{
  for(const object of [null,{size:4,customMetadata:{sha256:'a'.repeat(64)},body:'bad'},{size:3,customMetadata:{sha256:'b'.repeat(64)},body:'bad'}]) {
    const e=env();e.UPDATE_BUCKET.get=async key=>key.startsWith('installers/latest/')?null:object;
    assert.equal((await portal.fetch(request('/download/windows'),e)).status,503);
  }
});
test('HEAD verifies the object without downloading its body',async()=>{
  const e=env();e.UPDATE_BUCKET.head=async()=>({size:3,customMetadata:{sha256:'a'.repeat(64)}});
  const r=await portal.fetch(request('/download/windows','HEAD'),e);assert.equal(r.status,200);assert.equal(await r.text(),'');assert.equal(r.headers.get('Content-Length'),'3');
});
test('published selection is used and an ETag mismatch blocks downloads',async()=>{
  const a={platform:'windows',version:'1.0.230',buildNumber:100230,size:3,sha256:'a'.repeat(64),filename:'SmartStock.exe',etag:'verified'};
  a.key=`installers/windows/${a.version}/${a.sha256}/${a.filename}`;
  const e=env();let etag='verified';
  e.UPDATE_BUCKET.get=async key=>key==='installers/latest/windows.json'?{size:400,json:async()=>a}:key===a.key?{size:3,etag,customMetadata:{sha256:a.sha256},body:'new'}:null;
  const r=await portal.fetch(request('/download/windows'),e);assert.equal(r.status,200);assert.equal(await r.text(),'new');
  etag='changed';assert.equal((await portal.fetch(request('/download/windows'),e)).status,503);
});
test('public downloads do not grant publishing access',async()=>{
  const e=env();e.INSTALLER_PUBLISH_KEY='x'.repeat(32);e.UPDATE_BUCKET.get=()=>assert.fail('No unauthorized storage access');
  assert.equal((await portal.fetch(request('/_release/publish','POST'),e)).status,403);
});
test('invalid selection, unsupported methods and arbitrary object paths fail',async()=>{
  const e=env();e.UPDATE_BUCKET.get=async()=>({size:400,json:async()=>({key:'../private'})});
  assert.equal((await portal.fetch(request('/download/windows'),e)).status,503);
  assert.equal((await portal.fetch(request('/download/windows','POST'),env())).status,405);
  assert.equal((await portal.fetch(request('/updates/windows/private.zip'),env())).status,404);
});
