// UI-only fixture checks. Does not connect to SmartStock, Supabase, or a store database.
// Run with Node and Playwright available (NODE_PATH may point to the bundled packages).
import { createServer } from 'node:http';
import { readFile, mkdir } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import { createRequire } from 'node:module';
import assert from 'node:assert/strict';
const require=createRequire(import.meta.url),{ chromium }=require('playwright');
const assets=new URL('../src/employee-web/',import.meta.url),output=new URL('../target/employee-web-ui/',import.meta.url);
let authenticated=true,activated=true,manager=true,editable=true,directoryCalls=0;
const alice={userId:1,name:'Alice Example',username:'alice',active:true},bob={userId:2,name:'Bob Example',username:'bob',active:false};
const session={clockId:1,revision:'fixture-original',workDate:'2026-10-20',store:'Main Store',timezone:'America/Guyana',clockIn:'2026-10-20T08:00:00-04:00',lunchStart:'2026-10-20T12:00:00-04:00',lunchEnd:'2026-10-20T12:30:00-04:00',breakStart:'2026-10-20T14:00:00-04:00',breakEnd:'2026-10-20T14:15:00-04:00',clockOut:'2026-10-20T17:00:00-04:00',hours:8.25,calculatedPay:825,provisional:false,autoClockOut:true,clockOutReview:'PENDING_REVIEW'};
const server=createServer(async(req,res)=>{
  try{
    const route=new URL(req.url,'http://localhost').pathname;
    res.setHeader('Cache-Control','no-store');
    if(route.startsWith('/employees/api/v1/')){
      let text='';for await(const chunk of req)text+=chunk;
      const body=JSON.parse(text||'{}'),action=route.slice('/employees/api/v1/'.length);
      res.setHeader('Content-Type','application/json');
      const send=data=>res.end(JSON.stringify({ok:true,data}));
      if(action==='activate'){activated=true;return send({activated:true});}
      if(!activated){res.statusCode=403;return res.end(JSON.stringify({ok:false,error:{code:'DEVICE_REQUIRED',message:'Scan an activation QR first.'}}));}
      if(action==='auth/login'){
        if(body.identifier==='mfa')return send({mfaRequired:true,preauth:'fixture'});
        authenticated=true;return send({csrfToken:'fixture-csrf'});
      }
      if(action==='auth/verify'){assert.equal(body.code,'123456');authenticated=true;return send({csrfToken:'fixture-csrf'});}
      if(!authenticated){res.statusCode=401;return res.end(JSON.stringify({ok:false,error:{code:'SESSION_REQUIRED',message:'Sign in to continue.'}}));}
      if(action==='auth/session')return send({csrfToken:'fixture-csrf'});
      if(action==='auth/logout'){authenticated=false;return send({});}
      if(action==='bootstrap')return send({userId:1,name:alice.name,canBrowse:manager,canEdit:manager&&editable});
      if(action==='history/correct'){
        assert.equal(manager&&editable,true);assert.equal(body.revision,session.revision);assert.equal(body.correction.reason,'N/A');
        assert.match(body.correction.clockIn,/2026-10-20T07:30/);
        assert.equal(body.correction.lunchStart,'2026-10-20T12:00:00');
        session.clockIn=body.correction.clockIn+'-04:00';session.hours=8.75;session.calculatedPay=875;session.revision='fixture-corrected';return send({corrected:true});
      }
      if(action==='employees'){
        directoryCalls++;assert.equal(manager,true);
        return send({employees:[alice,...(body.includeInactive?[bob]:[])].filter(e=>`${e.name} ${e.username}`.toLowerCase().includes(body.search.toLowerCase())),hasMore:false});
      }
      if(action==='history')return send({employee:body.userId===2?bob:alice,start:body.start||'2026-10-16',end:body.end||'2026-10-31',sessions:body.start?.startsWith('1999')?[]:[session],hasMore:false});
      throw new Error(`Unexpected API ${action}`);
    }
    const file=route==='/employees/'?'index.html':route.slice('/employees/'.length);
    if(!['index.html','app.js','app.css'].includes(file)){res.statusCode=404;return res.end();}
    res.setHeader('Content-Type',file.endsWith('.js')?'application/javascript':file.endsWith('.css')?'text/css':'text/html');
    res.end(await readFile(new URL(file,assets)));
  }catch(e){res.statusCode=500;res.end(String(e));}
});
await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
const origin=`http://127.0.0.1:${server.address().port}`,browser=await chromium.launch({headless:true,...(process.env.PLAYWRIGHT_CHANNEL?{channel:process.env.PLAYWRIGHT_CHANNEL}:{})});
try{
  await mkdir(output,{recursive:true});
  const page=await browser.newPage({viewport:{width:1280,height:900},timezoneId:'UTC'}),errors=[];
  page.on('pageerror',e=>errors.push(e.message));
  await page.goto(origin+'/employees/');await page.locator('.employee').first().waitFor();
  assert.equal(await page.locator('.employee').count(),1);
  await page.getByLabel('Include deactivated employees').check();await page.getByText('Bob Example',{exact:true}).waitFor();
  await page.getByLabel('Search employees').fill('Bob');await page.getByRole('button',{name:'Search',exact:true}).click();
  await page.waitForFunction(()=>document.querySelectorAll('.employee').length===1&&document.querySelector('.employee').textContent.includes('Bob'));
  await page.getByText('Bob Example',{exact:true}).click();await page.locator('.session').waitFor();
  assert.match(await page.locator('#employeeStatus').textContent(),/Deactivated/);
  assert.equal(await page.locator('.timestamps dt').count(),6);
  assert.match(await page.locator('.totals').textContent(),/8.25/);
  await page.screenshot({path:fileURLToPath(new URL('desktop.png',output)),fullPage:true});
  await page.setViewportSize({width:390,height:844});
  assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true);
  await page.screenshot({path:fileURLToPath(new URL('phone.png',output)),fullPage:true});
  await page.getByRole('button',{name:/Edit session/}).click();await page.locator('#sessionEditor').waitFor();
  assert.match(await page.getByLabel('Clock in',{exact:true}).inputValue(),/2026-10-20T08:00/);
  assert.match(await page.getByLabel('Clock out',{exact:true}).inputValue(),/2026-10-20T17:00/);
  assert.match(await page.getByLabel('Lunch start',{exact:true}).inputValue(),/T12:00/);
  assert.match(await page.getByLabel('Lunch end',{exact:true}).inputValue(),/T12:30/);
  assert.match(await page.getByLabel('Break start',{exact:true}).inputValue(),/T14:00/);
  assert.match(await page.getByLabel('Break end',{exact:true}).inputValue(),/T14:15/);
  assert.equal(await page.locator('#sessionEditor').evaluate(e=>e.scrollWidth<=e.clientWidth),true);
  await page.screenshot({path:fileURLToPath(new URL('phone-editor.png',output)),fullPage:true});
  await page.getByLabel('Clock in',{exact:true}).fill('2026-10-20T07:30');
  await page.getByRole('button',{name:'Save correction',exact:true}).click();await page.getByText('Session corrected. Hours and calculated earnings refreshed.').waitFor();
  assert.match(await page.locator('.totals').textContent(),/8.75/);assert.equal(await page.locator('#sessionEditor').isVisible(),false);
  await page.getByLabel('From',{exact:true}).fill('1999-01-01');await page.getByLabel('Through').fill('1999-01-15');
  await page.getByRole('button',{name:'Apply dates'}).click();await page.getByText(/No time-clock sessions/).waitFor();
  await page.getByRole('button',{name:'Sign out'}).click();await page.locator('#loginPanel').waitFor();
  assert.equal(await page.locator('.session').count(),0);
  manager=false;directoryCalls=0;
  await page.getByLabel('Username or email').fill('mfa');await page.getByLabel('Password',{exact:true}).fill('fixture-password');
  await page.getByRole('button',{name:'Sign in',exact:true}).click();await page.locator('#mfaForm').waitFor();
  await page.getByLabel('Authenticator code').fill('123456');await page.getByRole('button',{name:'Verify',exact:true}).click();
  await page.locator('.session').waitFor();assert.equal(directoryCalls,0);assert.equal(await page.locator('#directory').isVisible(),false);
  assert.equal(await page.getByRole('button',{name:/Edit session/}).count(),0);
  await page.getByRole('button',{name:'Sign out'}).click();await page.locator('#loginPanel').waitFor();
  activated=false;
  await page.goto(origin+'/employees/#activate=fixture');await page.getByRole('button',{name:'Activate browser',exact:true}).waitFor();
  assert.equal(new URL(page.url()).hash,'');
  await page.getByRole('button',{name:'Activate browser',exact:true}).click();await page.locator('#loginPanel').waitFor();
  assert.match(await page.locator('#notice').textContent(),/Browser activated/);
  authenticated=true;manager=true;editable=false;await page.goto(origin+'/employees/');await page.locator('.employee').first().waitFor();
  await page.locator('#employeeList').getByText('Alice Example',{exact:true}).click();await page.locator('.session').waitFor();
  assert.equal(await page.getByRole('button',{name:/Edit session/}).count(),0);
  assert.deepEqual(errors,[]);
  console.log('Employee web UI checks passed: directory, inactive search, history, correction with blank reason, store timezone, mobile editor, read-only permission, empty state, logout, self-only, MFA, activation.');
}finally{await browser.close();await new Promise(resolve=>server.close(resolve));}
