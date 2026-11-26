const HOST='ordersmanagement.deckers.gy';
const API=/^\/employees\/api\/v1\/(?:auth\/(?:session|login|verify|logout)|activate|orders\/(?:dashboard|details|assign|lookup))$/;
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
const ASSET=/^\/employees\/(?:orders\.(?:html|css|js|webmanifest)|orders-barcode(?:\.js|-LICENSE\.txt)|orders-icon-(?:180|192|512)\.png|orders-icon-v2-(?:180|192|512|1024)\.png)$/;
function secured(response){
  const r=new Response(response.body,response);
  r.headers.set('Cache-Control','no-store');r.headers.set('X-Content-Type-Options','nosniff');
  r.headers.set('X-Frame-Options','DENY');r.headers.set('Referrer-Policy','no-referrer');
  r.headers.set('Content-Security-Policy',"default-src 'self'; connect-src 'self'; img-src 'self' data: blob:; media-src 'self' blob:; style-src 'self'; script-src 'self'; manifest-src 'self'; frame-ancestors 'none'; base-uri 'none'; form-action 'self'");
  return r;
}
const error=(status,code,message)=>secured(Response.json({ok:false,error:{code,message}},{status}));
// Public company identity already published by SmartStock's customer website.
// This path never reads employee/order data or forwards browser credentials.
async function publicBranding(env){
  const origin='https://www.deckers.gy',storeId=Number(env.PUBLIC_STORE_ID||1);
  const requestStorefront=env.STOREFRONT?env.STOREFRONT.fetch.bind(env.STOREFRONT):fetch;
  const response=await requestStorefront(origin+'/shop/api/v1/catalog',{method:'POST',headers:{Origin:origin,'X-Storefront-Request':'same-origin','Content-Type':'application/json'},body:JSON.stringify({storeId}),redirect:'manual',signal:AbortSignal.timeout(10000)});
  if(!response.ok)throw new Error('Public company response status '+response.status);
  const catalog=await response.json(),brand=catalog.branding||{};
  const text=v=>typeof v==='string'?v.slice(0,512):'';
  const join=(...values)=>values.map(text).map(v=>v.trim()).filter(Boolean).join(' · ');
  const result={name:text(brand.name),motto:join(brand.mottoLine1,brand.mottoLine2),address:join(brand.addressLine1,brand.addressLine2),phone:join(brand.phoneLine1,brand.phoneLine2),email:join(brand.emailLine1,brand.emailLine2)};
  if(typeof brand.logo==='string'&&/^[a-f0-9]{64}$/i.test(brand.logo)){
    try{
      const logo=await requestStorefront(origin+'/shop/image?'+new URLSearchParams({storeId:String(storeId),kind:'logo',v:brand.logo}),{redirect:'manual',signal:AbortSignal.timeout(10000)});
      const type=logo.headers.get('Content-Type')?.split(';')[0];
      if(logo.ok&&['image/png','image/jpeg'].includes(type)&&Number(logo.headers.get('Content-Length')||0)<=2*1024*1024){
        const bytes=new Uint8Array(await logo.arrayBuffer());
        if(bytes.length<=2*1024*1024){let binary='';for(let i=0;i<bytes.length;i+=8192)binary+=String.fromCharCode(...bytes.subarray(i,i+8192));result.logo=`data:${type};base64,${btoa(binary)}`;}
      }
    }catch{ /* Company name and contact details still render when the logo is unavailable. */ }
  }
  return result;
}
export default {async fetch(request,env){
  const url=new URL(request.url);
  if(url.hostname!==HOST)return error(404,'NOT_FOUND','Not found.');
  if(MOBILE.test(url.pathname))return mobileManagement(request,env,url);
  if(url.pathname==='/employees/branding'&&request.method==='GET'){
    try{return secured(Response.json({ok:true,data:await publicBranding(env)}));}
    catch(e){console.warn('Orders public branding: '+e.name+': '+e.message);return error(503,'BRANDING_UNAVAILABLE','Company information is temporarily unavailable.');}
  }
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
