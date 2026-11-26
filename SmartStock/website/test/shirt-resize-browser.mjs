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
const server=spawn(process.execPath,[path.join(root,'node_modules/vite/bin/vite.js'),'--host','127.0.0.1','--port','5183','--strictPort'],{cwd:root,windowsHide:true,stdio:'ignore'});
let browser;
try{
 for(let i=0;i<100;i++){try{if((await fetch('http://127.0.0.1:5183/shop/')).ok)break;}catch{}await new Promise(r=>setTimeout(r,100));}
 browser=await chromium.launch({channel:'msedge',headless:true});
 const page=await browser.newPage({viewport:{width:1400,height:1100}}),errors=[];
 page.on('pageerror',e=>errors.push(e.message));
 const blank={widthIn:0,heightIn:0,singlePresetId:0,fullPresetId:0};
 const shirt={id:1,name:'Resize test shirt',description:'Synthetic',variants:[{id:11,name:'Medium',size:'M',color:'Olive',price:1000}],config:{websiteEnabled:true,useSystemPresets:true,frontMockup:'/shop/shirt-front.png',backMockup:'/shop/shirt-back.png',colors:{Olive:'#6b7042'},vinylColors:['#000000'],areas:{FRONT:{...blank,x:.3,y:.31,w:.4,h:.48},BACK:{...blank,x:.3,y:.31,w:.4,h:.48},CHEST:{...blank,x:.57,y:.3,w:.13,h:.13}}},guides:{FRONT:{},CHEST:{},BACK:{}},addOns:[{id:1,kind:'BOTH',name:'Artwork',mode:'FULL',materialId:1,material:'Transfer paper',price:500}]};
 await page.route('**/shop/api/v1/**',async route=>{
  const name=new URL(route.request().url()).pathname.split('/v1/')[1];
  assert.ok(['stores','catalog'].includes(name),`Browse-only mode called ${name}`);
  const body=name==='stores'?{stores:[{id:1,name:'Synthetic store'}]}:{browseOnly:true,locationId:1,capturedAt:new Date().toISOString(),products:[],projects:[],shirts:[shirt],store:{id:1,name:'Synthetic store'},settings:{currency:'GYD',pickup_hours:48}};
  await route.fulfill({contentType:'application/json',body:JSON.stringify(body)});
 });
 await page.goto('http://127.0.0.1:5183/shop/services/custom-t-shirts');
 await page.getByRole('button',{name:'Customize Resize test shirt'}).click();
 await page.locator('header nav[aria-label="Main"]').getByRole('button',{name:'Services',exact:true}).click();
 await page.getByRole('link').filter({has:page.getByRole('heading',{name:'Shirts',exact:true})}).click();
 await page.getByRole('heading',{name:'Available shirts',exact:true}).waitFor();
 assert.equal(await page.locator('.shirt-designer').count(),0,'Reopening the group must show the catalog');
 await page.getByRole('button',{name:'Customize Resize test shirt'}).click();
 await page.getByRole('button',{name:'← Choose another shirt',exact:true}).click();
 await page.getByRole('heading',{name:'Available shirts',exact:true}).waitFor();
 await page.getByRole('button',{name:'Customize Resize test shirt'}).click();
 await page.getByRole('button',{name:'Add Artwork',exact:true}).click();
 await page.getByLabel('Text',{exact:true}).fill('Nishan');
 const canvas=page.locator('.shirt-preview > canvas');await canvas.waitFor({state:'visible'});
 assert.ok((await page.locator('.shirt-preview').innerText()).includes('10 × 11 inches'));
 assert.ok(Number(await page.getByLabel('Width in inches',{exact:true}).inputValue())>0);
 async function dragCorner(corner,scale,box={x:.3,y:.31,w:.4,h:.44}){
  await canvas.scrollIntoViewIfNeeded();const rect=await canvas.boundingBox();
  const x=Number(await page.getByLabel('Horizontal position',{exact:true}).inputValue()),y=Number(await page.getByLabel('Vertical position',{exact:true}).inputValue());
  const w=Number(await page.getByLabel('Width',{exact:true}).inputValue()),h=Number(await page.getByLabel('Height',{exact:true}).inputValue());
  const sx=[0,1,1,0][corner],sy=[0,0,1,1][corner];
  const start={x:rect.x+(box.x+(x+sx*w)*box.w)*rect.width,y:rect.y+(box.y+(y+sy*h)*box.h)*rect.height};
  const end={x:start.x+(sx?1:-1)*w*(scale-1)*box.w*rect.width,y:start.y+(sy?1:-1)*h*(scale-1)*box.h*rect.height};
  await page.mouse.move(start.x,start.y);await page.mouse.down();await page.mouse.move(end.x,end.y,{steps:8});await page.mouse.up();
  const after=Number(await page.getByLabel('Width',{exact:true}).inputValue());assert.ok(Math.abs(after-w*scale)<.015,`Corner ${corner}: ${after} vs ${w*scale}`);
  await page.getByRole('button',{name:'Undo',exact:true}).click();await page.locator('.shirt-layers button').last().click();
  assert.ok(Math.abs(Number(await page.getByLabel('Width',{exact:true}).inputValue())-w)<.001);
 }
 for(let corner=0;corner<4;corner++)await dragCorner(corner,.75);
 await dragCorner(2,1.1);
 await page.getByLabel('Artwork type',{exact:true}).selectOption('IMAGE');
 await page.getByLabel('Upload Artwork',{exact:true}).setInputFiles({name:'logo.png',mimeType:'image/png',buffer:await readFile(path.join(root,'public/shirt-front.png'))});
 await page.getByRole('button',{name:'2. Artwork — logo.png',exact:true}).waitFor();
 for(let corner=0;corner<4;corner++)await dragCorner(corner,.7);
 await page.screenshot({path:path.join(output,'front-handles.png'),fullPage:true});
 await page.getByRole('button',{name:'Left chest',exact:true}).click();
 await page.getByLabel('Artwork type',{exact:true}).selectOption('TEXT');
 await page.getByRole('button',{name:'Add Artwork',exact:true}).click();await page.getByLabel('Text',{exact:true}).fill('Nishan');
 assert.ok((await page.locator('.shirt-preview').innerText()).includes('3 × 3 inches'));
 await dragCorner(2,.75,{x:.54,y:.32,w:.12,h:.12});
 await page.getByRole('button',{name:'3D preview',exact:true}).click();await page.locator('.shirt-3d-stage[data-ready="true"]').waitFor({timeout:30000});
 await page.getByRole('button',{name:'Front view',exact:true}).click();await page.locator('.shirt-3d-stage').screenshot({path:path.join(output,'chest-3d.png')});
 await page.setViewportSize({width:390,height:844});await page.getByRole('button',{name:'Edit artwork',exact:true}).click();
 await canvas.scrollIntoViewIfNeeded();const rect=await canvas.boundingBox();
 const touch={x:rect.x+(.54+.9*.12)*rect.width,y:rect.y+(.32+.25*.12)*rect.height};
 const cdp=await page.context().newCDPSession(page),before=Number(await page.getByLabel('Width',{exact:true}).inputValue());
 await cdp.send('Input.dispatchTouchEvent',{type:'touchStart',touchPoints:[touch]});
 await cdp.send('Input.dispatchTouchEvent',{type:'touchMove',touchPoints:[{x:touch.x-8,y:touch.y-4}]});
 await cdp.send('Input.dispatchTouchEvent',{type:'touchEnd',touchPoints:[]});
 assert.ok(Number(await page.getByLabel('Width',{exact:true}).inputValue())<before,'Touch corner must resize artwork');
 await page.screenshot({path:path.join(output,'mobile-handles.png'),fullPage:true});
 assert.ok(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+2));
 assert.deepEqual(errors,[]);console.log('Resize browser acceptance passed: four corners for text and images, grow/shrink, undo, letter defaults, chest 3D, mobile touch, no account/estimate requests.');
}finally{await browser?.close();server.kill();}
