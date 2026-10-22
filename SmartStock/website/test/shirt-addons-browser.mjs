// Synthetic end-to-end coverage; no live store, uploads, or accounts.
import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
import {spawn} from 'node:child_process';
import {mkdir,readFile} from 'node:fs/promises';
import path from 'node:path';
const require=createRequire(import.meta.url);
const {chromium}=process.env.CODEX_NODE_MODULES?require(path.join(process.env.CODEX_NODE_MODULES,'playwright')):require('playwright');
const website=path.resolve(import.meta.dirname,'..'),output=path.resolve(website,'../../output/shirt-addons');await mkdir(output,{recursive:true});
const server=spawn(process.execPath,[path.join(website,'node_modules/vite/bin/vite.js'),'--host','127.0.0.1','--port','5182','--strictPort'],{cwd:website,windowsHide:true,stdio:'ignore'});
let browser;
try{
 for(let i=0;i<100;i++){try{if((await fetch('http://127.0.0.1:5182/shop/')).ok)break;}catch{}await new Promise(r=>setTimeout(r,100));}
 browser=await chromium.launch({channel:'msedge',headless:true});const page=await browser.newPage({viewport:{width:1400,height:1000}}),errors=[];page.on('pageerror',e=>errors.push(e.message));
 const areas={FRONT:{x:.3,y:.31,w:.4,h:.48,widthIn:10,heightIn:12},CHEST:{x:.57,y:.3,w:.13,h:.13,widthIn:3,heightIn:3},BACK:{x:.3,y:.31,w:.4,h:.48,widthIn:10,heightIn:12}};
 const addOns=[{id:10,materialId:1,name:'3in line',material:'Vinyl',price:50,pricingMode:'PER_LINE',kind:'TEXT',mode:'SINGLE',widthIn:3,heightIn:.5},{id:11,materialId:2,name:'Logo',material:'Transfer paper',price:150,pricingMode:'FIXED_PRESET',kind:'BOTH',mode:'FULL',widthIn:3,heightIn:3},{id:12,materialId:3,name:'Crest',material:'Embroidery',price:700,pricingMode:'FIXED_PRESET',kind:'BOTH'}];
 const shirt={id:1,name:'Custom tee',variants:[{id:11,name:'Medium black',size:'M',color:'Black',price:1000}],config:{useSystemPresets:true,websiteEnabled:true,frontMockup:'/shop/shirt-front.png',backMockup:'/shop/shirt-back.png',colors:{Black:'#272727'},vinylColors:['#ffffff','#ff0000'],areas},guides:{FRONT:{},CHEST:{},BACK:{}},addOns};
 let latest=null,submitted=null;const requests=[];
 function estimate(d){latest=d;const charges=Object.entries(d.areas).flatMap(([area,a])=>a.layers.map((l,layerIndex)=>({...addOns.find(o=>o.id===l.presetId),area,layerIndex,description:l.text||'Uploaded logo'})));const prints=charges.reduce((n,c)=>n+c.price,0),quantity=d.groups[0].quantity,total=(1000+prints)*quantity;return {fingerprint:'synthetic',itemId:1,itemName:shirt.name,quantity,lines:[{...shirt.variants[0],quantity,printPrice:prints,total}],charges,subtotal:total,vat:0,rounding:0,total};}
 await page.route('**/shop/api/v1/**',async route=>{const name=new URL(route.request().url()).pathname.split('/v1/')[1],body=route.request().postDataJSON()||{};let result={};
  if(name==='stores')result={stores:[{id:1,name:'Synthetic store',timezone:'America/Guyana'}]};
  else if(name==='catalog')result={locationId:1,capturedAt:new Date().toISOString(),products:[],projects:[],shirts:[shirt],unavailableServices:[],store:{id:1,name:'Synthetic store'},settings:{enabled:true,currency:'GYD',pickup_hours:48}};
  else if(name==='auth/session')result={email:'shirt@example.test',csrf:'test-csrf'};
  else if(name==='favorite-state')result={favorites:[]};
  else if(name==='shirt-estimate')result=estimate(body.shirtDesign);
  else if(name==='custom-quote'){submitted=body;result={requestId:body.requestId,status:'AWAITING_ARTWORK'};}
  else if(name==='custom-quote-file'){requests.push(body);result={saved:true};}
  else if(name==='shirt-finalize')result={requestId:body.requestId,status:'REQUESTED'};
  await route.fulfill({contentType:'application/json',body:JSON.stringify(result)});
 });
 await page.goto('http://127.0.0.1:5182/shop/services/custom-t-shirts');await page.getByLabel('Choose an add-on').waitFor();await page.getByRole('button',{name:'Edit artwork',exact:true}).click();
 await page.getByLabel('Print material',{exact:true}).selectOption('2');await page.getByLabel('Choose an add-on').selectOption('11');await page.getByLabel('Artwork type',{exact:true}).selectOption('IMAGE');
 const before=await page.locator('.shirt-preview > canvas').evaluate(c=>c.toDataURL());
 await page.getByLabel('Upload Logo',{exact:true}).setInputFiles({name:'logo.png',mimeType:'image/png',buffer:await readFile(path.join(website,'public/shirt-front.png'))});
 await page.getByRole('button',{name:'1. Logo — logo.png',exact:true}).waitFor();await page.waitForFunction(()=>document.querySelector('.shirt-estimate strong')?.textContent?.includes('1,150'));
 await page.waitForFunction(before=>document.querySelector('.shirt-preview > canvas').toDataURL()!==before,before);
 assert.equal(latest.areas.FRONT.layers[0].presetId,11);assert.equal(latest.areas.FRONT.layers[0].type,'IMAGE');
 await page.getByRole('button',{name:'Large back',exact:true}).click();await page.getByLabel('Print material',{exact:true}).selectOption('1');await page.getByLabel('Choose an add-on').selectOption('10');await page.getByRole('button',{name:'Add 3in line',exact:true}).click();await page.getByLabel('Text',{exact:true}).fill('FIRST LINE');
 await page.getByRole('button',{name:'Add 3in line',exact:true}).click();await page.getByLabel('Text',{exact:true}).fill('SECOND LINE');await page.getByLabel('Element vinyl color').selectOption('#ff0000');
 await page.waitForFunction(()=>document.querySelector('.shirt-estimate strong')?.textContent?.includes('1,250'));
 assert.equal(latest.areas.BACK.layers.length,2);assert.ok(latest.areas.BACK.layers[1].y>latest.areas.BACK.layers[0].y+latest.areas.BACK.layers[0].h);assert.equal(latest.areas.BACK.layers[1].w,.3);assert.equal(latest.areas.BACK.layers[1].vinylColor,'#ff0000');
 await page.getByRole('button',{name:'Delete layer',exact:true}).click();await page.waitForFunction(()=>document.querySelector('.shirt-estimate strong')?.textContent?.includes('1,200'));await page.getByRole('button',{name:'Undo',exact:true}).click();await page.waitForFunction(()=>document.querySelector('.shirt-estimate strong')?.textContent?.includes('1,250'));
 addOns.push({id:13,materialId:4,name:'Future line',material:'Future material',price:60,pricingMode:'PER_LINE',kind:'TEXT'});
 await page.reload();await page.getByLabel('Choose an add-on').waitFor();await page.getByRole('button',{name:'Large back',exact:true}).click();await page.getByRole('button',{name:'2. 3in line — SECOND LINE',exact:true}).click();assert.equal(await page.getByLabel('Text',{exact:true}).inputValue(),'SECOND LINE');
 // Mix a full-color logo and vinyl text on the same side without flattening the logo.
 await page.getByLabel('Print material',{exact:true}).selectOption('2');await page.getByLabel('Choose an add-on').selectOption('11');await page.getByLabel('Artwork type',{exact:true}).selectOption('IMAGE');await page.getByRole('button',{name:'Place image',exact:true}).click();await page.waitForFunction(()=>document.querySelector('.shirt-estimate strong')?.textContent?.includes('1,400'));assert.equal(latest.areas.BACK.layers[2].printMode,'FULL');assert.equal(latest.areas.BACK.layers[1].printMode,'SINGLE');
 await page.getByRole('button',{name:'Left chest',exact:true}).click();await page.getByLabel('Print material',{exact:true}).selectOption('3');await page.getByLabel('Artwork type',{exact:true}).selectOption('IMAGE');await page.getByRole('button',{name:'Place image',exact:true}).click();await page.waitForFunction(()=>document.querySelector('.shirt-estimate strong')?.textContent?.includes('2,100'));assert.equal(latest.areas.CHEST.layers[0].presetId,12);
 await page.getByLabel('Print material',{exact:true}).selectOption('4');await page.getByRole('button',{name:'Add Future line',exact:true}).click();await page.getByLabel('Text',{exact:true}).fill('FUTURE');await page.waitForFunction(()=>document.querySelector('.shirt-estimate strong')?.textContent?.includes('2,160'));assert.equal(latest.areas.CHEST.layers[1].presetId,13);await page.getByLabel('Width in inches',{exact:true}).fill('2.5');await page.waitForTimeout(600);assert.ok(Math.abs(latest.areas.CHEST.layers[1].w*3-2.5)<.001);
 await page.getByRole('button',{name:'Edit artwork',exact:true}).click();await page.screenshot({path:path.join(output,'desktop.png'),fullPage:true});await page.setViewportSize({width:390,height:844});await page.screenshot({path:path.join(output,'mobile.png'),fullPage:true});assert.ok(await page.evaluate(()=>document.documentElement.scrollWidth<=window.innerWidth+2),'Mobile controls must fit');
 await page.getByRole('button',{name:'Submit for final quote',exact:true}).click();await page.getByRole('heading',{name:'Request received.'}).waitFor();assert.equal(submitted.shirtDesign.areas.BACK.layers.length,3);assert.equal(submitted.shirtDesign.areas.FRONT.layers[0].presetId,11);assert.equal(submitted.shirtDesign.areas.CHEST.layers.length,2);assert.equal(requests.length,1);assert.deepEqual(errors,[]);
 console.log('Add-on browser acceptance passed: logo upload/preview, repeated sized lines, separate colors, delete/undo, draft reload, mixed methods, mobile, embroidery, automatic future material/preset, submission.');
}catch(e){const page=browser?.contexts()[0]?.pages()[0];if(page){console.error((await page.locator('body').innerText()).slice(-4000));await page.screenshot({path:path.join(output,'failure.png'),fullPage:true});}throw e;}finally{await browser?.close();server.kill();}
