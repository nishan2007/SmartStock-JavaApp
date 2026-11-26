const HOST='balancesheet.deckers.gy';
const API=/^\/employees\/api\/v1\/(?:auth\/(?:session|login|verify|logout)|activate|balance-sheet)$/;
const MOBILE=/^\/employees\/mobile-management\/(?:app\.(?:js|css)|screens\.js|device-info\.js|screen-modules\/[A-Z_]+\.js|screen-assets\/[a-zA-Z0-9_./-]+|signin\.css|reconnect\.js|repair-qr(?:\.js|-LICENSE\.txt)|manifest\.webmanifest|icons\.ttf|icons-LICENSE\.txt|(?:logo|icon-(?:180|192|512)|network|home|account)\.png|icons\/[A-Z_]+\.(?:png|svg)|pair|repair|redeem|state|open|screen|screen-api|network-challenge|link-request|link-status|link-info|approve-link)?$/;
async function mobileManagement(request,env,url){
  const route=url.pathname.split('/').pop();
  const asset=['','app.js','screens.js','device-info.js','app.css','signin.css','reconnect.js','repair-qr.js','repair-qr-LICENSE.txt','manifest.webmanifest','logo.png','icon-180.png','icon-192.png','icon-512.png','network.png','home.png','account.png','icons.ttf','icons-LICENSE.txt'].includes(route)||url.pathname.includes('/icons/')||url.pathname.includes('/screen-assets/')||url.pathname.includes('/screen-modules/');
  if(request.method!==(asset?'GET':'POST'))return error(405,'METHOD_NOT_ALLOWED',asset?'GET is required.':'POST is required.');
  if(!asset&&request.headers.get('Origin')!==`https://${HOST}`)return error(403,'ORIGIN_DENIED','This web origin is not allowed.');
  const headers=new Headers(request.headers);
  for(const name of ['Forwarded','X-Forwarded-Host','X-Forwarded-For'])headers.delete(name);
  url.hostname=env.EMPLOYEE_TUNNEL_HOST;
  try{const upstream=await fetch(new Request(url,{method:request.method,headers,body:asset?undefined:request.body,duplex:'half',redirect:'manual'}),{cf:{cacheTtl:0,cacheEverything:false}});const response=secured(upstream);const policy=upstream.headers.get('Content-Security-Policy');if(policy)response.headers.set('Content-Security-Policy',policy);return response;}
  catch{return error(503,'STORE_UNAVAILABLE','The store server is unavailable. Please try again.');}
}
const ASSET=/^\/employees\/(?:balance-sheet\.(?:html|css|js|webmanifest)|icons\/balance-sheet(?:\.svg|-(?:180|192|512)\.png))$/;
function secured(response){
  const r=new Response(response.body,response);
  r.headers.set('Cache-Control','no-store');
  r.headers.set('X-Content-Type-Options','nosniff');r.headers.set('X-Frame-Options','DENY');
  r.headers.set('Referrer-Policy','no-referrer');
  r.headers.set('Permissions-Policy','camera=(), microphone=(), geolocation=()');
  r.headers.set('Content-Security-Policy',"default-src 'self'; connect-src 'self'; img-src 'self' data:; style-src 'self'; script-src 'self'; manifest-src 'self'; frame-ancestors 'none'; base-uri 'none'; form-action 'self'");
  return r;
}
const error=(status,code,message)=>secured(Response.json({ok:false,error:{code,message}},{status}));
export default {async fetch(request,env){
  const url=new URL(request.url);
  if(url.hostname!==HOST)return error(404,'NOT_FOUND','Not found.');
  if(MOBILE.test(url.pathname))return mobileManagement(request,env,url);
  if(['/', '/employees', '/employees/'].includes(url.pathname))
    return secured(Response.redirect(`https://${HOST}/employees/balance-sheet.html`,302));
  if(request.method==='GET'&&ASSET.test(url.pathname)){
    const asset=new URL(request.url);asset.pathname=url.pathname.slice('/employees'.length);
    const response=secured(await env.ASSETS.fetch(new Request(asset,request)));
    if(url.pathname.endsWith('.webmanifest'))response.headers.set('Content-Type','application/manifest+json');
    return response;
  }
  if(url.pathname==='/employees/health'&&request.method==='GET'){
    try{
      // The installed app asset is also a compatibility probe: an older gateway
      // can be healthy without supporting Balance Sheet at all.
      const response=await fetch(`https://${env.EMPLOYEE_TUNNEL_HOST}/employees/balance-sheet.html`,{
        redirect:'manual',signal:AbortSignal.timeout(15000),cf:{cacheTtl:0,cacheEverything:false}});
      if(response.status===404)return error(503,'SERVER_UPDATE_REQUIRED','The store server needs the Balance Sheet gateway update.');
      if(!response.ok||!response.headers.get('Content-Type')?.includes('text/html'))
        return error(503,'STORE_UNAVAILABLE','The store server is unavailable. Please try again.');
      const health=await fetch(`https://${env.EMPLOYEE_TUNNEL_HOST}/employees/health`,{
        redirect:'manual',signal:AbortSignal.timeout(15000),cf:{cacheTtl:0,cacheEverything:false}});
      if(!health.ok)return error(503,'STORE_UNAVAILABLE','The store server is unavailable. Please try again.');
      return secured(Response.json({ok:true}));
    }catch{return error(503,'STORE_UNAVAILABLE','The store server is unavailable. Please try again.');}
  }
  if(!API.test(url.pathname))return error(404,'NOT_FOUND','Not found.');
  if(request.method!=='POST')return error(405,'METHOD_NOT_ALLOWED','POST is required.');
  if(request.headers.get('Origin')!==`https://${HOST}`)return error(403,'ORIGIN_DENIED','This web origin is not allowed.');
  const headers=new Headers(request.headers);
  for(const name of ['Forwarded','X-Forwarded-Host','X-Forwarded-For'])headers.delete(name);
  // Only this checked, same-origin gateway can translate the origin for the
  // existing loopback listener; register /v1 APIs never become public.
  headers.set('Origin',env.EMPLOYEE_ORIGIN);url.hostname=env.EMPLOYEE_TUNNEL_HOST;
  try{
    const upstream=await fetch(new Request(url,{method:'POST',headers,body:request.body,duplex:'half',redirect:'manual'}),
      {cf:{cacheTtl:0,cacheEverything:false}});
    if(url.pathname.endsWith('/balance-sheet')&&upstream.status===404)
      return error(503,'SERVER_UPDATE_REQUIRED','The store server needs the Balance Sheet gateway update.');
    return secured(upstream);
  }catch{return error(503,'STORE_UNAVAILABLE','The store server is unavailable. Please try again.');}
}};
