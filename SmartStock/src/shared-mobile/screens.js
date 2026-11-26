'use strict';
// Controllers share the Deckers session; their DOM and state belong to one mounted screen.
window.DeckersScreens = {
  async mount(host, packet, bridge) {
    const lifetime = new AbortController(), timers = new Set(), intervals = new Set(), frames = new Set(), observers = new Set(), streams = new Set();
    const events = new EventTarget(), root = host.attachShadow({mode:'open'});
    let alive = true;
    const asset = path => new URL('screen-assets/' + path, location.href).href;
    const body = document.createElement('body'), parsed = new DOMParser().parseFromString('<body '+packet.bodyAttributes+'>'+packet.body+'</body>', 'text/html');
    for (const attribute of parsed.body.attributes) body.setAttribute(attribute.name, attribute.value);
    body.innerHTML = parsed.body.innerHTML;
    const resourceDirectory=packet.styles[0].slice(0,packet.styles[0].lastIndexOf('/')+1);
    body.querySelectorAll('img[src]').forEach(image=>{const source=image.getAttribute('src');if(source&&!/^(https?:|data:|blob:)/.test(source))image.src=asset(resourceDirectory+source.replace(/^(\.\/|\/)+/,''));});
    root.append(body);
    for (const resource of packet.styles) {
      const response = await fetch(asset(resource), {signal:lifetime.signal});
      if (!response.ok) throw new Error('The screen styles could not be loaded.');
      const style = document.createElement('style');
      style.textContent = (await response.text()).replace(/:root\b/g, ':host').replace(/url\((['"]?)([^)'"\s]+)\1\)/g, (match,quote,url) => {
        if (/^(data:|blob:|https?:)/.test(url)) return match;
        const resolved = new URL(url.replace(/^\//,''), 'https://assets.invalid/'+resource).pathname.slice(1);
        return 'url("'+asset(resolved)+'")';
      });
      root.prepend(style);
    }
    const style = document.createElement('style');
    style.textContent = ':host{display:block;min-width:0}body{margin:0;min-height:0;background:transparent}body>header:not(.top):not(.site-header):not(:has(#connection)),body>footer,#auth,#login,#loginPanel,#activation,#logout,#remoteLogin,#remoteMfa,a[href*="mobile-management/"]{display:none!important}.phone-nav{position:static!important;padding:10px 0!important}body{padding-bottom:0!important}main{padding:12px 0 24px!important;max-width:100%;margin:0}[hidden]{display:none!important}';
    style.textContent+='body>header.top{position:static;height:auto;min-height:0;padding:10px;display:flex;flex-wrap:wrap;background:#25272b}body>header.top>.brand{display:none}body>header.site-header{display:flex;background:transparent;padding:0 0 10px}body>header.site-header>div:first-child{display:none}body>header:has(#connection){display:flex;background:transparent;padding:0;color:#25272b}body>header:has(#connection)>div:first-child{display:none}.shell{height:auto;min-height:70vh}';
    root.append(style);
    // Remove only redundant screen branding. Workflow headings, dialog titles and
    // toolbar actions remain available to their original controllers.
    const headings={
      ORDERS:'#workspace>.heading h1,#workspace>.heading .eyebrow',
      INVENTORY:'main>.intro',CUSTOMERS:'main>.intro',PHONE_MINUTES:'.app-header',
      RECEIVING:'main>.intro',IMAGE_STUDIO:'main>h1,main>#company,main>#motto,main>#logo',
      MOBILE_ITEMS:'#home>h1,#spoils>h1',EDIT_ITEMS:'#home>h1',CUSTOM_ORDER_SPOILS:'#spoils>h1'
    };
    if(headings[packet.app]){
      style.textContent+=headings[packet.app]+'{display:none!important}';
      body.querySelectorAll(headings[packet.app]).forEach(element=>{element.hidden=true;});
    }
    const on = (target,type,listener,options) => target.addEventListener(type,listener,{...(typeof options==='object'?options:{}),capture:typeof options==='boolean'?options:options?.capture,signal:lifetime.signal});
    const timeout = (fn,delay,...args) => { const id=setTimeout(()=>{timers.delete(id);if(alive)fn(...args);},delay);timers.add(id);return id; };
    let virtual = new URL(packet.path, location.origin);
    const navigate = value => bridge.navigate(new URL(value, virtual));
    const virtualLocation = new Proxy({}, {get:(_,key)=>key==='assign'||key==='replace'?navigate:key==='reload'?()=>bridge.reload():virtual[key],set:(_,key,value)=>{if(key==='href')navigate(value);return true;}});
    const virtualHistory = {replaceState:(_state,_title,url)=>{if(url)virtual=new URL(url,virtual);},pushState:(_state,_title,url)=>navigate(url),back:()=>bridge.home()};
    const documentFacade = new Proxy(document, {
      get(target,key) {
        if(key==='body'||key==='documentElement')return body;
        if(key==='readyState')return 'complete';
        if(key==='getElementById')return id=>root.getElementById(id);
        if(key==='querySelector'||key==='querySelectorAll')return root[key].bind(root);
        if(key==='addEventListener')return (type,listener,options)=>type==='DOMContentLoaded'?timeout(()=>listener(new Event(type)),0):on(type==='visibilitychange'?document:body,type,listener,options);
        if(key==='removeEventListener')return (type,listener,options)=>(type==='visibilitychange'?document:body).removeEventListener(type,listener,options);
        if(key==='head')return {appendChild:element=>{if(element.tagName==='SCRIPT')return loadLibrary(element.src).then(()=>element.onload?.()).catch(error=>element.onerror?.(error));return root.appendChild(element);},append:element=>{if(element.tagName==='SCRIPT')return loadLibrary(element.src).then(()=>element.onload?.()).catch(error=>element.onerror?.(error));return root.append(element);}};
        const value=Reflect.get(target,key,target);return typeof value==='function'?value.bind(target):value;
      },set(target,key,value){if(key==='title')return true;return Reflect.set(target,key,value,target);}
    });
    const locals = {document:documentFacade,location:virtualLocation,history:virtualHistory};
    const windowFacade = new Proxy(locals, {
      get(target,key) {
        if(key==='window'||key==='self')return windowFacade;
        if(key==='addEventListener')return (type,listener,options)=>on(['resize','orientationchange','error','online','offline'].includes(type)?window:events,type,listener,options);
        if(key==='removeEventListener')return (type,listener,options)=>{events.removeEventListener(type,listener,options);window.removeEventListener(type,listener,options);};
        if(key==='dispatchEvent')return event=>events.dispatchEvent(event);
        if(key in target)return target[key];
        const value=window[key];return typeof value==='function'&&!/^class\s/.test(Function.prototype.toString.call(value))&&!/^[A-Z]/.test(String(key))?value.bind(window):value;
      },set(target,key,value){target[key]=value;return true;}
    });
    const navigatorFacade = new Proxy(navigator, {
      has:(target,key)=>key!=='serviceWorker'&&key in target,
      get(target,key) {
        if(key==='serviceWorker')return undefined;
        if(key==='mediaDevices')return {getUserMedia:async constraints=>{const stream=await navigator.mediaDevices.getUserMedia(constraints);if(!alive){stream.getTracks().forEach(track=>track.stop());throw new DOMException('Screen closed','AbortError');}streams.add(stream);return stream;},enumerateDevices:()=>navigator.mediaDevices.enumerateDevices()};
        const value=Reflect.get(target,key,target);return typeof value==='function'?value.bind(target):value;
      }
    });
    async function screenFetch(input, options={}) {
      if(!alive)throw new DOMException('Screen closed','AbortError');
      const url=new URL(typeof input==='string'?input:input.url,virtual), marker='/api/v1/', index=url.pathname.indexOf(marker);
      if(index<0)return fetch(url.href,{...options,signal:AbortSignal.any([lifetime.signal,...(options.signal?[options.signal]:[])])});
      const route=url.pathname.slice(index+marker.length), headers=new Headers(options.headers), payload=options.body?JSON.parse(options.body):{};
      try {
        let data;
        if(route==='session'||route==='auth/session')data=await bridge.session();
        else if(route==='logout'||route==='auth/logout'){await bridge.logout();data={};}
        else {
          if(/^(auth\/|login$|activate$)/.test(route))throw new Error('Use the Deckers account to sign in.');
          const operation=packet.app==='SCHEDULER'?route:url.pathname.startsWith('/employees/')?route:'mobile/'+route;
          data=await bridge.api(operation,payload,headers.get('Idempotency-Key'),lifetime.signal);
          if(operation==='mobile/bootstrap')data.deviceApps=[packet.app];
        }
        if(!alive)throw new DOMException('Screen closed','AbortError');
        return Response.json({ok:true,data});
      }catch(error){
        if(error.name==='AbortError')throw error;
        bridge.error(error);
        return Response.json({ok:false,error:{code:error.code||'SCREEN_ERROR',message:error.message}},{status:error.status===403?422:error.status||503});
      }
    }
    async function loadLibrary(path) {
      const name=new URL(path,virtual).pathname.split('/').pop();
      if(name!=='orders-barcode.js')throw new Error('This screen library is unavailable.');
      if(window.ZXingBrowser||window.ZXing)return;
      await new Promise((resolve,reject)=>{const script=document.createElement('script');script.src=asset('mobile-web/'+name);script.onload=resolve;script.onerror=reject;document.head.append(script);});
    }
    const trackedObserver = Constructor => class extends Constructor {constructor(callback){super(callback);observers.add(this);}};
    Object.assign(locals,{fetch:screenFetch,navigator:navigatorFacade,setTimeout:timeout,clearTimeout:id=>{timers.delete(id);clearTimeout(id);},setInterval:(fn,delay,...args)=>{const id=setInterval(()=>{if(alive)fn(...args);},delay);intervals.add(id);return id;},clearInterval:id=>{intervals.delete(id);clearInterval(id);},requestAnimationFrame:fn=>{const id=requestAnimationFrame(time=>{frames.delete(id);if(alive)fn(time);});frames.add(id);return id;},cancelAnimationFrame:id=>{frames.delete(id);cancelAnimationFrame(id);},MutationObserver:trackedObserver(MutationObserver),ResizeObserver:trackedObserver(ResizeObserver)});
    on(body,'click',event=>{const anchor=event.composedPath().find(node=>node.tagName==='A');if(anchor&&anchor.getAttribute('href')&&!anchor.hasAttribute('download')){const url=new URL(anchor.getAttribute('href'),virtual);if(url.origin===virtual.origin){event.preventDefault();navigate(url);}}});
    const dispose=()=>{if(!alive)return;events.dispatchEvent(new Event('pagehide'));alive=false;lifetime.abort();timers.forEach(clearTimeout);intervals.forEach(clearInterval);frames.forEach(cancelAnimationFrame);observers.forEach(observer=>observer.disconnect());streams.forEach(stream=>stream.getTracks().forEach(track=>track.stop()));root.querySelectorAll('video').forEach(video=>video.srcObject?.getTracks().forEach(track=>track.stop()));host.remove();};
    try {
      for(const library of packet.libraries)await loadLibrary(library);
      const module=await import(new URL(packet.module,location.href).href);
      module.mount({...locals,window:windowFacade});
      dispose.canLeave=()=>{const event=new Event('beforeunload',{cancelable:true});events.dispatchEvent(event);return !event.defaultPrevented||window.confirm('Leave this screen? Unsaved changes will be lost.');};
      return dispose;
    }catch(error){dispose();throw error;}
  }
};
