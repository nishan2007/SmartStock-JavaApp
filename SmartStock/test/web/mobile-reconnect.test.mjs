import {test} from 'node:test';
import assert from 'node:assert/strict';
import vm from 'node:vm';
import fs from 'node:fs';
const reconnect = fs.readFileSync(new URL('../../src/shared-mobile/reconnect.js', import.meta.url), 'utf8');
const launcher = fs.readFileSync(new URL('../../src/shared-mobile/app.js', import.meta.url), 'utf8');
class Element {
  constructor(tag='div') { this.tag=tag;this.children=[];this.hidden=false;this.disabled=false;this.textContent='';this.value='';const classes=new Set();this.classList={toggle:(name,force)=>{if(force)classes.add(name);else classes.delete(name);},remove:name=>classes.delete(name),contains:name=>classes.has(name)}; }
  append(...nodes) { this.children.push(...nodes); }
  appendChild(node) { this.children.push(node);return node; }
  prepend(node) { this.children.unshift(node); }
  setAttribute(key,value) { this[key]=value; }
  removeAttribute(key) { delete this[key]; }
  addEventListener(name,handler) { this['on'+name]=handler; }
  replaceChildren(...nodes) { this.children=[...nodes]; }
  querySelectorAll(selector) { return nodes(this).filter(node=>node.tag===selector); }
  close() { this.open=false; }
  remove() { this.removed=true; }
  focus() { this.focused=true; }
}
function nodes(root) { return [root,...root.children.flatMap(nodes)]; }
const tick = async () => { for(let i=0;i<6;i++)await new Promise(resolve=>setImmediate(resolve)); };
function app(options={}) {
  const body=new Element('body'), events=new Map(),timers=[],calls=[],stored=new Map(options.stored||[]);
  const url=new URL(options.url||'https://inventory.deckers.gy/employees/inventory.html');
  const location={pathname:url.pathname,hostname:url.hostname,origin:url.origin,href:url.href,search:url.search,reload(){this.reloaded=true;}};
  const popup={location:{replace(url){popup.url=url;}},close(){this.closed=true;}};
  let approved=false;
  const fetch=async(input,init)=>{
    const path=new URL(input,location.href).pathname;calls.push({path,init});
    let data;
    if(path.endsWith('/repair'))data={paired:true,repaired:true};
    else if(path.endsWith('/link-request'))data={requestId:'request-1',approvalUrl:'https://timeclock.deckers.gy/employees/mobile-management/#link=request-1',expiresAt:new Date(Date.now()+600000).toISOString()};
    else if(path.endsWith('/link-status'))data={approved};
    else return Response.json({ok:false,error:{code:options.code||'DEVICE_REQUIRED',message:'Device approval needed'}},{status:401});
    return Response.json({ok:true,data});
  };
  const window={fetch,addEventListener:(name,handler)=>events.set('window-'+name,handler),open(){window.opened=true;return popup;},...(options.qr?{ZXingBrowser:options.qr}:{})};
  const document={head:new Element('head'),body,readyState:'loading',hidden:false,createElement:tag=>new Element(tag),addEventListener:(name,handler)=>events.set(name,handler)};
  const context={window,document,location,URL,URLSearchParams,Date,JSON,sessionStorage:{getItem:key=>stored.get(key)||null,setItem:(key,value)=>stored.set(key,value),removeItem:key=>stored.delete(key)},navigator:{...(options.mediaDevices?{mediaDevices:options.mediaDevices}:{}),clipboard:{writeText:async value=>{context.copied=value;}}},setTimeout:fn=>{timers.push(fn);return timers.length;},clearTimeout(){}};
  vm.runInNewContext(reconnect,context);
  return {body,window,location,popup,calls,stored,timers,events,context,approve(){approved=true;},button:text=>nodes(body).find(node=>node.tag==='button'&&node.textContent===text)};
}
test('missing device approval offers existing pairing without consuming the app response',async()=>{
  const fixture=app();const response=await fixture.window.fetch('/employees/api/v1/auth/session');
  assert.equal((await response.json()).error.code,'DEVICE_REQUIRED');
  assert.ok(fixture.button('Use existing iPhone pairing'));
  assert.equal(fixture.window.opened,undefined);
  assert.equal(fixture.calls.length,1);
});
test('home-screen app requests a connection and resumes only after paired-browser approval',async()=>{
  const fixture=app();await fixture.window.fetch('/employees/api/v1/auth/session');
  await fixture.button('Use existing iPhone pairing').onclick();await tick();
  const request=fixture.calls.find(call=>call.path.endsWith('/link-request'));
  assert.deepEqual(JSON.parse(request.init.body),{app:'INVENTORY'});
  assert.equal(request.init.credentials,'same-origin');
  assert.match(fixture.popup.url,/#link=request-1$/);
  assert.equal(fixture.calls.some(call=>call.path.endsWith('/approve-link')),false);
  assert.equal(fixture.location.reloaded,undefined);
  assert.ok(fixture.stored.has('smartstock.pendingDeviceLink'));
  fixture.approve();await fixture.timers.pop()();await tick();
  assert.equal(fixture.location.reloaded,true);
  assert.equal(fixture.stored.has('smartstock.pendingDeviceLink'),false);
});
test('pending link survives a home-screen app reload while switching to Safari',async()=>{
  const pending={requestId:'request-1',approvalUrl:'https://timeclock.deckers.gy/employees/mobile-management/#link=request-1',expiresAt:new Date(Date.now()+600000).toISOString()};
  const fixture=app({stored:[['smartstock.pendingDeviceLink',JSON.stringify(pending)]]});fixture.approve();
  await fixture.window.fetch('/employees/api/v1/auth/session');await tick();
  assert.equal(fixture.location.reloaded,true);assert.equal(fixture.window.opened,undefined);
  assert.equal(fixture.calls.some(call=>call.path.endsWith('/link-request')),false);
});
test('expired employee login and disabled apps never offer a device-permission bypass',async()=>{
  for(const code of ['SESSION_EXPIRED','PERMISSION_DENIED','DEVICE_APP_DISABLED']) {
    const fixture=app({code});await fixture.window.fetch('/employees/api/v1/auth/session');
    assert.equal(fixture.button('Use existing iPhone pairing'),undefined);
  }
});
test('paired Safari asks for explicit confirmation before linking an installed app',async()=>{
  const elements=new Map(),calls=[];const element=id=>{if(!elements.has(id))elements.set(id,new Element());return elements.get(id);};
  const context={document:{getElementById:element,createElement:tag=>new Element(tag),addEventListener(){}},window:{addEventListener(){}},location:{href:'https://timeclock.deckers.gy/employees/mobile-management/#link=request-1',pathname:'/employees/mobile-management/',hash:'#link=request-1'},history:{replaceState(){}},URLSearchParams,AbortController,setTimeout,clearTimeout,
    fetch:async(route,init)=>{calls.push({route,init});return Response.json({ok:true,data:route==='link-info'?{appName:'Inventory',deviceName:'My iPhone'}:{approved:true}});}};
  vm.runInNewContext(launcher,context);await tick();
  assert.deepEqual(calls.map(call=>call.route),['link-info']);
  assert.match(element('status').textContent,/Inventory.*My iPhone/);
  await element('confirmation').children[0].onclick();assert.equal(calls[1].route,'approve-link');assert.match(element('status').textContent,/Return to your home-screen app/);
});
test('the installed workspace links to the same approved phone without requesting an app grant',async()=>{
  const fixture=app({url:'https://timeclock.deckers.gy/employees/mobile-management/'});
  await fixture.window.fetch('/employees/api/v1/auth/session');
  await fixture.button('Use existing iPhone pairing').onclick();await tick();
  assert.deepEqual(JSON.parse(fixture.calls.find(call=>call.path.endsWith('/link-request')).init.body),{app:'WORKSPACE'});
  assert.equal(fixture.calls.some(call=>call.path.endsWith('/pair')),false);
});

test('re-pair is a separate option directly below existing iPhone pairing and rejects unrelated QR links',async()=>{
 const fixture=app({url:'https://deckersapps.deckers.gy/employees/mobile-management/'});
 await fixture.window.fetch('/employees/api/v1/auth/session');
 const buttons=nodes(fixture.body).filter(node=>node.tag==='button');
 assert.equal(buttons[0].textContent,'Use existing iPhone pairing');assert.equal(buttons[1].textContent,'Scan re-pair QR code');
 const input=nodes(fixture.body).find(node=>node.type==='url'),form=nodes(fixture.body).find(node=>node.tag==='form');
 input.value='https://evil.example/employees/mobile-management/#repair=aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa';form.onsubmit({preventDefault(){}});await tick();
 assert.equal(fixture.calls.some(call=>call.path.endsWith('/repair')),false);
 assert.equal(fixture.location.reloaded,undefined);
});

test('re-pair scans into the current installed app, stops the camera and uses the one-use repair endpoint',async()=>{
 let callback,trackStopped=false,decoderStopped=false;
 const qr={BrowserQRCodeReader:class{async decodeFromStream(stream,video,handler){callback=handler;return {stop(){decoderStopped=true;}};}}};
 const mediaDevices={getUserMedia:async()=>({getTracks:()=>[{stop(){trackStopped=true;}}]})};
 const fixture=app({url:'https://deckersapps.deckers.gy/employees/mobile-management/',qr,mediaDevices});
 await fixture.window.fetch('/employees/api/v1/auth/session');
 await fixture.button('Scan re-pair QR code').onclick();assert.equal(fixture.window.opened,undefined);
 callback({getText:()=> 'https://deckersapps.deckers.gy/employees/mobile-management/#repair=aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa'});await tick();
 const repair=fixture.calls.find(call=>call.path.endsWith('/repair'));assert.ok(repair);
 assert.deepEqual(JSON.parse(repair.init.body),{token:'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa'});assert.equal(repair.init.credentials,'same-origin');
 assert.equal(trackStopped,true);assert.equal(decoderStopped,true);assert.equal(fixture.location.reloaded,true);
 assert.equal(fixture.calls.some(call=>call.path.endsWith('/link-request')||call.path.endsWith('/pair')),false);
});

test('camera denial keeps repair-link fallback available without changing approval',async()=>{
 const qr={BrowserQRCodeReader:class{}};
 const mediaDevices={getUserMedia:async()=>{throw Object.assign(new Error('Denied'),{name:'NotAllowedError'});}};
 const fixture=app({url:'https://deckersapps.deckers.gy/employees/mobile-management/',qr,mediaDevices});
 await fixture.window.fetch('/employees/api/v1/auth/session');await fixture.button('Scan re-pair QR code').onclick();
 assert.ok(nodes(fixture.body).some(node=>node.textContent.includes('Camera permission was not granted')));
 assert.equal(fixture.calls.some(call=>call.path.endsWith('/repair')),false);
 const input=nodes(fixture.body).find(node=>node.type==='url'),form=nodes(fixture.body).find(node=>node.tag==='form');
 input.value='https://deckersapps.deckers.gy/employees/mobile-management/#repair=bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb';form.onsubmit({preventDefault(){}});await tick();
 assert.equal(fixture.calls.filter(call=>call.path.endsWith('/repair')).length,1);assert.equal(fixture.location.reloaded,true);
});

test('cancel stops a camera permission request that resolves after leaving the scanner',async()=>{
 let resolveCamera,trackStopped=false,decoderStarted=false;
 const qr={BrowserQRCodeReader:class{async decodeFromStream(){decoderStarted=true;}}};
 const mediaDevices={getUserMedia:()=>new Promise(resolve=>{resolveCamera=resolve;})};
 const fixture=app({url:'https://deckersapps.deckers.gy/employees/mobile-management/',qr,mediaDevices});
 await fixture.window.fetch('/employees/api/v1/auth/session');const opening=fixture.button('Scan re-pair QR code').onclick();await tick();
 fixture.button('Cancel scan').onclick();resolveCamera({getTracks:()=>[{stop(){trackStopped=true;}}]});await opening;
 assert.equal(trackStopped,true);assert.equal(decoderStarted,false);assert.equal(fixture.calls.some(call=>call.path.endsWith('/repair')),false);
});
