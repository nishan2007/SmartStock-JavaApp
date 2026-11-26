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
const server=spawn(process.execPath,[path.join(root,'node_modules/vite/bin/vite.js'),'--host','127.0.0.1','--port','5185','--strictPort'],{cwd:root,windowsHide:true,stdio:'ignore'});
let browser;
try{
 for(let i=0;i<100;i++){try{if((await fetch('http://127.0.0.1:5185/shop/')).ok)break;}catch{}await new Promise(r=>setTimeout(r,100));}
 browser=await chromium.launch({channel:'msedge',headless:true});
 const page=await browser.newPage({viewport:{width:1400,height:1100}}),errors=[];
 page.on('pageerror',e=>errors.push(e.message));
 const blank={widthIn:0,heightIn:0,singlePresetId:0,fullPresetId:0};
 const shirt={id:1,name:'Resize test shirt',description:'Synthetic',variants:[{id:11,name:'Medium',size:'M',color:'Olive',price:1000}],config:{shirtType:'COLLARED',neckline:'COLLAR',websiteEnabled:true,useSystemPresets:true,frontMockup:'/shop/shirt-collared-front.png',backMockup:'/shop/shirt-collared-back.png',colors:{Olive:'#6b7042'},vinylColors:['#000000'],areas:{FRONT:{...blank,x:.3,y:.31,w:.4,h:.48},BACK:{...blank,x:.3,y:.31,w:.4,h:.48},CHEST:{...blank,x:.57,y:.3,w:.13,h:.13}}},guides:{FRONT:{},CHEST:{},BACK:{}},addOns:[{id:1,kind:'BOTH',name:'Artwork',mode:'FULL',materialId:1,material:'Transfer paper',price:500}]};
 await page.route('**/shop/api/v1/**',async route=>{
  const name=new URL(route.request().url()).pathname.split('/v1/')[1];
  assert.ok(['stores','catalog'].includes(name),`Browse-only mode called ${name}`);
  const body=name==='stores'?{stores:[{id:1,name:'Synthetic store'}]}:{browseOnly:true,locationId:1,capturedAt:new Date().toISOString(),products:[],projects:[],shirts:[shirt],store:{id:1,name:'Synthetic store'},settings:{currency:'GYD',pickup_hours:48}};
  await route.fulfill({contentType:'application/json',body:JSON.stringify(body)});
 });
 await page.goto('http://127.0.0.1:5185/shop/services/custom-t-shirts');
 await page.getByRole('button',{name:'Customize Resize test shirt'}).click();
 const stage=page.locator('.shirt-3d-stage');await stage.locator('canvas').waitFor();await page.locator('.shirt-3d-stage[data-ready="true"]').waitFor();
 assert.ok((await stage.getAttribute('data-model')).includes('polo.glb?v=3'));
 await page.getByRole('button',{name:'Front view',exact:true}).click();await stage.screenshot({path:path.join(output,'polo-front.png')});
 const model=page.getByLabel('Rotatable 3D shirt preview');
 for(const [name,direction] of [['left',1],['right',-1]]){
  await page.getByRole('button',{name:'Front view',exact:true}).click();
  const r=await model.boundingBox();await page.mouse.move(r.x+r.width*.5,r.y+r.height*.5);await page.mouse.down();await page.mouse.move(r.x+r.width*(.5+direction*.25),r.y+r.height*.5,{steps:15});await page.mouse.up();
  await stage.screenshot({path:path.join(output,'polo-'+name+'.png')});
 }
 await page.getByRole('button',{name:'Back view',exact:true}).click();await stage.screenshot({path:path.join(output,'polo-back.png')});
 await page.getByRole('button',{name:'Add Artwork',exact:true}).click();await page.getByLabel('Text',{exact:true}).fill('POLO');
 await page.getByRole('button',{name:'3D preview',exact:true}).click();await page.locator('.shirt-3d-stage[data-ready="true"]').waitFor();
 await page.getByRole('button',{name:'Front view',exact:true}).click();await stage.screenshot({path:path.join(output,'polo-artwork.png')});
 await page.setViewportSize({width:390,height:844});await page.locator('.shirt-3d-stage[data-ready="true"]').waitFor();
 assert.deepEqual(errors,[]);console.log('Polo browser acceptance passed: front, back, both sides, artwork, mobile, versioned model URL.');
}finally{await browser?.close();server.kill();}
