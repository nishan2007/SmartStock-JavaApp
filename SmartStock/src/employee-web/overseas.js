'use strict';
// Uses the employee gateway's existing authenticated, CSRF-protected transport.
(() => {
  const host=document.getElementById('overseasOrders');
  let current=null,permissions=new Set(),lines=[],stores=[],store=0,generation=0,sellers=[];
  const n=(tag,text)=>{const e=document.createElement(tag);if(text!==undefined)e.textContent=text;return e;};
  const val=(o,k)=>o?.[k]??'';
  const allowed=p=>permissions.has('OVERSEAS_ORDERS_'+p);
  const input=(label,value='',type='text')=>{const wrap=n('label',label),field=n('input');field.type=type;field.value=value;wrap.append(field);return {wrap,field};};
  const button=(label,work)=>{const b=n('button',label);b.type='button';b.addEventListener('click',()=>busy(b,work));return b;};
  async function send(action,body={}) {
    const data=await api('overseas',{locationId:store,action,...body});
    if(data.permissions)permissions=new Set(data.permissions);
    return data;
  }
  function identity(){return current?{id:current.overseas_order_id,revision:current.revision}:{};}
  async function mutate(action,body={}) {
    const data=await send(action,{...identity(),requestId:crypto.randomUUID(),...body});
    if(data.order){current=data.order;lines=current.lines.map(l=>({...l}));renderEditor(data);}return data;
  }
  function reset(){generation++;current=null;lines=[];permissions.clear();stores=[];store=0;host.replaceChildren();host.hidden=true;}
  window.addEventListener('employee-signed-out',reset);
  window.addEventListener('employee-ready',async event=>{
    reset();if(!event.detail.canOverseas)return;host.hidden=false;stores=event.detail.overseasStores||[];
    if(!stores.length){host.append(n('p','No store is assigned to your account.'));return;}
    store=Number(stores[0].id);renderShell();try{await list();}catch(e){notice(e.message);}
  });
  function renderShell(){
    host.replaceChildren();host.append(n('h2','Overseas Orders'),n('p','Prepare quotes here. Payments, printed receipts and customer collection are handled at the store register.'));
    const bar=n('div');bar.className='filters';bar.id='overseasLookup';const select=n('select');select.setAttribute('aria-label','Store');select.title='Choose the Deckers store this order is for';
    stores.forEach(s=>{const o=n('option',s.name);o.value=s.id;select.append(o);});select.value=store;
    select.addEventListener('change',()=>{store=Number(select.value);current=null;lines=[];document.getElementById('overseasEditor').replaceChildren();list().catch(e=>notice(e.message));});bar.append(select);
    const search=input('Search customer, phone or order');search.field.id='overseasSearch';bar.append(search.wrap);
    const filter=n('select');filter.id='overseasFilter';filter.setAttribute('aria-label','Order status');for(const [value,label]of [['ACTIVE','Active orders'],['ALL','All orders'],['UNPAID','Unpaid quotes'],['ARRIVED','Arrived, awaiting collection'],['COMPLETED','Completed']]){const o=n('option',label);o.value=value;filter.append(o);}bar.append(filter);
    bar.append(button('Search',list),button('New quote',async()=>{if(!allowed('QUOTE'))throw new Error('Quote permission is required.');current=null;lines=[];renderEditor();}));host.append(bar);
    const results=n('div');results.id='overseasResults';const editor=n('div');editor.id='overseasEditor';host.append(results,editor);
  }
  async function list(){const version=++generation,data=await send('LIST',{search:document.getElementById('overseasSearch').value,filter:document.getElementById('overseasFilter').value});if(version!==generation)return;const results=document.getElementById('overseasResults');results.replaceChildren();if(!data.rows.length)results.append(n('p','No matching orders.'));for(const row of data.rows){const card=n('article');card.className='session';card.append(n('strong',`${row.number} · ${row.customerName}`),n('p',`${row.customerPhone} · ${trackingLabel(row.tracking,row.storeName)} · Total GYD ${row.total} · Paid ${row.paid} · Balance ${row.balance}`),button('Open order',async()=>{const d=await send('DETAIL',{id:row.id});current=d.order;lines=current.lines.map(l=>({...l}));renderEditor(d);}));results.append(card);}}
  function newLine(){return {seller:'',description:'',product_url:'',options:'',quantity:1,unit_price:'0',usd_cost:'',exchange_rate:'',screenshot:'',purchased:0,shipped:0,arrived:0,collected:0};}
  function showWorkspace(open){
    document.getElementById('overseasLookup').hidden=open;
    document.getElementById('overseasResults').hidden=open;
  }
  async function closeEditor(){current=null;lines=[];document.getElementById('overseasEditor').replaceChildren();showWorkspace(false);await list();}
  function photo(line){
    if(!line.screenshot&&!line._preview)return null;
    const image=n('img');image.className='overseas-thumbnail';image.alt=`Screenshot of ${line.description||'item'}`;
    if(line._preview)image.src=line._preview;
    else if(current)send('IMAGE',{id:current.overseas_order_id,reference:line.screenshot}).then(r=>{if(image.isConnected)image.src=r.data;}).catch(e=>notice(e.message));
    return image;
  }
  const cents=value=>Math.round((Number(value)||0)*100);
  const money=value=>`GYD ${(value/100).toFixed(2)}`;
  const itemTotal=line=>cents(line.unit_price)*Number(line.quantity||0);
  function cartLine(line){
    const card=n('article');card.className='overseas-cart-line';const image=photo(line);if(image){card.classList.add('has-photo');card.append(image);}
    const text=n('div');text.append(n('strong',line.description||'Untitled item'));if(line.seller)text.append(n('p',`Seller: ${line.seller}`));
    if(line.options)text.append(n('p',line.options));
    text.append(n('p',`${line.quantity} × ${money(cents(line.unit_price))}`));card.append(text,n('strong',money(itemTotal(line))));return card;
  }
  function renderEditor(detail,editing=false,startStep=0){
    showWorkspace(true);
    if(allowed('QUOTE')&&(!current||current.state==='DRAFT'||editing))renderWizard(detail,startStep);
    else renderOverview(detail);
  }
  function storeName(){return current?.store_name||stores.find(s=>Number(s.id)===Number(store))?.name||'selected store';}
  function fillSellers(list){list.replaceChildren();for(const seller of sellers){const option=n('option');option.value=seller;list.append(option);}}
  function trackingLabel(stage,store){return ({DRAFT:'Draft',WAITING_FOR_PAYMENT:'Waiting for payment',PROCESSING:'Processing',ORDERED:'Ordered',PARTIALLY_ORDERED:'Partially ordered',ARRIVED:`Arrived at Deckers ${store||storeName()}`,PARTIALLY_ARRIVED:`Partially arrived at Deckers ${store||storeName()}`,COLLECTED:'Collected',CANCELLED:'Cancelled'})[stage]||stage||'Waiting for payment';}
  function progression(line){
    const wrap=n('div');wrap.className='overseas-tracking';const stage=line.tracking||(line.collected===line.quantity?'COLLECTED':line.arrived===line.quantity?'ARRIVED':line.purchased>0?'ORDERED':Number(current.paid)>0?'PROCESSING':'WAITING_FOR_PAYMENT');
    wrap.append(n('strong',trackingLabel(stage,current.store_name)));
    const steps=n('ol');steps.className='overseas-steps';const index=line.collected===line.quantity?4:line.arrived===line.quantity?3:line.purchased===line.quantity?2:Number(current.paid)>0?1:0;
    ['Waiting for payment','Processing','Ordered',`Arrived at Deckers ${current.store_name||storeName()}`].forEach((label,i)=>{const li=n('li',label);if(i===Math.min(index,3))li.setAttribute('aria-current','step');steps.append(li);});
    wrap.append(steps,n('p',`Ordered ${line.purchased||0}/${line.quantity} · Arrived ${line.arrived||0}/${line.quantity} · Collected ${line.collected||0}/${line.quantity}`));return wrap;
  }  function renderOverview(detail){
    const editor=document.getElementById('overseasEditor');editor.replaceChildren();
    editor.append(button('Back to orders',closeEditor),n('h3',`${current.number} · ${current.state}`),n('p',`${current.customer_name} · ${current.customer_phone}`));
    editor.append(n('p',`Paid ${money(cents(current.paid))} · Balance ${money(cents(current.balance))}`));
    editor.append(n('p',`Store: Deckers ${current.store_name||storeName()}`));
    for(const line of lines){const card=cartLine(line);card.append(progression(line));
      if(allowed('SHIP')&&current.state==='ISSUED'){
        const actions=n('div');actions.className='overseas-actions';
        if(line.purchased<line.quantity)actions.append(button('Mark ordered',async()=>mutate('TRACK',{stage:'ORDERED',lineIds:[line.line_id]})));
        if(line.purchased>0&&line.arrived<line.quantity)actions.append(button(`Arrived at Deckers ${current.store_name||storeName()}`,async()=>mutate('TRACK',{stage:'ARRIVED',lineIds:[line.line_id]})));
        card.append(actions);
      }editor.append(card);
    }
    editor.append(n('p',cents(current.shipping)>0?`Extra shipping ${money(cents(current.shipping))}`:'Shipping included in item prices'),n('strong',`Order total ${money(cents(current.total))}`));
    if(current.expected_date)editor.append(n('p',`Expected arrival: ${current.expected_date}`));
    if(current.notes)editor.append(n('p',current.notes));
    const actions=n('div');actions.className='overseas-actions';
    if(allowed('QUOTE')&&current.state==='ISSUED')actions.append(button('Edit quote',async()=>renderEditor(detail,true)));
    if(allowed('SHIP')&&current.state==='ISSUED')actions.append(button('Update shipment quantities',async()=>shipment()));editor.append(actions);
    if(detail?.payments?.length){const history=n('details');history.append(n('summary','Payment history'));for(const p of detail.payments)history.append(n('p',`${p.created_at} · ${p.payment_action} GYD ${p.payment_amount} · ${p.payment_method} · ${p.taken_by_name}`));editor.append(history);}
    if(detail?.events?.length){const history=n('details');history.append(n('summary','Order history'));for(const e of detail.events)history.append(n('p',`${e.at} · ${e.action} · ${e.actor} · ${e.reason}`));editor.append(history);}
  }
  function renderWizard(detail,startStep=0){
    const editor=document.getElementById('overseasEditor');editor.replaceChildren();
    let step=startStep,activeItem=-1,customerId=current?.customer_id??null,dirty=false,saving=false,pendingReads=0;
    const fields={},panels=[],steps=['Customer','Items','Delivery','Review cart'];
    editor.append(button('Back to orders',async()=>{if(saving)return;if(!dirty||confirm('Leave this quote and discard unsaved changes?'))await closeEditor();}));
    editor.append(n('h3',current?.state==='ISSUED'?`Edit ${current.number}`:'Prepare overseas quote'));
    const progress=n('ol');progress.className='overseas-steps';progress.setAttribute('aria-label','Quote progress');
    const stage=n('p');stage.className='overseas-stage';stage.setAttribute('aria-live','polite');editor.append(progress,stage);
    const form=n('form');form.className='overseas-form';form.noValidate=true;form.addEventListener('submit',e=>e.preventDefault());form.addEventListener('input',()=>{dirty=true;});editor.append(form);
    for(const title of steps){const panel=n('section');panel.className='overseas-step';panel.append(n('h4',title));panels.push(panel);form.append(panel);}
    function field(key,label,type,panel,value=val(current,key)){
      const f=input(label,value,type);fields[key]=f.field;panel.append(f.wrap);return f.field;
    }
    panels[0].append(n('p',`Order for Deckers ${storeName()}. To choose another store, return to the order list before creating the quote.`));
    const name=field('customer_name','Customer name','text',panels[0]);name.required=true;name.maxLength=300;
    const phone=field('customer_phone','WhatsApp / phone','tel',panels[0]);phone.required=true;phone.maxLength=100;
    const phoneMatches=n('div');phoneMatches.setAttribute('aria-live','polite');panels[0].append(phoneMatches);
    let phoneVersion=0,phoneTimer,phoneChecked='',phoneResult=null;
    function selectCustomer(c){customerId=c.customerId;name.value=c.name;phone.value=c.phone;dirty=true;phoneVersion++;phoneChecked='';phoneResult=null;phoneMatches.replaceChildren(n('p',`Selected customer: ${c.name} · ${c.accountNumber||c.phone}`));}
    async function checkPhone(){
      clearTimeout(phoneTimer);const query=phone.value.trim(),version=++phoneVersion,session=viewer;
      const result=await send('CUSTOMER_PHONE',{phone:query});
      if(version!==phoneVersion||query!==phone.value.trim()||viewer!==session||!phoneMatches.isConnected)return null;
      phoneChecked=query;phoneResult=result;phoneMatches.replaceChildren();
      const matches=result.customers||[];
      if(matches.length){phoneMatches.append(n('p','This phone number belongs to an existing customer. Select the account below.'));
        for(const c of matches){if(c.active)phoneMatches.append(button(`Select ${c.name} · ${c.accountNumber||c.phone}`,async()=>selectCustomer(c)));else phoneMatches.append(n('p',`${c.name} · Inactive account. Ask an administrator to reactivate it.`));}
      }else if(result.reserved)phoneMatches.append(n('p','This number is reserved to an existing account. Ask an administrator to check the customer record.'));
      return result;
    }
    phone.addEventListener('input',()=>{customerId=null;phoneVersion++;phoneChecked='';phoneResult=null;phoneMatches.replaceChildren();clearTimeout(phoneTimer);phoneTimer=setTimeout(()=>checkPhone().catch(e=>notice(e.message)),350);});
    async function validCustomer(){
      const result=phoneChecked===phone.value.trim()?phoneResult:await checkPhone();if(!result)return false;
      if(!result.validPhone){notice('Enter a complete Guyana or international phone number.');return false;}
      if(result.reserved&&!(result.customers||[]).some(c=>c.active&&c.customerId===customerId)){show(0);notice('Select the existing customer account for this phone number.');return false;}return true;
    }
    const customerSearch=input('Find existing customer');panels[0].append(customerSearch.wrap,button('Find customer',async()=>{
      const data=await send('CUSTOMERS',{search:customerSearch.field.value});const choices=n('div');
      for(const c of data.customers)choices.append(button(`${c.name} · ${c.phone} · ${c.accountNumber}`,async()=>{selectCustomer(c);choices.remove();}));
      if(!data.customers.length)choices.append(n('p','No match. A new customer account will be created when this quote is issued.'));panels[0].append(choices);
    }));
    const itemList=n('div'),itemEditor=n('div');panels[1].append(itemList,itemEditor);
    panels[2].append(n('p','Item prices already include shipping. Add an extra shipping charge only if needed; otherwise leave it blank.'));
    const shipping=field('shipping','Extra shipping GYD (optional)','number',panels[2],Number(current?.shipping)?current.shipping:'');shipping.min='0';shipping.step='0.01';
    field('expected_date','Expected arrival (optional)','date',panels[2]);
    const notesLabel=n('label','Notes'),notes=n('textarea');notes.value=val(current,'notes');notes.maxLength=5000;notesLabel.append(notes);panels[2].append(notesLabel);
    const review=n('div');panels[3].append(review);
    const reason=input('Adjustment reason (required after a payment or purchase)');
    if(current?.state==='ISSUED')panels[3].append(reason.wrap);
    const nav=n('div');nav.className='overseas-actions';form.append(nav);
    function show(next){
      step=next;panels.forEach((p,i)=>{p.hidden=i!==step;});progress.replaceChildren();
      steps.forEach((title,i)=>{const li=n('li',`${i+1}. ${title}`);if(i===step)li.setAttribute('aria-current','step');progress.append(li);});
      stage.textContent=`Step ${step+1} of 4 · ${steps[step]}`;
      if(step===1){renderItems();send('SELLERS').then(data=>{sellers=data.sellers||[];const list=document.getElementById('overseasSellers');if(list)fillSellers(list);}).catch(e=>notice(e.message));}if(step===3)renderReview();renderNav();
    }
    function validFields(panel){for(const f of panel.querySelectorAll('input,textarea'))if(!f.checkValidity()){f.reportValidity();return false;}return true;}
    function validateItems(){
      if(!lines.length){notice('Add at least one item to the cart.');return false;}
      const bad=lines.findIndex(l=>!l.description?.trim()||!Number.isInteger(Number(l.quantity))||Number(l.quantity)<1||Number(l.quantity)>100000||!Number.isFinite(Number(l.unit_price))||Number(l.unit_price)<0||l.unit_price==='');
      if(bad>=0){activeItem=bad;show(1);notice('Check the item description, quantity and selling price.');return false;}return true;
    }
    function validateAll(){
      if(!validFields(panels[0])){show(0);validFields(panels[0]);return false;}
      if(!validateItems())return false;
      if(!validFields(panels[2])){show(2);validFields(panels[2]);return false;}return true;
    }
    function renderNav(){
      nav.replaceChildren();if(step>0)nav.append(button('Back',async()=>show(step-1)));
      if(step<3){const next=button(['Continue to items','Continue to delivery','Review cart'][step],async()=>{
        if(step===0&&!validFields(panels[0]))return;
        if(step===0&&!await validCustomer())return;
        if(step===1&&(!validateItems()||activeItem>=0)){if(activeItem>=0)notice('Add this item to the cart before continuing.');return;}
        if(step===2&&!validFields(panels[2]))return;show(step+1);
      });next.hidden=step===1&&activeItem>=0;nav.append(next);}
      else {
        nav.append(button('Save draft',async()=>saveWorkflow(false)));
        if(!current||current.state==='DRAFT')nav.append(button('Issue quote',async()=>saveWorkflow(true)));
        else {nav.lastChild.textContent='Save changes';}
      }
    }
    async function readScreenshot(file,line){
      if(!file)return;if(file.size>2000000||!['image/png','image/jpeg'].includes(file.type))throw new Error('Choose a PNG or JPEG under 2 MB.');
      pendingReads++;try{const data=await new Promise((resolve,reject)=>{const reader=new FileReader();reader.onload=()=>resolve(reader.result);reader.onerror=()=>reject(new Error('Screenshot could not be read.'));reader.readAsDataURL(file);});line._preview=data;line._pendingScreenshot=data.split(',')[1];dirty=true;renderItems();notice('Screenshot ready. It will be uploaded when you save the quote.');}finally{pendingReads--;}
    }
    function renderItems(){
      itemList.replaceChildren();itemEditor.replaceChildren();itemList.hidden=activeItem>=0;itemEditor.hidden=activeItem<0;
      if(activeItem<0){
        if(!lines.length)itemList.append(n('p','Add each requested item to your cart.'));
        lines.forEach((line,index)=>{const card=cartLine(line),actions=n('div');actions.className='overseas-actions';actions.append(button(`Edit item ${index+1}`,async()=>{activeItem=index;renderItems();renderNav();}),button(`Remove item ${index+1}`,async()=>{lines.splice(index,1);dirty=true;renderItems();}));card.append(actions);itemList.append(card);});
        itemList.append(button('Add item',async()=>{lines.push(newLine());activeItem=lines.length-1;dirty=true;renderItems();renderNav();}));
        itemList.append(n('p',`${lines.length} item${lines.length===1?'':'s'} · Items subtotal ${money(lines.reduce((sum,l)=>sum+itemTotal(l),0))}`));return;
      }
      const line=lines[activeItem];itemEditor.append(n('h4',`Item ${activeItem+1}`));
      for(const [key,label,type]of [['description','Description','text'],['seller','Seller','text'],['product_url','Product link','url'],['options','Size / colour / options','text'],['quantity','Quantity','number'],['unit_price','Price each GYD (shipping included)','number']]){
        const f=input(label,val(line,key),type);if(['description','quantity','unit_price'].includes(key))f.field.required=true;
        if(type==='number'){f.field.min=key==='quantity'?'1':'0';f.field.max=key==='quantity'?'100000':'';f.field.step=key==='quantity'?'1':'0.01';}
        if(key==='seller'){f.field.maxLength=200;f.field.setAttribute('list','overseasSellers');const choices=n('datalist');choices.id='overseasSellers';fillSellers(choices);itemEditor.append(choices);}
        f.field.addEventListener('input',()=>{line[key]=f.field.value;dirty=true;});itemEditor.append(f.wrap);
      }
      const image=photo(line);if(image)itemEditor.append(image);
      const screenshot=input('Screenshot PNG / JPEG (under 2 MB)','','file');screenshot.field.accept='image/png,image/jpeg';screenshot.field.addEventListener('change',()=>readScreenshot(screenshot.field.files[0],line).catch(e=>notice(e.message)));itemEditor.append(screenshot.wrap);
      const paste=n('div','Paste a screenshot here (desktop browser)');paste.tabIndex=0;paste.className='empty';paste.addEventListener('paste',e=>{const file=Array.from(e.clipboardData?.items||[]).find(i=>i.type.startsWith('image/'))?.getAsFile();if(file){e.preventDefault();readScreenshot(file,line).catch(error=>notice(error.message));}});itemEditor.append(paste);
      const costs=n('details');costs.append(n('summary','Purchase cost reference (optional)'));
      for(const [key,label]of [['usd_cost','USD unit cost (optional reference)'],['exchange_rate','Exchange rate (reference only)']]){const f=input(label,val(line,key),'number');f.field.min=key==='exchange_rate'?'0.0001':'0';f.field.step=key==='exchange_rate'?'0.0001':'0.01';f.field.addEventListener('input',()=>{line[key]=f.field.value;dirty=true;});costs.append(f.wrap);}itemEditor.append(costs);
      itemEditor.append(button('Add to cart',async()=>{if(!validFields(itemEditor))return;activeItem=-1;renderItems();renderNav();}));
    }
    function renderReview(){
      review.replaceChildren();review.append(n('p',`Store: Deckers ${storeName()}`),n('h4',name.value),n('p',phone.value),button('Edit customer',async()=>show(0)));
      lines.forEach(line=>review.append(cartLine(line)));
      review.append(button('Edit items',async()=>{activeItem=-1;show(1);}));
      const subtotal=lines.reduce((sum,l)=>sum+itemTotal(l),0),summary=n('dl');summary.className='overseas-cart-totals';
      const totals=[['Items subtotal',money(subtotal)]];if(cents(shipping.value)>0)totals.push(['Extra shipping',money(cents(shipping.value))]);totals.push(['Total',money(subtotal+cents(shipping.value))]);
      for(const [label,value]of totals){summary.append(n('dt',label),n('dd',value));}review.append(summary,n('p','Shipping is included in the item prices.'));
      if(fields.expected_date.value)review.append(n('p',`Expected arrival: ${fields.expected_date.value}`));if(notes.value)review.append(n('p',notes.value));
      review.append(button('Edit delivery',async()=>show(2)),n('p','Review the customer, items and final total. Issuing the quote makes it available to the store registers; no payment is taken here.'));
    }
    function cleanLine(line){const {_preview,_pendingScreenshot,...saved}=line;return saved;}
    function payload(){const order={customer_id:customerId,notes:notes.value,lines:lines.map(cleanLine)};for(const [key,f]of Object.entries(fields))order[key]=f.value;return order;}
    async function saveWorkflow(issue){
      if(saving)return;if(pendingReads)throw new Error('Wait for the screenshot to finish loading.');if(!validateAll()||!await validCustomer())return;
      saving=true;const session=viewer;const controls=Array.from(editor.querySelectorAll('button,input,textarea'));controls.forEach(c=>{c.disabled=true;});
      try{
        let result=await send('SAVE',{...identity(),requestId:crypto.randomUUID(),order:payload(),reason:reason.field.value});
        if(viewer!==session)return;current=result.order;
        lines=lines.map((line,i)=>({...current.lines[i],...line,line_id:current.lines[i].line_id}));
        let uploaded=false;
        for(const line of lines)if(line._pendingScreenshot){const result=await send('UPLOAD',{...identity(),requestId:crypto.randomUUID(),base64:line._pendingScreenshot});if(viewer!==session)return;line.screenshot=result.reference;delete line._pendingScreenshot;uploaded=true;}
        if(uploaded){result=await send('SAVE',{...identity(),requestId:crypto.randomUUID(),order:payload(),reason:reason.field.value});if(viewer!==session)return;current=result.order;}
        if(issue){result=await send('ISSUE',{...identity(),requestId:crypto.randomUUID()});if(viewer!==session)return;current=result.order;}
        lines=current.lines.map(l=>({...l}));dirty=false;renderEditor(result,false,3);notice(issue?'Quote issued. The store can now collect payment.':'Quote saved.');
      }finally{saving=false;controls.forEach(c=>{c.disabled=false;});}
    }
    show(startStep);
  }
  function shipment(){const editor=document.getElementById('overseasEditor'),panel=n('div'),updates=[];panel.append(n('h4','Cumulative shipment quantities'));for(const l of current.lines){panel.append(n('strong',`${l.description} · ${l.quantity} ordered`));const update={line_id:l.line_id};for(const key of ['purchased','shipped','arrived']){const f=input(key[0].toUpperCase()+key.slice(1),l[key],'number');f.field.min='0';f.field.max=l.quantity;f.field.step='1';update[key]=f.field;panel.append(f.wrap);}updates.push(update);}const reason=input('Reason (required for corrections)');panel.append(reason.wrap,button('Save shipment quantities',async()=>{const lines=updates.map(u=>({line_id:u.line_id,purchased:Number(u.purchased.value),shipped:Number(u.shipped.value),arrived:Number(u.arrived.value)}));await mutate('SHIPMENT',{lines,reason:reason.field.value});}));editor.append(panel);panel.scrollIntoView({behavior:'smooth'});}
})();
