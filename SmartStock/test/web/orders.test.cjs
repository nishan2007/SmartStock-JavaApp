const {test}=require('node:test');
const assert=require('node:assert/strict');
const vm=require('node:vm');
const fs=require('node:fs');
const path=require('node:path');
const source=fs.readFileSync(path.join(__dirname,'../../src/mobile-web/orders.js'),'utf8');
async function app(permissions=['ORDERS_MANAGER_DASHBOARD'],sessionError=false,remote=false,scanner={}){
  const elements=new Map(),requests=[];
  const get=id=>{if(!elements.has(id))elements.set(id,{hidden:['workspace','logout','assignment'].includes(id),value:'',innerHTML:'',textContent:'',classList:{toggle(){}},setAttribute(){},showModal(){this.open=true},close(){this.open=false},replaceChildren(){this.innerHTML=''}});return elements.get(id)};
  const order={orderId:9,orderNumber:'CO-9',customer:'<img src=x onerror=alert(1)>',phone:'123',status:'NEW',balance:50,assigned:'',pricingState:'FINAL'};
  const fetch=async(url,init)=>{requests.push({url,...init});if(url==='/employees/health')return {ok:true};const route=url.split('/api/v1')[1];const data=['/session','/auth/session'].includes(route)?{csrfToken:'csrf',permissions}:route==='/orders/dashboard'?{dashboard:{metrics:{overdue:1,dueToday:2,ready:3,unpaid:50},actions:[order]},employees:[],branding:{name:'Sample Company',motto:'Made for you',address:'Sample address',phone:'555-0100',email:'sample@example.test',logo:'javascript:invalid'}}:route==='/orders/lookup'?{found:true,order:{...order,orderId:12,orderNumber:'CO-OLDER',status:'DELIVERED'}}:route==='/orders/details'?{details:'Order details\nAudit History\n2026-10-22 - Assigned to Daniel'}:{saved:true};return {ok:!sessionError,status:sessionError?401:200,json:async()=>sessionError?{ok:false,error:{code:'ACTIVATION_REQUIRED',message:'Scan a current activation QR'}}:{ok:true,data}}};
  vm.runInNewContext(source,{document:{getElementById:get},fetch,Intl,Date,console,URLSearchParams,history:{replaceState(){}},...(remote?{location:{pathname:'/employees/orders.html',hash:'#activate=one-use'}}:{}),crypto:{randomUUID:()=> 'request-key'},...scanner});
  await new Promise(setImmediate);return {get,requests,order};
}
test('unpaired browser cannot load any order data',async()=>{const a=await app([],true);assert.equal(a.requests.length,1);assert.equal(a.get('workspace').hidden,true);assert.match(a.get('notice').textContent,/activation QR/)});
test('employee without dashboard permission cannot load orders',async()=>{const a=await app(['NEW_ITEM']);assert.equal(a.requests.length,1);assert.match(a.get('notice').textContent,/does not have access/)});
test('order data is escaped and search filters the queue',async()=>{const a=await app();assert.match(a.get('orders').innerHTML,/&lt;img/);assert.doesNotMatch(a.get('orders').innerHTML,/<img/);a.get('search').value='missing';a.get('search').oninput();assert.match(a.get('orders').innerHTML,/No orders to show/)});
test('read-only dashboard user sees details without assignment controls',async()=>{const a=await app();a.get('orders').onclick({target:{closest:()=>({dataset:{id:'9'}})}});await new Promise(setImmediate);assert.equal(a.get('detailText').textContent,'Order details');assert.equal(a.get('assignment').hidden,true)});
test('manager save includes CSRF, idempotency and selected store order',async()=>{const a=await app(['MANAGE_CUSTOM_ORDERS']);a.get('orders').onclick({target:{closest:()=>({dataset:{id:'9'}})}});await new Promise(setImmediate);assert.equal(a.get('assignment').hidden,false);a.get('employee').value='4';a.get('status').value='ASSIGNED';await a.get('assignment').onsubmit({preventDefault(){}});const request=a.requests.find(r=>r.url.endsWith('/orders/assign'));assert.equal(request.headers['X-CSRF-Token'],'csrf');assert.equal(request.headers['Idempotency-Key'],'request-key');assert.deepEqual(JSON.parse(request.body),{orderId:9,employeeId:4,status:'ASSIGNED'})});
test('remote iPhone entry redeems the QR and uses employee gateway auth and CSRF',async()=>{
  const a=await app(['MANAGE_CUSTOM_ORDERS'],false,true);
  assert.deepEqual(a.requests.map(r=>r.url),['/employees/health','/employees/api/v1/activate','/employees/api/v1/auth/session','/employees/api/v1/orders/dashboard']);
  assert.equal(JSON.parse(a.requests[1].body).token,'one-use');
  assert.equal(a.requests[3].headers['X-CSRF-Token'],'csrf');
  assert.equal(a.get('signin').hidden,true);assert.equal(a.get('workspace').hidden,false);
});
test('sign-out clears the order queue, metrics and open detail data',async()=>{
  const a=await app();a.get('detailText').textContent='private order';await a.get('logout').onclick();
  assert.equal(a.get('orders').innerHTML,'');assert.equal(a.get('metrics').innerHTML,'');assert.equal(a.get('detailText').textContent,'');assert.equal(a.get('workspace').hidden,true);
});

