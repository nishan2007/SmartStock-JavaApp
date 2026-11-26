const {test}=require('node:test');
const assert=require('node:assert/strict'),vm=require('node:vm'),fs=require('node:fs'),path=require('node:path');
const source=fs.readFileSync(path.join(__dirname,'../../src/mobile-web/app.js'),'utf8');
function startup(call,loadBootstrap=async()=>{}){
 const status={textContent:''};
 const ctx={call,loadBootstrap,cachedBootstrap:()=>null,filterChoices(){},show(screen){ctx.screen=screen},note(message,error){ctx.message=message;ctx.error=error},activate:async()=>false,designReturn(){},newItemEntry(){ctx.newItemEntry=true},$:()=>status};
 vm.createContext(ctx);
 for(const name of ['resume','boot']){const start=source.indexOf('async function '+name+'(');vm.runInContext(source.slice(start,source.indexOf('\n',start)),ctx)}
 return {ctx,status};
}
test('failed session request displays the error and ends the connecting status',async()=>{
 const app=startup(async()=>{throw new Error('The register took too long to respond.')});await app.ctx.boot();
 assert.equal(app.ctx.screen,'activation');assert.equal(app.ctx.message,'The register took too long to respond.');assert.equal(app.ctx.error,true);assert.match(app.status.textContent,/Reload/);
});
test('failed initial catalogue request is visible instead of becoming an activation prompt',async()=>{
 const app=startup(async()=>({csrfToken:'test',permissions:[]}),async()=>{throw new Error('Catalogue unavailable')});await app.ctx.boot();
 assert.equal(app.ctx.message,'Catalogue unavailable');assert.equal(app.ctx.error,true);
});
test('expected activation and expired sessions still lead to their appropriate screens',async()=>{
 for(const [code,screen] of [['ACTIVATION_REQUIRED','activation'],['LOGIN_REQUIRED','login'],['SESSION_INVALID','login'],['SESSION_EXPIRED','login']]){
  const app=startup(async()=>{throw Object.assign(new Error(code),{code})});await app.ctx.boot();assert.equal(app.ctx.screen,screen);assert.equal(app.ctx.message,'');
  if(screen==='activation')assert.match(app.status.textContent,/Scan a current activation QR/);
 }
});
test('a working session and catalogue open the home screen',async()=>{
 const app=startup(async()=>({csrfToken:'test',permissions:[]}));await app.ctx.boot();assert.equal(app.ctx.screen,'home');assert.equal(app.ctx.message,'');assert.equal(app.ctx.newItemEntry,true);
});
