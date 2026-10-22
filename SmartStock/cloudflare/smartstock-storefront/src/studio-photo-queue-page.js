export const queuePage=String.raw`<!doctype html><html lang="en"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><link rel="manifest" href="/photo.webmanifest"><link rel="apple-touch-icon" href="/photo-icon-180.png"><meta name="theme-color" content="#7133a8"><meta name="apple-mobile-web-app-capable" content="yes"><meta name="apple-mobile-web-app-title" content="PICS"><title>SmartStudio · Photo queue</title>
<style>body{margin:0;background:#f4f3f8;color:#242035;font:17px system-ui}main{max-width:480px;margin:28px auto;padding:24px}h1{font-size:30px}p{line-height:1.5}section{background:white;border-radius:20px;padding:24px;box-shadow:0 8px 32px #24153612}button,.button{display:block;border:0;border-radius:12px;background:#7133a8;color:white;padding:15px;margin:14px 0;text-align:center;font:inherit;cursor:pointer}.secondary{background:#ece5f4;color:#53237e}button,input[type=text]{width:100%;box-sizing:border-box;font:inherit}input[type=text]{padding:12px;border:1px solid #cdc6d5;border-radius:10px;margin-top:8px}button:disabled{opacity:.5}img{width:100%;max-height:340px;object-fit:contain;border-radius:12px}[hidden]{display:none!important}small{color:#625b70}</style>
<main><small>DECKERS · SMARTSTUDIO</small><h1>Photo queue</h1><section><p id="status" role="status" aria-live="polite">Take pictures for several people, one at a time. Each photo goes to your store's shared queue.</p><label id="storeChoice" hidden>Paired store<select id="stores"></select></label><button id="forget" class="secondary" hidden>Forget this phone pairing</button><div id="activation" hidden><p>Home-screen apps can keep pairing separate from your browser. In SmartStudio, open Photo Queue, choose Pair staff phone, and scan its QR code here or paste its private link.</p><button id="scan">Scan pairing QR code</button><div id="scanner" hidden><video id="scanVideo" autoplay muted playsinline style="width:100%;border-radius:12px"></video><button id="stopScan" class="secondary">Cancel scan</button></div><label for="pairLink">Staff phone pairing link</label><input id="pairLink" type="text" autocomplete="off" autocapitalize="none" spellcheck="false"><button id="activate">Activate this app</button><small>Keep this upload-only pairing link private.</small></div><div id="controls"><label for="name">Name or reference <small>(optional)</small></label><input id="name" type="text" maxlength="80" autocomplete="off" placeholder="e.g. Person 1"><img id="preview" alt="Selected photo" hidden><label class="button">Take photo<input id="camera" type="file" accept="image/*" capture="environment" hidden></label><label class="button secondary">Choose from Photos<input id="library" type="file" accept="image/*" hidden></label><label><input id="removeBackground" type="checkbox" checked> Remove background after upload</label><button id="send" disabled>Add to queue</button></div></section><p id="count">No photos sent yet.</p><small>No template needs to be open. Staff can select photos on any paired store computer. Photos are deleted 24 hours after staff mark them printed. <span id="accessNote">Private upload links last 12 hours. Paired staff phones stay connected until revoked in SmartStudio.</span></small></main>
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
$('camera').onchange=choose;$('library').onchange=choose;
$('send').onclick=async()=>{if(!file||sending)return;sending=true;$('send').disabled=true;$('name').disabled=true;$('removeBackground').disabled=true;$('camera').disabled=true;$('library').disabled=true;$('status').textContent='Saving to the store queue…';try{
 const img=new Image();img.src=url;await img.decode();const scale=Math.min(1,1600/Math.max(img.naturalWidth,img.naturalHeight));const canvas=document.createElement('canvas');canvas.width=Math.max(1,Math.round(img.naturalWidth*scale));canvas.height=Math.max(1,Math.round(img.naturalHeight*scale));const g=canvas.getContext('2d');g.fillStyle='white';g.fillRect(0,0,canvas.width,canvas.height);g.drawImage(img,0,0,canvas.width,canvas.height);
 let blob=await new Promise(ok=>canvas.toBlob(ok,'image/jpeg',.92));if(!blob||blob.size>2097152)blob=await new Promise(ok=>canvas.toBlob(ok,'image/jpeg',.78));if(!blob||blob.size>2097152)throw Error('Choose a smaller photo.');
 const query=new URLSearchParams({instance,uploadId,name:$('name').value.trim(),removeBackground:String($('removeBackground').checked)});const response=await fetch('/shop/studio-photo-queue/upload?'+query,{method:'POST',headers:{Authorization:'Bearer '+token,'Content-Type':'image/jpeg'},body:blob,signal:AbortSignal.timeout(35000)});const result=await response.json();if(!response.ok){if(response.status===403&&paired){pairs=pairs.filter(x=>x.instance!==instance);savePairs();throw Error('This phone pairing was revoked. Ask staff to pair this phone again in SmartStudio.');}throw Error(result.message||'Photo was not saved. Try again.');}
 uploaded++;$('count').textContent=uploaded+' photo'+(uploaded===1?'':'s')+' saved to the queue.';$('status').textContent=$('removeBackground').checked?'Saved. Background removal is queued at the store. Take the next person’s photo whenever you are ready.':'Saved. Original photo kept. Take the next person’s photo whenever you are ready.';file=null;uploadId=null;$('name').value='';$('camera').value='';$('library').value='';$('preview').hidden=true;URL.revokeObjectURL(url);url=null;
}catch(e){$('status').textContent=e.name==='TimeoutError'?'The connection timed out. Retry with this photo selected; it will not be added twice.':e.message;}finally{sending=false;$('send').disabled=!file;$('name').disabled=false;$('removeBackground').disabled=false;$('camera').disabled=false;$('library').disabled=false;}};
</script></html>`;
