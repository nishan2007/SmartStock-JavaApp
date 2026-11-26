import {test} from 'node:test';
import assert from 'node:assert/strict';
import vm from 'node:vm';
import fs from 'node:fs';
const source=fs.readFileSync(new URL('../../src/shared-mobile/app.js',import.meta.url),'utf8');
class Element {
 constructor(){this.dataset={};this.value='';this.textContent='';this.children=[];this.classList={toggle(){},remove(){}};this.hidden=true;this.firstChild={textContent:''};this.parentElement={firstChild:{textContent:''}};}
 addEventListener(n,f){this['on'+n]=f;} append(...n){this.children.push(...n);} replaceChildren(...n){this.children=n;this.textContent='';}
 setAttribute(k,v){this[k]=v;}removeAttribute(k){delete this[k];}querySelectorAll(){return [];}close(){this.open=false;this.onclose?.();}showModal(){this.open=true;}focus(){}reset(){this.resetCount=(this.resetCount||0)+1;}
}
const tick=async()=>{for(let i=0;i<14;i++)await new Promise(r=>setImmediate(r));};
function setup(pathname='/employees/mobile-management/'){
 const els=new Map(),calls=[];let profile={name:'Jamie Davis',nickname:'Jamie',email:'jamie@example.com',phone:'123',photo:'data:image/jpeg;base64,TEST'};
 const get=id=>{if(!els.has(id))els.set(id,new Element());return els.get(id);};
 const location={pathname,href:'https://deckersapps.deckers.gy'+pathname,hash:''};
 const fetch=async(url,options)=>{const body=JSON.parse(options.body),route=new URL(url,location.href).pathname;calls.push({route,body,headers:options.headers});
 let data=route.endsWith('/session')?{csrfToken:'csrf'}:route.endsWith('/state')?{employeeName:'Jamie Davis',name:'Phone',apps:[],screens:true}:route.endsWith('/profile')?profile:route.endsWith('/nickname')?(profile={...profile,nickname:body.nickname}):{};
 return Response.json({ok:true,data});};
 vm.runInNewContext(source,{window:{addEventListener(){}},document:{getElementById:get,createElement:()=>new Element(),addEventListener(){}},location,history:{replaceState(){}},fetch,URL,URLSearchParams,AbortController,AbortSignal,DOMException,setTimeout,clearTimeout});return {get,calls};
}
test('profile displays photo twice, falls back to initials, saves nickname and clears private data',async()=>{
 const {get,calls}=setup();await tick();assert.equal(get('emailValue').textContent,'jamie@example.com');
 for(const id of ['profile','accountPhoto']){assert.equal(get(id).children.length,1);get(id).children[0].onerror();assert.equal(get(id).textContent,'JD');}
 get('editNickname').onclick();assert.equal(get('nicknameDialog').open,true);get('nicknameInput').value='New name';
 get('nicknameForm').onsubmit({preventDefault(){}});await tick();assert.equal(get('nicknameValue').textContent,'New name');
 const write=calls.find(c=>c.route.endsWith('/nickname'));assert.equal(write.headers['X-CSRF-Token'],'csrf');assert.deepEqual(write.body,{nickname:'New name'});
 get('logout').onclick();await tick();assert.equal(get('emailValue').textContent,'');assert.equal(get('accountPhoto').textContent,'');assert.equal(get('passwordDialog').open,false);
});
test('password confirmation prevents submission and valid form uses authenticated account endpoint',async()=>{
 const {get,calls}=setup();await tick();get('currentPassword').value='old secret';get('newPassword').value='new secret';get('confirmPassword').value='different';
 get('passwordForm').onsubmit({preventDefault(){}});await tick();assert.equal(calls.filter(c=>c.route.endsWith('/password')).length,0);assert.match(get('passwordStatus').textContent,/do not match/);
 get('confirmPassword').value='new secret';get('passwordCode').value='123456';get('passwordForm').onsubmit({preventDefault(){}});await tick();
 const write=calls.find(c=>c.route.endsWith('/password'));assert.equal(write.headers['X-CSRF-Token'],'csrf');assert.equal(write.body.currentPassword,'old secret');assert.equal(write.body.code,'123456');assert.equal(get('passwordDialog').open,false);assert.ok(get('passwordForm').resetCount>0);
});

for(const pathname of ['/mobile-management/','/scheduler/mobile-management/'])test('legacy native session does not send its CSRF to employee Auth: '+pathname,async()=>{const {get,calls}=setup(pathname);await tick();assert.equal(get('home').hidden,false);assert.equal(calls.some(c=>c.route.includes('/account/')),false);assert.equal(get('changePassword').disabled,true);assert.equal(get('accountAvailability').hidden,false);});