test('audit history is separate and collapsed whenever an order is opened',async()=>{const a=await app();const click=()=>a.get('orders').onclick({target:{closest:()=>({dataset:{id:'9'}})}});click();await new Promise(setImmediate);assert.equal(a.get('auditHistory').hidden,false);assert.equal(a.get('auditHistory').open,false);assert.equal(a.get('detailText').textContent,'Order details');assert.match(a.get('auditText').textContent,/Assigned to Daniel/);a.get('auditHistory').open=true;a.get('detail').close();click();await new Promise(setImmediate);assert.equal(a.get('auditHistory').open,false)});

test('bundled iPhone decoder reads Code 128 produced by the receipt renderer',()=>{
  const lib=require('../../src/mobile-web/orders-barcode.js'),fixture=require('./fixtures/order-label-code128.json');
  const pixels=new Uint8ClampedArray(require('node:zlib').inflateSync(Buffer.from(fixture.rgbaDeflate,'base64')));
  const canvas={width:fixture.width,height:fixture.height,getContext:()=>({getImageData:()=>({data:pixels})})};
  assert.equal(new lib.BrowserMultiFormatOneDReader().decodeFromCanvas(canvas).getText(),fixture.orderNumber);
});
function scannerMock(){const state={stopped:0,callback:null};const controls={stop(){state.stopped++}};return {state,context:{navigator:{mediaDevices:{getUserMedia(){}}},ZXingBrowser:{BrowserMultiFormatOneDReader:class{async decodeFromConstraints(constraints,video,callback){state.callback=callback;state.constraints=constraints;return controls;}}}},controls};}
test('scanning a label opens the matching order and stops camera decoding',async()=>{
  const scanner=scannerMock(),a=await app(['MANAGE_CUSTOM_ORDERS'],false,false,scanner.context);await a.get('scanOrder').onclick();
  assert.equal(scanner.state.constraints.video.facingMode.ideal,'environment');
  scanner.state.callback({getText:()=> 'CO-9'},null,scanner.controls);await new Promise(setImmediate);
  assert.equal(a.get('search').value,'CO-9');assert.equal(a.get('detailTitle').textContent,'CO-9');assert.equal(a.get('orderScanner').open,false);assert.ok(scanner.state.stopped>0);
  const count=a.requests.length;scanner.state.callback({getText:()=> 'CO-9'},null,scanner.controls);await new Promise(setImmediate);assert.equal(a.requests.length,count);
});
test('scanned historical labels use store lookup and delivered orders stay read-only',async()=>{
  const scanner=scannerMock(),a=await app(['MANAGE_CUSTOM_ORDERS'],false,true,scanner.context);await a.get('scanOrder').onclick();scanner.state.callback({getText:()=> 'CO-OLDER'},null,scanner.controls);await new Promise(setImmediate);
  const request=a.requests.find(r=>r.url.endsWith('/orders/lookup'));assert.deepEqual(JSON.parse(request.body),{barcode:'CO-OLDER'});assert.equal(request.headers['X-CSRF-Token'],'csrf');assert.equal(a.get('detailTitle').textContent,'CO-OLDER');assert.equal(a.get('assignment').hidden,true);
});
test('closing the scanner prevents a late result from opening an order',async()=>{
  const scanner=scannerMock(),a=await app(undefined,false,false,scanner.context);await a.get('scanOrder').onclick();a.get('closeScanner').onclick();scanner.state.callback({getText:()=> 'CO-9'},null,scanner.controls);await new Promise(setImmediate);assert.equal(a.get('detail').open,undefined);assert.equal(a.get('search').value,'');
});

test('company details come from server settings and unsafe logos are rejected',async()=>{const a=await app();assert.equal(a.get('companyName').textContent,'Sample Company');assert.equal(a.get('footerCompany').textContent,'Sample Company');assert.equal(a.get('companyMotto').textContent,'Made for you');assert.equal(a.get('companyAddress').textContent,'Sample address');assert.match(a.get('companyContact').textContent,/555-0100/);assert.equal(a.get('companyLogo').hidden,true);assert.equal(a.get('companyInitial').textContent,'S')});
