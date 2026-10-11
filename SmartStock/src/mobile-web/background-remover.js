(() => {
  'use strict';
  const $ = id => document.getElementById(id);
  let csrf = '', file = null, originalUrl = '', resultUrl = '', ready = false, busy = false, enhancementAvailable = false;
  let progressAvailable=false,requestId=null,polling=null;
  async function call(route, body = {}, write = false) {
    const response = await fetch('/api/v1' + route, { method: 'POST', credentials: 'include', headers: {
      'Content-Type': 'application/json', ...(write ? { 'X-CSRF-Token': csrf } : {})
    }, body: JSON.stringify(body) });
    const reply = await response.json();
    if (!response.ok || !reply.ok) throw new Error(reply.error?.message || 'Unable to process this photo.');
    return reply.data;
  }
  function updateControls() {
    $('photo').disabled = busy; $('removeBackground').disabled = busy;
    $('processingMode').disabled=busy||!progressAvailable;
    $('scale').disabled = busy || !enhancementAvailable;
    $('remove').disabled = busy || !ready || !file || (!$('removeBackground').checked && Number($('scale').value) === 1);
  }
  function clearResult() {
    if (resultUrl) URL.revokeObjectURL(resultUrl);
    resultUrl = ''; $('result').hidden = true; $('result').removeAttribute('src');
    $('download').hidden = true; $('download').removeAttribute('href');
  }
  $('removeBackground').onchange = updateControls; $('scale').onchange = updateControls;
  $('cancel').onclick=async()=>{
    if(!requestId)return;$('cancel').disabled=true;
    try{await call('/studio/progress',{requestId,cancel:true},true);$('status').textContent='Cancellation requested…';}
    catch(e){$('status').textContent=e.message;$('cancel').disabled=false;}
  };
  async function poll(id){
    if(id!==requestId)return;
    try{const p=await call('/studio/progress',{requestId:id},true);
      if(id!==requestId||p.state!=='running')return;
      if(p.total>0){$('progress').max=p.total;$('progress').value=p.completed;}else $('progress').removeAttribute('value');
      $('status').textContent=`${p.note||'Enhancing'}${p.device?' · '+p.device:''}${p.total?' · '+p.percent+'% ('+p.completed+'/'+p.total+' tiles)':''}${p.remainingSeconds>=0?' · about '+Math.max(1,Math.ceil(p.remainingSeconds/60))+' min remaining':''}`;
    }catch(e){/* Image request remains authoritative if a progress poll fails. */}
    finally{if(id===requestId)polling=setTimeout(()=>poll(id),1000);}
  }
  $('photo').onchange = () => {
    clearResult(); if (originalUrl) URL.revokeObjectURL(originalUrl);
    file = $('photo').files[0] || null; $('original').hidden = true;
    if (file && (!['image/png', 'image/jpeg'].includes(file.type) || file.size > 6 * 1024 * 1024)) {
      $('status').textContent = 'Choose a PNG or JPEG image of at most 6 MB.'; file = null;
    }
    if (file) {
      originalUrl = URL.createObjectURL(file); $('original').src = originalUrl; $('original').hidden = false;
      $('status').textContent = 'Your photo is ready.';
    }
    updateControls();
  };
  $('remove').onclick = async () => {
    if (!file || busy || !ready) return;
    const scale = Number($('scale').value), cutout = $('removeBackground').checked;
    busy = true; updateControls(); clearResult();
    $('status').textContent = scale > 1 ? 'Preparing enhancement… Large images can take several minutes.' : 'Removing background…';
    try {
      await $('original').decode();
      const encoded = await new Promise((resolve, reject) => {
        const reader = new FileReader(); reader.onload = () => resolve(String(reader.result).split(',')[1]);
        reader.onerror = () => reject(new Error('The photo could not be read.')); reader.readAsDataURL(file);
      });
      requestId=progressAvailable&&scale>1?crypto.randomUUID():null;
      if(requestId){$('cancel').hidden=false;$('cancel').disabled=false;$('progress').hidden=false;polling=setTimeout(()=>poll(requestId),1000);}
      const data = await call('/studio/remove-background', { bytesBase64: encoded, removeBackground: cutout, scale,
        ...(requestId?{requestId,processingMode:$('processingMode').value}:{}) }, true);
      const bytes = Uint8Array.from(atob(data.bytesBase64), c => c.charCodeAt(0));
      resultUrl = URL.createObjectURL(new Blob([bytes], { type: 'image/png' }));
      $('result').src = resultUrl; await $('result').decode();
      if ($('result').naturalWidth !== $('original').naturalWidth * scale || $('result').naturalHeight !== $('original').naturalHeight * scale)
        throw new Error('Update the store server to return the requested enhancement scale.');
      $('result').hidden = false; $('download').href = resultUrl;
      $('download').download = file.name.replace(/\.[^.]+$/, '') + '-processed.png'; $('download').hidden = false;
      const w = $('result').naturalWidth, h = $('result').naturalHeight;
      $('status').textContent = `${w} × ${h} pixels · ${(w / 300).toFixed(1)} × ${(h / 300).toFixed(1)} inches at 300 PPI. Ready to download.`;
    } catch (e) { clearResult(); $('status').textContent = e.message; }
    finally { requestId=null;clearTimeout(polling);$('cancel').hidden=true;$('progress').hidden=true;busy = false; updateControls(); }
  };
  (async () => {
    try {
      const session = await call('/session'); csrf = session.csrfToken;
      const brand = await call('/studio/branding'); $('company').textContent = brand.name;
      if (brand.logo) { $('logo').src = brand.logo; $('logo').hidden = false; }
      document.title = 'Image Studio · ' + brand.name;
      enhancementAvailable = brand.studioEnhancementAvailable === true;
      progressAvailable=brand.studioProgressAvailable===true;
      $('scale').title = enhancementAvailable ? 'Enhance at 2× or 4×.' : 'Ask an administrator to install Image enhancement in Status > AI Models, then reload this page.';
      $('contact').textContent = [brand.phone, brand.email].filter(Boolean).join(' · ');
      ready = true; $('status').textContent = enhancementAvailable ? 'Choose a photo to get started.' : 'Background removal is ready. An administrator can install the enhancement model in Status > AI Models.';
      updateControls();
    } catch (e) { $('status').textContent = e.message; $('login').hidden = false; }
  })();
  window.addEventListener('pagehide', () => { if (originalUrl) URL.revokeObjectURL(originalUrl); if (resultUrl) URL.revokeObjectURL(resultUrl); });
})();
