/* Existing iPhone home-screen apps have separate cookies from the paired Safari browser. */
(() => {
  'use strict';
  const originalFetch = window.fetch.bind(window);
  const path = location.pathname;
  const employee = path.startsWith('/employees/');
  const scheduler = path.startsWith('/scheduler/');
  const prefix = employee ? '/employees/mobile-management/' : scheduler ? '/scheduler/mobile-management/' : '/mobile-management/';
  // Keep existing per-app login/MFA forms, but present the same Deckers identity everywhere.
  if (!path.includes('/mobile-management/')) {
    const theme = document.createElement('link'); theme.rel = 'stylesheet'; theme.href = prefix + 'signin.css'; document.head.append(theme);
    const setupTheme = () => {
      const header = document.createElement('header'); header.className = 'deckers-signin-header';
      const logo = document.createElement('img'); logo.src = prefix + 'logo.png'; logo.alt = 'Deckers';
      const home = document.createElement('a'); home.href = prefix; home.textContent = 'All apps'; header.append(logo,home); document.body.prepend(header);
      // Obsolete per-app activation endpoints are disabled server-side. Point to the single approval workflow.
      for (const form of document.querySelectorAll('form#activation,form#activateBrowser')) {
        const legacy = form.closest('details') || form;
        legacy.classList.add('deckers-legacy-activation');
      }
      const authSection = document.querySelector('section#auth,section#loginPanel,section#login');
      if (authSection) {
        const help = document.createElement('p'), link = document.createElement('a');
        help.className = 'deckers-device-help'; link.href = prefix; link.textContent = 'Open Deckers workspace · device approval and available apps'; help.append(link); authSection.append(help);
      }
      const update = () => {
        const active = [...document.querySelectorAll('section#auth,section#login,section#loginPanel,section#mfa')].some(section => !section.hidden && !section.classList.contains('hidden') && getComputedStyle(section).display !== 'none');
        if (document.body.classList.contains('deckers-signin') !== active) document.body.classList.toggle('deckers-signin',active);
      };
      new MutationObserver(update).observe(document.body,{subtree:true,attributes:true,attributeFilter:['hidden','class'],childList:true}); update();
    };
    if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded',setupTheme,{once:true}); else setupTheme();
  }
  function appKey() {
    if (path.includes('/mobile-management/')) return 'WORKSPACE';
    if (scheduler) return 'SCHEDULER';
    if (/orders/.test(path) || location.hostname === 'ordersmanagement.deckers.gy') return 'ORDERS';
    if (/inventory/.test(path) || location.hostname === 'inventory.deckers.gy') return 'INVENTORY';
    if (/receiving/.test(path)) return 'RECEIVING';
    if (/balance-sheet/.test(path)) return 'BALANCE_SHEET';
    if (/minutes/.test(path)) return 'PHONE_MINUTES';
    if (/customers/.test(path)) return 'CUSTOMERS';
    if (/edit-items/.test(path)) return 'EDIT_ITEMS';
    if (/photo/.test(path)) return 'IMAGE_STUDIO';
    if (/background-remover/.test(path)) return 'IMAGE_STUDIO';
    if (/design/.test(path)) return 'CUSTOM_ORDER_DESIGN';
    if (location.hostname === 'overseasshipping.deckers.gy' || /overseas/.test(path)) return 'OVERSEAS_ORDERS';
    if (employee) return 'EMPLOYEE_WEB';
    if (new URLSearchParams(location.search).get('spoils') === '1') return 'CUSTOM_ORDER_SPOILS';
    return 'MOBILE_ITEMS';
  }
  let panel, request, timer, polling = false;
  const pendingKey = 'smartstock.pendingDeviceLink';
  function remember(value) { try { if(value)sessionStorage.setItem(pendingKey,JSON.stringify(value));else sessionStorage.removeItem(pendingKey); } catch { } }
  async function call(route, body) {
    const response = await originalFetch(prefix + route, {method:'POST', credentials:'same-origin', cache:'no-store', headers:{'Content-Type':'application/json'}, body:JSON.stringify(body)});
    const reply = await response.json();
    if (!response.ok || !reply.ok) throw new Error(reply.error?.message || 'Could not connect this app.');
    return reply.data;
  }
  function element(tag, text) { const node = document.createElement(tag); if (text) node.textContent = text; return node; }
  let qrLibrary;
  async function qrReader() {
    if (!window.ZXingBrowser) {
      if (!qrLibrary) qrLibrary = new Promise((resolve,reject) => {
        const script=document.createElement('script');script.src=prefix+'repair-qr.js';
        script.onload=resolve;script.onerror=()=>{qrLibrary=null;script.remove();reject(new Error('The QR scanner could not load. Try again or paste the re-pair link.'));};document.head.append(script);
      });
      await qrLibrary;
    }
    return new window.ZXingBrowser.BrowserQRCodeReader();
  }
  function repairToken(value) {
    let url;try {url=new URL(value);}catch {throw new Error('Scan a Re-pair QR issued for this phone in Mobile Management.');}
    const token=new URLSearchParams(url.hash.slice(1)).get('repair');
    if(url.origin!=='https://deckersapps.deckers.gy'||url.pathname!=='/employees/mobile-management/'||!token||! /^[A-Za-z0-9_-]{20,300}$/.test(token))throw new Error('Use the Re-pair QR for this phone, not an app link or a new-phone pairing QR.');
    return token;
  }
  function show() {
    if (panel) return;
    if (!document.body) { document.addEventListener('DOMContentLoaded', show, {once:true}); return; }
    panel = element('section'); panel.setAttribute('role','region');
    panel.setAttribute('aria-labelledby','deviceReconnectTitle');panel.className = 'card auth device-reconnect';
    const title = element('h2','Connect this home-screen app'); title.id = 'deviceReconnectTitle';
    const explanation = element('p','Use your existing Safari approval, or scan an administrator’s Re-pair QR to reconnect this browser to the same approved device.');
    const status = element('p'); status.setAttribute('role','status');
    const connect = element('button','Use existing iPhone pairing'); connect.type = 'button';
    const repair = element('button','Scan re-pair QR code');repair.type='button';repair.className='secondary';
    const repairSection=element('section');repairSection.className='device-repair-controls';repairSection.hidden=true;
    const help=element('p','Ask a device manager to select this phone in Mobile Management and choose Re-pair QR. Its enabled apps stay the same. You will sign in again after reconnecting.');
    const video=element('video');video.setAttribute('playsinline','');video.setAttribute('autoplay','');video.muted=true;video.hidden=true;
    const photo=element('input');photo.type='file';photo.accept='image/*';photo.hidden=true;
    const choose=element('button','Choose a QR image');choose.type='button';choose.className='secondary';choose.onclick=()=>{photo.value='';photo.click();};
    const fallback=element('form'),label=element('label','Or paste the re-pair link'),link=element('input'),useLink=element('button','Use re-pair link');
    link.type='url';link.autocomplete='off';link.required=true;link.setAttribute('aria-label','Re-pair QR link');label.append(link);useLink.type='submit';fallback.append(label,useLink);
    const cancel=element('button','Cancel scan');cancel.type='button';cancel.className='secondary';
    repairSection.append(help,video,choose,photo,fallback,cancel);
    let stream,controls,scanVersion=0,repairing=false;
    function stopScanner(){scanVersion++;controls?.stop();controls=null;stream?.getTracks().forEach(track=>track.stop());stream=null;video.srcObject=null;video.hidden=true;}
    async function redeem(value){
      const token=repairToken(value);if(repairing)return;
      repairing=true;stopScanner();repair.disabled=choose.disabled=useLink.disabled=true;status.textContent='Reconnecting this device…';
      try{await call('repair',{token});remember(null);request=null;link.value='';location.reload();}
      catch(error){status.textContent=error.message;}
      finally{repairing=false;repair.disabled=choose.disabled=useLink.disabled=false;}
    }
    fallback.onsubmit=event=>{event.preventDefault();redeem(link.value.trim()).catch(error=>{status.textContent=error.message;});};
    photo.onchange=async event=>{
      const file=event.target.files?.[0];if(!file)return;stopScanner();const version=scanVersion;let url;
      try{const reader=await qrReader();url=URL.createObjectURL(file);const result=await reader.decodeFromImageUrl(url);if(version===scanVersion)await redeem(result.getText());}
      catch(error){status.textContent=error.message||'No re-pair QR was found in that image.';}finally{if(url)URL.revokeObjectURL(url);photo.value='';}
    };
    repair.onclick=async()=>{
      clearTimeout(timer);request=null;remember(null);connect.disabled=false;open.hidden=copy.hidden=true;stopScanner();repairSection.hidden=false;
      const version=scanVersion;status.textContent='Point the camera at the Re-pair QR for this phone.';
      try{
        const reader=await qrReader();
        if(version!==scanVersion)return;
        if(!navigator.mediaDevices?.getUserMedia)throw new Error('Camera access is unavailable. Choose a QR image or paste the re-pair link.');
        const camera=await navigator.mediaDevices.getUserMedia({video:{facingMode:{ideal:'environment'}},audio:false});
        if(version!==scanVersion){camera.getTracks().forEach(track=>track.stop());return;}
        stream=camera;video.hidden=false;
        const running=await reader.decodeFromStream(camera,video,result=>{if(result&&version===scanVersion)redeem(result.getText()).catch(error=>{status.textContent=error.message;});});
        if(version!==scanVersion)running?.stop();else controls=running;
      }catch(error){stopScanner();status.textContent=error.name==='NotAllowedError'?'Camera permission was not granted. Choose a QR image or paste the re-pair link.':error.message;}
    };
    cancel.onclick=()=>{stopScanner();repairSection.hidden=true;link.value='';status.textContent='';};
    window.addEventListener?.('pagehide',stopScanner);
    document.addEventListener('visibilitychange',()=>{if(document.hidden)stopScanner();});
    const open = element('a','Open confirmation in Safari'); open.target = '_blank'; open.rel = 'noopener'; open.hidden = true;
    const copy = element('button','Copy Safari confirmation link'); copy.type = 'button'; copy.hidden = true;
    const close = element('button','Close'); close.type = 'button';
    close.onclick = () => { stopScanner();clearTimeout(timer); request = null; remember(null); panel.remove(); panel = null; };
    async function poll() {
      if (polling || !request || !panel) return;
      polling = true;
      try {
        const pending=request,result = await call('link-status', {requestId:pending.requestId});
        if(request!==pending||!panel)return;
        if (result.approved) { request = null; remember(null); location.reload(); return; }
      } catch (error) { status.textContent = error.message; connect.disabled = false; request = null; remember(null); }
      finally { polling = false; }
      if (request && panel) timer = setTimeout(poll, 2000);
    }
    connect.onclick = async () => {
      stopScanner();repairSection.hidden=true;
      connect.disabled = true;clearTimeout(timer);
      // Create the window inside the tap event, before the network request, for Safari popup rules.
      let confirmation = null;
      try {confirmation = window.open('about:blank', '_blank');if (confirmation) confirmation.opener = null;} catch { }
      try {
        request = await call('link-request', {app:appKey()});
        remember(request);
        open.href = request.approvalUrl;open.hidden = false;copy.hidden = false;
        status.textContent = 'Confirm in Safari where you paired this iPhone, then return here. If the opened browser does not recognize your pairing, copy the link and paste it into that Safari browser.';
        if (confirmation) confirmation.location.replace(request.approvalUrl);
        poll();
      } catch (error) { if (confirmation) confirmation.close(); status.textContent = error.message;connect.disabled = false; }
    };
    copy.onclick = async () => {
      try { await navigator.clipboard.writeText(request.approvalUrl); status.textContent = 'Copied. Paste the link into Safari where you paired, confirm, then return to this home-screen app.'; }
      catch { status.textContent = 'Touch and hold the confirmation link to copy it, then paste it into Safari where you paired.'; }
    };
    panel.append(title, explanation, status, connect, repair, repairSection, element('p'), open, element('p'), copy, close);
    document.body.prepend(panel);panel.scrollIntoView?.({block:'start'});connect.focus();
    try {
      const pending = JSON.parse(sessionStorage.getItem(pendingKey) || 'null');
      if(pending && Date.parse(pending.expiresAt) > Date.now()) {
        request = pending;connect.disabled = true;open.href = pending.approvalUrl;open.hidden = false;copy.hidden = false;
        status.textContent = 'Waiting for confirmation in your paired Safari browser. Return here after confirming.';poll();
      } else remember(null);
    } catch { remember(null); }
    document.addEventListener('visibilitychange', () => { if (!document.hidden && request && panel) {clearTimeout(timer);poll();} });
  }
  window.DeckersReconnect={show};
  window.fetch = async function(input, init) {
    const response = await originalFetch(input, init);
    try {
      const url = new URL(typeof input === 'string' || input instanceof URL ? input : input.url, location.href);
      const api = employee ? '/employees/api/v1/' : scheduler ? '/scheduler/api/v1/' : '/api/v1/';
      if (url.origin === location.origin && url.pathname.startsWith(api) && [401,403].includes(response.status)) {
        const reply = await response.clone().json();
        if (['DEVICE_REQUIRED','DEVICE_REVOKED','ACTIVATION_REQUIRED'].includes(reply.error?.code)) show();
      }
    } catch { /* Preserve the application's own response/error handling. */ }
    return response;
  };
})();
