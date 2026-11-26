// Synthetic browser acceptance. No live requests, customers, or uploads.
import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
import {spawn} from 'node:child_process';
import {mkdir,readFile} from 'node:fs/promises';
import path from 'node:path';
const require=createRequire(import.meta.url);
const {chromium}=process.env.CODEX_NODE_MODULES?require(path.join(process.env.CODEX_NODE_MODULES,'playwright')):require('playwright');
const root=path.resolve(import.meta.dirname,'..'),output=path.resolve(root,'../../output/shirt-resize');
await mkdir(output,{recursive:true});
const server=spawn(process.execPath,[path.join(root,'node_modules/vite/bin/vite.js'),'--host','127.0.0.1','--port','5187','--strictPort'],{cwd:root,windowsHide:true,stdio:'ignore'});
let browser;
try{
 for(let i=0;i<100;i++){try{if((await fetch('http://127.0.0.1:5187/shop/')).ok)break;}catch{}await new Promise(r=>setTimeout(r,100));}
 browser=await chromium.launch({channel:'msedge',headless:true});
 const page=await browser.newPage({viewport:{width:1400,height:1100}}),errors=[];
 page.on('pageerror',e=>errors.push(e.message));
 const blank={widthIn:0,heightIn:0,singlePresetId:0,fullPresetId:0};
 const shirt={id:1,name:'Resize test shirt',description:'Synthetic',variants:[{id:11,name:'Medium',size:'M',color:'Olive',price:1000}],config:{websiteEnabled:true,useSystemPresets:true,frontMockup:'/shop/shirt-front.png',backMockup:'/shop/shirt-back.png',colors:{Olive:'#6b7042'},vinylColors:['#000000'],areas:{FRONT:{...blank,x:.3,y:.31,w:.4,h:.48},BACK:{...blank,x:.3,y:.31,w:.4,h:.48},CHEST:{...blank,x:.57,y:.3,w:.13,h:.13}}},guides:{FRONT:{},CHEST:{},BACK:{}},addOns:[{id:1,kind:'TEXT',widthIn:8,heightIn:3,name:'Per line 2inx10in',mode:'FULL',materialId:1,material:'Transfer paper',price:500},{id:2,kind:'BOTH',widthIn:4,heightIn:4,name:'Logo',mode:'FULL',materialId:1,material:'Transfer paper',price:500}]};
 await page.route('**/shop/api/v1/**',async route=>{
  const name=new URL(route.request().url()).pathname.split('/v1/')[1];
  assert.ok(['stores','catalog'].includes(name),`Browse-only mode called ${name}`);
  const body=name==='stores'?{stores:[{id:1,name:'Synthetic store'}]}:{browseOnly:true,locationId:1,capturedAt:new Date().toISOString(),products:[],projects:[],shirts:[shirt],store:{id:1,name:'Synthetic store'},settings:{currency:'GYD',pickup_hours:48}};
  await route.fulfill({contentType:'application/json',body:JSON.stringify(body)});
 });
 await page.goto('http://127.0.0.1:5187/shop/services/custom-t-shirts');
 await page.getByRole('button',{name:'Customize Resize test shirt'}).click();
 await page.getByRole('button',{name:'Add Per line 2inx10in',exact:true}).click();
 assert.equal(Number(await page.getByLabel('Width in inches',{exact:true}).inputValue()),8);
 assert.equal(Number(await page.getByLabel('Height in inches',{exact:true}).inputValue()),3);
 await page.getByLabel('Text',{exact:true}).fill('TESTING PRINT GUIDE');
 await page.getByLabel('Font size (pt)',{exact:true}).fill('36');
 const canvas=page.locator('.shirt-preview > canvas');await canvas.waitFor();await page.waitForTimeout(200);
 const small=await canvas.evaluate(c=>c.toDataURL());
 await page.getByLabel('Font size (pt)',{exact:true}).fill('240');
 await page.waitForFunction(old=>document.querySelector('.shirt-preview > canvas').toDataURL()!==old,small);
 assert.equal(Number(await page.getByLabel('Height in inches',{exact:true}).inputValue()),3);
 assert.equal(Number(await page.getByLabel('Width in inches',{exact:true}).inputValue()),8);
 await canvas.screenshot({path:path.join(output,'guide-clipping.png')});
 await page.getByLabel('Choose an add-on',{exact:true}).selectOption('2');
 await page.getByLabel('Artwork type',{exact:true}).selectOption('IMAGE');
 await page.getByLabel('Upload Logo',{exact:true}).setInputFiles({name:'logo.png',mimeType:'image/png',buffer:await readFile(path.join(root,'public/shirt-front.png'))});
 await page.getByRole('button',{name:'2. Logo — logo.png',exact:true}).waitFor();
 assert.equal(Number(await page.getByLabel('Width in inches',{exact:true}).inputValue()),4);
 assert.equal(Number(await page.getByLabel('Height in inches',{exact:true}).inputValue()),4);
 const widthBefore=await page.getByLabel('Width in inches',{exact:true}).inputValue();
 await page.getByLabel('Image zoom',{exact:true}).fill('1');await page.waitForTimeout(200);
 const beforeZoom=await canvas.evaluate(c=>c.toDataURL());
 await page.getByLabel('Image zoom',{exact:true}).fill('3');
 await page.waitForFunction(old=>document.querySelector('.shirt-preview > canvas').toDataURL()!==old,beforeZoom);
 assert.equal(await page.getByLabel('Width in inches',{exact:true}).inputValue(),widthBefore);
 assert.equal(await page.getByLabel('Image zoom',{exact:true}).inputValue(),'3');
 await page.getByRole('button',{name:'3D preview',exact:true}).click();await page.locator('.shirt-3d-stage[data-ready="true"]').waitFor();
 await page.waitForTimeout(350);await page.reload();await page.getByRole('button',{name:'Customize Resize test shirt'}).click();
 await page.getByRole('button',{name:'2. Logo — logo.png',exact:true}).click();
 assert.equal(await page.getByLabel('Image zoom',{exact:true}).inputValue(),'3');
 assert.deepEqual(errors,[]);console.log('Shared preset browser acceptance passed: explicit 8×3 dimensions override the 2×10 name, logo uses the shared 4×4 size, font and zoom retain their box.');
}finally{await browser?.close();server.kill();}
