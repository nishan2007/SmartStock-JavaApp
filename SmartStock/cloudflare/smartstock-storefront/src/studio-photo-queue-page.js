export const queuePage=String.raw`<!doctype html><html lang="en"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><link rel="manifest" href="/photo.webmanifest"><link rel="apple-touch-icon" href="/photo-icon-180.png"><meta name="theme-color" content="#7133a8"><meta name="apple-mobile-web-app-capable" content="yes"><meta name="apple-mobile-web-app-title" content="PICS"><title>SmartStudio · Photo queue</title>
<style>body{margin:0;background:#f4f3f8;color:#242035;font:17px system-ui}main{max-width:480px;margin:28px auto;padding:24px}h1{font-size:30px}p{line-height:1.5}section{background:white;border-radius:20px;padding:24px;box-shadow:0 8px 32px #24153612}button,.button{display:block;border:0;border-radius:12px;background:#7133a8;color:white;padding:15px;margin:14px 0;text-align:center;font:inherit;cursor:pointer}.secondary{background:#ece5f4;color:#53237e}button,input[type=text]{width:100%;box-sizing:border-box;font:inherit}input[type=text]{padding:12px;border:1px solid #cdc6d5;border-radius:10px;margin-top:8px}button:disabled{opacity:.5}img{width:100%;max-height:340px;object-fit:contain;border-radius:12px}[hidden]{display:none!important}small{color:#625b70}</style>
<main><small>DECKERS · SMARTSTUDIO</small><h1>Photo queue</h1><section><p id="status" role="status" aria-live="polite">Take pictures for several people, one at a time. Each photo goes to your store's shared queue.</p><label id="storeChoice" hidden>Paired store<select id="stores"></select></label><button id="forget" class="secondary" hidden>Forget this phone pairing</button><div id="activation" hidden><p>Home-screen apps can keep pairing separate from your browser. In SmartStudio, open Photo Queue, choose Pair staff phone, and scan its QR code here or paste its private link.</p><button id="scan">Scan pairing QR code</button><div id="scanner" hidden><video id="scanVideo" autoplay muted playsinline style="width:100%;border-radius:12px"></video><button id="stopScan" class="secondary">Cancel scan</button></div><label for="pairLink">Staff phone pairing link</label><input id="pairLink" type="text" autocomplete="off" autocapitalize="none" spellcheck="false"><button id="activate">Activate this app</button><small>Keep this upload-only pairing link private.</small></div><div id="controls"><label for="name">Name or reference <small>(optional)</small></label><input id="name" type="text" maxlength="80" autocomplete="off" placeholder="e.g. Person 1"><img id="preview" alt="Selected photo" hidden><button id="openCamera">Take photo</button><div id="photoCamera" hidden><video id="photoVideo" autoplay muted playsinline style="width:100%;border-radius:12px"></video><button id="shutter">Capture photo</button><button id="closeCamera" class="secondary">Cancel camera</button></div><div id="lightControls" hidden><label for="photoLight">Photo brightness <output id="lightValue">0 EV</output></label><input id="photoLight" type="range" min="0" max="1.5" step="0.1" value="0" style="width:100%"><small>Adjust until the face looks natural. The saved photo uses this brightness too.</small></div><label class="button secondary">Use phone camera instead<input id="camera" type="file" accept="image/*" capture="environment" hidden></label><label class="button secondary">Choose from Photos<input id="library" type="file" accept="image/*" hidden></label><p><small>For best detail, use the rear camera at 1× in good light. You can also take the picture in the Camera app, then choose it from Photos.</small></p><label><input id="removeBackground" type="checkbox" checked> Remove background after upload</label><button id="send" disabled>Add to queue</button></div></section><p id="count">No photos sent yet.</p><small>No template needs to be open. Staff can select photos on any paired store computer. Photos are deleted 24 hours after upload, whether printed or not. <span id="accessNote">Private upload links last 12 hours. Paired staff phones stay connected until revoked in SmartStudio.</span></small></main>
<script src="/photo-qr.js"></script><script>
const p=new URLSearchParams(location.hash.slice(1));history.replaceState(null,'',location.pathname);
const $=id=>document.getElementById(id),storageKey='smartstudio.photoPhones.v1';let pairs=[];
try{pairs=JSON.parse(localStorage.getItem(storageKey)||'[]');if(!Array.isArray(pairs))pairs=[];pairs=pairs.filter(x=>x&&/^[a-f0-9-]{36}$/.test(x.instance)&&/^[A-Za-z0-9_-]{43}$/.test(x.token));}catch{pairs=[];}
let instance=p.get('instance'),token=p.get('token'),paired=p.get('paired')==='1',file,url,uploadId,uploaded=0,sending=false;
function savePairs(){try{localStorage.setItem(storageKey,JSON.stringify(pairs));return true;}catch{return false;}}
let storageFailed=false;
if(paired&&/^[a-f0-9-]{36}$/.test(instance||'')&&/^[A-Za-z0-9_-]{43}$/.test(token||'')){
 pairs=pairs.filter(x=>x.instance!==instance);pairs.push({instance,token,store:('Store '+(p.get('store')||'')).slice(0,80)});storageFailed=!savePairs();
}
if(!instance&&!token&&pairs.length){({instance,token}=pairs[pairs.length-1]);paired=true;}
if(!/^[a-f0-9-]{36}$/.test(instance||'')||!/^[A-Za-z0-9_-]{43}$/.test(token||'')){
 $('status').textContent='Pair this staff phone once: in SmartStudio open Photo Queue, choose Pair staff phone, and scan the QR code. Then return to pic.deckers.gy anytime.';$('controls').hidden=true;$('activation').hidden=false;
}else if(paired){$('accessNote').textContent='This staff phone stays paired until revoked in SmartStudio. The store server must be online to receive photos.';if(storageFailed)$('status').textContent='Browser storage is disabled. This session can upload, but enable storage to remember this phone.';}
function activateLink(value){
 try{
  const link=new URL(value.trim()),grant=new URLSearchParams(link.hash.slice(1));
  if(link.protocol!=='https:'||link.hostname!=='pic.deckers.gy'||link.port||link.pathname!=='/'||link.username||link.password||grant.get('paired')!=='1'||!/^[a-f0-9-]{36}$/.test(grant.get('instance')||'')||!/^[A-Za-z0-9_-]{43}$/.test(grant.get('token')||''))throw Error();
  const next={instance:grant.get('instance'),token:grant.get('token'),store:('Store '+(grant.get('store')||'')).slice(0,80)};
  const previous=pairs;pairs=pairs.filter(x=>x.instance!==next.instance);pairs.push(next);
  if(!savePairs()){pairs=previous;$('status').textContent='Enable storage for this app, then try activating again.';return;}
  $('pairLink').value='';location.reload();
 }catch{$('status').textContent='Paste the full private link from SmartStudio Photo Queue: Pair staff phone. Temporary photo links cannot activate this app.';}
};
$('activate').onclick=()=>activateLink($('pairLink').value);
let scanStream,scanTimer,scanGeneration=0;
function stopScan(){scanGeneration++;clearTimeout(scanTimer);if(scanStream)scanStream.getTracks().forEach(track=>track.stop());scanStream=null;$('scanVideo').srcObject=null;$('scanner').hidden=true;$('scan').disabled=false;}
$('stopScan').onclick=stopScan;
window.addEventListener('pagehide',stopScan);
document.addEventListener('visibilitychange',()=>{if(document.hidden)stopScan();});
$('scan').onclick=async()=>{
 stopScan();const generation=scanGeneration;$('scan').disabled=true;
 try{
  if(!navigator.mediaDevices?.getUserMedia)throw Error('Camera scanning is unavailable. Paste the pairing link instead.');
  const stream=await navigator.mediaDevices.getUserMedia({video:{facingMode:{ideal:'environment'}},audio:false});
  if(generation!==scanGeneration){stream.getTracks().forEach(track=>track.stop());return;}
  scanStream=stream;$('scanner').hidden=false;const video=$('scanVideo');video.srcObject=stream;await video.play();
  const canvas=document.createElement('canvas'),ctx=canvas.getContext('2d',{willReadFrequently:true});
  $('status').textContent='Point the camera at the staff phone pairing QR code in SmartStudio.';
  function frame(){
   if(generation!==scanGeneration)return;
   try{
    if(video.readyState>=2&&video.videoWidth){const scale=Math.min(1,800/video.videoWidth);canvas.width=Math.round(video.videoWidth*scale);canvas.height=Math.round(video.videoHeight*scale);ctx.drawImage(video,0,0,canvas.width,canvas.height);const pixels=ctx.getImageData(0,0,canvas.width,canvas.height),code=jsQR(pixels.data,pixels.width,pixels.height);if(code){stopScan();activateLink(code.data);return;}}
    scanTimer=setTimeout(frame,180);
   }catch{stopScan();$('status').textContent='Could not read the camera. Try scanning again or paste the pairing link.';}
  }
  frame();
 }catch(e){if(generation===scanGeneration){stopScan();$('status').textContent=e.name==='NotAllowedError'?'Camera access was denied. Allow camera access and try again, or paste the pairing link.':e.message;}}
};
$('forget').hidden=!paired;
$('forget').onclick=()=>{if(sending||!confirm('Forget this store pairing on this phone? Staff can also revoke it from SmartStudio.'))return;pairs=pairs.filter(x=>x.instance!==instance);savePairs();location.replace(location.pathname);};
$('storeChoice').hidden=!paired||pairs.length<2;
for(const pair of pairs){const option=document.createElement('option');option.value=pair.instance;option.textContent=pair.store;option.selected=pair.instance===instance;$('stores').append(option);}
$('stores').onchange=()=>{if(file&&!confirm('Discard the unsent photo and switch stores?')){$('stores').value=instance;return;}const pair=pairs.find(x=>x.instance===$('stores').value);if(pair){pairs=pairs.filter(x=>x.instance!==pair.instance);pairs.push(pair);savePairs();location.reload();}};
function choose(e){if(sending)return;file=e.target.files[0];if(!file)return;uploadId=crypto.randomUUID();if(url)URL.revokeObjectURL(url);url=URL.createObjectURL(file);$('preview').src=url;$('preview').hidden=false;$('send').disabled=false;$('status').textContent='Review the photo and name, then add it to the queue.';}
// Keep the native picker as an explicit fallback; live capture avoids its dark still processing.
let photoStream,photoGeneration=0,capturing=false,photoBrightness=0;
function setPhotoBrightness(value){photoBrightness=Number(value)||0;$('photoLight').value=String(photoBrightness);$('lightValue').textContent=photoBrightness.toFixed(1)+' EV';const filter='brightness('+Math.pow(2,photoBrightness)+')';$('photoVideo').style.filter=filter;$('preview').style.filter=filter;}
$('photoLight').oninput=e=>{if(!sending)setPhotoBrightness(e.target.value);};
function stopPhotoCamera(){photoGeneration++;if(photoStream)photoStream.getTracks().forEach(t=>t.stop());photoStream=null;$('photoVideo').srcObject=null;$('photoCamera').hidden=true;$('openCamera').disabled=sending;$('shutter').disabled=false;}
$('closeCamera').onclick=stopPhotoCamera;
window.addEventListener('pagehide',stopPhotoCamera);
document.addEventListener('visibilitychange',()=>{if(document.hidden)stopPhotoCamera();});
$('openCamera').onclick=async()=>{
 if(sending||capturing)return;stopPhotoCamera();stopScan();const generation=photoGeneration;$('openCamera').disabled=true;
 try{
  if(!navigator.mediaDevices?.getUserMedia)throw Error('Live camera is unavailable. Use phone camera instead or choose from Photos.');
  const stream=await navigator.mediaDevices.getUserMedia({audio:false,video:{facingMode:{ideal:'environment'},width:{ideal:4096},height:{ideal:3072}}});
  if(generation!==photoGeneration){stream.getTracks().forEach(t=>t.stop());return;}
  photoStream=stream;const track=stream.getVideoTracks()[0],caps=track.getCapabilities?.()||{};
  for(const key of ['focusMode','exposureMode','whiteBalanceMode'])if(caps[key]?.includes('continuous'))await track.applyConstraints({advanced:[{[key]:'continuous'}]}).catch(()=>{});
  if(generation!==photoGeneration)return;
  $('photoCamera').hidden=false;$('photoVideo').srcObject=stream;await $('photoVideo').play();
  if(generation!==photoGeneration)return;
  $('lightControls').hidden=false;$('status').textContent='Let the camera focus, then adjust brightness if the face looks dark and capture the photo.';
 }catch(e){if(generation===photoGeneration){stopPhotoCamera();$('status').textContent=e.name==='NotAllowedError'?'Allow camera access, or use phone camera instead or choose from Photos.':e.message;}}
};
$('shutter').onclick=async()=>{
 if(!photoStream||capturing||sending)return;const video=$('photoVideo'),generation=photoGeneration;
 if(video.readyState<2||!video.videoWidth||!video.videoHeight){$('status').textContent='Wait for the camera picture, then capture again.';return;}
 capturing=true;$('shutter').disabled=true;const canvas=document.createElement('canvas');
 try{
  canvas.width=video.videoWidth;canvas.height=video.videoHeight;const g=canvas.getContext('2d');if(!g)throw Error('Could not capture the photo. Use phone camera instead.');g.drawImage(video,0,0);
  const blob=await new Promise(ok=>canvas.toBlob(ok,'image/jpeg',.98));if(!blob)throw Error('Could not capture the photo. Try again.');
  if(generation!==photoGeneration)return;
  choose({target:{files:[blob]}});stopPhotoCamera();$('status').textContent='Review the face and adjust brightness if needed, then add to the queue.';
 }catch(e){if(generation===photoGeneration)$('status').textContent=e.message;}finally{canvas.width=canvas.height=1;capturing=false;$('shutter').disabled=false;}
};
function choosePicker(e){if(sending||capturing)return;stopPhotoCamera();setPhotoBrightness(0);choose(e);$('lightControls').hidden=!file;}
$('camera').onchange=choosePicker;$('library').onchange=choosePicker;
// Adapt native still images by dimensions across phone models.
async function preparePhoto(source,sourceUrl,brightness=0){
 const img=new Image();img.src=sourceUrl;
 try{await img.decode();}catch{throw Error('This photo cannot be opened. Choose a JPEG from Photos or retake it.');}
 if(!img.naturalWidth||!img.naturalHeight)throw Error('This photo has no image dimensions. Please retake it.');
 const limit=2097152,maxEdge=4096,longEdge=Math.max(img.naturalWidth,img.naturalHeight);
 if(!brightness&&source.type==='image/jpeg'&&source.size<=limit&&longEdge<=maxEdge&&img.naturalWidth*img.naturalHeight<=12000000)return source;
 const canvas=document.createElement('canvas');
 try{
  for(let edge=Math.min(maxEdge,longEdge);;edge=Math.max(1200,Math.floor(edge*.8))){
   const scale=Math.min(1,edge/longEdge,Math.sqrt(12000000/(img.naturalWidth*img.naturalHeight)));
   canvas.width=Math.max(1,Math.floor(img.naturalWidth*scale));canvas.height=Math.max(1,Math.floor(img.naturalHeight*scale));
   const g=canvas.getContext('2d');if(!g)throw Error('Photo processing is unavailable. Please reopen the page.');
   g.imageSmoothingEnabled=true;g.imageSmoothingQuality='high';g.fillStyle='white';g.fillRect(0,0,canvas.width,canvas.height);g.drawImage(img,0,0,canvas.width,canvas.height);
   // Pixel correction also works on Safari versions without CanvasRenderingContext2D.filter.
   if(brightness){const pixels=g.getImageData(0,0,canvas.width,canvas.height),gain=Math.pow(2,brightness);for(let i=0;i<pixels.data.length;i+=4){pixels.data[i]*=gain;pixels.data[i+1]*=gain;pixels.data[i+2]*=gain;}g.putImageData(pixels,0,0);}
   for(const quality of [.95,.92,.88]){
    const blob=await new Promise(ok=>canvas.toBlob(ok,'image/jpeg',quality));
    if(!blob)throw Error('Could not prepare this photo. Please retake it.');
    if(blob.size<=limit)return blob;
   }
   if(edge<=1200)throw Error('This photo could not fit the upload limit. Please choose another photo.');
  }
 }finally{canvas.width=canvas.height=1;}
}
$('send').onclick=async()=>{if(!file||sending)return;sending=true;$('send').disabled=true;$('name').disabled=true;$('removeBackground').disabled=true;$('camera').disabled=true;$('library').disabled=true;$('status').textContent='Saving to the store queue…';try{
 stopPhotoCamera();$('photoLight').disabled=true;const blob=await preparePhoto(file,url,photoBrightness);
 const query=new URLSearchParams({instance,uploadId,name:$('name').value.trim(),removeBackground:String($('removeBackground').checked)});const response=await fetch('/shop/studio-photo-queue/upload?'+query,{method:'POST',headers:{Authorization:'Bearer '+token,'Content-Type':'image/jpeg'},body:blob,signal:AbortSignal.timeout(35000)});const result=await response.json();if(!response.ok){if(response.status===403&&paired){pairs=pairs.filter(x=>x.instance!==instance);savePairs();throw Error('This phone pairing was revoked. Ask staff to pair this phone again in SmartStudio.');}throw Error(result.message||'Photo was not saved. Try again.');}
 uploaded++;$('count').textContent=uploaded+' photo'+(uploaded===1?'':'s')+' saved to the queue.';$('status').textContent=$('removeBackground').checked?'Saved. Background removal is queued at the store. Take the next person’s photo whenever you are ready.':'Saved. Original photo kept. Take the next person’s photo whenever you are ready.';file=null;uploadId=null;setPhotoBrightness(0);$('lightControls').hidden=true;$('name').value='';$('camera').value='';$('library').value='';$('preview').hidden=true;URL.revokeObjectURL(url);url=null;
}catch(e){$('status').textContent=e.name==='TimeoutError'?'The connection timed out. Retry with this photo selected; it will not be added twice.':e.message;}finally{sending=false;$('photoLight').disabled=false;$('openCamera').disabled=false;$('send').disabled=!file;$('name').disabled=false;$('removeBackground').disabled=false;$('camera').disabled=false;$('library').disabled=false;}};
</script></html>`;
