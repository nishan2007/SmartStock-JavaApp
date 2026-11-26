// Synthetic browser acceptance only. Never connects to a configured store or Supabase.
import {createServer} from 'node:http';
import {readFile,mkdir} from 'node:fs/promises';
import {createRequire} from 'node:module';
import assert from 'node:assert/strict';
const require=createRequire(import.meta.url),{chromium}=require('playwright');
const assets=new URL('../src/employee-web/',import.meta.url),output=new URL('../target/balance-sheet-web-ui/',import.meta.url);
let revision=1,authenticated=true,historyAllowed=true,mutations=0,nextEvent=1,dropNextMutation=false,activations=0;
const drafts=new Map(),events=[],replies=new Map();
const sheet={balanceBf:0,cashInHand:0,totalIncome:100,totalReceivables:0,totalExpenses:0,totalPayables:0,balanceCf:100,
 income:[{label:'SALE CASH',amount:100}],receivables:[],expenses:[],payables:[],drawerCash:[],deviceSales:[],deviceOrders:[],devicePayments:[],accountPayments:[],bankTransactions:[],pendingCheques:[],drawerChecks:[],overseasCash:{broughtForward:0,received:0,expenses:0,carriedForward:0,lines:[]}};
const snapshot=()=>structuredClone({sheet,from:'2026-10-25',to:'2026-10-25',drafts:[...drafts.values()]});
const append=(type,details={})=>{events.unshift({eventId:nextEvent++,revision,eventType:type,employee:'Fixture Employee',device:'Fixture browser',capturedAt:details.capturedAt,receivedAt:new Date().toISOString(),details,after:snapshot(),before:events[0]?.after||null});};
const server=createServer(async(req,res)=>{try{
 const path=new URL(req.url,'http://localhost').pathname;res.setHeader('Cache-Control','no-store');
 if(path.startsWith('/employees/api/v1/')){
  let text='';for await(const chunk of req)text+=chunk;const b=JSON.parse(text||'{}'),route=path.slice('/employees/api/v1/'.length);
  res.setHeader('Content-Type','application/json');const send=data=>res.end(JSON.stringify({ok:true,data}));const fail=(status,message)=>{res.statusCode=status;res.end(JSON.stringify({ok:false,error:{code:status===409?'BALANCE_SHEET_CONFLICT':'SESSION_EXPIRED',message}}));};
  if(route==='activate'){assert.equal(b.token,'fixture-activation-token-1234567890');activations++;return send({activated:true});}
  if(route==='auth/login'){authenticated=true;return send({});}
  if(!authenticated)return fail(401,'Sign in again.');
  if(route==='auth/session')return send({csrfToken:'fixture',userId:12,permissions:['BALANCE_SHEET','EDIT_BALANCE_SHEET',...(historyAllowed?['VIEW_BALANCE_SHEET_HISTORY']:[])]});
  assert.equal(req.headers['x-csrf-token'],'fixture');
  if(route==='auth/logout'){authenticated=false;return send({});}
  assert.equal(route,'balance-sheet');
  if(b.action==='OPEN'||b.action==='POLL')return send({workspaceId:'00000000-0000-0000-0000-000000000001',revision,storeZoneId:'America/Guyana',isAdmin:true,sheet,drafts:[...drafts.values()]});
  if(b.action==='CAPTURE'){
   if(replies.has(b.eventKey))return send(replies.get(b.eventKey));
   const old=drafts.get(b.formId);if((old?.revision||0)!==b.formRevision)return fail(409,'Entry changed in another window.');
   drafts.set(b.formId,{formId:b.formId,revision:b.formRevision+1,content:b.content});revision++;append(b.eventType,b);const result={revision,formRevision:b.formRevision+1};replies.set(b.eventKey,result);return send(result);
  }
  if(b.action==='MUTATE'){
   if(replies.has(b.eventKey))return send(replies.get(b.eventKey));
   if(b.expectedRevision!==revision)return fail(409,'The sheet changed.');mutations++;revision++;
   if(b.change==='ADD_EXPENSE'){sheet.expenses.push({label:b.payload.expense.payee,amount:b.payload.expense.amount});sheet.totalExpenses+=Number(b.payload.expense.amount);}
   append(b.change,b);const result={updated:true,revision,submissionId:42};replies.set(b.eventKey,result);if(dropNextMutation){dropNextMutation=false;res.destroy();return;}return send(result);
  }
  if(b.action==='HISTORY')return send({events:events.filter(e=>!b.before||e.eventId<b.before),hasMore:false});
  if(b.action==='EVENT'){const e=events.find(e=>e.eventId===b.eventId);return send({after:e.after,before:e.before});}
  if(b.action==='READ'){
   if(b.query==='WORKSPACES')return send({rows:[{workspaceId:'00000000-0000-0000-0000-000000000001',from:'2026-10-25',to:'2026-10-25',cashDrawerSessionIds:[]}]});
   if(b.query==='EDIT_CONTEXT')return send({context:{submissionId:42,periodStart:'2026-10-25',periodEnd:'2026-10-25',notes:'Saved notes',eligibility:{editable:true,currentRevision:0},expenses:[],otherIncome:[],auditHistory:[]}});
   if(b.query==='SUBMISSIONS')return send({rows:[{submissionId:42,periodStart:'2026-10-25',periodEnd:'2026-10-25',submittedByName:'Fixture',revisionNo:0,balanceCf:100}]});
   if(b.query==='SUBMISSION')return send({sheet});
   return send({rows:[]});
  }
  throw Error('Unknown fixture action '+b.action);
 }
 const file=path.split('/').at(-1);if(!['balance-sheet.html','balance-sheet.js','balance-sheet.css'].includes(file)){res.statusCode=404;return res.end();}
 res.setHeader('Content-Type',file.endsWith('.js')?'application/javascript':file.endsWith('.css')?'text/css':'text/html');res.end(await readFile(new URL(file,assets)));
}catch(e){res.statusCode=500;res.end(String(e.stack));}});
await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));await mkdir(output,{recursive:true});
const browser=await chromium.launch({headless:true,...(process.env.PLAYWRIGHT_CHANNEL?{channel:process.env.PLAYWRIGHT_CHANNEL}:{})});
try{
 const context=await browser.newContext({viewport:{width:1440,height:1000}}),page=await context.newPage(),errors=[];
 page.on('pageerror',e=>errors.push(e.message));
 await page.goto('http://127.0.0.1:'+server.address().port+'/employees/balance-sheet.html');await page.locator('#workspace').waitFor({state:'visible'});
 await page.locator('#actions button[data-change="ADD_EXPENSE"]').click();
 await page.locator('#entry [name="amount"]').fill('12x');await page.waitForTimeout(750);
 assert(events.some(e=>e.eventType==='INPUT'&&e.details.content.values.amount==='12x'),'Incomplete typing must be recorded');
 await page.locator('#entry [name="amount"]').fill('500');await page.locator('#entry [name="category"]').fill('Other');await page.locator('#entry [name="payee"]').fill('Supplies');await page.locator('#saveEntry').click();await page.locator('#editor').waitFor({state:'hidden'});
 assert.equal(mutations,1);assert.equal(sheet.totalExpenses,500);
 await page.locator('#actions button[data-change="ADD_EXPENSE"]').click();await page.locator('#entry [name="amount"]').fill('50');await page.locator('#entry [name="category"]').fill('Other');await page.locator('#entry [name="payee"]').fill('One-time save');dropNextMutation=true;await page.locator('#saveEntry').click();await page.waitForTimeout(700);assert.equal(mutations,2);if(await page.locator('#editor').isVisible())await page.locator('#saveEntry').click();await page.locator('#editor').waitFor({state:'hidden'});assert.equal(mutations,2,'Retry after a lost response must not post money twice');

 await page.locator('#actions button[data-change="ADD_OTHER_INCOME"]').click();await page.locator('#entry [name="source"]').fill('Cancelled source');await page.locator('#cancelEntry').click();await page.locator('#editor').waitFor({state:'hidden'});
 assert(events.some(e=>e.eventType==='CANCEL'&&e.details.content.values.source==='Cancelled source'));assert.equal(mutations,2);
 await page.locator('#sheetDraft [name="notes"]').fill('Keep my note while totals update');sheet.totalIncome=900;revision++;
 await page.waitForTimeout(2600);assert.equal(await page.locator('#sheetDraft [name="notes"]').inputValue(),'Keep my note while totals update');assert.match(await page.locator('#totals').textContent(),/900/);
 await context.setOffline(true);await page.locator('#sheetDraft [name="notes"]').fill('Offline draft');await page.waitForTimeout(800);assert.match(await page.locator('#connection').textContent(),/Disconnected|Unsaved/);
 await context.setOffline(false);await page.waitForTimeout(2600);assert(events.some(e=>e.details.content?.values?.notes==='Offline draft'),'Reconnection must drain queued input');
 await page.locator('[data-tab="saved"]').click();await page.getByRole('button',{name:'Edit latest',exact:true}).click();await page.locator('#addExpenseRow').click();await page.locator('[data-row-kind="expenses"] [data-field="amount"]').fill('77');await page.waitForTimeout(650);await page.locator('[data-row-kind="expenses"] button').click();await page.locator('#cancelEntry').click();await page.locator('#editor').waitFor({state:'hidden'});
 assert(events.some(e=>e.eventType==='REMOVE'&&e.details.content.rows.expenses.length===0),'Removed revision rows must be recorded');
 await page.locator('[data-tab="history"]').click();await page.locator('#timeline .event').first().waitFor();await page.locator('#timeline .event button').first().click();await page.locator('#viewer').waitFor({state:'visible'});assert.match(await page.locator('#viewerContent').textContent(),/Complete snapshot/);await page.locator('#closeViewer').click();
 await page.locator('#timeline input').nth(0).check();await page.locator('#timeline input').nth(1).check();await page.locator('#compare').click();await page.locator('#viewer').waitFor({state:'visible'});await page.locator('#closeViewer').click();
 await page.screenshot({path:new URL('desktop.png',output).pathname.replace(/^\/([A-Za-z]:)/,'$1'),fullPage:true});
 await page.setViewportSize({width:390,height:844});await page.locator('[data-tab="current"]').click();assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),'Phone page must not overflow horizontally');await page.screenshot({path:new URL('phone.png',output).pathname.replace(/^\/([A-Za-z]:)/,'$1'),fullPage:true});
 assert.deepEqual(errors,[]);
 authenticated=false;await page.locator('#refresh').click();await page.locator('#auth').waitFor({state:'visible'});assert(await page.locator('#workspace').isHidden());await context.close();
 authenticated=true;historyAllowed=false;const limited=await browser.newContext(),limitedPage=await limited.newPage();await limitedPage.goto('http://127.0.0.1:'+server.address().port+'/employees/balance-sheet.html');await limitedPage.locator('#workspace').waitFor({state:'visible'});assert(await limitedPage.locator('#historyTab').isHidden());await limited.close();
 authenticated=false;const fresh=await browser.newContext(),activationPage=await fresh.newPage();await activationPage.goto('http://127.0.0.1:'+server.address().port+'/employees/balance-sheet.html');await activationPage.locator('#auth').waitFor({state:'visible'});await activationPage.locator('#activation summary').click();await activationPage.locator('#activationLink').fill('https://evil.example/#activate=fixture-activation-token-1234567890');await activationPage.locator('#activateBrowser button').click();assert.equal(activations,0);await activationPage.locator('#activationLink').fill('https://timeclock.deckers.gy/employees/#activate=fixture-activation-token-1234567890');await activationPage.locator('#activateBrowser button').click();await activationPage.waitForFunction(()=>document.getElementById('notice').textContent.includes('Browser activated'));assert.equal(activations,1);assert.equal(await activationPage.locator('#activationLink').inputValue(),'');assert.match(activationPage.url(),/127.0.0.1/);await fresh.close();
console.log('Balance sheet browser checks passed: typing, posting, cancellation, reconnect, live updates, row removal, snapshots, comparison, phone layout.');
}finally{await browser.close();await new Promise(resolve=>server.close(resolve));}
