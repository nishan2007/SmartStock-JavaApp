/** Permanent employee-only entry point through the existing store tunnel. */
export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    if (url.hostname !== 'timeclock.deckers.gy') return new Response('Not found', {status:404});
    if (url.pathname === '/') return Response.redirect('https://timeclock.deckers.gy/employees/',302);
    if (url.pathname === '/employees/overseas-entry.js') return new Response(`
      (()=>{const inviteHash=location.hash;document.addEventListener('DOMContentLoaded',()=>{const link=document.createElement('a');const invited=new URLSearchParams(inviteHash.slice(1)).has('activate');
      link.textContent=invited?'Activate this browser for Overseas Orders':'Open Overseas Orders';
      link.href='https://overseasshipping.deckers.gy/employees/'+(invited?inviteHash:'');
      const target=invited?document.getElementById('activation'):document.querySelector('footer');
      if(target){const row=document.createElement('p');row.append(link);target.append(row);}});})();
    `,{headers:{'Content-Type':'application/javascript; charset=utf-8','Cache-Control':'no-store','X-Content-Type-Options':'nosniff'}});
    if (url.pathname !== '/employees' && !url.pathname.startsWith('/employees/')) return new Response('Not found',{status:404});
    if (/^\/employees\/icons\/timeclock-(180|192|512)\.png$/.test(url.pathname) || url.pathname === '/employees/manifest.webmanifest') {
      const assetUrl = new URL(request.url);assetUrl.pathname = url.pathname.slice('/employees'.length);
      const asset = await env.ASSETS.fetch(new Request(assetUrl, request));
      const response = new Response(asset.body, asset);
      response.headers.set('Cache-Control','public, max-age=3600');
      if(url.pathname.endsWith('.webmanifest')) response.headers.set('Content-Type','application/manifest+json');
      return response;
    }
    url.hostname = env.EMPLOYEE_TUNNEL_HOST;
    const headers = new Headers(request.headers);
    headers.delete('Forwarded'); headers.delete('X-Forwarded-Host'); headers.delete('X-Forwarded-For');
    const upstream = await fetch(new Request(url, {method:request.method, headers,
      body:['GET','HEAD'].includes(request.method)?undefined:request.body, redirect:'manual'}),
      {cf:{cacheTtl:0,cacheEverything:false}});
    const response = new Response(upstream.body, upstream);
    response.headers.set('Cache-Control','no-store');
    if(response.ok && response.headers.get('Content-Type')?.includes('text/html')) {
      const metadata = '<meta name="apple-mobile-web-app-capable" content="yes"><meta name="apple-mobile-web-app-title" content="Time Clock"><link rel="apple-touch-icon" sizes="180x180" href="/employees/icons/timeclock-180.png"><link rel="icon" type="image/png" href="/employees/icons/timeclock-192.png"><link rel="manifest" href="/employees/manifest.webmanifest">';
      return new HTMLRewriter()
        .on('meta[name="apple-mobile-web-app-capable"],meta[name="apple-mobile-web-app-title"],link[rel="apple-touch-icon"],link[rel="icon"],link[rel="manifest"]',{element(element){element.remove();}})
        .on('head',{element(element){element.append(metadata,{html:true});}})
        .on('head',{element(element){element.append('<script src="/employees/overseas-entry.js"></script>',{html:true});}})
        .on('title',{element(element){element.setInnerContent('Deckers Time Clock');}})
        .transform(response);
    }
    return response;
  }
};
