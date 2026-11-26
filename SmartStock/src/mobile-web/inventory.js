(()=>{'use strict';
const $=id=>document.getElementById(id),remote=location.pathname.startsWith('/employees/');
let csrf='',preauth='',kind='',rows=[],offset=0,version=0,timer,controls,scanVersion=0,decoder;
const esc=v=>String(v??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const money=v=>new Intl.NumberFormat('en-GY',{style:'currency',currency:'GYD'}).format(Number(v||0));
function note(text,error=false){$('notice').textContent=text;$('notice').classList.toggle('error',error)}
function reset(){version++;stopScan();csrf='';preauth='';rows=[];offset=0;$('results').replaceChildren();$('count').textContent='';$('updated').textContent='';$('detailBody').replaceChildren();$('detail').close();$('workspace').hidden=true;$('logout').hidden=true;$('auth').hidden=false;$('login').hidden=false;$('mfa').hidden=true;$('mfaQr').removeAttribute('src');$('mfaSecret').textContent='';}
async function call(route,body={}){
 const auth=remote&&['session','logout'].includes(route)?'auth/'+route:route;
 const response=await fetch((remote?'/employees/api/v1/':'/api/v1/')+auth,{method:'POST',credentials:'include',cache:'no-store',headers:{'Content-Type':'application/json',...(csrf?{'X-CSRF-Token':csrf}:{})},body:JSON.stringify(body)});
 let reply;try{reply=await response.json()}catch{throw new Error('The store connection is unavailable. Try again shortly.')}
 if(!response.ok||!reply.ok){if(response.status===401||response.status===403)reset();throw new Error(reply.error?.message||'The store could not complete this request.')}
 return reply.data;
}
function branding(b={}){$('company').textContent=b.name||'SmartStock';$('footerCompany').textContent=b.name||'SmartStock';$('companyDetails').textContent=[b.motto,b.address,b.phone,b.email].filter(Boolean).join(' · ');if(/^data:image\/(png|jpeg);base64,[A-Za-z0-9+/=]+$/.test(b.logo||''))$('logo').src=b.logo;}
function stock(item){if(item.product_type!=='INVENTORY')return ['service','Not tracked'];const n=Number(item.quantity);return n<=0?['out','Out of stock']:n<=Number(item.reorder)?['low',n+' · Low stock']:['',n+' in stock'];}
function render(){
 $('results').innerHTML=rows.length?rows.map((item,index)=>{const [state,label]=stock(item);return `<button class="item" data-index="${index}"><div class="item-top"><span class="tag">${item.kind==='product'?'INVENTORY':item.kind==='variant'?'CUSTOM VARIANT':'CUSTOM ITEM'}</span><span aria-hidden="true">↗</span></div><h3>${esc(item.name)}</h3><p>${esc([item.sku||item.barcode||'No code',item.size,item.color].filter(Boolean).join(' · '))}</p><p class="item-classification">Item Type: ${esc(item.item_type_name||'Not set')}${item.kind==='variant'?' · from main item':''}</p><div class="item-bottom"><span class="price">${esc(item.pricing_type==='VARIABLE'?'Price on request':money(item.price)+(item.pricing_type==='AREA'?' / '+(item.area_price_unit||'area'):''))}</span><span class="stock ${state}">${esc(label)}</span></div></button>`}).join(''):'<div class="empty"><h3>No matching items</h3><p>Try a different name or barcode, or change your filters.</p></div>';
 $('count').textContent=rows.length+' items loaded'+($('more').hidden?'':' · More available');
}
async function search(append=false){
 if(!csrf)return;clearTimeout(timer);const current=++version;
 if(!append){offset=0;rows=[];$('results').replaceChildren();$('more').hidden=true;}
 $('results').setAttribute('aria-busy','true');$('more').disabled=true;note('Searching your store…');
 try{const data=await call('inventory/search',{query:$('query').value,kind,stock:$('stock').value,offset});if(current!==version)return;
 rows=append?rows.concat(data.items):data.items;offset=data.nextOffset;$('more').hidden=!data.hasMore;branding(data.branding);render();$('updated').textContent='Store #'+data.locationId+' · Updated '+new Date().toLocaleTimeString([],{hour:'2-digit',minute:'2-digit'});note('');
 }catch(e){if(current===version){$('more').hidden=true;$('results').replaceChildren();note(e.message,true);}}
 finally{if(current===version){$('results').setAttribute('aria-busy','false');$('more').disabled=false;}}
}
async function boot(){try{
 if(remote){
  try{const response=await fetch('/employees/branding',{cache:'no-store'});if(response.ok){const reply=await response.json();if(reply.ok)branding(reply.data);}}catch{}
  const health=await fetch('/employees/health',{cache:'no-store'});if(!health.ok){const reply=await health.json();throw new Error(reply.error?.message||'The store is unavailable. Try again shortly.');}
 }
 const token=new URLSearchParams(location.hash.slice(1)).get('activate');if(location.hash)history.replaceState(null,'',location.pathname);if(token)await call('activate',{token});
 const session=await call('session');csrf=session.csrfToken;if(!(session.permissions||[]).includes('VIEW_INVENTORY')){reset();throw new Error('Your account needs View Inventory permission. Ask your manager to review your role.');}
 $('auth').hidden=true;$('workspace').hidden=false;$('logout').hidden=false;await search();
 }catch(e){note(e.message,true)}}
 $('login').onsubmit=async e=>{e.preventDefault();$('loginButton').disabled=true;const secret=$('password').value;$('password').value='';note('Signing in…');try{const data=await call(remote?'auth/login':'login',{identifier:$('identifier').value.trim(),...(remote?{password:secret}:{secret})});if(data.mfaRequired){preauth=data.preauth;$('login').hidden=true;$('mfa').hidden=false;$('mfaQr').hidden=!data.qrCode;if(data.qrCode)$('mfaQr').src=data.qrCode;$('mfaSecret').textContent=data.secret||'';}else await boot();}catch(err){note(err.message,true)}finally{$('loginButton').disabled=false}};
 $('mfa').onsubmit=async e=>{e.preventDefault();const code=$('code').value;$('code').value='';try{await call('auth/verify',{preauth,code});preauth='';$('mfaQr').removeAttribute('src');$('mfaSecret').textContent='';await boot();}catch(err){reset();note(err.message,true)}};
 $('logout').onclick=async()=>{try{await call('logout');reset();note('Signed out.')}catch(e){note(e.message,true)}};
 $('retry').onclick=boot;$('refresh').onclick=()=>search();$('more').onclick=()=>search(true);
 $('query').oninput=()=>{version++;$('more').hidden=true;clearTimeout(timer);timer=setTimeout(()=>search(),250)};
 $('searchForm').onsubmit=e=>{e.preventDefault();search()};$('stock').onchange=()=>search();
 document.querySelectorAll('[data-kind]').forEach(button=>button.onclick=()=>{kind=button.dataset.kind;document.querySelectorAll('[data-kind]').forEach(b=>b.setAttribute('aria-pressed',String(b===button)));search()});
 $('results').onclick=e=>{const button=e.target.closest('[data-index]');if(!button)return;const item=rows[Number(button.dataset.index)];if(!item)return;$('detailTitle').textContent=item.name;$('detailBody').innerHTML='<p>'+esc(item.description||'No description provided.')+'</p><dl>'+[['Type',item.kind==='product'?'Inventory':item.kind==='variant'?'Custom variant':'Custom item'],[item.kind==='variant'?'Item Type (from main item)':'Item Type',item.item_type_name||'—'],['SKU',item.sku||'—'],['Barcode',item.barcode||'—'],['Size',item.size||'—'],['Colour',item.color||'—'],['Price',item.pricing_type==='VARIABLE'?'Price on request':money(item.price)+(item.pricing_type==='AREA'?' / '+(item.area_price_unit||'area'):'')],['Stock',stock(item)[1]],['Reorder level',item.product_type==='INVENTORY'?item.reorder:'Not tracked']].map(([k,v])=>'<dt>'+esc(k)+'</dt><dd>'+esc(v)+'</dd>').join('')+'</dl>';$('detail').showModal()};
 $('closeDetail').onclick=()=>$('detail').close();
 function stopScan(){scanVersion++;if(controls){controls.stop();controls=null;}const video=$('video');if(video.srcObject)video.srcObject.getTracks().forEach(t=>t.stop());video.srcObject=null;if($('scanner').open)$('scanner').close();}
 async function reader(){if(typeof ZXingBrowser==='undefined'){if(!decoder)decoder=new Promise((resolve,reject)=>{const script=document.createElement('script');script.src='./orders-barcode.js';script.onload=resolve;script.onerror=()=>{decoder=null;script.remove();reject(new Error('Scanner unavailable. Type the barcode in search.'))};document.head.append(script)});await decoder;}return new ZXingBrowser.BrowserMultiFormatReader(undefined,{delayBetweenScanAttempts:200})}
 function scanned(text){if(!text||text.length>300)return;stopScan();$('query').value=text;kind='';$('stock').value='';document.querySelectorAll('[data-kind]').forEach(b=>b.setAttribute('aria-pressed',String(b.dataset.kind==='')));search();}
 $('scan').onclick=async()=>{stopScan();const current=scanVersion;$('scanner').showModal();$('scanStatus').textContent='Starting rear camera…';try{if(!navigator.mediaDevices?.getUserMedia)throw new Error('Camera unavailable. Choose a photo or type the barcode in search.');const scanner=await reader();if(current!==scanVersion)return;const active=await scanner.decodeFromConstraints({video:{facingMode:{ideal:'environment'}},audio:false},$('video'),(result,error,c)=>{if(current!==scanVersion)return;if(result){c.stop();scanned(result.getText());}});if(current!==scanVersion){active.stop();return;}controls=active;$('scanStatus').textContent='Hold the barcode inside the frame.';}catch(e){if(current===scanVersion)$('scanStatus').textContent=e.name==='NotAllowedError'?'Allow camera access, choose a photo, or type the barcode in search.':e.message;}};
 $('closeScan').onclick=stopScan;$('scanner').addEventListener('cancel',stopScan);$('scanner').addEventListener('close',()=>{if(controls||$('video').srcObject)stopScan()});
 $('photo').onchange=async e=>{const file=e.target.files[0];if(!file)return;const current=scanVersion;let url;try{url=URL.createObjectURL(file);const scanner=await reader();const result=await scanner.decodeFromImageUrl(url);if(current===scanVersion)scanned(result.getText());}catch{if(current===scanVersion)$('scanStatus').textContent='No barcode found. Try a sharper photo or type the code in search.';}finally{if(url)URL.revokeObjectURL(url);e.target.value=''}};
 document.addEventListener('visibilitychange',()=>{if(document.hidden){stopScan();version++;rows=[];$('results').replaceChildren();$('detailBody').replaceChildren();$('detail').close();}else if(csrf)boot()});
 boot();
})();
