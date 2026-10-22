import {bounded} from './studio-capture.js';
import {templateSubmissionPage} from './template-submit-page.js';
const headers={'Cache-Control':'no-store','Referrer-Policy':'no-referrer','X-Content-Type-Options':'nosniff'};
const error=(message,status)=>Response.json({message},{status,headers});
export async function templateSubmissionRoute(request,env,configured){
 const url=new URL(request.url);
 if(url.pathname==='/shop/template-submit'&&['GET','HEAD'].includes(request.method))return new Response(request.method==='HEAD'?null:templateSubmissionPage,{headers:{...headers,'Content-Type':'text/html; charset=utf-8','Content-Security-Policy':"default-src 'none'; script-src 'unsafe-inline'; style-src 'unsafe-inline'; img-src blob:; connect-src 'self'; base-uri 'none'; frame-ancestors 'none'; form-action 'none'",'Permissions-Policy':'camera=(self), microphone=(), geolocation=()'}});
 if(request.method!=='POST')return error('Use POST.',405);
 if(request.headers.get('Origin')!==url.origin)return error('Open your organization link in a browser.',403);
 const store=configured.find(o=>o.instanceId===url.searchParams.get('instance'));
 if(!store)return error('This store link is unavailable.',400);
 const key=JSON.parse(env.ORIGIN_KEYS_JSON||'{}')[store.instanceId];
 if(!key||key.length<32)return error('Store submission service is unavailable.',503);
 if(!(request.headers.get('Content-Type')||'').startsWith('application/json'))return error('Send JSON.',415);
 try{
  const body=await bounded(request,12*1024*1024);
  const destination=new URL('/shop/internal/template-submit',store.url);
  const response=await fetch(destination,{method:'POST',headers:{'Content-Type':'application/json','X-Storefront-Key':key,'X-Storefront-Instance':store.instanceId,'X-Submission-Client':request.headers.get('CF-Connecting-IP')||'unknown'},body,redirect:'manual',signal:AbortSignal.timeout(30000)});
  if(![200,400,403,409,413,429].includes(response.status))return error('The store is unavailable. Keep your entries and retry.',503);
  return new Response(response.body,{status:response.status,headers:{...headers,'Content-Type':'application/json'}});
 }catch(e){return error(e.status===413?'Submission exceeds 12 MB.':'Cannot reach the store. Keep your entries and retry.',e.status===413?413:503);}
}
