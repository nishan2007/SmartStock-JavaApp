const HOST='deckersapps.deckers.gy';
const HOME='/employees/mobile-management/';
export default {
  async fetch(request,env) {
    const url=new URL(request.url);
    if(url.hostname!==HOST)return new Response('Not found',{status:404});
    if(url.pathname==='/')return Response.redirect('https://'+HOST+HOME+url.search+url.hash,302);
    if(url.pathname!=='/employees'&&!url.pathname.startsWith('/employees/'))return new Response('Not found',{status:404});
    if(!['GET','HEAD','POST'].includes(request.method))return new Response('Method not allowed',{status:405});
    if(['GET','HEAD'].includes(request.method)&&url.pathname===HOME+'app.js'){
      const asset=await env.WORKSPACE_ASSETS.fetch(new Request(new URL('/app.js',url),{method:request.method}));
      const response=new Response(asset.body,asset);
      response.headers.set('Cache-Control','no-store');
      response.headers.set('X-Content-Type-Options','nosniff');
      return response;
    }
    if(request.method==='POST'&&request.headers.get('Origin')!=='https://'+HOST)return new Response('This web origin is not allowed.',{status:403});
    const headers=new Headers(request.headers);
    for(const name of ['Forwarded','X-Forwarded-Host','X-Forwarded-For'])headers.delete(name);
    url.hostname=env.EMPLOYEE_TUNNEL_HOST;
    try {
      const upstream=await fetch(new Request(url,{method:request.method,headers,body:request.method==='POST'?request.body:undefined,redirect:'manual',duplex:'half'}),{cf:{cacheTtl:0,cacheEverything:false}});
      if(request.method==='POST'&&upstream.status===403) {
        const body=await upstream.clone().json().catch(()=>null);
        if(['ORIGIN_DENIED','ORIGIN_INVALID'].includes(body?.error?.code))return Response.json({ok:false,error:{code:'SERVER_UPDATE_REQUIRED',message:'Install SmartStock 1.1.131 or later on the store server to enable this Deckers address.'}},{status:503,headers:{'Cache-Control':'no-store'}});
      }
      const response=new Response(upstream.body,upstream);
      response.headers.set('Cache-Control','no-store');
      response.headers.set('X-Content-Type-Options','nosniff');
      response.headers.set('Referrer-Policy','no-referrer');
      return response;
    }catch{return Response.json({ok:false,error:{code:'STORE_UNAVAILABLE',message:'The store server is unavailable. Please try again.'}},{status:503,headers:{'Cache-Control':'no-store'}});}
  }
};
