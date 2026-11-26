// Synthetic browser acceptance. Never connects to a configured store.
import {createServer} from 'node:http';
import {readFile,mkdir} from 'node:fs/promises';
import {createRequire} from 'node:module';
import assert from 'node:assert/strict';
const require=createRequire(import.meta.url),{chromium}=require('playwright');
const assets=new URL('../src/mobile-web/',import.meta.url),output=new URL('../target/inventory-web-ui/',import.meta.url);
let authorized=true,permissions=['VIEW_INVENTORY'],requests=[];
const items=[
 {kind:'product',id:1,name:'Classic cotton shirt',sku:'SH-001',barcode:'0012345678905',size:'M',color:'Purple',description:'Soft cotton shirt',price:2500,quantity:12,reorder:3,product_type:'INVENTORY',pricing_type:'FIXED'},
 {kind:'custom',id:2,name:'Custom printed mug',sku:'MUG-01',barcode:'0099',description:'Made for your design',price:1800,quantity:2,reorder:4,product_type:'INVENTORY',pricing_type:'FIXED'},
 {kind:'variant',id:3,name:'Custom polo · Navy',sku:'POLO-N',barcode:'0007',price:0,quantity:0,reorder:0,product_type:'INVENTORY',pricing_type:'VARIABLE'},
 {kind:'custom',id:4,name:'Logo design',sku:'DESIGN',price:0,quantity:0,reorder:0,product_type:'SERVICE',pricing_type:'VARIABLE'}
];
const server=createServer(async(req,res)=>{try{
 const path=new URL(req.url,'http://localhost').pathname;res.setHeader('Cache-Control','no-store');
 if(path.includes('/api/v1/')){
  let text='';for await(const chunk of req)text+=chunk;const b=JSON.parse(text||'{}'),route=path.split('/api/v1/')[1];
  res.setHeader('Content-Type','application/json');const send=data=>res.end(JSON.stringify({ok:true,data}));
  if(route==='auth/login'){authorized=true;return send({})}
  if(route==='auth/logout'){authorized=false;return send({})}
  if(!authorized){res.statusCode=401;return res.end(JSON.stringify({ok:false,error:{message:'Sign in again.'}}))}
  if(route==='auth/session')return send({csrfToken:'test-csrf',permissions});
  assert.equal(req.headers['x-csrf-token'],'test-csrf');requests.push(b);
  const matches=items.filter(i=>(!b.query||[i.name,i.sku,i.barcode].some(v=>v?.toLowerCase().includes(b.query.toLowerCase())))&&(!b.kind||(b.kind==='product'?i.kind==='product':i.kind!=='product'))&&(!b.stock||(i.product_type==='INVENTORY'&&(b.stock==='out'?i.quantity<=0:b.stock==='low'?i.quantity>0&&i.quantity<=i.reorder:i.quantity>0))));
  return send({items:b.offset?matches.slice(2):matches.slice(0,2),hasMore:!b.offset&&matches.length>2,nextOffset:b.offset?matches.length:2,locationId:1,branding:{name:'DECKERS',motto:'Your ideas, made real',address:'Georgetown, Guyana'}});
 }
 if(path==='/employees/health'){res.setHeader('Content-Type','application/json');return res.end(JSON.stringify({ok:true}));}
 if(path==='/employees/branding'){res.setHeader('Content-Type','application/json');return res.end(JSON.stringify({ok:true,data:{name:'DECKERS'}}));}
 const file=path.split('/').pop();if(!/^(inventory\.(html|js|css|webmanifest)|inventory-icon(-\d+\.png|\.svg)|orders-barcode\.js)$/.test(file)){res.statusCode=404;return res.end()}
 res.setHeader('Content-Type',file.endsWith('.js')?'application/javascript':file.endsWith('.css')?'text/css':file.endsWith('.png')?'image/png':file.endsWith('.svg')?'image/svg+xml':'text/html');res.end(await readFile(new URL(file,assets)));
 }catch(e){res.statusCode=500;res.end(String(e));}});
await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));await mkdir(output,{recursive:true});
const browser=await chromium.launch({headless:true,...(process.env.SMARTSTOCK_TEST_BROWSER_CHANNEL?{channel:process.env.SMARTSTOCK_TEST_BROWSER_CHANNEL}:{})});
try{
 const page=await browser.newPage({viewport:{width:390,height:844}}),errors=[];page.on('pageerror',e=>errors.push(e.message));
 await page.goto('http://127.0.0.1:'+server.address().port+'/employees/inventory.html');await page.locator('.item').first().waitFor();
 assert.equal(await page.locator('#company').textContent(),'DECKERS');assert.equal(await page.locator('.item').count(),2);
 await page.locator('#more').click();await page.waitForFunction(()=>document.querySelectorAll('.item').length===4);assert.match(await page.locator('#results').textContent(),/Not tracked/);
 await page.locator('#query').fill('0012345678905');await page.waitForFunction(()=>document.querySelectorAll('.item').length===1);assert.equal(requests.at(-1).query,'0012345678905');
 await page.locator('.item').click();await page.locator('#detail').waitFor({state:'visible'});assert.match(await page.locator('#detailBody').textContent(),/0012345678905/);await page.locator('#closeDetail').click();
 await page.locator('#query').fill('');await page.locator('[data-kind="custom"]').click();await page.locator('#stock').selectOption('low');await page.waitForFunction(()=>document.querySelectorAll('.item').length===1);assert.match(await page.locator('#results').textContent(),/Custom printed mug/);
 await page.locator('#query').fill('<script>');await page.locator('#searchForm').evaluate(f=>f.requestSubmit());await page.locator('.empty').waitFor();assert.equal(await page.locator('#results script').count(),0);
 await page.locator('#query').fill('');await page.locator('#stock').selectOption('');await page.locator('[data-kind=""]').click();await page.locator('.item').first().waitFor();
 assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),'Phone layout overflow');
 await page.screenshot({path:new URL('phone.png',output).pathname.replace(/^\/([A-Za-z]:)/,'$1'),fullPage:true});
 await page.setViewportSize({width:1280,height:900});await page.screenshot({path:new URL('desktop.png',output).pathname.replace(/^\/([A-Za-z]:)/,'$1'),fullPage:true});
 await page.locator('#scan').click();await page.locator('#scanner').waitFor({state:'visible'});await page.locator('#closeScan').click();assert(await page.locator('#scanner').isHidden());
 authorized=false;await page.locator('#refresh').click();await page.locator('#auth').waitFor({state:'visible'});assert.equal(await page.locator('.item').count(),0);
 authorized=true;permissions=['ACCESS_EMPLOYEE_WEB'];await page.reload();await page.waitForFunction(()=>document.getElementById('notice').textContent.includes('View Inventory'));assert(await page.locator('#workspace').isHidden());
 assert.deepEqual(errors,[]);console.log('Inventory browser checks passed: combined list, pagination, typed/barcode search, custom/stock filters, details, escaping, phone/desktop layout, scanner close, session expiry, permission denial.');
}finally{await browser.close();await new Promise(resolve=>server.close(resolve));}
