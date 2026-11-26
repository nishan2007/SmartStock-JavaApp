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
const server=spawn(process.execPath,[path.join(root,'node_modules/vite/bin/vite.js'),'--host','127.0.0.1','--port','5184','--strictPort'],{cwd:root,windowsHide:true,stdio:'ignore'});
let browser;
try{
 for(let i=0;i<100;i++){try{if((await fetch('http://127.0.0.1:5184/shop/')).ok)break;}catch{}await new Promise(r=>setTimeout(r,100));}
 browser=await chromium.launch({channel:'msedge',headless:true});
 const page=await browser.newPage({viewport:{width:1400,height:1100}}),errors=[];
 page.on('pageerror',e=>errors.push(e.message));
 const blank={widthIn:0,heightIn:0,singlePresetId:0,fullPresetId:0};
 const shirt={id:1,name:'Resize test shirt',description:'Synthetic',variants:[{id:11,name:'Medium',size:'M',color:'Olive',price:1000}],config:{websiteEnabled:true,useSystemPresets:true,frontMockup:'/shop/shirt-front.png',backMockup:'/shop/shirt-back.png',colors:{Olive:'#6b7042'},vinylColors:['#000000'],areas:{FRONT:{...blank,x:.3,y:.31,w:.4,h:.48},BACK:{...blank,x:.3,y:.31,w:.4,h:.48},CHEST:{...blank,x:.57,y:.3,w:.13,h:.13}}},guides:{FRONT:{},CHEST:{},BACK:{}},addOns:[{id:1,kind:'BOTH',name:'ARCH',mode:'FULL',materialId:1,material:'Transfer paper',price:500}]};
 await page.route('**/shop/api/v1/**',async route=>{
  const name=new URL(route.request().url()).pathname.split('/v1/')[1];
  assert.ok(['stores','catalog'].includes(name),`Browse-only mode called ${name}`);
  const body=name==='stores'?{stores:[{id:1,name:'Synthetic store'}]}:{browseOnly:true,locationId:1,capturedAt:new Date().toISOString(),products:[],projects:[],shirts:[shirt],store:{id:1,name:'Synthetic store'},settings:{currency:'GYD',pickup_hours:48}};
  await route.fulfill({contentType:'application/json',body:JSON.stringify(body)});
 });
 await page.goto('http://127.0.0.1:5184/shop/services/custom-t-shirts');
 await page.getByRole('button',{name:'Customize Resize test shirt'}).click();
 await page.getByRole('button',{name:'Add ARCH',exact:true}).click();
 await page.getByLabel('Text',{exact:true}).fill('DECKERS');
 assert.equal(await page.getByLabel('Text shape',{exact:true}).inputValue(),'ARCH');
 assert.equal(await page.getByLabel('Arch curve',{exact:true}).inputValue(),'120');
 const canvas=page.locator('.shirt-preview > canvas');await canvas.scrollIntoViewIfNeeded();
 await page.waitForTimeout(250);const arch=await canvas.evaluate(c=>c.toDataURL());
 await page.getByLabel('Text shape',{exact:true}).selectOption('STRAIGHT');
 await page.waitForFunction(before=>document.querySelector('.shirt-preview > canvas').toDataURL()!==before,arch);
 const straight=await canvas.evaluate(c=>c.toDataURL());
 await page.getByLabel('Text shape',{exact:true}).selectOption('ARCH');
 await page.getByLabel('Arch curve',{exact:true}).fill('70');
 await page.waitForFunction(before=>document.querySelector('.shirt-preview > canvas').toDataURL()!==before,straight);
 await page.waitForTimeout(350);await page.reload();
 await page.getByRole('button',{name:'Customize Resize test shirt'}).click();
 await page.getByRole('button',{name:'1. ARCH — DECKERS',exact:true}).click();
 assert.equal(await page.getByLabel('Arch curve',{exact:true}).inputValue(),'70');
 await page.getByRole('button',{name:'Edit artwork',exact:true}).click();
 await canvas.screenshot({path:path.join(output,'arch-2d.png')});
 await page.getByRole('button',{name:'3D preview',exact:true}).click();
 await page.locator('.shirt-3d-stage[data-ready="true"]').waitFor();
 await page.locator('.shirt-3d-stage').screenshot({path:path.join(output,'arch-3d.png')});
 assert.deepEqual(errors,[]);console.log('Arch browser acceptance passed: automatic curve, straight/arch switching, adjustable curve, saved draft reload, 2D and 3D rendering.');
}finally{await browser?.close();server.kill();}
