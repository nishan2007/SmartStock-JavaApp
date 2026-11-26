import {test} from 'node:test';
import assert from 'node:assert/strict';
import vm from 'node:vm';
import fs from 'node:fs';
const source=fs.readFileSync(new URL('../../src/shared-mobile/app.js',import.meta.url),'utf8');
class Element {
  constructor(){this.dataset={};this.value='';this.children=[];this.classList={toggle(){},remove(){}};}
  addEventListener(name,handler){this['on'+name]=handler;}
  append(...nodes){this.children.push(...nodes);}
  replaceChildren(...nodes){this.children=nodes;}
  setAttribute(name,value){this[name]=value;}
  removeAttribute(name){delete this[name];}
  querySelectorAll(){return [];}
  close(){}
}
const tick=async()=>{for(let i=0;i<12;i++)await new Promise(resolve=>setTimeout(resolve,0));};
test('saved-password submission ignores a second submit while sign-in is pending',async()=>{
  const elements=new Map(),calls=[];let completeLogin;
  const get=id=>{if(!elements.has(id))elements.set(id,new Element());return elements.get(id);};
  const location={pathname:'/employees/mobile-management/',href:'https://deckersapps.deckers.gy/employees/mobile-management/',hash:''};
  const fetch=async(url,options)=>{
    const route=new URL(url,location.href).pathname.split('/').pop(),body=JSON.parse(options.body);calls.push({route,body});
    if(route==='session')return Response.json({ok:false,error:{code:'SESSION_REQUIRED'}},{status:401});
    if(route==='login')return new Promise(resolve=>{completeLogin=()=>resolve(Response.json({ok:true,data:{csrfToken:'signed-in'}}));});
    return Response.json({ok:true,data:route==='state'?{employeeName:'Employee',name:'Phone',apps:[]}: {name:'Employee'}});
  };
  vm.runInNewContext(source,{window:{addEventListener(){}},document:{getElementById:get,addEventListener(){},createElement:()=>new Element()},location,history:{replaceState(){}},fetch,URL,URLSearchParams,AbortController,AbortSignal,DOMException,setTimeout,clearTimeout});
  await tick();
  // Password managers can fill DOM values without firing input/change events.
  get('identifier').value=' employee@example.com ';get('password').value=' saved secret ';
  get('loginForm').onsubmit({preventDefault(){}});get('loginForm').onsubmit({preventDefault(){}});await tick();
  assert.equal(calls.filter(c=>c.route==='login').length,1);
  assert.equal(calls.find(c=>c.route==='login').body.identifier,'employee@example.com');
  assert.equal(calls.find(c=>c.route==='login').body.password,' saved secret ');
  completeLogin();await tick();assert.equal(get('home').hidden,false);assert.equal(get('password').value,'');
});
test('Home switches mounted screens using one employee session and the same CSRF token',async()=>{
  const elements=new Map(),calls=[],mounted=[];let disposals=0;
  const get=id=>{if(!elements.has(id))elements.set(id,new Element());return elements.get(id);};
  const apps=[{key:'ORDERS',name:'Orders',localOnly:false},{key:'INVENTORY',name:'Inventory',localOnly:false}];
  const location={pathname:'/employees/mobile-management/',href:'https://timeclock.deckers.gy/employees/mobile-management/',origin:'https://timeclock.deckers.gy',hash:''};
  const fetch=async(url,options)=>{
    const route=new URL(url,location.href).pathname.split('/').pop(),body=JSON.parse(options.body);calls.push({route,body,headers:options.headers});
    const data=route==='session'?{csrfToken:'one-session',permissions:[],userId:7}:route==='state'?{screens:true,employeeName:'Test Employee',name:'Test Phone',apps}:route==='screen'?{app:body.app,path:'/employees/orders.html'}:{};
    return Response.json({ok:true,data});
  };
  const window={addEventListener(){},DeckersScreens:{mount:async(host,packet,bridge)=>{mounted.push(packet.app);assert.equal((await bridge.session()).csrfToken,'one-session');await bridge.api(packet.app==='ORDERS'?'orders/dashboard':'inventory/search',{});return ()=>disposals++;}}};
  vm.runInNewContext(source,{window,document:{getElementById:get,createElement:()=>new Element(),addEventListener(){}},location,history:{replaceState(){}},fetch,URL,URLSearchParams,AbortController,AbortSignal,DOMException,setTimeout,clearTimeout});
  await tick();
  assert.equal(get('workspaceTitle').textContent,'Your workspace');
  get('apps').children[0].onclick();await tick();
  assert.equal(get('workspaceTitle').textContent,'Orders');
  assert.equal(get('homeTab')['aria-current'],'page');
  assert.equal(get('screenPanel').hidden,false);
  await get('homeTab').onclick();await tick();
  get('apps').children[1].onclick();await tick();
  assert.deepEqual(mounted,['ORDERS','INVENTORY']);assert.equal(disposals,1);
  assert.equal(calls.filter(call=>call.route==='session').length,1);
  assert.equal(calls.filter(call=>call.route==='open'||call.route==='login').length,0);
  assert.equal(calls.filter(call=>call.route==='screen-api').length,2);
  assert.ok(calls.filter(call=>call.route==='screen-api').every(call=>call.headers['X-CSRF-Token']==='one-session'));
  assert.equal(location.pathname,'/employees/mobile-management/');
});


