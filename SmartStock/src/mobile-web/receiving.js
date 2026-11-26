(()=>{'use strict';
const $=id=>document.getElementById(id),esc=v=>String(v??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
let csrf='',items=[],lines=[],pending=null,busy=false,searchVersion=0,sessionVersion=0;
let selected=null, photos=[], photoIndex=0, galleryVersion=0, mayEdit=false;
let barcodeRequest=null, uploads=[], photoRequest=null, editing=false;
let scannerVersion=0, scannerStream=null, scannerControls=null;
let shelfQueue=[], shelfIndex=0;
const photoSources=new Map();
function note(message,error=false){$('notice').textContent=message;$('notice').classList.toggle('error',error);}
function clearSelection(){
 galleryVersion++;selected=null;photos=[];photoIndex=0;$('selected').hidden=true;
}
function reset(){closeScanner();closeShelfPrompt();sessionVersion++;searchVersion++;csrf='';items=[];lines=[];pending=null;busy=false;mayEdit=false;editing=false;barcodeRequest=null;uploads=[];photoRequest=null;clearSelection();photoSources.clear();$('results').replaceChildren();$('lines').replaceChildren();$('history').replaceChildren();for(const id of ['confirm','photoDialog','barcodeDialog'])$(id).close();$('workspace').hidden=true;$('logout').hidden=true;$('auth').hidden=false;}
async function call(route,body={},key){const response=await fetch('/api/v1/'+route,{method:'POST',credentials:'include',cache:'no-store',headers:{'Content-Type':'application/json',...(csrf?{'X-CSRF-Token':csrf}:{}),...(key?{'Idempotency-Key':key}:{})},body:JSON.stringify(body)});let reply;try{reply=await response.json();}catch{throw new Error('Store connection unavailable. Check store Wi-Fi and try again.');}if(!response.ok||!reply.ok){if(response.status===401||response.status===403&&reply.error?.code!=='PERMISSION_DENIED')reset();const error=new Error(reply.error?.message||'The store could not complete the request.');error.code=reply.error?.code;error.status=response.status;throw error;}return reply.data;}
function updateTotal(){
 const units=lines.reduce((sum,line)=>sum+(Number(line.quantity)||0),0);
 $('total').textContent=`${lines.length} ${lines.length===1?'item':'items'} · ${units} ${units===1?'unit':'units'}`;
}
function render(){
 const locked=!!pending||busy;
 $('lines').innerHTML=lines.length?lines.map((line,i)=>`<details class="line">
  <summary><img id="linePhoto${i}" hidden alt=""><strong>${esc(line.name)}</strong><span class="unit-count" id="lineCount${i}">${line.quantity} units</span></summary>
  <small>${esc(line.code)} · On hand ${line.quantityOnHand}</small>
  <div class="line-controls"><label>New units delivered<input type="number" min="1" max="1000000" step="1" required data-qty="${i}" value="${line.quantity}" ${locked?'disabled':''}></label>
  <button type="button" class="remove" data-remove="${i}" ${locked?'disabled':''}>Remove</button></div>
 </details>`).join(''):'<p>No items yet. Scan or search to start.</p>';
 lines.forEach((line,index)=>{
  if(!line.photo)return;
  const image=$('linePhoto'+index);
  image.onload=()=>{image.hidden=false;};
  photoSource(line.photo).then(src=>{image.src=src;}).catch(()=>{});
 });
 updateTotal();
 for(const id of ['scanBarcode','searchButton','query','quantity','plus','minus','addToBatch','addBarcode','addPhotos'])$(id).disabled=locked;
 $('review').disabled=!lines.length||locked;
}
async function boot(){try{const token=new URLSearchParams(location.hash.slice(1)).get('activate');if(location.hash)history.replaceState(null,'',location.pathname);if(token)await call('activate',{token});const session=await call('session');csrf=session.csrfToken;if(!session.permissions.includes('RECEIVING_INVENTORY')){reset();throw new Error('Receiving Inventory permission is required. Ask your manager to review your role.');}mayEdit=session.permissions.includes('EDIT_ITEM');$('auth').hidden=true;$('workspace').hidden=false;$('logout').hidden=false;$('store').textContent='Receiving into store #'+session.locationId;$('historySection').hidden=!session.permissions.includes('VIEW_RECEIVING_HISTORY');render();note('');}catch(e){note(e.message,true);}}
$('login').onsubmit=async e=>{e.preventDefault();$('loginButton').disabled=true;const secret=$('secret').value;$('secret').value='';try{await call('login',{identifier:$('identifier').value.trim(),secret});await boot();}catch(err){note(err.message,true);}finally{$('loginButton').disabled=false;}};
$('retry').onclick=boot;$('logout').onclick=async()=>{if(busy)return;try{await call('logout');reset();note('Signed out.');}catch(e){note(e.message,true);}};
$('searchForm').onsubmit=e=>{
 e.preventDefault();
 return searchItems();
};
async function searchItems(){
 if(!csrf||pending||busy||editing)return;
 const version=++searchVersion;
 clearSelection();$('results').replaceChildren();note('Searching…');
 try{
  const data=await call('receiving/search',{query:$('query').value.trim()});
  if(version!==searchVersion||!csrf)return;
  items=data.items;
  if(items.length===1){selectItem(items[0]);return;}
  $('results').innerHTML=items.map((item,i)=>`<button class="result" type="button" data-add="${i}">${esc(item.name)}<small>${esc(item.itemType)} · ${esc(item.code)} · ${item.quantityOnHand} in stock</small></button>`).join('')||'<p>No matching inventory items.</p>';
  note(items.length===300?'Narrow your search for more specific results.':'');
 }catch(err){if(version===searchVersion)note(err.message,true);}
}

function stopCamera(){
 scannerVersion++;
 scannerControls?.stop();
 scannerControls=null;
 scannerStream?.getTracks().forEach(track=>track.stop());
 scannerStream=null;
 $('scannerVideo').srcObject=null;
 $('scannerVideo').hidden=true;
}
function closeScanner(){
 stopCamera();
 if($('scanner').open)$('scanner').close();
}
function readBarcode(result){
 const barcode=String(result.getText()).trim();
 if(!barcode||barcode.length>300||/[\x00-\x1f\x7f]/.test(barcode)){
  $('scannerStatus').textContent='This barcode cannot be used for item search.';
  return;
 }
 closeScanner();
 $('query').value=barcode;
 return searchItems();
}
$('scanBarcode').onclick=async()=>{
 if(!csrf||pending||busy||editing||$('scanner').open)return;
 const version=++scannerVersion;
 const status=$('scannerStatus'),video=$('scannerVideo');
 $('scanner').showModal();
 if(!navigator.mediaDevices?.getUserMedia){
  status.textContent='Live camera is unavailable. Scan a photo or enter the barcode in search.';
  return;
 }
 status.textContent='Starting rear camera…';
 try{
  const reader=new ZXingBrowser.BrowserMultiFormatOneDReader(undefined,{delayBetweenScanAttempts:250});
  const stream=await navigator.mediaDevices.getUserMedia({video:{facingMode:{ideal:'environment'},width:{ideal:1920},height:{ideal:1080}},audio:false});
  if(version!==scannerVersion){
   stream.getTracks().forEach(track=>track.stop());
   return;
  }
  scannerStream=stream;
  video.srcObject=stream;
  video.hidden=false;
  status.textContent='Point the rear camera at the full barcode.';
  const controls=await reader.decodeFromVideoElement(video,(result,error,active)=>{
   if(version!==scannerVersion||!result)return;
   scannerControls=active;
   readBarcode(result);
  });
  if(version!==scannerVersion)controls.stop();
  else scannerControls=controls;
 }catch(error){
  if(version!==scannerVersion)return;
  stopCamera();
  status.textContent=error.name==='NotAllowedError'
   ?'Camera access was declined. Allow camera access in Safari, or scan a photo.'
   :'The camera could not start. Scan a photo or enter the barcode in search.';
 }
};
$('closeScanner').onclick=closeScanner;
$('scanner').addEventListener('cancel',event=>{event.preventDefault();closeScanner();});
$('scanPhoto').onclick=()=>{
 stopCamera();
 $('scannerStatus').textContent='Take or select a clear photo of the full barcode.';
 $('barcodePhoto').value='';
 $('barcodePhoto').click();
};
$('barcodePhoto').onchange=async event=>{
 const file=event.target.files?.[0];
 if(!file||!$('scanner').open)return;
 const version=++scannerVersion;
 $('scannerStatus').textContent='Reading barcode photo…';
 const url=URL.createObjectURL(file);
 try{
  const reader=new ZXingBrowser.BrowserMultiFormatOneDReader();
  const result=await reader.decodeFromImageUrl(url);
  if(version===scannerVersion)await readBarcode(result);
 }catch{
  if(version===scannerVersion)$('scannerStatus').textContent='No barcode found. Try a clearer photo showing the entire barcode.';
 }finally{URL.revokeObjectURL(url);}
};
document.addEventListener?.('visibilitychange',()=>{
 if(document.hidden&&scannerStream)closeScanner();
});
window.addEventListener('pagehide',closeScanner);

$('results').onclick=e=>{
 const button=e.target.closest('[data-add]');
 if(button&&!pending&&!busy&&!editing&&items[Number(button.dataset.add)])selectItem(items[Number(button.dataset.add)]);
};

const target=item=>({itemType:item.itemType,itemId:item.itemId});
const sameItem=(a,b)=>a.itemType===b.itemType&&a.itemId===b.itemId;
async function selectItem(item){
 const version=++galleryVersion;
 selected=item;photos=[];photoIndex=0;
 $('results').replaceChildren();$('selected').hidden=false;
 $('itemName').textContent=item.name;
 $('itemDetails').textContent=[item.code?'SKU '+item.code:'',item.color,'On hand '+item.quantityOnHand].filter(Boolean).join(' · ');
 $('quantity').value=String(lines.find(line=>sameItem(line,item))?.quantity??1);
 $('addBarcode').hidden=!mayEdit;$('addPhotos').hidden=!mayEdit;
 render();renderGallery('Loading photos…');note('');
 try{
  const data=await call('receiving/gallery',target(item));
  if(version!==galleryVersion)return;
  photos=data.photos;renderGallery();
 }catch(err){if(version===galleryVersion)renderGallery(err.message);}
}

function photoSource(reference){
 if(/^https:\/\//i.test(reference))return Promise.resolve(reference);
 if(!photoSources.has(reference)){
  const source=call('images/fetch',{reference}).then(data=>`data:${data.contentType};base64,${data.bytesBase64}`);
  photoSources.set(reference,source);
  source.catch(()=>{if(photoSources.get(reference)===source)photoSources.delete(reference);});
  if(photoSources.size>24)photoSources.delete(photoSources.keys().next().value);
 }
 return photoSources.get(reference);
}

function renderGallery(message='No photos yet'){
 const photo=$('currentPhoto'),reference=photos[photoIndex],version=galleryVersion;
 photo.hidden=true;
 $('photoMessage').hidden=false;
 $('photoMessage').textContent=reference?'Loading photo…':message;
 $('previousPhoto').hidden=$('nextPhoto').hidden=photos.length<2;
 $('previousPhoto').disabled=photoIndex===0;
 $('nextPhoto').disabled=photoIndex>=photos.length-1;
 $('photoCount').hidden=!reference;
 $('photoCount').textContent=reference?`${photoIndex+1} of ${photos.length}`:'';
 const thumbnails=$('thumbnails');
 thumbnails.replaceChildren();
 thumbnails.hidden=!photos.length;
 photos.forEach((ref,index)=>{
  const button=document.createElement('button'),image=document.createElement('img');
  button.type='button';button.dataset.photo=String(index);button.className='thumbnail';
  button.setAttribute('aria-label',`Show photo ${index+1}`);
  button.setAttribute('aria-pressed',String(index===photoIndex));
  image.alt='';image.loading='lazy';button.append(image);thumbnails.append(button);
  photoSource(ref).then(src=>{image.src=src;}).catch(()=>{});
 });
 if(!reference)return;
 const stillCurrent=()=>version===galleryVersion&&photos[photoIndex]===reference;
 const unavailable=()=>{if(stillCurrent())$('photoMessage').textContent='Photo unavailable';};
 photoSource(reference).then(src=>{
  if(!stillCurrent())return;
  photo.alt=`${selected.name}, photo ${photoIndex+1}`;
  photo.onload=()=>{if(stillCurrent()){photo.hidden=false;$('photoMessage').hidden=true;}};
  photo.onerror=unavailable;photo.src=src;
  if(photo.complete&&photo.naturalWidth)photo.onload();
 }).catch(unavailable);
}
function showPhoto(index){
 if(index<0||index>=photos.length)return;
 photoIndex=index;renderGallery();
 const strip=$('thumbnails'),button=strip.children[index];
 if(button&&strip.scrollWidth>strip.clientWidth){
  if(button.offsetLeft<strip.scrollLeft)strip.scrollLeft=button.offsetLeft;
  const right=button.offsetLeft+button.offsetWidth;
  if(right>strip.scrollLeft+strip.clientWidth)strip.scrollLeft=right-strip.clientWidth;
 }
}
$('previousPhoto').onclick=()=>showPhoto(photoIndex-1);
$('nextPhoto').onclick=()=>showPhoto(photoIndex+1);
$('thumbnails').onclick=e=>{
 const button=e.target.closest('[data-photo]');
 if(button)showPhoto(Number(button.dataset.photo));
};

function stepQuantity(step){
 const field=$('quantity');
 field.value=String(Math.min(1000000,Math.max(1,(Number(field.value)||0)+step)));
}
$('minus').onclick=()=>stepQuantity(-1);
$('plus').onclick=()=>stepQuantity(1);
$('quantityForm').onsubmit=e=>{
 e.preventDefault();
 if(!selected||pending||busy||editing)return;
 const quantity=Number($('quantity').value);
 if(!Number.isInteger(quantity)||quantity<1||quantity>1000000)return;
 const line=lines.find(entry=>sameItem(entry,selected));
 if(line)line.quantity=quantity;
 else{
  if(lines.length>=300){note('Post this batch before adding more items.',true);return;}
  lines.push({...selected,quantity,photo:photos[0]});
 }
 render();note((line?'Updated ':'Added ')+selected.name+' in your batch.');
 $('query').value='';$('query').focus();
};

$('addBarcode').onclick=()=>{
 if(!selected||!mayEdit||pending||busy||editing)return;
 $('barcodeLabel').textContent='Scan or enter a barcode for '+selected.name;
 $('barcodeStatus').textContent='';$('barcode').value='';
 $('barcodeDialog').showModal();$('barcode').focus();
};
$('barcodeForm').onsubmit=async e=>{
 e.preventDefault();
 if(!selected||editing)return;
 const barcode=$('barcode').value.trim();
 if(!barcode){$('barcodeStatus').textContent='Scan or enter a barcode first.';return;}
 const item=selected,body={...target(item),barcode},version=sessionVersion;
 if(!barcodeRequest||JSON.stringify(barcodeRequest.body)!==JSON.stringify(body))
  barcodeRequest={body,key:crypto.randomUUID()};
 setEditing(true);
 try{
  const result=await call('receiving/add-barcode',body,barcodeRequest.key);
  if(version!==sessionVersion)return;
  barcodeRequest=null;$('barcodeDialog').close();
  const place=result.destination==='PRIMARY'?' as the primary barcode':
   result.destination==='ADDITIONAL'?' as an additional barcode':'';
  note('Barcode '+result.barcode+' added to '+item.name+place+'.');
 }catch(err){if(version===sessionVersion)$('barcodeStatus').textContent=err.message;else note(err.message,true);}
 finally{setEditing(false);}
};
$('closeBarcode').onclick=()=>{if(!editing)$('barcodeDialog').close();};
$('barcodeDialog').addEventListener('cancel',e=>{if(editing)e.preventDefault();});

$('addPhotos').onclick=()=>{
 if(!selected||!mayEdit||pending||busy||editing)return;
 $('uploadStatus').textContent='';$('photoDialog').showModal();
};
$('takePhoto').onclick=()=>$('camera').click();
$('choosePhotos').onclick=()=>$('photoFiles').click();
$('camera').onchange=$('photoFiles').onchange=e=>{
 if(!selected||editing||uploads.length)return;
 uploads=Array.from(e.target.files);e.target.value='';
 if(uploads.length)uploadPhotos();
};
$('retryPhoto').onclick=uploadPhotos;
$('closePhoto').onclick=closePhoto;
$('photoDialog').addEventListener('cancel',e=>{e.preventDefault();closePhoto();});
function closePhoto(){
 if(editing)return;
 uploads=[];photoRequest=null;$('retryPhoto').hidden=true;$('photoDialog').close();
}
function setEditing(value){
 editing=value;
 for(const id of ['barcode','saveBarcode','closeBarcode','closePhoto','retryPhoto'])$(id).disabled=value;
 $('takePhoto').disabled=$('choosePhotos').disabled=value||uploads.length>0;
}
async function uploadPhotos(){
 if(!selected||editing||!uploads.length)return;
 const version=sessionVersion,item=selected;
 galleryVersion++; // Ignore an older gallery read once an upload starts.
 setEditing(true);$('retryPhoto').hidden=true;
 try{
  while(uploads.length&&version===sessionVersion){
   $('uploadStatus').textContent=`Uploading… ${uploads.length} remaining`;
   if(!photoRequest){
    const bytesBase64=await encodePhoto(uploads[0]);
    if(version!==sessionVersion)return;
    photoRequest={key:crypto.randomUUID(),body:{...target(item),bytesBase64}};
   }
   const result=await call('receiving/add-photo',photoRequest.body,photoRequest.key);
   if(version!==sessionVersion)return;
   uploads.shift();photoRequest=null;photos=result.photos;
   showPhoto(photos.indexOf(result.reference));
  }
  $('uploadStatus').textContent='Photos added.';
 }catch(err){
  if(version!==sessionVersion){note(err.message,true);return;}
  $('uploadStatus').textContent=err.message;
  if(err.status>=400&&err.status<500||!photoRequest){uploads=[];photoRequest=null;}
  else $('retryPhoto').hidden=false;
 }finally{setEditing(false);}
}
async function encodePhoto(file){
 const url=URL.createObjectURL(file),image=new Image();
 try{
  image.src=url;
  try{await image.decode();}catch{throw new Error('Choose a supported photo, such as JPEG or PNG.');}
  const canvas=document.createElement('canvas'),scale=Math.min(1,1200/Math.max(image.naturalWidth,image.naturalHeight));
  canvas.width=Math.max(1,Math.round(image.naturalWidth*scale));
  canvas.height=Math.max(1,Math.round(image.naturalHeight*scale));
  const context=canvas.getContext('2d');
  context.fillStyle='#fff';context.fillRect(0,0,canvas.width,canvas.height);
  context.drawImage(image,0,0,canvas.width,canvas.height);
  return canvas.toDataURL('image/jpeg',.8).split(',')[1];
 }finally{URL.revokeObjectURL(url);}
}
$('lines').oninput=e=>{
 if(e.target.dataset.qty===undefined||pending||busy)return;
 const index=Number(e.target.dataset.qty),line=lines[index];
 line.quantity=Number(e.target.value);
 $('lineCount'+index).textContent=line.quantity+' units';
 if(selected&&sameItem(line,selected))$('quantity').value=e.target.value;
 updateTotal();
};
$('lines').onclick=e=>{const button=e.target.closest('[data-remove]');if(!button||pending||busy)return;lines.splice(Number(button.dataset.remove),1);render();};
function confirmDelivery(){
 $('summary').textContent=lines.length+' items · '+lines.reduce((n,l)=>n+l.quantity,0)+' new units';
 $('confirm').showModal();
}
function closeShelfPrompt(){
 shelfQueue=[];shelfIndex=0;
 if($('shelfDialog').open)$('shelfDialog').close();
}
function showShelfItem(){
 const line=shelfQueue[shelfIndex];
 $('shelfProgress').textContent='Item '+(shelfIndex+1)+' of '+shelfQueue.length+' missing a sales shelf';
 $('shelfItem').textContent=line.name;
 $('shelfItemCode').textContent=line.code||'';
 $('salesShelf').value=line.newSalesShelf||'';
 $('shelfStatus').textContent='';
 if(!$('shelfDialog').open)$('shelfDialog').showModal();
 $('salesShelf').focus();
}
$('batchForm').onsubmit=e=>{
 e.preventDefault();
 if(!lines.length||pending||busy)return;
 if(lines.some(l=>!Number.isInteger(l.quantity)||l.quantity<1||l.quantity>1000000)){
  note('Enter whole delivered quantities from 1 to 1,000,000.',true);return;
 }
 shelfQueue=lines.filter(line=>line.salesShelf==='');
 shelfIndex=0;
 if(shelfQueue.length)showShelfItem();
 else confirmDelivery();
};
$('shelfForm').onsubmit=e=>{
 e.preventDefault();
 if(!shelfQueue[shelfIndex])return;
 const name=$('salesShelf').value.trim();
 if(!name||name.length>100){
  $('shelfStatus').textContent='Enter a sales shelf of up to 100 characters.';
  return;
 }
 shelfQueue[shelfIndex++].newSalesShelf=name;
 if(shelfIndex<shelfQueue.length)showShelfItem();
 else{closeShelfPrompt();confirmDelivery();}
};
$('cancelShelf').onclick=closeShelfPrompt;
$('shelfDialog').addEventListener('cancel',e=>{e.preventDefault();closeShelfPrompt();});
$('cancel').onclick=()=>{if(busy)return;if(pending){note('The previous post is unconfirmed. Retry the same delivery to safely retrieve its result.',true);return;}$('confirm').close();};
$('confirm').addEventListener('cancel',e=>{if(busy||pending)e.preventDefault();});
$('post').onclick=async()=>{if(busy)return;if(!pending)pending={key:crypto.randomUUID(),body:{lines:lines.map(l=>({itemType:l.itemType,itemId:l.itemId,countedStock:l.quantityOnHand,expectedSystemStock:l.quantityOnHand,quantity:l.quantity,salesShelf:l.newSalesShelf}))}};busy=true;render();$('post').disabled=true;$('cancel').disabled=true;const version=sessionVersion;try{const result=await call('receiving/post',pending.body,pending.key);if(version!==sessionVersion)return;pending=null;lines=[];items=[];clearSelection();$('results').replaceChildren();$('confirm').close();note('Delivery received. Slip '+result.receiveId+' · '+result.lineCount+' items.');}catch(e){if(e.code==='STOCK_CHANGED'){pending=null;lines=[];items=[];clearSelection();$('results').replaceChildren();$('confirm').close();note('Stock changed during this delivery. Search and rebuild the batch using current stock before posting.',true);}else if(e.status>=400&&e.status<500){pending=null;$('confirm').close();note(e.message,true);}else{note(e.message+' Retry Post delivery with the same batch if the result is uncertain.',true);}}finally{busy=false;$('post').disabled=false;$('cancel').disabled=false;render();}};
$('historyForm').onsubmit=async e=>{e.preventDefault();const version=sessionVersion;try{const data=await call('receiving/history',{search:$('historyQuery').value,fromDate:$('from').value,toDate:$('to').value});if(version!==sessionVersion||!csrf)return;$('history').innerHTML=data.records.map(r=>`<div class="entry"><strong>${esc(r.receiveId||'Legacy movement')} · ${esc(r.productName)} · +${r.changeQuantity}</strong><span>${esc(new Date(r.createdAtEpochMillis).toLocaleString())} · ${esc(r.receivedBy)} · ${esc(r.storeName)}</span></div>`).join('')||'<p>No receiving history for these filters.</p>';}catch(err){note(err.message,true);}};
window.addEventListener('beforeunload',e=>{if(lines.length||pending){e.preventDefault();e.returnValue='';}});render();boot();
})();


