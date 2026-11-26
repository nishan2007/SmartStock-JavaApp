// Synthetic browser acceptance; never connects to the store.
import {createServer} from 'node:http';
import {readFile,mkdir} from 'node:fs/promises';
import {fileURLToPath} from 'node:url';
import {createRequire} from 'node:module';
import assert from 'node:assert/strict';

const {chromium}=createRequire(import.meta.url)('playwright');
const account={customerId:1,accountNumber:'C001',name:'Test Customer',phone:'6001234',email:'test@example.com',
  active:true,creditLimit:5000,currentBalance:1000,totalDue:1000,availableCredit:4000,customerSince:2020};
const sale={eventId:'SALE:4',locationId:1,transactionType:'SALE',documentNumber:'S-4',amount:1000,createdAtEpochMillis:Date.now()};
const order={eventId:'CUSTOM_ORDER:2',locationId:1,transactionType:'CUSTOM_ORDER',documentNumber:'O-2',amount:1500,createdAtEpochMillis:Date.now()-86400000};
const historyRows=[sale,...Array.from({length:9},(_,index)=>({
  eventId:'LEDGER:'+index,locationId:1,transactionType:'MANUAL_CHARGE',amount:10,createdAtEpochMillis:Date.now()-1000-index
}))];
let saved,adjustment,restricted=false,retryKey;
async function api(req,route){
  let text='';
  for await(const chunk of req)text+=chunk;
  const body=JSON.parse(text||'{}');
  if(route==='auth/session')return {csrfToken:'test',permissions:restricted?['CUSTOMER_ACCOUNTS']:
    ['CUSTOMER_ACCOUNTS','SET_CREDIT_LIMIT','EDIT_ACCOUNT_NUMBER']};
  assert.equal(req.headers['x-csrf-token'],'test');
  switch(route){
    case 'customers/list': return {accounts:[account]};
    case 'customers/details': return account;
    case 'customers/history':
      return body.query?{transactions:body.query==='navy'?[order]:[],count:body.query==='navy'?1:0,hasMore:false}:
        {transactions:historyRows,hasMore:false};
    case 'customers/transaction':
      if(body.eventId===order.eventId)return {transaction:order,order:{
        summary:{status:'DELIVERED',customer_name:'Test Customer',total_amount:1500,amount_paid:1500,balance_due:0,
          due_date:'2026-11-24',order_notes:'Call on completion'},
        lines:[{custom_order_line_id:1,item_name:'Custom polo',item_color:'Navy',unit_price:1500,line_total:1500}],
        payments:[{payment_action:'PAYMENT',payment_amount:1500,payment_method:'CARD',payment_reference:'ORDER-PAY-2'}],
        activity:[{action_type:'CREATE',user_name:'Fixture employee'}]
      }};
      return {transaction:sale,sale:{summary:{receipt_number:'S-4',total_amount:1000,amount_paid:0,payment_method:'ACCOUNT'},
        items:[{item_name:'Test shirt',quantity:1,unit_price:1000,line_total:1000}]}};
    case 'customers/save': saved=body;return {customerId:body.customerId||2};
    case 'customers/payment-options':
      return {balances:[{documentType:'SALE',documentId:4,documentNumber:'S-4',balanceDue:1000}],drawers:[]};
    case 'customers/record-adjustment':
      adjustment=body;
      if(!retryKey){retryKey=body.idempotencyKey;throw new Error('Simulated lost response');}
      assert.equal(body.idempotencyKey,retryKey);
      return {transactionId:1};
    default: throw new Error('Unexpected fixture route '+route);
  }
}
const server=createServer(async(req,res)=>{
  try{
    const path=new URL(req.url,'http://localhost').pathname;
    if(path.includes('/api/v1/')){
      res.setHeader('Content-Type','application/json');
      res.end(JSON.stringify({ok:true,data:await api(req,path.split('/api/v1/')[1])}));
      return;
    }
    const file=path.split('/').pop();
    const types={html:'text/html',js:'application/javascript',css:'text/css',ttf:'font/ttf'};
    if(!/^(customers\.(html|js|css)|minutes-icons\.ttf)$/.test(file)){res.statusCode=404;res.end();return;}
    res.setHeader('Content-Type',types[file.split('.').pop()]);
    res.end(await readFile(new URL('../src/mobile-web/'+file,import.meta.url)));
  }catch(error){res.statusCode=500;res.end(String(error));}
});
await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
const browser=await chromium.launch({headless:true,channel:'msedge'});
const output=new URL('../target/customers-web-ui/',import.meta.url);
try{
  const url='http://127.0.0.1:'+server.address().port+'/employees/customers.html';
  const page=await browser.newPage({viewport:{width:390,height:844}}),errors=[];
  page.on('pageerror',error=>errors.push(error.message));
  await page.goto(url);
  await page.locator('[data-id="1"]').waitFor();
  await page.locator('#query').fill('missing');
  assert.equal(await page.locator('[data-id]').count(),0);
  await page.locator('#query').fill('C001');
  await page.locator('[data-id="1"]').click();
  await page.locator('#historyRows button').first().waitFor();
  assert.equal(await page.locator('#historyRows button').count(),10);
  assert.equal(await page.locator('#accountTotal').textContent(),'G$1,000');
  await page.locator('#historyRows button').first().click();
  await page.getByText('Test shirt').waitFor();
  await page.locator('#closeTransaction').click();

  await page.locator('#historyQuery').fill('navy');
  await page.getByRole('button',{name:/Order O-2/}).click();
  await page.locator('#orderSections').waitFor();
  await page.getByRole('button',{name:'Items',exact:true}).click();
  await page.getByText('Navy',{exact:true}).waitFor();
  await page.getByRole('button',{name:'Payments',exact:true}).click();
  await page.getByText('ORDER-PAY-2').waitFor();
  await page.getByRole('button',{name:'Activity',exact:true}).click();
  await page.getByText('Audit history',{exact:true}).click();
  await page.getByText('Fixture employee',{exact:false}).waitFor();
  await page.locator('#closeTransaction').click();
  assert.equal(await page.locator('#historyQuery').inputValue(),'navy');

  await page.locator('#editAccount').click();
  await page.locator('[name="name"]').fill('Updated Customer');
  await page.locator('#save').click();
  await page.locator('#detail').waitFor({state:'hidden'});
  assert.equal(saved.name,'Updated Customer');
  assert.equal(saved.expectedAccount.name,'Test Customer');

  await page.locator('#recordPayment').click();
  await page.locator('[data-balance-index="0"]').fill('300');
  await page.locator('[name="paymentReference"]').fill('TEST-CARD');
  await page.locator('#saveAdjustment').click();
  await page.getByText('The store connection is unavailable.').waitFor();
  await page.locator('#saveAdjustment').click();
  await page.locator('#accountAdjustment').waitFor({state:'hidden'});
  assert.equal(adjustment.amount,300);
  assert.deepEqual(adjustment.allocations,[{documentType:'SALE',documentId:4,amount:300}]);

  await page.locator('#backToCustomers').click();
  await page.locator('#add').click();
  await page.locator('[name="name"]').fill('New Customer');
  await page.locator('#save').click();
  await page.locator('#detail').waitFor({state:'hidden'});
  assert.equal(saved.name,'New Customer');
  assert.equal(saved.customerId,undefined);
  assert.equal(saved.active,true);
  assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true);
  assert.deepEqual(errors,[]);

  restricted=true;
  await page.reload();
  await page.locator('[data-id="1"]').click();
  await page.locator('#editAccount').click();
  assert.equal(await page.locator('[name="accountNumber"]').isDisabled(),true);
  assert.equal(await page.locator('[name="creditLimit"]').isDisabled(),true);

  if(process.env.CUSTOMERS_WORKSPACE_FIXTURE){
    const screen=await browser.newPage({viewport:{width:390,height:844}});
    await screen.goto(process.env.CUSTOMERS_WORKSPACE_FIXTURE);
    await screen.getByRole('button',{name:'Customer Accounts',exact:true}).click();
    await screen.getByRole('button',{name:/Morgan Ellis/}).click();
    await screen.getByRole('button',{name:/Sale S-10482/}).waitFor();
    await mkdir(output,{recursive:true});
    const shot=name=>screen.screenshot({path:fileURLToPath(new URL(name+'.png',output))});
    await shot('account');
    await screen.getByRole('button',{name:/Sale S-10482/}).click();
    await screen.getByText('Embroidery',{exact:true}).waitFor();
    await shot('sale');
    await screen.getByRole('button',{name:'Close transaction details'}).click();
    await screen.locator('#historyQuery').fill('O-2086');
    await screen.getByText('1 result',{exact:true}).waitFor();
    await screen.getByRole('button',{name:/Order O-2086/}).click();
    await screen.getByText('Order overview',{exact:true}).waitFor();
    await shot('order-overview');
    await screen.getByRole('button',{name:'Items',exact:true}).click();
    await shot('order-items');
    await screen.getByRole('button',{name:'Payments',exact:true}).click();
    await shot('order-payments');
    await screen.getByRole('button',{name:'Activity',exact:true}).click();
    await screen.getByText('Order activity',{exact:true}).waitFor();
    await shot('order-activity');
    await screen.getByRole('button',{name:'Close transaction details'}).click();
    assert.equal(await screen.locator('#historyQuery').inputValue(),'O-2086');
    await screen.setViewportSize({width:320,height:760});
    assert.equal(await screen.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true);
    await screen.setViewportSize({width:1280,height:900});
    assert.equal(await screen.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true);
  }
  console.log('Customer browser account, history, sale, order, permissions and payment retry passed.');
}finally{
  await browser.close();
  server.close();
}