for(const remembered of [false,true])test('closing workspace revokes only temporary sessions: '+remembered,async()=>{
  const elements=new Map(),events={},calls=[];
  const get=id=>{if(!elements.has(id))elements.set(id,new Element());return elements.get(id);};
  const location={pathname:'/employees/mobile-management/',href:'https://timeclock.deckers.gy/employees/mobile-management/',hash:''};
  const fetch=async(url,options)=>{
    const route=new URL(url,location.href).pathname.split('/').pop();calls.push({route,options});
    return Response.json({ok:true,data:route==='session'?{csrfToken:'exit-csrf',rememberDevice:remembered}:route==='state'?{employeeName:'Employee',name:'Device',apps:[]}: {}});
  };
  const window={addEventListener(name,fn){events[name]=fn;}};
  vm.runInNewContext(source,{window,document:{getElementById:get,addEventListener(){}},location,history:{replaceState(){}},fetch,URL,URLSearchParams,AbortController,AbortSignal,DOMException,setTimeout,clearTimeout});
  await tick();events.pagehide();await tick();
  const logouts=calls.filter(c=>c.route==='logout');assert.equal(logouts.length,remembered?0:1);
  if(!remembered){assert.equal(logouts[0].options.keepalive,true);assert.equal(logouts[0].options.headers['X-CSRF-Token'],'exit-csrf');assert.equal(get('apps').children.length,0);}
});

test('login never sends an employee persistence preference and uses the server MFA policy',async()=>{
  const elements=new Map(),calls=[];
  const get=id=>{if(!elements.has(id)){const el=new Element();el.focus=()=>{};elements.set(id,el);}return elements.get(id);};
  const location={pathname:'/employees/mobile-management/',href:'https://timeclock.deckers.gy/employees/mobile-management/',hash:''};
  const fetch=async(url,options)=>{
    const route=new URL(url,location.href).pathname.split('/').pop(),body=JSON.parse(options.body);calls.push({route,body});
    if(route==='session')return Response.json({ok:false,error:{code:'SESSION_REQUIRED'}},{status:401});
    return Response.json({ok:true,data:route==='login'?{mfaRequired:true,preauth:'challenge'}:route==='verify'?{csrfToken:'verified',rememberDevice:true}:{employeeName:'Employee',name:'Phone',apps:[]}});
  };
  vm.runInNewContext(source,{window:{addEventListener(){}},document:{getElementById:get,addEventListener(){}},location,history:{replaceState(){}},fetch,URL,URLSearchParams,AbortController,AbortSignal,DOMException,setTimeout,clearTimeout});
  await tick();get('identifier').value='employee';get('password').value='password';
  get('loginForm').onsubmit({preventDefault(){}});await tick();
  assert.equal(calls.find(c=>c.route==='login').body.rememberDevice,undefined);
  get('code').value='123456';get('mfaForm').onsubmit({preventDefault(){}});await tick();
  const verification=calls.find(c=>c.route==='verify').body;assert.equal(verification.preauth,'challenge');assert.equal(verification.rememberDevice,undefined);assert.equal(get('home').hidden,false);
});

