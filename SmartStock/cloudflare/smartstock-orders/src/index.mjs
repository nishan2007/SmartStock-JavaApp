const HOST='ordersmanagement.deckers.gy';
const API=/^\/employees\/api\/v1\/(?:auth\/(?:session|login|verify|logout)|activate|orders\/(?:dashboard|details|assign))$/;
const ASSET=/^\/employees\/(?:orders\.(?:html|css|js|webmanifest)|orders-icon-(?:180|192|512)\.png)$/;
function secured(response){
  const r=new Response(response.body,response);
  r.headers.set('Cache-Control','no-store');r.headers.set('X-Content-Type-Options','nosniff');
  r.headers.set('X-Frame-Options','DENY');r.headers.set('Referrer-Policy','no-referrer');
  r.headers.set('Content-Security-Policy',"default-src 'self'; connect-src 'self'; img-src 'self' data:; style-src 'self'; script-src 'self'; manifest-src 'self'; frame-ancestors 'none'; base-uri 'none'; form-action 'self'");
  return r;
}
const error=(status,code,message)=>secured(Response.json({ok:false,error:{code,message}},{status}));
export default {async fetch(request,env){
  const url=new URL(request.url);
  if(url.hostname!==HOST)return error(404,'NOT_FOUND','Not found.');
  if(['/', '/employees', '/employees/'].includes(url.pathname))return secured(Response.redirect(`https://${HOST}/employees/orders.html`,302));
  if(request.method==='GET'&&ASSET.test(url.pathname)){
    const asset=new URL(request.url);asset.pathname=url.pathname.slice('/employees'.length);
    const response=secured(await env.ASSETS.fetch(new Request(asset,request)));
    if(url.pathname.endsWith('.webmanifest'))response.headers.set('Content-Type','application/manifest+json');
    return response;
  }
  if(url.pathname==='/employees/health'&&request.method==='GET'){
    try{
      const response=await fetch(`https://${env.EMPLOYEE_TUNNEL_HOST}/employees/orders-health`,{redirect:'manual',cf:{cacheTtl:0,cacheEverything:false}});
      if(response.status===404)return error(503,'SERVER_UPDATE_REQUIRED','The store server needs the Orders Management gateway update.');
      if(!response.ok)return error(503,'STORE_UNAVAILABLE','The store server is unavailable. Please try again.');
      return secured(Response.json({ok:true}));
    }catch{return error(503,'STORE_UNAVAILABLE','The store server is unavailable. Please try again.');}
  }
  if(!API.test(url.pathname))return error(404,'NOT_FOUND','Not found.');
  if(request.method!=='POST')return error(405,'METHOD_NOT_ALLOWED','POST is required.');
  if(request.headers.get('Origin')!==`https://${HOST}`)return error(403,'ORIGIN_DENIED','This web origin is not allowed.');
  const headers=new Headers(request.headers);
  for(const name of ['Forwarded','X-Forwarded-Host','X-Forwarded-For'])headers.delete(name);
  headers.set('Origin',env.EMPLOYEE_ORIGIN);url.hostname=env.EMPLOYEE_TUNNEL_HOST;
  try{
    const upstream=await fetch(new Request(url,{method:'POST',headers,body:request.body,duplex:'half',redirect:'manual'}),{cf:{cacheTtl:0,cacheEverything:false}});
    if(url.pathname.includes('/orders/')&&upstream.status===404)return error(503,'SERVER_UPDATE_REQUIRED','The store server needs the Orders Management gateway update.');
    return secured(upstream);
  }catch{return error(503,'STORE_UNAVAILABLE','The store server is unavailable. Please try again.');}
}};
