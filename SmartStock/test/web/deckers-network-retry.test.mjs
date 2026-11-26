import {test} from 'node:test';
import assert from 'node:assert/strict';
import vm from 'node:vm';
import fs from 'node:fs';
const source=fs.readFileSync(new URL('../../src/shared-mobile/app.js',import.meta.url),'utf8');
function harness(request,callOverride){
 const states=[],issued=[];
 const call=callOverride||(async()=>{const challenge='proof-'+issued.length;issued.push(challenge);return {challenge,probeUrl:'https://store.example:8444/mobile-management/network-probe'};});
 const code=source.slice(source.indexOf('  let networkCheckRevision='),source.indexOf('  async function mountScreen'));
 const check=vm.runInNewContext(code+';localChallenge',{request,call,URL,connection:value=>states.push(value),setTimeout:fn=>setTimeout(fn,0)});
 return {check,states,issued};
}
test('a cold LAN failure retries with fresh proof before the caller can save',async()=>{
 let probes=0,saves=0;
 const h=harness(async(url,body,options)=>{assert.equal(options.credentials,'omit');assert.equal(options.timeout,10000);if(++probes===1)throw new TypeError('Load failed');});
 const proof=await h.check({key:'MOBILE_ITEMS',localOnly:true});saves++;
 assert.equal(proof,'proof-1');assert.equal(probes,2);assert.equal(saves,1);assert.deepEqual(h.states,[true]);
});
test('unreachable LAN remains denied after bounded retries',async()=>{
 let probes=0;const h=harness(async()=>{probes++;throw new TypeError('offline');});
 await assert.rejects(h.check({key:'MOBILE_ITEMS',localOnly:true}),error=>error.code==='LAN_ONLY');
 assert.equal(probes,2);assert.deepEqual(h.states,[false]);
});
test('an older background failure cannot overwrite a newer successful check',async()=>{
 const pending=[];const h=harness(()=>new Promise((resolve,reject)=>pending.push({resolve,reject})));
 const old=h.check({key:'MOBILE_ITEMS',localOnly:true});await Promise.resolve();
 const latest=h.check({key:'MOBILE_ITEMS',localOnly:true});
 while(pending.length<2)await new Promise(resolve=>setTimeout(resolve,1));
 pending[1].resolve();await latest;pending[0].reject(new Error('old socket'));
 while(pending.length<3)await new Promise(resolve=>setTimeout(resolve,1));
 pending[2].reject(new Error('still stale'));await assert.rejects(old,error=>error.code==='LAN_ONLY');assert.deepEqual(h.states,[true]);
});
test('auth failures and invalid probe addresses are not retried as LAN failures',async()=>{
 let calls=0;const h=harness(()=>assert.fail('must not probe'),async()=>{calls++;throw Object.assign(new Error('session expired'),{status:401});});
 await assert.rejects(h.check({localOnly:true}),error=>error.status===401);assert.equal(calls,1);assert.deepEqual(h.states,[]);
 const bad=harness(()=>assert.fail('must not probe'),async()=>({probeUrl:'http://store.example/mobile-management/network-probe'}));
 await assert.rejects(bad.check({localOnly:true}),/address is invalid/);
});