test('iPhone autofill waits for the initial session probe and keeps credentials committed after submit',async()=>{
  const elements=new Map(),calls=[];let completeProbe;
  const get=id=>{if(!elements.has(id))elements.set(id,new Element());return elements.get(id);};
  const location={pathname:'/employees/mobile-management/',href:'https://deckersapps.deckers.gy/employees/mobile-management/',hash:''};
  const fetch=async(url,options)=>{
    const route=new URL(url,location.href).pathname.split('/').pop(),body=JSON.parse(options.body);calls.push({route,body});
    if(route==='session')return new Promise(resolve=>{completeProbe=()=>resolve(Response.json({ok:false,error:{code:'SESSION_REQUIRED'}},{status:401}));});
    return Response.json({ok:true,data:route==='login'?{csrfToken:'signed-in'}:route==='state'?{employeeName:'Employee',name:'Phone',apps:[]}:{name:'Employee'}});
  };
  vm.runInNewContext(source,{window:{addEventListener(){}},document:{getElementById:get,addEventListener(){},createElement:()=>new Element()},location,history:{replaceState(){}},fetch,URL,URLSearchParams,AbortController,AbortSignal,DOMException,setTimeout,clearTimeout});
  get('loginForm').onsubmit({preventDefault(){}});
  // Safari commits these without input/change, after dispatching submit.
  get('identifier').value=' employee@example.com ';get('password').value=' saved password ';
  await tick();assert.equal(calls.filter(c=>c.route==='login').length,0);
  completeProbe();await tick();
  const logins=calls.filter(c=>c.route==='login');assert.equal(logins.length,1);
  assert.equal(logins[0].body.identifier,'employee@example.com');assert.equal(logins[0].body.password,' saved password ');
  assert.equal(get('home').hidden,false);assert.equal(get('password').value,'');
});

test('an incomplete autofill sends no auth request and allows a completed manual retry',async()=>{
  const elements=new Map(),calls=[];
  const get=id=>{if(!elements.has(id))elements.set(id,new Element());return elements.get(id);};
  const location={pathname:'/employees/mobile-management/',href:'https://deckersapps.deckers.gy/employees/mobile-management/',hash:''};
  const fetch=async(url,options)=>{
    const route=new URL(url,location.href).pathname.split('/').pop();calls.push(route);
    if(route==='session')return Response.json({ok:false,error:{code:'SESSION_REQUIRED'}},{status:401});
    return Response.json({ok:true,data:route==='login'?{csrfToken:'signed-in'}:route==='state'?{employeeName:'Employee',name:'Phone',apps:[]}:{name:'Employee'}});
  };
  vm.runInNewContext(source,{window:{addEventListener(){}},document:{getElementById:get,addEventListener(){},createElement:()=>new Element()},location,history:{replaceState(){}},fetch,URL,URLSearchParams,AbortController,AbortSignal,DOMException,setTimeout,clearTimeout});
  await tick();get('identifier').value='employee';get('loginForm').onsubmit({preventDefault(){}});await tick();
  assert.equal(calls.filter(c=>c==='login').length,0);assert.match(get('status').textContent,/tap Sign in/);
  get('password').value='saved password';get('loginForm').onsubmit({preventDefault(){}});await tick();
  assert.equal(calls.filter(c=>c==='login').length,1);assert.equal(get('home').hidden,false);
});
