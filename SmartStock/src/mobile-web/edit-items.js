/* Dedicated entry reuses the authenticated local item API and shared editor. */
const sharedShow=show,sharedOpenEditor=openEditor,sharedApplyBarcode=applyScannedBarcode;
let itemDirty=false;
function editorAllowed(k){return k==='product'?can('EDIT_ITEM'):can('MANAGE_CUSTOM_ORDER_ITEMS','CUSTOM_ORDER_ITEMS','MANAGE_CUSTOM_ORDERS')}
function updateEditorTabs(){document.querySelectorAll('[data-searchkind]').forEach(b=>{b.hidden=!editorAllowed(b.dataset.searchkind);b.classList.toggle('hidden',b.hidden);b.setAttribute('aria-selected',String(b.dataset.searchkind===kind))})}
show=function(id){
 if(id==='home'){
  mode='edit';if(!editorAllowed(kind))kind=editorAllowed('product')?'product':'custom';
  id='search';itemDirty=false;updateEditorTabs();
  $('#results').innerHTML='<p>Search for an item to view and edit its details.</p>';
  $('#storeIdentity').textContent=bootstrap?.locationId?'Store #'+bootstrap.locationId+' · Connected securely to this store':'Store network · Sign in to confirm your store';
  if(!editorAllowed(kind)){sharedShow('search');$('#searchForm').classList.add('hidden');$('#scanSearch').classList.add('hidden');note('Your account does not have permission to edit items.',true);return}
  $('#searchForm').classList.remove('hidden');$('#scanSearch').classList.remove('hidden');
 }
 sharedShow(id);
};
openEditor=function(k,item){if(!editorAllowed(k)){note('Your account does not have permission to edit this item.',true);return}sharedOpenEditor(k,item);itemDirty=false;
 const sections={name:'Item details',costPrice:'Pricing & stock',productType:'Item settings',categoryId:'Classification',additionalBarcodes:'Additional barcodes',extraBarcodes:'Additional barcodes'};
 for(const [name,title] of Object.entries(sections)){const input=$('#fields [name="'+name+'"]');if(input){const heading=document.createElement('h2');heading.className='editor-section';heading.textContent=title;const field=input.closest('.barcode-field')||input.closest('label');field.before(heading)}}
 $('#save').textContent='Save changes';$('#saveAndNew').classList.add('hidden');$('#saveAndPrint').classList.add('hidden');
};
$('#itemForm').addEventListener('input',()=>itemDirty=true);
window.addEventListener('beforeunload',e=>{if(itemDirty){e.preventDefault();e.returnValue=''}});
$('#editorBack').onclick=()=>{if(itemDirty&&!confirm('Discard unsaved changes?'))return;itemDirty=false;show('home')};
$('#search .back').classList.add('hidden');
$('#scanSearch').onclick=()=>{scanTarget='__search';scanAppend=false;startLiveScanner()};
applyScannedBarcode=function(barcode){if(scanTarget==='__search'){$('#searchForm input').value=barcode;$('#searchForm').requestSubmit()}else{sharedApplyBarcode(barcode);itemDirty=true}};
$$('[data-searchkind]').forEach(b=>b.onclick=()=>{kind=b.dataset.searchkind;updateEditorTabs();$('#results').innerHTML='';if($('#searchForm input').value.trim())$('#searchForm').requestSubmit()});

// Always refresh store metadata when opening the dedicated editor.
cachedBootstrap=()=>null;
