const HOST='overseasshipping.deckers.gy';
const API=/^\/employees\/api\/v1\/(?:auth\/(?:session|login|verify|logout)|activate|bootstrap|overseas)$/;
const ASSET=/^\/employees\/(?:index\.html|app\.js|app\.css|overseas\.js|icons\/(?:timeclock|overseas-v1)-(?:180|192|512)\.png)$/;
function secured(response){
  const r=new Response(response.body,response);r.headers.set('Cache-Control','no-store');
  r.headers.set('X-Content-Type-Options','nosniff');r.headers.set('X-Frame-Options','DENY');r.headers.set('Referrer-Policy','no-referrer');
  r.headers.set('Content-Security-Policy',"default-src 'self'; connect-src 'self'; img-src 'self' data:; style-src 'self'; script-src 'self'; manifest-src 'self'; frame-ancestors 'none'; base-uri 'none'; form-action 'self'");
  return r;
}
function error(status,code,message){return secured(Response.json({ok:false,error:{code,message}},{status}));}
export default {
  async fetch(request,env){
    const url=new URL(request.url);
    if(url.hostname!==HOST)return error(404,'NOT_FOUND','Not found.');
    if(url.pathname==='/')return secured(Response.redirect(`https://${HOST}/employees/`,302));
    if(url.pathname==='/employees/manifest.webmanifest')return secured(Response.json({id:'/employees/',name:'Deckers Overseas Orders',short_name:'Overseas Orders',start_url:'/employees/',scope:'/employees/',display:'standalone',theme_color:'#17324d',background_color:'#f5f7fa',icons:[{src:'/employees/icons/overseas-v1-192.png',sizes:'192x192',type:'image/png',purpose:'any maskable'},{src:'/employees/icons/overseas-v1-512.png',sizes:'512x512',type:'image/png',purpose:'any maskable'}]}));
    if(request.method==='GET'&&(url.pathname==='/employees/'||url.pathname==='/employees'||ASSET.test(url.pathname))){
      const asset=new URL(request.url);asset.pathname=url.pathname.replace(/^\/employees/,'')||'/';
      const response=secured(await env.ASSETS.fetch(new Request(asset,request)));
      if(response.ok&&response.headers.get('Content-Type')?.includes('text/html'))return new HTMLRewriter()
        .on('body',{element(e){e.setAttribute('data-workspace','overseas');}})
        .on('title',{element(e){e.setInnerContent('Deckers Overseas Orders');}})
        .on('header h1',{element(e){e.setInnerContent('Overseas Orders');}})
        .on('#loginPanel > p:not(.eyebrow)',{element(e){e.setInnerContent('Prepare overseas quotes and track orders for your Deckers store.');}})
        .on('meta[name="apple-mobile-web-app-title"]',{element(e){e.setAttribute('content','Overseas Orders');}})
        .on('link[rel="apple-touch-icon"]',{element(e){e.setAttribute('href','/employees/icons/overseas-v1-180.png');}})
        .on('link[rel="icon"]',{element(e){e.setAttribute('href','/employees/icons/overseas-v1-192.png');}})
        .transform(response);
      return response;
    }
    if(url.pathname==='/employees/health'&&request.method==='GET'){
      try{
        const origin=`https://${env.EMPLOYEE_TUNNEL_HOST}`;
        const [health,feature]=await Promise.all(['/employees/health','/employees/overseas.js'].map(path=>fetch(origin+path,{redirect:'manual',cf:{cacheTtl:0,cacheEverything:false}})));
        if(!health.ok)return error(503,'STORE_UNAVAILABLE','The store gateway is unavailable.');
        if(!feature.ok)return error(503,'SERVER_UPDATE_REQUIRED','Install SmartStock 1.1.63 or later on the store server to enable overseas orders.');
        return secured(Response.json({ok:true}));
      }catch{return error(503,'STORE_UNAVAILABLE','The store gateway is unavailable.');}
    }
    if(!API.test(url.pathname))return error(404,'NOT_FOUND','Not found.');
    if(request.method!=='POST')return error(405,'METHOD_NOT_ALLOWED','POST is required.');
    // Only a request originating on this exact employee-facing site can be translated
    // to the existing gateway's canonical origin. CSRF tokens are still checked there.
    if(request.headers.get('Origin')!==`https://${HOST}`)return error(403,'ORIGIN_DENIED','This web origin is not allowed.');
    const headers=new Headers(request.headers);
    for(const name of ['Forwarded','X-Forwarded-Host','X-Forwarded-For'])headers.delete(name);
    headers.set('Origin',env.EMPLOYEE_ORIGIN);url.hostname=env.EMPLOYEE_TUNNEL_HOST;
    try{
      const upstream=await fetch(new Request(url,{method:'POST',headers,body:request.body,duplex:'half',redirect:'manual'}),{cf:{cacheTtl:0,cacheEverything:false}});
      if(url.pathname.endsWith('/overseas')&&upstream.status===404)return error(503,'SERVER_UPDATE_REQUIRED','Install SmartStock 1.1.63 or later on the store server to enable overseas orders.');
      return secured(upstream);
    }catch{return error(503,'STORE_UNAVAILABLE','The store gateway is unavailable. Please try again.');}
  }
};
