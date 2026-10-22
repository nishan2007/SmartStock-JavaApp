import qrScript from './vendor/jsQR.js';
import {photoIcons} from './photo-icons.js';
import {bounded} from './studio-capture.js';
import {queuePage} from './studio-photo-queue-page.js';
const headers={'Cache-Control':'no-store','Referrer-Policy':'no-referrer','X-Content-Type-Options':'nosniff'};
const json=(message,status)=>Response.json({message},{status,headers});
export async function queueRoute(request,env,configured){
 const url=new URL(request.url);
 if(['GET','HEAD'].includes(request.method)){
  let body,type;
  if(url.pathname==='/photo.webmanifest'){body=JSON.stringify({id:'/',name:'Deckers PICS',short_name:'PICS',start_url:'/',scope:'/',display:'standalone',background_color:'#f4f3f8',theme_color:'#7133a8',icons:[192,512].map(size=>({src:'/photo-icon-'+size+'.png',sizes:size+'x'+size,type:'image/png',purpose:'any maskable'}))});type='application/manifest+json';}
  else if(url.pathname==='/photo-qr.js'){body=qrScript;type='application/javascript';}
  else {const match=/^\/photo-icon-(180|192|512)\.png$/.exec(url.pathname);if(match){body=Uint8Array.from(atob(photoIcons[match[1]]),c=>c.charCodeAt(0));type='image/png';}}
  if(type)return new Response(request.method==='HEAD'?null:body,{headers:{...headers,'Content-Type':type}});
 }

 if(url.pathname==='/shop/studio-photo-queue'&&['GET','HEAD'].includes(request.method))return new Response(request.method==='HEAD'?null:queuePage,{headers:{...headers,'Content-Type':'text/html; charset=utf-8','Content-Security-Policy':"default-src 'none'; script-src 'self' 'unsafe-inline'; manifest-src 'self'; media-src 'self' blob:; style-src 'unsafe-inline'; img-src 'self' blob:; connect-src 'self'; base-uri 'none'; frame-ancestors 'none'; form-action 'none'",'Permissions-Policy':'camera=(self), microphone=(), geolocation=()'}});
 if(request.method!=='POST')return json('Use POST.',405);
 if(request.headers.get('Origin')!==url.origin)return json('Open the photo queue link in your browser.',403);
 const store=configured.find(o=>o.instanceId===url.searchParams.get('instance'));
 const token=request.headers.get('Authorization')||'',id=url.searchParams.get('uploadId'),name=url.searchParams.get('name')||'';
 if(!store||!/^Bearer [A-Za-z0-9_-]{43}$/.test(token)||!/^[a-f0-9-]{36}$/.test(id||'')||name.length>80)return json('This photo queue link or upload is invalid.',400);
 const removeBackground=url.searchParams.get('removeBackground')||'true';
 if(!['true','false'].includes(removeBackground))return json('Invalid background removal option.',400);
 const key=JSON.parse(env.ORIGIN_KEYS_JSON||'{}')[store.instanceId];
 if(!key||key.length<32)return json('The store photo queue is unavailable.',503);
 if(request.headers.get('Content-Type')!=='image/jpeg')return json('Send a JPEG photo.',415);
 try{
  const body=await bounded(request,2*1024*1024);
  const destination=new URL('/shop/internal/studio-photo-queue',store.url);destination.search=new URLSearchParams({uploadId:id,name,removeBackground}).toString();
  // Workers supports manual/follow, but throws before sending for redirect: 'error'.
  // Reject 3xx below so photo bytes and credentials never follow a redirect.
  const response=await fetch(destination,{method:'POST',headers:{'Content-Type':'image/jpeg',Authorization:token,'X-Storefront-Key':key,'X-Storefront-Instance':store.instanceId},body,redirect:'manual',signal:AbortSignal.timeout(25000)});
  if(![200,400,403,409,413].includes(response.status))return json('The store server is offline or unavailable. Keep this photo selected and try again.',503);
  return new Response(response.body,{status:response.status,headers:{...headers,'Content-Type':'application/json'}});
 }catch(e){return json(e.status===413?'Choose a photo of at most 2 MB.':'Cannot reach the store queue. Keep this photo selected and try again.',e.status===413?413:503);}
}
