(()=>{'use strict';
const $=id=>document.getElementById(id),remote=true;
let csrf='',preauth='',rows=[],customerTypes=[],selected=null,permissions=[],generation=0;
let currentAccount=null,historyRows=[],historyTimer,accountVersion=0,historyVersion=0,transactionVersion=0,activeTransaction=null;
let adjustment=null;
const esc=v=>String(v??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const number=new Intl.NumberFormat('en-GY',{minimumFractionDigits:0,maximumFractionDigits:2});
const money=v=>{const value=Number(v||0);return (value<0?'-':'')+'G$'+number.format(Math.abs(value));};
function note(t,error=false){$('notice').textContent=t;$('notice').classList.toggle('error',error)}
function reset(){generation++;showDirectory();historyRows=[];permissions=[];csrf='';preauth='';rows=[];customerTypes=[];selected=null;$('results').replaceChildren();$('editor').reset();$('detail').close();$('detailTitle').textContent='Customer account';$('balances').textContent='';$('workspace').hidden=true;$('logout').hidden=true;$('auth').hidden=false;$('login').hidden=false;$('mfa').hidden=true;$('mfaQr').removeAttribute('src');$('mfaSecret').textContent='';}
async function call(route,body={}){const response=await fetch('/employees/api/v1/'+(['session','logout'].includes(route)?'auth/':'')+route,{method:'POST',credentials:'include',cache:'no-store',headers:{'Content-Type':'application/json',...(csrf?{'X-CSRF-Token':csrf}:{})},body:JSON.stringify(body)});let reply;try{reply=await response.json()}catch{throw Error('The store connection is unavailable.')}if(!response.ok||!reply.ok){if(response.status===401||response.status===403)reset();throw Error(reply.error?.message||'The store could not complete this request.')}return reply.data;}
function render(){const q=$('query').value.trim().toLowerCase(),filtered=rows.filter(r=>[r.name,r.accountNumber,r.phone,r.email].some(v=>String(v||'').toLowerCase().includes(q)));$('count').textContent=filtered.length+' of '+rows.length+' accounts';$('results').innerHTML=filtered.map(r=>'<button class="item" data-id="'+r.customerId+'"><span class="tag">'+esc(r.accountNumber||'No account number')+' · '+(r.active?'Active':'Inactive')+'</span><h3>'+esc(r.name)+'</h3><p>'+esc([r.phone,r.email].filter(Boolean).join(' · '))+'</p><span class="price">'+esc(money(r.totalDue))+' due</span></button>').join('')||'<p>No matching accounts.</p>';}
async function load(){const version=++generation;rows=[];$('results').replaceChildren();note('Loading accounts…');try{const data=await call('customers/list');if(version!==generation||!csrf)return;rows=data.accounts;customerTypes=data.customerTypes||[];render();note('')}catch(e){if(version===generation)note(e.message,true)}}
async function boot(){try{const token=new URLSearchParams(location.hash.slice(1)).get('activate');if(location.hash)history.replaceState(null,'',location.pathname);if(token)await call('activate',{token});const session=await call('session');csrf=session.csrfToken;permissions=session.permissions||[];if(!permissions.includes('CUSTOMER_ACCOUNTS')){reset();throw Error('Customer Accounts permission is required.')} $('auth').hidden=true;$('workspace').hidden=false;$('logout').hidden=false;await load();}catch(e){note(e.message,true)}}
function showDirectory(){
  clearTimeout(historyTimer);
  ++accountVersion;
  ++historyVersion;
  ++transactionVersion;
  currentAccount=null;
  adjustment=null;
  activeTransaction=null;
  historyRows=[];
  $('customerAccount').hidden=true;
  $('customerDirectory').hidden=false;
  $('historyQuery').value='';
  $('historyRows').replaceChildren();
  $('historyNotice').textContent='';
  $('historyMore').hidden=true;
  $('transactionSheet').close();
  $('transactionTitle').textContent='Transaction details';
  $('transactionStatus').textContent='';
  $('transactionStatus').hidden=true;
  $('transactionBody').replaceChildren();
  $('accountAdjustment').close();
  for(const id of ['accountInitials','accountName','accountNumber','accountLimit','accountBalance','accountOrderDue','accountTotal','accountCredit','accountStatus'])
    $(id).textContent='';
}

function renderAccount(account,focus=false){
  currentAccount=account;
  $('accountInitials').textContent=account.name.trim().split(/\s+/).slice(0,2).map(word=>Array.from(word)[0]).join('').toLocaleUpperCase();
  $('accountName').textContent=account.name;
  $('accountNumber').textContent=account.accountNumber||'No account number';
  $('accountStatus').textContent=account.active?'Active':'Inactive';
  $('accountStatus').classList.toggle('inactive',!account.active);
  const amounts={accountLimit:'creditLimit',accountBalance:'currentBalance',accountOrderDue:'customOrderDue',accountTotal:'totalDue',accountCredit:'availableCredit'};
  for(const [id,field] of Object.entries(amounts)) $(id).textContent=money(account[field]);
  $('editAccount').hidden=!permissions.includes('CUSTOMER_ACCOUNTS');
  $('recordPayment').hidden=!account.active;
  $('addCharge').hidden=!account.active;
  $('customerDirectory').hidden=true;
  $('customerAccount').hidden=false;
  if(focus){window.scrollTo(0,0);$('accountName').focus({preventScroll:true});}
}

async function openAccount(id){
  const version=++accountVersion;
  note('Loading account…');
  try{
    const account=await call('customers/details',{customerId:id});
    if(version!==accountVersion||!csrf)return;
    $('historyQuery').value='';
    renderAccount(account,true);
    note('');
    await loadHistory();
  }catch(error){if(version===accountVersion)note(error.message,true);}
}

const human=value=>String(value??'').toLowerCase().replaceAll('_',' ').replace(/^./,c=>c.toUpperCase());
const typeName=kind=>({SALE:'Sale',CUSTOM_ORDER:'Order',SALES_ORDER:'Order',PAYMENT:'Payment',MANUAL_CHARGE:'Charge'}[kind]||human(kind));
const displayMethod=value=>({ACCOUNT:'Charged to account',ACCOUNT_CREDIT:'Account credit',MMG:'MMG'}[value]||human(value));
const present=value=>value!==undefined&&value!==null&&value!=='';
const date=(value,withTime=false)=>{
  if(!value)return '';
  const d=new Date(/^\d{4}-\d{2}-\d{2}$/.test(value)?value+'T12:00:00-04:00':value);
  if(Number.isNaN(d.getTime()))return '';
  return new Intl.DateTimeFormat('en-GB',{timeZone:'America/Guyana',day:'numeric',month:'short',year:'numeric',
    ...(withTime?{hour:'numeric',minute:'2-digit',hour12:true}:{})}).format(d);
};

function transactionName(row){
  return [typeName(row.transactionType),row.transactionType==='PAYMENT'?(row.paymentId||row.documentNumber):row.documentNumber].filter(Boolean).join(' ');
}

function renderHistory(){
  $('historyRows').innerHTML=historyRows.map((row,index)=>{
    const description=row.transactionType==='PAYMENT'?[displayMethod(row.paymentMethod),row.storeName].filter(Boolean).join(' · '):
      [row.itemSummary||row.note||row.storeName,row.transactionType==='CUSTOM_ORDER'?human(row.documentStatus):displayMethod(row.paymentMethod)].filter(Boolean).join(' · ');
    const selected=activeTransaction?.eventId===row.eventId&&activeTransaction.locationId===row.locationId;
    return `<li><button type="button" data-transaction="${index}"${selected?' aria-current="true"':''}><span aria-hidden="true" class="history-icon" data-kind="${esc(row.transactionType)}"></span>
      <span class="history-main"><strong>${esc(transactionName(row))}</strong><small title="${esc(description)}">${esc(description)}</small></span>
      <span class="history-value"><small>${esc(date(row.createdAtEpochMillis))}</small><strong>${money(row.amount)}</strong></span><span class="history-chevron" aria-hidden="true"></span></button></li>`;
  }).join('');
}

async function loadHistory(append=false){
  if(!currentAccount)return;
  const version=++historyVersion;
  const query=$('historyQuery').value.trim();
  const offset=append?historyRows.length:0;
  if(!append){historyRows=[];renderHistory();}
  $('historyHeading').textContent=query?'Search results':'10 most recent transactions';
  $('historyMore').hidden=true;
  $('historyNotice').textContent=append?'Loading more…':'Loading transactions…';
  try{
    const data=await call('customers/history',{customerId:currentAccount.customerId,query,offset});
    if(version!==historyVersion)return;
    historyRows=[...historyRows,...data.transactions];
    renderHistory();
    $('historyMore').hidden=!data.hasMore;
    $('historyNotice').textContent=historyRows.length?(query?`${data.count} ${data.count===1?'result':'results'}`:''):
      (query?'No matching transactions.':'No transactions on this account yet.');
  }catch(error){if(version===historyVersion){$('historyNotice').textContent=error.message;$('historyMore').hidden=!append;}}
}

const fieldList=(rows,totals=false)=>{
  const visible=rows.filter(([,value])=>present(value));
  return visible.length?`<dl class="detail-fields${totals?' detail-totals':''}">${visible.map(([label,value])=>
    `<div><dt>${esc(label)}</dt><dd>${esc(value)}</dd></div>`).join('')}</dl>`:'';
};
const section=(title,content,id='')=>content?`<section${id?` id="transaction${id}"`:''}>${title?`<h3>${esc(title)}</h3>`:''}${content}</section>`:'';
const paragraph=value=>present(value)?`<p>${esc(value)}</p>`:'';
const amount=value=>present(value)?money(value):'';
const eventCard=(title,subtitle,body='')=>`<div class="detail-card"><strong>${esc(title)}</strong>${subtitle?`<small>${esc(subtitle)}</small>`:''}${body}</div>`;

function saleDetails(sale,row){
  const s=sale.summary;
  const items=sale.items.length?`<table aria-label="Sale items"><thead><tr><th scope="col">Item</th><th scope="col">Qty</th><th scope="col">Unit price</th><th scope="col">Amount</th></tr></thead><tbody>${sale.items.map(item=>{
    const details=[item.sku,item.size_snapshot,item.color_snapshot,
      Number(item.original_unit_price)>Number(item.unit_price)?'Original '+money(item.original_unit_price):'',
      item.discount_amount?'Discount '+money(item.discount_amount):''].filter(present).join(' · ');
    return `<tr><td><strong>${esc(item.item_name)}</strong>${details?`<br><small>${esc(details)}</small>`:''}</td>
      <td>${esc(item.quantity)}</td><td>${money(item.unit_price)}</td><td>${money(item.line_total)}</td></tr>`;
  }).join('')}</tbody></table>`:'';
  const charged=s.payment_method==='ACCOUNT';
  const activity=(sale.returns||[]).map(r=>eventCard(r.return_receipt_number||'Return',date(r.created_at,true),
    fieldList([['Refund',amount(r.refund_amount)],['Method',displayMethod(r.refund_method)],['Handled by',r.user_name],['Reason',r.reason]]))).join('');
  const payments=(sale.payments||[]).map(p=>eventCard(p.payment_id||'Account payment',date(p.created_at,true),
    fieldList([['Applied',amount(p.amount)],['Method',displayMethod(p.payment_method)],['Reference',p.payment_reference]]))).join('');
  return section('',fieldList([['Receipt number',s.receipt_number],['Date & time',date(s.created_at,true)],['Store',s.store_name]]))+
    section('Items',items||paragraph('No item details are available.'))+
    fieldList([['Subtotal',amount(s.subtotal_amount)],['Discount',s.discount_amount?money(s.discount_amount):''],
      ['VAT',s.vat_amount?money(s.vat_amount):''],['Returned',s.returned_amount?money(s.returned_amount):''],['Total',amount(s.total_amount)]],true)+
    section('Payment',fieldList([['Method',displayMethod(s.payment_method)],['Status',human(s.payment_status)],['Reference',s.payment_reference],
      ['Paid',amount(s.amount_paid)],['Balance due',charged&&present(s.amount_paid)?money(Math.max(0,s.total_amount-(s.returned_amount||0)-s.amount_paid)):'']])+paragraph(sale.taskPaymentSummary))+
    section('Sale information',fieldList([['Cashier',s.cashier],['Sale status',human(s.status||row.documentStatus)],['Authorized by',s.representative_name]]))+
    section('Account payments',payments)+section('Returns',activity);
}

function groupOrderLines(lines,addons){
  const groups=new Map();
  for(const line of lines){
    const printAddons=addons.filter(a=>String(a.custom_order_line_id)===String(line.custom_order_line_id));
    const {custom_order_line_id,custom_item_id,custom_variant_id,...properties}=line;
    const key=JSON.stringify([properties,printAddons.map(({custom_order_line_id,...addon})=>addon)]);
    const group=groups.get(key);
    if(group)group.quantity++;
    else groups.set(key,{line,addons:printAddons,quantity:1});
  }
  return [...groups.values()];
}

function orderLine({line,addons,quantity},pending){
  const dimensions=[line.width_value,line.length_value].filter(present).join(' × ');
  const headline=[quantity+' × '+line.item_name,line.variant_name,line.item_size!==line.variant_name?line.item_size:''].filter(Boolean).join(' · ');
  return `<article class="detail-card"><div class="order-item-header"><span class="history-icon" aria-hidden="true"></span>
    <div class="detail-card-head"><div><strong>${esc(headline)}</strong>${!pending&&present(line.unit_price)?`<small>${amount(line.unit_price)} each</small>`:''}</div>
    <strong>${pending?'Price pending':amount(present(line.line_total)?Number(line.line_total)*quantity:null)}</strong></div></div>
    ${fieldList([['Color',line.item_color],
      ['Dimensions',dimensions?[dimensions,line.dimension_unit].filter(present).join(' '):''],
      ['Area',present(line.area_value)?[line.area_value,line.area_unit].filter(present).join(' '):''],
      ['Area price',pending?'':amount(line.area_price)],['Customization',line.customization_details],['Instructions',line.order_instructions],
      ['Production',human(line.production_status)],['Fulfillment',human(line.delivery_status)],
      ['Return',line.return_status==='NONE'?'':human(line.return_status)],['Returned',line.returned_amount?money(line.returned_amount):'']])}
    ${addons.map(a=>fieldList([['Add-on',[a.print_material_name,a.print_size_name,a.print_description].filter(present).join(' · ')],
      ['Charge',pending?'':amount(a.print_charge)]])).join('')}</article>`;
}

function orderDetails(order,row){
  const s=order.summary,pending=s.pricing_state==='PENDING';
  const overview=fieldList([['Customer',s.customer_name],['Account',currentAccount.accountNumber],['Phone',s.customer_phone],
    ['Store',s.location_name||row.storeName],['Placed',date(s.created_at||row.createdAtEpochMillis,true)],['Due',date(s.due_date)],
    ['Taken by',s.taken_by_name],['Assigned to',s.assigned_to_name],['Assigned by',s.assigned_by_name],['Assigned on',date(s.assigned_at,true)],
    ['Linked invoice',s.source_invoice_id],['Billed through invoice',s.invoice_billed?'Yes':''],['Source quotation',s.source_quotation_id]])+
    fieldList([['Order total',pending?'Price pending':amount(s.total_amount)],['Paid',amount(s.amount_paid)],
      ['Balance due',pending?'Price pending':amount(s.balance_due)]],true)+
    section('Fulfillment',fieldList([['Status',human(s.status)],['Completed',date(s.completed_at,true)],['Delivered',date(s.delivered_at,true)],
      ['Cancelled',date(s.cancelled_at,true)],['Cancelled by',s.cancelled_by_name],['Reason',s.cancellation_reason]]))+
    section('Order notes',paragraph(s.order_notes));
  const files=[...(order.files||[]),...(order.proofs||[])].map(file=>{
    const id=file.file_id||file.proof_id;
    const label=[file.filename,file.revision?'Proof '+file.revision:'',human(file.status)].filter(Boolean).join(' · ');
    const content=order.canOpenFiles?`<button class="order-file" type="button" data-order-file="${esc(id)}">${esc(label)}<span class="history-chevron" aria-hidden="true"></span></button>`:
      `<span class="order-file">${esc(label)}</span>`;
    return `<div class="detail-card">${content}${paragraph(file.feedback)}<small role="status"></small></div>`;
  }).join('');
  const items=groupOrderLines(order.lines||[],order.addons||[]).map(group=>orderLine(group,pending)).join('');
  const paymentTerms=fieldList([['Payment status',human(s.payment_status)],['Method',displayMethod(s.payment_method)],['Reference',s.payment_reference],
    ['Minimum deposit',s.minimum_deposit_required?money(s.minimum_deposit_required):''],['Deposit override',s.deposit_override_reason],
    ['Override by',s.deposit_override_by_name],['Pricing note',s.pricing_defer_reason],['Pricing deferred by',s.pricing_defer_by_name],
    ['Price finalized',date(s.pricing_finalized_at,true)],['Price finalized by',s.pricing_finalized_by_name]]);
  const payments=(order.payments||[]).map(p=>{
    const summary=[human(p.payment_action),date(p.created_at),displayMethod(p.payment_method),p.reversed?'Reversed':'',p.voided_at?'Voided':''].filter(Boolean).join(' · ');
    return `<div class="detail-card order-payment"><div class="detail-card-head"><div><strong>${esc(summary)}</strong>
      ${p.payment_reference?`<small>Ref. ${esc(p.payment_reference)}</small>`:''}</div><strong>${amount(p.payment_amount)}</strong></div>
      ${fieldList([['Recorded',date(p.created_at,true)],['Taken by',p.taken_by_name],['Voided',date(p.voided_at,true)],['Reason',p.void_reason]])}</div>`;
  }).join('')||paragraph('No payments recorded.');
  const events=[
    ...(order.deliveries||[]).map(e=>[e.delivered_at,'Delivery',e.delivered_by_name,e.item_name,e.delivery_notes,e.device_name]),
    ...(order.production||[]).map(e=>[e.created_at,human(e.new_status),e.updated_by_name,e.item_name,e.notes,e.device_name,
      [['Previous status',human(e.old_status)]]]),
    ...(order.returns||[]).map(e=>[e.created_at,human(e.return_type),e.created_by_name,e.item_name,e.reason,'',
      [['Refund',amount(e.refund_amount)],['Restock',human(e.restock_action)]]])
  ].sort((a,b)=>String(b[0]).localeCompare(String(a[0])));
  const audit=(order.activity||[]).map(e=>eventCard(human(e.action_type),[date(e.created_at,true),e.user_name].filter(Boolean).join(' · '),
    fieldList([['Field',human(e.field_name)],['Before',e.old_value],['After',e.new_value],['Reason',e.reason]]))).join('');
  const activity=events.map(([when,title,by,item,notes,device,extra])=>eventCard(title,[date(when,true),by].filter(Boolean).join(' · '),
    fieldList([['Item',item],['Notes',notes],['Device',device],...(extra||[]) ]))).join('')+
    (audit?`<details class="detail-card"><summary>Audit history</summary>${audit}</details>`:'');
  const authorization=(order.authorization||[]).map(a=>fieldList([['Representative',a.representative_name],
    ['Captured',date(a.captured_at,true)],['Captured by',a.captured_by_name],['Device',a.device_name],
    ['Signature',a.has_signature?'Captured':a.signature_purged_at?'Expired':''],['Unsigned reason',a.unsigned_reason]])).join('');
  return section('Order overview',overview,'Overview')+
    section('Order items',(items||paragraph('No items recorded.'))+section('Artwork & attachments',files),'Items')+
    section('Payment history',payments+section('Payment details',paymentTerms)+section('Account authorization',authorization),'Payments')+
    section('Order activity',activity||paragraph('No activity recorded.'),'Activity');
}

function generalDetails(row,payment){
  const fields=[['Date & time',date(row.createdAtEpochMillis,true)],['Store',row.storeName],['Recorded by',row.userName],
    ['Document',row.documentNumber],['Reference',row.paymentReference],['Payment method',displayMethod(row.paymentMethod)],
    ['Payment status',human(row.paymentStatus)],['Status',human(row.documentStatus)],['Device',row.deviceName],['Cash drawer',row.cashDrawerName],
    ['Amount',amount(row.amount)],['Notes',row.note]];
  const allocations=payment?.allocations?.map(a=>eventCard(a.targetLabel,amount(a.appliedAmount),
    fieldList([['Document total',amount(a.chargeTotal)],['Document paid',amount(a.chargePaid)],['Status',human(a.paymentStatus)]]))).join('');
  const detailsMissing=['SALE','CUSTOM_ORDER','SALES_ORDER'].includes(row.transactionType);
  return section('Transaction details',fieldList(fields))+section('Applied to',allocations)+
    (detailsMissing?paragraph('The full document is unavailable from this store. The history entry is shown above.'):'');
}

function setActiveSection(sectionName){
  for(const button of $('orderSections').querySelectorAll('button'))
    button.setAttribute('aria-pressed',String(button.dataset.section===sectionName));
}
function updateSection(){
  const body=$('transactionBody');
  if(body.scrollTop>0&&body.scrollHeight-body.scrollTop-body.clientHeight<12){setActiveSection('Activity');return;}
  let active='Overview';
  for(const name of ['Items','Payments','Activity']){
    const section=$('transaction'+name);
    if(section&&section.getBoundingClientRect().top<=body.getBoundingClientRect().top+24)active=name;
  }
  setActiveSection(active);
}

async function openTransaction(row){
  const version=++transactionVersion;
  const sheet=$('transactionSheet');
  const body=$('transactionBody');
  const order=row.transactionType==='CUSTOM_ORDER'||row.transactionType==='SALES_ORDER';
  $('transactionTitle').textContent=transactionName(row);
  $('transactionIcon').dataset.kind=row.transactionType;
  $('transactionStatus').hidden=true;
  $('orderSections').hidden=true;
  body.innerHTML='<p role="status">Loading transaction details…</p>';
  body.scrollTop=0;
  sheet.classList.toggle('is-order',order);
  activeTransaction=row;
  for(const button of $('historyRows').querySelectorAll('[data-transaction]')){
    const entry=historyRows[Number(button.dataset.transaction)];
    if(entry===row)button.setAttribute('aria-current','true');
    else button.removeAttribute('aria-current');
  }
  if(!sheet.open)sheet.showModal();
  try{
    const data=await call('customers/transaction',{customerId:currentAccount.customerId,locationId:row.locationId??0,eventId:row.eventId});
    if(version!==transactionVersion||!sheet.open)return;
    body.innerHTML=data.order?orderDetails(data.order,row):data.sale?saleDetails(data.sale,row):generalDetails(data.transaction||row,data.payment);
    if(data.order){
      const status=data.order.summary.status;
      $('transactionStatus').textContent=human(status);
      $('transactionStatus').classList.toggle('inactive',!['READY','COMPLETED','DELIVERED'].includes(status));
      $('transactionStatus').hidden=!status;
      $('orderSections').hidden=false;
      setActiveSection('Overview');
    }
  }catch(error){
    if(version===transactionVersion&&sheet.open)body.innerHTML=`<p role="alert">${esc(error.message)}</p><button type="button" data-retry-transaction>Try again</button>`;
  }
}

async function downloadOrderFile(button){
  const version=transactionVersion;
  const message=button.parentElement.querySelector('[role="status"]');
  const request={customerId:currentAccount.customerId,eventId:activeTransaction.eventId,fileId:button.dataset.orderFile};
  button.disabled=true;
  message.textContent='Loading attachment…';
  try{
    let offset=0,filename,totalBytes;
    const chunks=[];
    do{
      const chunk=await call('customers/order-file',{...request,offset});
      if(version!==transactionVersion)return;
      const bytes=Uint8Array.from(atob(chunk.bytesBase64),character=>character.charCodeAt(0));
      if(!bytes.length||chunk.totalBytes!==Number(chunk.totalBytes)||
          (totalBytes!==undefined&&totalBytes!==chunk.totalBytes)||offset+bytes.length>chunk.totalBytes)
        throw Error('The attachment changed while it was loading.');
      totalBytes=chunk.totalBytes;
      filename=chunk.filename;
      offset+=bytes.length;
      chunks.push(bytes);
    }while(offset<totalBytes);
    const objectUrl=URL.createObjectURL(new Blob(chunks,{type:'application/octet-stream'}));
    try{
      const anchor=document.createElement('a');
      anchor.href=objectUrl;
      anchor.download=filename;
      $('transactionBody').append(anchor);
      anchor.click();
      anchor.remove();
    }finally{
      setTimeout(()=>URL.revokeObjectURL(objectUrl),1000);
    }
    message.textContent='';
  }catch(error){if(version===transactionVersion)message.textContent=error.message;}
  finally{button.disabled=false;}
}

function paymentFields(){
  if(!adjustment||adjustment.action!=='PAYMENT')return;
  const form=$('adjustmentForm');
  const method=form.elements.paymentMethod.value;
  $('paymentDrawer').hidden=method!=='CASH';
  form.elements.drawerSessionId.required=method==='CASH';
  $('paymentReference').hidden=method==='APPLY_CREDIT';
  form.elements.paymentReference.required=['CARD','CHEQUE','MMG'].includes(method);
  const inputs=[...$('paymentBalances').querySelectorAll('[data-balance-index]')];
  const total=inputs.reduce((sum,input)=>sum+Number(input.value||0),0);
  $('paymentTotal').textContent=adjustment.loading?'':`Payment total ${money(total)}`;
  $('saveAdjustment').disabled=adjustment.loading||adjustment.saving||total<=0||
    (method==='CASH'&&!form.elements.drawerSessionId.value);
}

async function openAdjustment(action){
  if(!currentAccount?.active)return;
  const payment=action==='PAYMENT',form=$('adjustmentForm');
  adjustment={action,customerId:currentAccount.customerId,balances:[],loading:payment,saving:false,attempt:null};
  const state=adjustment;
  form.reset();
  $('adjustmentTitle').textContent=payment?'Record payment':'Add charge';
  $('saveAdjustment').textContent=payment?'Record payment':'Add charge';
  $('saveAdjustment').disabled=payment;
  $('adjustmentPayment').hidden=!payment;
  $('adjustmentCharge').hidden=payment;
  form.elements.chargeAmount.required=!payment;
  form.elements.description.required=!payment;
  form.elements.paymentReference.required=false;
  form.elements.drawerSessionId.required=false;
  $('paymentBalances').replaceChildren();
  $('paymentTotal').textContent='';
  $('adjustmentNotice').textContent=payment?'Loading open balances…':'';
  $('accountAdjustment').showModal();
  if(!payment)return;
  try{
    const data=await call('customers/payment-options',{customerId:state.customerId});
    if(adjustment!==state||!$('accountAdjustment').open)return;
    state.balances=data.balances;
    $('paymentBalances').innerHTML=state.balances.map((balance,index)=>
      `<label><span>${esc(balance.documentNumber)} · ${money(balance.balanceDue)} due</span><input type="number" min="0" max="${Number(balance.balanceDue)}" step="1" inputmode="numeric" data-balance-index="${index}"></label>`).join('')||
      '<p>No open balances are available to pay at this store.</p>';
    form.elements.drawerSessionId.innerHTML='<option value="">Select a cash drawer</option>'+data.drawers.map(drawer=>
      `<option value="${Number(drawer.sessionId)}">${esc(drawer.name)}</option>`).join('');
    if(data.drawers.length===1)form.elements.drawerSessionId.value=data.drawers[0].sessionId;
    if(!data.drawers.length)form.elements.paymentMethod.value='CARD';
    $('adjustmentNotice').textContent='';
    state.loading=false;
    paymentFields();
  }catch(error){
    if(adjustment===state)$('adjustmentNotice').textContent=error.message;
  }
}

async function saveAdjustment(event){
  event.preventDefault();
  const state=adjustment;
  if(!state||state.loading||state.saving)return;
  const fields=$('adjustmentForm').elements;
  const body={action:state.action,customerId:state.customerId};
  if(state.action==='CHARGE'){
    body.amount=Number(fields.chargeAmount.value);
    body.description=fields.description.value.trim();
  }else{
    body.paymentMethod=fields.paymentMethod.value;
    if(body.paymentMethod==='CASH')body.drawerSessionId=Number(fields.drawerSessionId.value);
    if(body.paymentMethod!=='APPLY_CREDIT')body.paymentReference=fields.paymentReference.value.trim();
    body.allocations=[...$('paymentBalances').querySelectorAll('[data-balance-index]')].filter(input=>Number(input.value)>0).map(input=>{
      const balance=state.balances[Number(input.dataset.balanceIndex)];
      return {documentType:balance.documentType,documentId:balance.documentId,amount:Number(input.value)};
    });
    body.amount=body.allocations.reduce((sum,balance)=>sum+balance.amount,0);
  }
  if(body.amount<=0)return;
  const fingerprint=JSON.stringify(body);
  if(state.attempt?.fingerprint!==fingerprint)state.attempt={fingerprint,key:crypto.randomUUID()};
  state.saving=true;
  $('saveAdjustment').disabled=true;
  $('closeAdjustment').disabled=true;
  $('adjustmentNotice').textContent='Saving…';
  try{
    await call('customers/record-adjustment',{...body,idempotencyKey:state.attempt.key});
    if(adjustment!==state||currentAccount?.customerId!==state.customerId)return;
    $('accountAdjustment').close();
    await refreshAccount(state.customerId);
    note(state.action==='CHARGE'?'Charge added.':'Payment recorded.');
  }catch(error){if(adjustment===state)$('adjustmentNotice').textContent=error.message;}
  finally{
    if(adjustment===state){
      state.saving=false;
      $('closeAdjustment').disabled=false;
      $('saveAdjustment').disabled=false;
      paymentFields();
    }
  }
}

async function refreshAccount(id){
  const version=accountVersion;
  const account=await call('customers/details',{customerId:id});
  if(version!==accountVersion||currentAccount?.customerId!==id)return;
  renderAccount(account);
  await loadHistory();
}

function edit(account){selected=account;const r=account||{active:true,creditLimit:0,customerSince:new Date().getFullYear()};$('editor').reset();const typeBox=$('editor').elements.customerTypeId;const options=[...customerTypes];if(r.customerTypeId&&!options.some(t=>t.customerTypeId===r.customerTypeId))options.push({customerTypeId:r.customerTypeId,name:r.customerTypeName||'Current type'});typeBox.innerHTML='<option value="">Select customer type</option>'+options.map(t=>'<option value="'+Number(t.customerTypeId)+'">'+esc(t.name)+'</option>').join('');for(const field of $('editor').elements){if(!field.name)continue;if(field.type==='checkbox')field.checked=!!r[field.name];else field.value=r[field.name]??'';} $('editor').elements.accountNumber.disabled=!permissions.includes('EDIT_ACCOUNT_NUMBER')||!account;$('editor').elements.creditLimit.disabled=!permissions.includes('SET_CREDIT_LIMIT');$('detailTitle').textContent=account?account.name:'New customer account';$('balances').textContent=account?'Account balance: '+money(account.currentBalance)+' · Custom orders: '+money(account.customOrderDue)+' · Available credit: '+money(account.availableCredit):'Account number is assigned by SmartStock.';$('saveNotice').textContent='';$('detail').showModal();}
 $('login').onsubmit=async e=>{e.preventDefault();$('loginButton').disabled=true;const secret=$('password').value;$('password').value='';note('Signing in…');try{const data=await call(remote?'auth/login':'login',{identifier:$('identifier').value.trim(),...(remote?{password:secret}:{secret})});if(data.mfaRequired){preauth=data.preauth;$('login').hidden=true;$('mfa').hidden=false;$('mfaQr').hidden=!data.qrCode;if(data.qrCode)$('mfaQr').src=data.qrCode;$('mfaSecret').textContent=data.secret||'';}else await boot();}catch(err){note(err.message,true)}finally{$('loginButton').disabled=false}};
 $('mfa').onsubmit=async e=>{e.preventDefault();const code=$('code').value;$('code').value='';try{await call('auth/verify',{preauth,code});preauth='';$('mfaQr').removeAttribute('src');$('mfaSecret').textContent='';await boot();}catch(err){reset();note(err.message,true)}};
 $('logout').onclick=async()=>{try{await call('logout');reset();note('Signed out.')}catch(e){note(e.message,true)}};

$('retry').onclick=boot;$('refresh').onclick=load;$('query').oninput=render;$('add').onclick=()=>edit(null);$('closeDetail').onclick=()=>$('detail').close();
$('results').onclick=e=>{const button=e.target.closest('[data-id]');if(button)openAccount(Number(button.dataset.id));};
$('backToCustomers').onclick=()=>{showDirectory();$('query').focus();};
$('editAccount').onclick=()=>{if(currentAccount&&permissions.includes('CUSTOMER_ACCOUNTS'))edit(currentAccount);};
$('historyQuery').oninput=()=>{
  clearTimeout(historyTimer);
  ++historyVersion;
  historyTimer=setTimeout(loadHistory,250);
};
$('historyMore').onclick=()=>loadHistory(true);
$('historyRows').onclick=e=>{
  const button=e.target.closest('[data-transaction]');
  if(button&&currentAccount)openTransaction(historyRows[Number(button.dataset.transaction)]);
};
$('recordPayment').onclick=()=>openAdjustment('PAYMENT');
$('addCharge').onclick=()=>openAdjustment('CHARGE');
$('closeAdjustment').onclick=()=>$('accountAdjustment').close();
$('accountAdjustment').addEventListener('cancel',event=>{if(adjustment?.saving)event.preventDefault();});
$('accountAdjustment').addEventListener('close',()=>{
  if($('accountAdjustment').open)return;
  adjustment=null;
  $('adjustmentForm').reset();
  $('paymentBalances').replaceChildren();
  $('adjustmentNotice').textContent='';
  $('paymentTotal').textContent='';
  $('closeAdjustment').disabled=false;
});
$('adjustmentForm').oninput=paymentFields;
$('adjustmentForm').onsubmit=saveAdjustment;
$('closeTransaction').onclick=()=>$('transactionSheet').close();
$('transactionSheet').addEventListener('close',()=>{
  if($('transactionSheet').open)return;
  ++transactionVersion;
  activeTransaction=null;
  $('historyRows').querySelector('[aria-current]')?.removeAttribute('aria-current');
  $('transactionBody').replaceChildren();
});
$('transactionBody').addEventListener('scroll',()=>{if(!$('orderSections').hidden)updateSection();});
$('orderSections').onclick=e=>{
  const button=e.target.closest('[data-section]');
  if(!button)return;
  const body=$('transactionBody'),target=$('transaction'+button.dataset.section);
  if(target){body.scrollTop+=target.getBoundingClientRect().top-body.getBoundingClientRect().top-8;setActiveSection(button.dataset.section);}
};
$('transactionBody').onclick=e=>{
  if(e.target.closest('[data-retry-transaction]')&&activeTransaction)openTransaction(activeTransaction);
  const file=e.target.closest('[data-order-file]');
  if(file&&activeTransaction)downloadOrderFile(file);
};
$('activation').onsubmit=async e=>{e.preventDefault();try{const value=$('activationLink').value.trim(),url=new URL(value);const token=new URLSearchParams(url.hash.slice(1)).get('activate');if(!token)throw Error('Paste a fresh activation link from Web App Device Management.');await call('activate',{token});$('activationLink').value='';note('Browser activated. Sign in to continue.')}catch(err){note(err.message,true)}};
$('editor').onsubmit=async e=>{e.preventDefault();const body={...(selected||{})};delete body.business;for(const field of $('editor').elements){if(!field.name||field.disabled)continue;body[field.name]=field.type==='checkbox'?field.checked:field.type==='number'?(field.value===''?null:Number(field.value)):field.value;}body.customerTypeId=body.customerTypeId?Number(body.customerTypeId):null;if(selected)body.expectedAccount=selected;$('save').disabled=true;$('saveNotice').textContent='Saving…';try{await call('customers/save',body);$('detail').close();const id=selected?.customerId;await load();if(id&&currentAccount?.customerId===id)await refreshAccount(id);note('Customer saved.')}catch(err){$('saveNotice').textContent=err.message;note(err.message,true)}finally{$('save').disabled=false}};
document.addEventListener('visibilitychange',()=>{if(document.hidden)reset();else boot()});boot();})();
