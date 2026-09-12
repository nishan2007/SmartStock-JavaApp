/* Browser contract test with an explicit in-memory API fixture; no live accounts or documents. */
const {chromium}=require('playwright');
const http=require('node:http');
const fs=require('node:fs');
const path=require('node:path');
const assert=require('node:assert/strict');
const root=path.resolve(__dirname,'../src/employee-registration-web');
const output=path.resolve(__dirname,'../target/portal-browser');
fs.mkdirSync(output,{recursive:true});
let saved={id:'00000000-0000-4000-8000-000000000001',email:'applicant@example.test',status:'DRAFT',revision:0,updatedAt:new Date().toISOString(),form:{},attachments:[],history:[]};
let failSave=false,company='Deckers';
const server=http.createServer(async(req,res)=>{
 const route=req.url.split('?')[0];
 const json=value=>{res.setHeader('Content-Type','application/json');res.end(JSON.stringify(value));};
 if(route==='/register/branding')return json({name:company,motto:'Grow with us',address:'Georgetown',phone:'600 1234'});
 if(route.startsWith('/register/api/')){
  let bytes='';for await(const chunk of req)bytes+=chunk;const body=JSON.parse(bytes||'{}');
  if(route==='/register/api/auth/session'){res.statusCode=401;return json({message:'Sign in.'});}
  if(route.startsWith('/register/api/auth/'))return json({csrfToken:'test-csrf'});
  if(route==='/register/api/application')return json(saved);
  if(route.endsWith('/save')||route.endsWith('/submit')){
   if(failSave){res.statusCode=503;return json({message:'Connection lost. Retry saving.'});}
   assert.equal(body.revision,saved.revision);saved={...saved,form:body.form,revision:saved.revision+1};
   if(route.endsWith('/submit')){saved.status='PENDING';saved.history.push({status:'PENDING',message:'Application submitted.',at:new Date().toISOString()});}return json(saved);
  }
  res.statusCode=404;return json({message:'Not implemented in fixture.'});
 }
 const file=route==='/register'?'index.html':path.basename(route);if(!['index.html','app.js','app.css'].includes(file)){res.statusCode=404;return res.end();}
 res.setHeader('Content-Type',file.endsWith('.css')?'text/css':file.endsWith('.js')?'text/javascript':'text/html');res.end(fs.readFileSync(path.join(root,file)));
});
(async()=>{
 await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));let browser;
 try{
  browser=await chromium.launch({headless:true,...(process.env.PORTAL_BROWSER_CHANNEL?{channel:process.env.PORTAL_BROWSER_CHANNEL}:{})});const page=await browser.newPage({viewport:{width:390,height:844}});const errors=[];page.on('pageerror',e=>errors.push(e.message));
  await page.goto('http://127.0.0.1:'+server.address().port+'/register');await page.getByRole('heading',{name:'Bring your potential. Build your future.'}).waitFor();
  await page.screenshot({path:path.join(output,'welcome-mobile.png'),fullPage:true});
  await page.locator('#signin').click();await page.locator('#auth-email').fill(saved.email);await page.locator('#auth-password').fill('test-password-only');await page.locator('#auth-submit').click();await page.locator('#continue').click();
  await page.locator('[name=firstName]').fill('Alex');await page.locator('[name=lastName]').fill('Example');await page.locator('[name=phone]').fill('6001234');await page.locator('[name=dateOfBirth]').fill('2000-01-01');
  await page.locator('[name=interests]').fill('Customer service');await page.locator('[name=introduction]').fill('I enjoy helping customers.');assert.equal(await page.locator('[data-step]').count(),3);assert.equal(await page.locator('[name=availability], [name=startDate], [name=preferredStores]').count(),0);await page.locator('#next').click();await page.locator('#next').click();
  await page.locator('[name=declaration]').check();await page.locator('#save-exit').click();await page.locator('#dashboard').waitFor({state:'visible'});assert.equal(saved.form.dateOfBirth,'2000-01-01');assert.equal(saved.attachments.length,0);
  company='Updated Company';await page.locator('#continue').click();await page.getByText('Updated Company',{exact:true}).first().waitFor();
  failSave=true;await page.locator('[name=firstName]').fill('Avery');await page.locator('#save-exit').click();await page.getByText('Not saved — please retry',{exact:true}).waitFor();assert.equal(saved.form.firstName,'Alex');assert.equal(await page.locator('#editor').isVisible(),true);
  failSave=false;await page.locator('#save-exit').click();await page.locator('#dashboard').waitFor({state:'visible'});assert.equal(saved.form.firstName,'Avery');
  await page.locator('#continue').click();await page.locator('[data-step="2"]').click();await page.screenshot({path:path.join(output,'review-mobile.png'),fullPage:true});
  page.once('dialog',dialog=>dialog.accept());await page.locator('#submit').click();await page.locator('#dashboard').waitFor({state:'visible'});assert.equal(saved.status,'PENDING');assert.equal(await page.locator('#continue').isVisible(),false);
  await page.screenshot({path:path.join(output,'submitted-mobile.png'),fullPage:true});
  assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth),false);assert.deepEqual(errors,[]);
  console.log('Portal browser checks passed: mobile workflow, autosave, zero documents, failed-save recovery, branding refresh, submitted read-only view.');
 }finally{if(browser)await browser.close();server.close();}
})().catch(error=>{console.error(error);process.exitCode=1;});
