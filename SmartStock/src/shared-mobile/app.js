'use strict';
(() => {
  const $ = id => document.getElementById(id);
  const mobile = location.pathname.startsWith('/mobile-management/');
  const scheduler = location.pathname.startsWith('/scheduler/');
  const api = mobile ? '/api/v1/' : scheduler ? '/scheduler/api/v1/' : '/employees/api/v1/';
  if (mobile) {
    $('identifier').parentElement.firstChild.textContent = 'Username, email or badge';
    $('passwordLabel').firstChild.textContent = 'Password or PIN';
    $('password').required = false;
  }

  let rememberDevice=false;
  let accountProfile=null,lastDeviceReport=0;
  let sharedSession=null, disposeScreen=null, activeApp=null;
  let csrf = '', preauth = '', workspace = null, busy = false, revision = 0;
  function panel(name) {
    const heading=name==='screenPanel'?activeApp?.name:name==='account'?'Your account':name==='home'?'Your workspace':'';
    $('workspaceTitle').textContent=heading||'';$('workspaceTitle').hidden=!heading;
    $('workspaceTitle').classList.toggle('long-title',(heading||'').length>12);
    $('screenPanel').setAttribute('data-app',activeApp?.key||'');
    $('accountTitle').hidden=name==='account';
    ['approval','loginPanel','home','account','confirmation','screenPanel'].forEach(id => { $(id).hidden = id !== name; });
    $('navigation').hidden = $('profile').hidden = !['home','account','screenPanel'].includes(name);
    for (const tab of ['home','account']) {
      $(tab+'Tab').removeAttribute('aria-current');
      if (name === tab || (name === 'screenPanel' && tab === 'home')) $(tab+'Tab').setAttribute('aria-current','page');
    }
  }
  function notice(message = '', error = false) { $('status').textContent = message; $('status').classList.toggle('error', error); }
  function clearPrivate() {
    clearAccount(); closeScreen(true); sharedSession=null; revision++; workspace = null; csrf = ''; preauth = '';
    $('apps').replaceChildren(); $('employeeName').textContent = $('deviceName').textContent = $('profile').textContent = '';
    $('password').value = $('code').value = $('search').value = ''; $('mfaSecret').textContent = '';
    $('mfaQr').removeAttribute('src'); $('mfaQr').hidden = true; $('networkDialog').close();
    $('connection').classList.remove('connected'); $('connectionText').textContent = 'Store connection checked when needed';
  }
  function avatar(target,name,photo) {
    const initials=(name||'Employee').trim().split(/\s+/).slice(0,2).map(word=>word[0]).join('').toLocaleUpperCase();
    target.textContent=initials;
    if(!photo||!/^data:image\/(jpeg|png);base64,/.test(photo))return;
    const image=document.createElement('img');image.alt='';
    image.onerror=()=>{target.textContent=initials;};
    image.src=photo;target.replaceChildren(image);
  }
  function renderProfile(profile) {
    accountProfile=profile;
    $('employeeName').textContent=profile.name||workspace?.employeeName||'Employee';
    $('nicknameValue').textContent=profile.nickname||'Not set';
    $('emailValue').textContent=profile.email||'Not available';
    $('phoneValue').textContent=profile.phone||'Not available';
    $('nicknameInput').value=profile.nickname||'';
    avatar($('profile'),profile.name,profile.photo);avatar($('accountPhoto'),profile.name,profile.photo);
  }
  const accountRequest=(route,body={})=>request('/employees/api/v1/account/'+route,body,{auth:true,timeout:60000});
  async function loadProfile() {
    // Legacy LAN/Scheduler sessions have their own authentication. Keep them usable
    // without sending their CSRF token to a different employee session.
    if(mobile||scheduler){
      renderProfile({name:workspace?.employeeName});
      $('accountAvailability').hidden=false;
      for(const id of ['editNickname','editNicknamePrimary','changePassword'])$(id).disabled=true;
      return;
    }
    const version=revision;
    const profile=await accountRequest('profile');
    if(version===revision&&workspace)renderProfile(profile);
  }
  function clearAccount() {
    accountProfile=null;
    for(const id of ['nicknameInput','currentPassword','newPassword','confirmPassword','passwordCode'])$(id).value='';
    for(const id of ['nicknameValue','emailValue','phoneValue','accountPhoto','nicknameStatus','passwordStatus'])$(id).textContent='';
    $('nicknameDialog').close();$('passwordDialog').close();
  }
  function signedOut() { clearPrivate(); panel('loginPanel'); $('loginForm').hidden = false; $('mfaForm').hidden = true; }
  async function request(url, body = {}, options = {}) {
    const controller = new AbortController(), timer = setTimeout(() => controller.abort(), options.timeout || 25000);
    try {
      const headers = {'Content-Type':'application/json'};
      if (csrf && options.auth) headers['X-CSRF-Token'] = csrf;
      if(options.key)headers['Idempotency-Key']=options.key;
      const response = await fetch(url, {method:'POST', credentials:options.credentials || 'same-origin', cache:'no-store', headers, body:JSON.stringify(body), signal:options.signal?AbortSignal.any([controller.signal,options.signal]):controller.signal});
      const result = await response.json();
      if (!response.ok || !result.ok) {
        const error = new Error(result.error?.message || 'The store could not complete this request.');
        error.code = result.error?.code; error.status = response.status; throw error;
      }
      return result.data;
    } catch (error) {
      if (error.name === 'AbortError') throw new Error('The store took too long to respond. Please try again.');
      throw error;
    } finally { clearTimeout(timer); }
  }
  const call = (route, body) => request(route, body, {auth:route==='screen-api'});
  const auth = (route, body) => request(api + (mobile ? route : 'auth/' + route), body, {auth:true});
  function connection(connected) {
    $('connection').classList.toggle('connected', connected);
    $('connectionText').textContent = connected ? 'Connected to store' : 'Outside store network';
  }
  function handleError(error) {
    if(error.code==='ALREADY_PAIRED')window.DeckersReconnect?.show();
    if (['DEVICE_REQUIRED','DEVICE_REVOKED','ACTIVATION_REQUIRED'].includes(error.code)) { clearPrivate(); panel('approval'); }
    else if (error.code === 'DEVICE_APP_DISABLED') { clearPrivate(); panel('approval'); $('approvalTitle').textContent = 'No apps enabled'; $('approvalMessage').textContent = 'This device is approved, but no apps are enabled here. Ask a device manager to update its apps in Device Management → Mobile Management, then check again.'; }
    else if (error.status === 401) signedOut();
    if (['SESSION_REQUIRED','LOGIN_REQUIRED'].includes(error.code)) { notice(); return; }
    notice(error.message || 'Could not connect to the store. Please try again.', true);
  }
  function renderApps() {
    const query = $('search').value.trim().toLocaleLowerCase();
    const apps = (workspace?.apps || []).filter(app => app.name.toLocaleLowerCase().includes(query));
    $('apps').replaceChildren();
    for (const app of apps) {
      const button = document.createElement('button'); button.className = 'app'; button.type = 'button'; button.dataset.app = app.key;
      const icon = document.createElement('span'); icon.className = 'app-icon';
      const image = document.createElement('img'); image.src = 'icons/' + app.key + '.png'; image.alt = ''; image.decoding = 'async'; icon.append(image);
      if (app.localOnly) {
        const badge = document.createElement('span'); badge.className = 'network-badge'; badge.title = 'Store network required';
        const network = document.createElement('i'); network.className = 'glyph glyph-network'; network.setAttribute('aria-hidden','true'); badge.append(network); icon.append(badge);
        button.setAttribute('aria-label', app.name + ', store network required');
      }
      const label = document.createElement('span'); label.className = 'app-label'; label.textContent = app.name;
      button.append(icon, label); button.addEventListener('click', () => openApp(app, button)); $('apps').append(button);
    }
    $('empty').hidden = apps.length > 0;
    $('empty').textContent = workspace?.apps.length ? 'No apps match your search.' : 'No apps are available for both this device and your employee role. Ask your manager to review access.';
  }
  async function load() {
    if(!closeScreen())return;
    const version = ++revision, state = await call('state');
    if (version !== revision) return;
    workspace = state; $('employeeName').textContent = state.employeeName; $('deviceName').textContent = state.name;
    avatar($('profile'),state.employeeName,'');
    loadProfile().catch(error=>{if(error.status===401||error.code==='DEVICE_REVOKED')handleError(error);});
    renderApps(); panel('home'); notice();
    if(!mobile&&!scheduler&&window.DeckersDeviceInfo&&Date.now()-lastDeviceReport>300000) {
      lastDeviceReport=Date.now();
      window.DeckersDeviceInfo.collect().then(info=>{if(version===revision&&workspace)return accountRequest('device-info',info);}).catch(()=>{});
    }
    const localApp=state.apps.find(app=>app.localOnly);
    if(localApp)localChallenge(localApp).catch(()=>{});
  }
  async function openApp(app, button, screenPath) {
    if (busy) return;
    busy = true; button.disabled = true; notice();
    const version = revision;
    try {
      const challenge=await localChallenge(app);
      if(version!==revision)return;
      if(!workspace.screens)throw new Error('Open the updated Deckers workspace to use these screens.');
      const packet=await call('screen',{app:app.key,...(challenge?{challenge}:{})});
      if(version!==revision)return;
      if(screenPath)packet.path=screenPath;
      await mountScreen(app,packet);

    } catch (error) {
      if (error.code === 'LAN_ONLY') { connection(false); $('networkDialog').showModal(); }
      else { handleError(error); if (error.status === 403 && workspace) await load().catch(handleError); }
    } finally { busy = false; button.disabled = false; }
  }
  function closeScreen(force=false){if(!force&&disposeScreen?.canLeave&&!disposeScreen.canLeave())return false;disposeScreen?.();disposeScreen=null;activeApp=null;$('screenHost').replaceChildren();return true;}
  let networkCheckRevision=0;
  async function localChallenge(app){
    if(!app.localOnly)return null;
    const check=++networkCheckRevision;
    for(let attempt=0;attempt<2;attempt++){
      const probe=await call('network-challenge',{app:app.key}),url=new URL(probe.probeUrl);
      if(url.protocol!=='https:'||url.pathname!=='/mobile-management/network-probe')throw new Error('The store connection address is invalid.');
      try{
        await request(url.href,{challenge:probe.challenge},{credentials:'omit',timeout:10000});
        if(check===networkCheckRevision)connection(true);
        return probe.challenge;
      }catch(cause){
        // Retry only the credential-free proof, before any item operation is sent.
        // Safari may need to reconnect its LAN socket after the app resumes.
        if(attempt===0){await new Promise(resolve=>setTimeout(resolve,250));continue;}
        if(check===networkCheckRevision)connection(false);
        const error=new Error('Cannot reach the store network. Check store Wi-Fi and try again.');error.code='LAN_ONLY';error.cause=cause;throw error;
      }
    }
  }
  async function mountScreen(app,packet){
    if(!closeScreen())return;activeApp=app;$('screenTitle').textContent=app.name;panel('screenPanel');
    const host=document.createElement('div');$('screenHost').append(host);
    const version=revision;
    if(app.key==='CUSTOM_ORDER_DESIGN'&&!new URL(packet.path,location.origin).searchParams.has('orderId')) {
      const form=document.createElement('form'),label=document.createElement('label'),input=document.createElement('input'),button=document.createElement('button'),message=document.createElement('p');
      form.className='auth';label.textContent='Custom order number';input.required=true;input.maxLength=160;input.autocomplete='off';label.append(input);button.type='submit';button.className='primary';button.textContent='Open order design';message.setAttribute('role','status');form.append(label,button,message);host.append(form);
      form.onsubmit=async event=>{event.preventDefault();button.disabled=true;message.textContent='Finding order...';try {
        const challenge=await localChallenge(app),data=await request('screen-api',{app:app.key,route:'mobile/orders/lookup',payload:{barcode:input.value.trim()},challenge},{auth:true});
        if(!data.found)throw new Error('No custom order matches that number at this store.');
        if(version!==revision||activeApp!==app)return;
        await mountScreen(app,{...packet,path:'/design.html?orderId='+encodeURIComponent(data.order.orderId)});
      }catch(error){message.textContent=error.message;}finally{button.disabled=false;}};
      disposeScreen=()=>host.remove();return;
    }
    const dispose=await window.DeckersScreens.mount(host,packet,{
      session:async()=>{if(!sharedSession){sharedSession=await auth('session');csrf=sharedSession.csrfToken;}return {...sharedSession,csrfToken:csrf};},
      api:async(route,payload,key,signal)=>{
        let challenge=await localChallenge(app);
        if(app.key==='PAYROLL'&&route==='payroll/dashboard'&&payload?.detectStore){
          try{challenge=await localChallenge({...app,localOnly:true});}catch{connection(false);}
        }
        if(version!==revision||activeApp!==app)throw new DOMException('Screen closed','AbortError');
        return request('screen-api',{app:app.key,route,payload,...(challenge?{challenge}:{})},{auth:true,key,signal,timeout:180000});
      },
      error:error=>{if(error.code==='LAN_ONLY'){connection(false);$('networkDialog').showModal();}else if(error.status===401||['DEVICE_REVOKED','DEVICE_REQUIRED'].includes(error.code))handleError(error);else if(error.status===403&&['DEVICE_APP_DISABLED','ACCESS_DENIED'].includes(error.code)){load().catch(handleError);notice(error.message,true);}},
      home:()=>load().catch(handleError),logout:async()=>{await auth('logout');signedOut();},reload:()=>openApp(app,$('screenHome')),
      navigate:url=>{
        const mapping={'/orders.html':'ORDERS','/employees/orders.html':'ORDERS','/inventory.html':'INVENTORY','/employees/inventory.html':'INVENTORY','/edit-items.html':'EDIT_ITEMS','/receiving.html':'RECEIVING','/background-remover.html':'IMAGE_STUDIO','/design.html':'CUSTOM_ORDER_DESIGN','/scheduler/':'SCHEDULER'};
        const target=workspace?.apps.find(candidate=>candidate.key===mapping[url.pathname]);
        if(target)openApp(target,$('screenHome'),url.pathname+url.search);else load().catch(handleError);
      }
    });
    if(version!==revision||activeApp!==app)dispose();else disposeScreen=dispose;
  }
  async function submit(form, work) {
    const buttons = [...form.querySelectorAll('button')]; buttons.forEach(button => { button.disabled = true; });
    try { notice(); await work(); } catch (error) { handleError(error); } finally { buttons.forEach(button => { button.disabled = false; }); }
  }
  let loginPending=false, startup=Promise.resolve();
  $('loginForm').addEventListener('submit', event => {
    event.preventDefault();
    if(loginPending)return;
    loginPending=true;
    // Finish the initial session probe before starting a new login. Otherwise its
    // late 401 can clear the password or replace a successful sign-in screen.
    submit($('loginForm'), async () => {
      await startup;
      // iPhone Password AutoFill can submit before committing both DOM values.
      // Read on the next task; input/change events are not guaranteed.
      await new Promise(resolve=>setTimeout(resolve,0));
      const identifier=$('identifier').value.trim(),secret=$('password').value;
      if(!identifier || (!mobile && !secret)) {
        notice('Your saved login is ready to fill. Check the username and password, then tap Sign in.');
        return;
      }
      const result = await auth('login', {identifier, [mobile ? 'secret' : 'password']:secret});
      $('password').value = '';
      if (result.mfaRequired) {
        preauth = result.preauth; $('loginForm').hidden = true; $('mfaForm').hidden = false;
        if (result.qrCode) { $('mfaQr').src = result.qrCode; $('mfaQr').hidden = false; }
        $('mfaSecret').textContent = result.secret || ''; $('code').focus();
      } else { rememberDevice=result.rememberDevice===true; csrf = result.csrfToken; sharedSession=null; await load(); }
    }).finally(()=>{loginPending=false;});
  });
  $('mfaForm').addEventListener('submit', event => {
    event.preventDefault(); submit($('mfaForm'), async () => {
      const code = $('code').value; $('code').value = '';
      try { const result = await auth('verify', {preauth, code}); rememberDevice=result.rememberDevice===true; csrf = result.csrfToken; preauth = ''; $('mfaQr').removeAttribute('src'); $('mfaQr').hidden = true; $('mfaSecret').textContent = ''; await load(); }
      catch (error) { signedOut(); throw error; }
    });
  });
  $('restartLogin').onclick = () => { signedOut(); notice(); };
  $('screenHome').onclick = $('homeTab').onclick = () => { load().catch(handleError); };
  $('accountTab').onclick = $('profile').onclick = () => { if(closeScreen()){panel('account');loadProfile().catch(handleError);} };
  $('editNickname').onclick=$('editNicknamePrimary').onclick=()=>{$('nicknameStatus').textContent='';$('nicknameInput').value=accountProfile?.nickname||'';$('nicknameDialog').showModal();$('nicknameInput').focus();};
  $('cancelNickname').onclick=()=>$('nicknameDialog').close();
  $('changePassword').onclick=()=>{$('passwordForm').reset();$('passwordStatus').textContent='';$('passwordDialog').showModal();$('currentPassword').focus();};
  $('cancelPassword').onclick=()=>{$('passwordDialog').close();$('passwordForm').reset();};
  $('passwordDialog').addEventListener('close',()=>{$('passwordForm').reset();});
  async function accountSubmit(form,status,work) {
    const version=revision,buttons=[...form.querySelectorAll('button')];buttons.forEach(button=>button.disabled=true);status.textContent='';
    try{await work(version);}catch(error){if(version===revision){status.textContent=error.message;if(['SESSION_REQUIRED','SESSION_EXPIRED','DEVICE_REVOKED','DEVICE_REQUIRED','DEVICE_APP_DISABLED'].includes(error.code))handleError(error);}}
    finally{buttons.forEach(button=>button.disabled=false);}
  }
  $('nicknameForm').onsubmit=event=>{event.preventDefault();accountSubmit($('nicknameForm'),$('nicknameStatus'),async version=>{
    const profile=await accountRequest('nickname',{nickname:$('nicknameInput').value.trim()});
    if(version!==revision)return;renderProfile(profile);$('nicknameDialog').close();notice('Nickname saved.');
  });};
  $('passwordForm').onsubmit=event=>{event.preventDefault();accountSubmit($('passwordForm'),$('passwordStatus'),async version=>{
    if($('newPassword').value!==$('confirmPassword').value)throw new Error('The new passwords do not match.');
    await accountRequest('password',{currentPassword:$('currentPassword').value,newPassword:$('newPassword').value,code:$('passwordCode').value.trim()});
    if(version!==revision)return;$('passwordForm').reset();$('passwordDialog').close();notice('Password updated. Your other Deckers workspace sessions have been signed out.');
  });};
  $('search').oninput = renderApps;
  $('refresh').onclick = () => submit($('account'), load);
  $('logout').onclick = () => submit($('account'), async () => { const session=await auth('session'); csrf=session.csrfToken; await auth('logout'); signedOut(); notice('Signed out. This device remains approved.'); });
  $('repairDevice').onclick=()=>window.DeckersReconnect?.show();
  $('retryApproval').onclick = () => start().catch(handleError);
  // Hiding the app for camera use or app switching must not sign the employee out.
  window.addEventListener('pagehide', () => {
    if (mobile || scheduler || !workspace || rememberDevice || !csrf) return;
    fetch(api + (mobile ? 'logout' : 'auth/logout'), {method:'POST',credentials:'same-origin',keepalive:true,headers:{'Content-Type':'application/json','X-CSRF-Token':csrf},body:'{}'}).catch(()=>{});
    clearPrivate();
  });
  window.addEventListener('pageshow', event => { if(event.persisted)start().catch(handleError); });
  window.addEventListener('offline', () => { connection(false); });
  document.addEventListener('visibilitychange', () => { if (!document.hidden && workspace && !busy) { if(activeApp)call('state').then(state=>{workspace=state;if(!state.apps.some(app=>app.key===activeApp?.key))load().catch(handleError);}).catch(handleError);else if(!$('account').hidden)loadProfile().catch(handleError);else load().catch(handleError); } });
  async function start() {
    const fragment = new URLSearchParams(location.hash.slice(1)), confirmationUrl = location.href;
    history.replaceState(null, '', location.pathname);
    if (fragment.has('link')) {
      panel('confirmation');
      try {
        const requestId = fragment.get('link'), info = await call('link-info', {requestId});
        notice(`Connect ${info.appName} to ${info.deviceName}? Only confirm a request you started on this phone.`);
        const confirm = document.createElement('button'); confirm.className = 'primary'; confirm.textContent = 'Connect this app';
        confirm.onclick = () => submit($('confirmation'), async () => { await call('approve-link', {requestId}); confirm.remove(); notice('Connected. Return to your home-screen app.'); });
        $('confirmation').replaceChildren(confirm);
      } catch (error) {
        notice(error.message + ' Open this link in the Safari browser where you paired this phone.', true);
        const link = document.createElement('a'); link.href = confirmationUrl; link.textContent = 'Open this confirmation link in your paired Safari browser'; $('confirmation').replaceChildren(link);
      }
      return;
    }
    if(fragment.has('repair')){try{await call('repair',{token:fragment.get('repair')});}catch(error){window.DeckersReconnect?.show();throw error;}}
    if (fragment.has('pair')) await call('pair', {token:fragment.get('pair')});
    if (fragment.has('handoff')) { const result = await call('redeem', {token:fragment.get('handoff')}); location.replace(result.url); return; }
    // Native authentication retains existing MFA, session expiry and CSRF checks.
    const session = await auth('session'); sharedSession=session; rememberDevice=session.rememberDevice===true; csrf = session.csrfToken;
    await load();
  }
  startup=start().catch(error=>{
    // The initial unauthenticated probe must not erase credentials being filled
    // by the password manager. It has no private workspace to clear yet.
    if(error.status===401 && !workspace) {
      panel('loginPanel');$('loginForm').hidden=false;$('mfaForm').hidden=true;
      if(!['SESSION_REQUIRED','LOGIN_REQUIRED'].includes(error.code))notice(error.message,true);
    } else handleError(error);
  });
})();
