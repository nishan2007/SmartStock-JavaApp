/* Run with Node and Playwright available on NODE_PATH. No live store connection. */
const { chromium } = require('playwright');
const { readFileSync } = require('node:fs');
const path = require('node:path');
const assert = require('node:assert/strict');

(async () => {
  const browser = await chromium.launch({headless:true,channel:process.env.SMARTSTOCK_TEST_BROWSER||'msedge'});
  try {
    for (const viewport of [{width:390,height:844},{width:1280,height:900}]) {
      const page=await browser.newPage({viewport});
      const errors=[];page.on('pageerror',e=>errors.push(e.message));
      const permissions=['VIEW','QUOTE','SHIP','ADJUST'].map(p=>'OVERSEAS_ORDERS_'+p);
      let order=null,actions=[],failUploads=1;
      await page.route('https://overseas.test/**',async route=>{
        const url=new URL(route.request().url());
        if(url.pathname==='/employees/'||url.pathname==='/employees/index.html')return route.fulfill({contentType:'text/html',body:readFileSync(path.join(__dirname,'../src/employee-web/index.html'),'utf8')});
        if(['/employees/app.js','/employees/app.css','/employees/overseas.js'].includes(url.pathname))return route.fulfill({contentType:url.pathname.endsWith('.css')?'text/css':'text/javascript',body:readFileSync(path.join(__dirname,'../src/employee-web',path.basename(url.pathname)),'utf8')});
        const routeName=url.pathname.split('/api/v1/')[1];let data={};
        if(routeName==='auth/session')data={csrfToken:'mock-csrf'};
        else if(routeName==='bootstrap')data={userId:1,name:'Quote manager',canBrowse:false,canEdit:false,canOverseas:true,overseasStores:[{id:1,name:'Test store'}]};
        else if(routeName==='history')data={employee:{name:'Quote manager',username:'manager',active:true},start:'2026-10-01',end:'2026-10-20',sessions:[],hasMore:false};
        else if(routeName==='overseas'){
          assert.equal(route.request().headers()['x-csrf-token'],'mock-csrf');
          const b=route.request().postDataJSON();actions.push(b);
          if(b.action==='CUSTOMER_PHONE')data={validPhone:b.phone.replace(/[^0-9]/g,'').length>=7,reserved:b.phone.includes('7058194'),customers:b.phone.includes('7058194')?[{customerId:42,name:'Existing customer',phone:'+5927058194',accountNumber:'CA-42',active:true}]:[]};
          else if(b.action==='SELLERS')data={sellers:['Amazon','Temu']};
          else if(b.action==='LIST')data={permissions,rows:[]};
          else if(b.action==='SAVE'){
            order={...b.order,overseas_order_id:'00000000-0000-0000-0000-000000000001',revision:(order?.revision||0)+1,state:order?.state||'DRAFT',store_name:'Test store',number:'Draft',paid:0,total:Number(b.order.shipping)+b.order.lines.reduce((sum,l)=>sum+Number(l.quantity)*Number(l.unit_price),0),balance:Number(b.order.shipping)+b.order.lines.reduce((sum,l)=>sum+Number(l.quantity)*Number(l.unit_price),0),lines:b.order.lines.map((l,i)=>({...l,line_id:`line-${i}`,purchased:0,shipped:0,arrived:0,collected:0}))};data={order,permissions,events:[],payments:[]};
          }else if(b.action==='ISSUE'){order={...order,state:'ISSUED',number:'OS-1-1',revision:order.revision+1};data={order,permissions,events:[],payments:[]};}
          else if(b.action==='UPLOAD'){
            if(failUploads-- > 0)return route.fulfill({status:503,contentType:'application/json',body:JSON.stringify({ok:false,error:{message:'Screenshot upload unavailable. Try saving again.'}})});
            data={reference:'asset:00000000-0000-0000-0000-000000000001'};
          }
          else if(b.action==='IMAGE')data={data:'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aN1sAAAAASUVORK5CYII='};
          else if(b.action==='SHIPMENT'){order.lines=order.lines.map(l=>({...l,...b.lines.find(u=>u.line_id===l.line_id)}));order.revision++;data={order,permissions,events:[],payments:[]};}
          else if(b.action==='TRACK'){order.lines=order.lines.map(l=>b.lineIds.includes(l.line_id)?{...l,purchased:Number(l.quantity),...(b.stage==='ARRIVED'?{shipped:Number(l.quantity),arrived:Number(l.quantity),tracking:'ARRIVED'}:{tracking:'ORDERED'})}:l);order.revision++;data={order,permissions,events:[],payments:[]};}
          else throw new Error('Unexpected action '+b.action);
        }
        return route.fulfill({contentType:'application/json',body:JSON.stringify({ok:true,data})});
      });
      await page.goto('https://overseas.test/employees/');
      await page.getByRole('button',{name:'New quote',exact:true}).click();
      assert.equal(await page.getByLabel('Extra shipping GYD (optional)',{exact:true}).isVisible(),false);
      await page.getByRole('button',{name:'Continue to items',exact:true}).click();
      assert.equal(await page.getByLabel('Customer name',{exact:true}).isVisible(),true,'Customer is required before continuing');
      await page.getByLabel('Customer name',{exact:true}).fill('Overseas customer');
      await page.getByLabel('WhatsApp / phone',{exact:true}).fill('7058194');
      await page.getByRole('button',{name:'Select Existing customer · CA-42',exact:true}).waitFor();
      await page.getByRole('button',{name:'Continue to items',exact:true}).click();
      assert.equal(await page.getByLabel('Customer name',{exact:true}).isVisible(),true,'Existing phone requires explicit customer selection');
      await page.getByRole('button',{name:'Select Existing customer · CA-42',exact:true}).click();
      assert.equal(await page.getByLabel('Customer name',{exact:true}).inputValue(),'Existing customer');
      await page.getByLabel('WhatsApp / phone',{exact:true}).fill('6145030');
      await page.getByRole('button',{name:'Continue to items',exact:true}).click();
      await page.getByLabel('Customer name',{exact:true}).waitFor({state:'hidden'});
      await page.getByRole('button',{name:'Continue to delivery',exact:true}).click();
      assert.equal(await page.getByText('Add at least one item to the cart.',{exact:true}).isVisible(),true);
      await page.getByRole('button',{name:'Add item',exact:true}).click();
      await page.getByLabel('Description',{exact:true}).fill('Customer shoes');
      await page.getByLabel('Seller',{exact:true}).fill('Amazon');
      await page.getByLabel('Product link',{exact:true}).fill('https://example.com/shoes');
      await page.getByLabel('Quantity',{exact:true}).fill('2');
      await page.getByLabel('Price each GYD (shipping included)',{exact:true}).fill('1000');
      await page.getByText('Purchase cost reference (optional)',{exact:true}).click();
      await page.getByLabel('USD unit cost (optional reference)',{exact:true}).fill('0.01');
      await page.getByLabel('Screenshot PNG / JPEG (under 2 MB)',{exact:true}).setInputFiles({name:'shoes.png',mimeType:'image/png',buffer:Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aN1sAAAAASUVORK5CYII=','base64')});
      await page.getByText('Screenshot ready. It will be uploaded when you save the quote.',{exact:true}).waitFor();
      assert.equal(actions.some(a=>a.action==='UPLOAD'),false,'Screenshot is staged before saving');
      await page.getByRole('button',{name:'Add to cart',exact:true}).click();
      await page.getByRole('button',{name:'Add item',exact:true}).click();
      await page.getByLabel('Description',{exact:true}).fill('Extra item');
      await page.getByLabel('Price each GYD (shipping included)',{exact:true}).fill('500');
      await page.getByRole('button',{name:'Add to cart',exact:true}).click();
      await page.getByRole('button',{name:'Remove item 2',exact:true}).click();
      await page.getByRole('button',{name:'Continue to delivery',exact:true}).click();
      assert.equal(await page.getByLabel('Extra shipping GYD (optional)',{exact:true}).inputValue(),'');
      await page.getByRole('button',{name:'Review cart',exact:true}).click();
      assert.equal(await page.locator('.overseas-cart-totals dd').last().innerText(),'GYD 2000.00','Blank extra shipping adds nothing to shipping-inclusive item prices');
      assert.equal(await page.locator('.overseas-cart-totals').getByText('Extra shipping',{exact:true}).count(),0);
      await page.getByRole('button',{name:'Edit delivery',exact:true}).click();
      await page.getByLabel('Extra shipping GYD (optional)',{exact:true}).fill('100');
      await page.getByRole('button',{name:'Review cart',exact:true}).click();
      assert.equal(await page.locator('.overseas-cart-totals dd').last().innerText(),'GYD 2100.00');
      assert.equal(await page.getByLabel('Extra shipping GYD (optional)',{exact:true}).isVisible(),false);
      await page.getByRole('button',{name:'Edit items',exact:true}).click();
      await page.getByRole('button',{name:'Edit item 1',exact:true}).click();
      assert.equal(await page.getByLabel('Description',{exact:true}).inputValue(),'Customer shoes');
      await page.getByLabel('Quantity',{exact:true}).fill('3');
      await page.getByRole('button',{name:'Add to cart',exact:true}).click();
      await page.getByRole('button',{name:'Continue to delivery',exact:true}).click();
      assert.equal(await page.getByLabel('Extra shipping GYD (optional)',{exact:true}).inputValue(),'100','Shipping survives back/edit navigation');
      await page.getByRole('button',{name:'Review cart',exact:true}).click();
      assert.equal(await page.locator('.overseas-cart-totals dd').last().innerText(),'GYD 3100.00');
      if(process.env.SMARTSTOCK_TEST_SCREENSHOT_DIR)await page.locator('#overseasOrders').screenshot({path:path.join(process.env.SMARTSTOCK_TEST_SCREENSHOT_DIR,`overseas-cart-${viewport.width}.png`)});
      await page.getByRole('button',{name:'Save draft',exact:true}).click();
      await page.getByText('Screenshot upload unavailable. Try saving again.',{exact:true}).waitFor();
      assert.equal(order.state,'DRAFT','An image failure must not issue the quote');
      await page.getByRole('button',{name:'Save draft',exact:true}).click();
      await page.getByText('Quote saved.',{exact:true}).waitFor();
      assert.equal(actions.find(a=>a.action==='SAVE').order.lines[0].usd_cost,'0.01');
      assert.equal(actions.filter(a=>a.action==='UPLOAD').length,2);
      assert.equal(actions.filter(a=>a.action==='UPLOAD')[0].base64,actions.filter(a=>a.action==='UPLOAD')[1].base64,'A failed upload retains the staged screenshot for retry');
      assert.equal(actions.filter(a=>a.action==='SAVE').every(a=>a.order.lines.every(l=>!('_preview' in l)&&!('_pendingScreenshot' in l))),true,'Temporary image data never enters order-save payloads');
      assert.equal(order.lines[0].seller,'Amazon');
      assert.equal(order.lines[0].screenshot,'asset:00000000-0000-0000-0000-000000000001');
      await page.getByRole('button',{name:'Issue quote',exact:true}).click();
      await page.getByRole('heading',{name:'OS-1-1 · ISSUED',exact:true}).waitFor();
      await page.getByText('Store: Deckers Test store',{exact:true}).waitFor();
      await page.getByRole('button',{name:'Mark ordered',exact:true}).click();
      await page.getByRole('button',{name:'Arrived at Deckers Test store',exact:true}).click();
      await page.getByText('Ordered 3/3 · Arrived 3/3 · Collected 0/3',{exact:true}).waitFor();
      assert.equal(await page.getByLabel('Description',{exact:true}).count(),0,'Issued order opens as an overview instead of an editing form');
      await page.getByRole('button',{name:'Update shipment quantities',exact:true}).click();
      for(const label of ['Purchased','Shipped','Arrived'])await page.getByLabel(label,{exact:true}).fill('2');
      await page.getByRole('button',{name:'Save shipment quantities',exact:true}).click();
      await page.getByText('Ordered 2/3 · Arrived 2/3 · Collected 0/3',{exact:true}).waitFor();
      if(process.env.SMARTSTOCK_TEST_SCREENSHOT_DIR)await page.locator('#overseasOrders').screenshot({path:path.join(process.env.SMARTSTOCK_TEST_SCREENSHOT_DIR,`overseas-${viewport.width}.png`)});
      assert.equal(await page.getByRole('button',{name:'Take payment',exact:true}).count(),0);
      assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true,'Phone layout must not overflow');
      await page.getByRole('button',{name:'Sign out',exact:true}).click();
      await page.getByRole('heading',{name:'Welcome back',exact:true}).waitFor();
      assert.equal(await page.locator('#overseasOrders').innerText(),'','Sign-out clears private order data');
      assert.deepEqual(errors,[]);
      await page.close();
    }
    console.log('Overseas web editor passed at phone and desktop widths.');
  } finally {await browser.close();}
})().catch(e=>{console.error(e);process.exitCode=1;});
