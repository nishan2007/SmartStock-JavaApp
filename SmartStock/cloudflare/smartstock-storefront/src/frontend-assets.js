/** Keep frontend updates independent of the installed store application's bundle. */
export function withFrontendAssets(gateway,scriptPath){
 if(!/^\/shop\/assets\/index-[\w-]+\.js$/.test(scriptPath))throw new Error('Invalid frontend script path');
 return {async fetch(request,env,ctx){
  const url=new URL(request.url),read=request.method==='GET'||request.method==='HEAD';
  if(read&&(url.pathname.startsWith('/shop/assets/')||url.pathname.startsWith('/shop/fonts/')||/^\/shop\/shirt-[^/]+\.png$/.test(url.pathname))){
   const asset=await env.FRONTEND_ASSETS.fetch(request);if(asset.status!==404)return asset;
  }
  let response=await gateway.fetch(request,env,ctx);
  const page=url.pathname==='/shop/'||/^\/shop\/(?:services|projects|products)(?:\/|$)/.test(url.pathname)||/^\/shop\/(?:business|about|made|stores)\/?$/.test(url.pathname);
  if(response.status>=500&&request.method==='GET'&&page&&(request.headers.get('Accept')||'').includes('text/html')){
   response=await env.FRONTEND_ASSETS.fetch(new Request(new URL('/shop/',request.url),request));
  }
  if((response.headers.get('content-type')||'').includes('text/html')&&page){
   const body=(await response.text()).replace(/src="\/shop\/assets\/index-[^"]+\.js"/,`src="${scriptPath}"`);
   const headers=new Headers(response.headers);headers.delete('content-length');headers.delete('etag');headers.set('Cache-Control','no-store');
   return new Response(body,{status:response.status,headers});
  }
  return response;
 }};
}
