'use strict';
(() => {
  const $=id=>document.getElementById(id),money=value=>'G$'+Number(value||0).toLocaleString('en-GY',{maximumFractionDigits:0});
  const amountValue=input=>/^(?:\d+|\d{1,3}(?:,\d{3})+)$/.test(input.value.trim())?Number(input.value.replaceAll(',','')):NaN;
  let data=null,expanded='',dirty=false,pending=false,revision=0;
  const requests=new Map();
  const rowKey=row=>`${row.userId}:${row.start}:${row.end}`;
  const status=row=>Number(row.balance)<=0?'Paid':Number(row.paid)>0?'Part-paid':'Unpaid';
  const periodLabel=(start,end)=>{const a=new Date(start+'T12:00:00Z'),b=new Date(end+'T12:00:00Z'),month=date=>new Intl.DateTimeFormat('en',{month:'short',timeZone:'UTC'}).format(date);return a.getUTCFullYear()===b.getUTCFullYear()?`${month(a)} ${a.getUTCDate()}–${a.getUTCMonth()===b.getUTCMonth()?'':month(b)+' '}${b.getUTCDate()}, ${b.getUTCFullYear()}`:`${month(a)} ${a.getUTCDate()}, ${a.getUTCFullYear()}–${month(b)} ${b.getUTCDate()}, ${b.getUTCFullYear()}`;};
  function notice(text='',error=false){$('payrollStatus').textContent=text;$('payrollStatus').classList.toggle('error',error);}
  async function api(route,body){const response=await fetch('/employees/api/v1/payroll/'+route,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(body||{})});const packet=await response.json();if(!response.ok||!packet.ok)throw new Error(packet.error?.message||'Payroll could not be loaded.');return packet.data;}
  function element(tag,classes,text){const node=document.createElement(tag);if(classes)node.className=classes;if(text!==undefined)node.textContent=text;return node;}
  function field(label,input){const wrap=element('label','',label);wrap.append(input);return wrap;}
  function selectedRows(){return (data?.rows||[]).filter(row=>`${row.start}:${row.end}`===$('payrollPeriod').value&&(!$('payrollStore').value||$('payrollStore').value==='0'||row.stores.includes(Number($('payrollStore').value))));}
  function paymentForm(row){
    const form=element('form','payroll-payment'),entry=element('div','payroll-entry');
    const amount=element('input','payroll-amount');amount.type='text';amount.max=String(Math.max(0,Number(row.cashBalance??row.balance)));amount.required=true;amount.inputMode='numeric';amount.value=Number(amount.max).toLocaleString('en-GY');amount.setAttribute('aria-label','Amount paid in GYD');
    const date=element('input');date.type='date';date.required=true;date.value=data.today;date.max=data.today;
    entry.append(field('Amount paid (G$)',amount),field('Date',date));form.append(entry);
    const details=element('details','payroll-method');details.append(element('summary','','Cash in hand'));
    const method=element('select');for(const [value,label]of [['CASH','Cash in hand'],['BANK','Bank account']]){const option=element('option','',label);option.value=value;method.append(option);}
    const reference=element('input');reference.maxLength=500;const referenceField=field('Bank reference',reference);referenceField.hidden=true;
    details.append(field('Payment method',method),referenceField);
    const button=element('button','payroll-save','Save payment');button.type='submit';const preview=element('p','payroll-preview'),footer=element('div','payroll-footer');footer.append(preview,details);form.append(button,footer);
    const due=()=>Number((method.value==='BANK'?row.bankBalance:row.cashBalance)??row.balance);
    const limit=()=>method.value==='CASH'?Math.max(due(),Number(row.balance)):due();
    const update=()=>{amount.max=String(limit());const value=amountValue(amount),remaining=due()-value;preview.textContent=!Number.isSafeInteger(value)||value<=0?'Enter the exact amount paid in whole GYD.':value>limit()?'Amount exceeds the remaining balance.':remaining<0?`Balance after payment: G$0 · Paid (${money(-remaining)} above rounded cash amount)`: `Balance after payment: ${money(remaining)} · ${remaining===0?'Paid':'Part-paid'}`;};update();
    amount.addEventListener('blur',()=>{const value=amountValue(amount);if(Number.isSafeInteger(value))amount.value=value.toLocaleString('en-GY');});
    form.addEventListener('input',()=>{dirty=true;update();});method.onchange=()=>{referenceField.hidden=method.value!=='BANK';reference.required=method.value==='BANK';details.querySelector('summary').textContent=method.value==='BANK'?'Bank account':'Cash in hand';dirty=true;update();};
    form.onsubmit=async event=>{
      event.preventDefault();if(pending)return;
      const value=amountValue(amount);if(!Number.isSafeInteger(value)||value<=0||value>limit()){notice('Enter a whole GYD amount within the remaining balance.',true);return;}
      const payload={userId:row.userId,start:row.start,end:row.end,amount:value,date:date.value,expectedPaid:row.paid,method:method.value,reference:reference.value.trim()};
      const canonical=JSON.stringify(payload),previous=requests.get(rowKey(row));const key=previous?.canonical===canonical?previous.key:crypto.randomUUID();requests.set(rowKey(row),{canonical,key});
      pending=true;button.disabled=true;button.textContent='Saving…';notice();
      try{await api('pay',{...payload,idempotencyKey:key});dirty=false;requests.delete(rowKey(row));await load(false);notice(`Recorded ${money(value)} for ${row.name}.`);}
      catch(error){notice(error.message+' Your payment details are still here; retry only when ready.',true);}
      finally{pending=false;button.disabled=false;button.textContent='Save payment';}
    };return form;
  }
  function render(){
    const rows=selectedRows(),host=$('payrollEmployees');host.replaceChildren();
    for(const row of rows){
      const card=element('section','payroll-employee'),head=element('button','payroll-person'),photo=element('span','payroll-photo',row.name.trim().split(/\s+/).slice(0,2).map(word=>word[0]).join(''));
      head.type='button';head.setAttribute('aria-expanded',String(expanded===rowKey(row)));head.setAttribute('aria-label',row.name+', '+status(row)+', '+(Number(row.balance)>0?'record payment':'payment summary'));
      if(/^data:image\/(jpeg|png);base64,/.test(row.photo||'')){const initials=photo.textContent,img=element('img');img.alt='';img.onerror=()=>{photo.textContent=initials;};photo.replaceChildren(img);img.src=row.photo;}
      const person=element('span','payroll-name');person.append(element('strong','',row.name),element('span','payroll-hours',Number(row.hours).toLocaleString()+' hrs'));
      head.append(photo,person,element('span','payroll-badge'+(status(row)==='Part-paid'?' partial':''),status(row)),element('span','payroll-chevron'));
      head.onclick=()=>{if(pending)return;if(dirty&&!window.confirm('Discard unsaved payment details?'))return;dirty=false;expanded=expanded===rowKey(row)?'':rowKey(row);render();};card.append(head);
      const values=element('dl','payroll-values');for(const [label,value]of [['Total pay',row.total],['Paid',row.paid],['Balance',row.balance]]){const group=element('div',label==='Balance'?'balance':'');group.append(element('dt','',label),element('dd','',money(value)));values.append(group);}card.append(values);
      if(expanded===rowKey(row)){
        if(Number(row.balance)>0)card.append(paymentForm(row));
        else card.append(element('p','payroll-preview payroll-paid','Paid in full. No balance remaining.'));
      }host.append(card);
    }
    const totals=rows.reduce((sum,row)=>({hours:sum.hours+Number(row.hours),total:sum.total+Number(row.total),paid:sum.paid+Number(row.paid),balance:sum.balance+Number(row.balance)}),{hours:0,total:0,paid:0,balance:0});
    $('payrollTotals').replaceChildren(element('strong','',`${rows.length} employees · Total hours: ${totals.hours.toLocaleString()} hrs`),element('span','',`Total pay: ${money(totals.total)} · Paid: ${money(totals.paid)} · Balance: ${money(totals.balance)}`));$('payrollTotals').hidden=!rows.length;
    $('payrollScope').hidden=$('payrollStore').value==='0'||!rows.length;if(!rows.length)notice('No payroll records for this store and pay period.');
  }
  async function load(initial){
    const version=++revision;notice('Loading payroll…');$('payrollRefresh').disabled=true;
    try{
      const next=await api('dashboard',{detectStore:initial,...($('payrollPeriod').value?{period:$('payrollPeriod').value}:{})});if(version!==revision)return;data=next;
      const oldStore=initial?String(data.defaultStore||0):$('payrollStore').value,oldPeriod=$('payrollPeriod').value;
      $('payrollStore').replaceChildren();for(const store of [{id:0,name:'All stores'},...data.stores]){const option=element('option','',store.name);option.value=String(store.id);$('payrollStore').append(option);}$('payrollStore').value=oldStore;if(!$('payrollStore').value)$('payrollStore').value='0';
      const periods=data.periods||[...new Set(data.rows.map(row=>`${row.start}:${row.end}`))].sort().reverse();$('payrollPeriod').replaceChildren();for(const value of periods){const [start,end]=value.split(':');const option=element('option','',periodLabel(start,end));option.value=value;$('payrollPeriod').append(option);}
      if(periods.includes(oldPeriod))$('payrollPeriod').value=oldPeriod;else{$('payrollPeriod').value=periods.find(value=>{const[start,end]=value.split(':');return start<=data.today&&end>=data.today;})||periods[0]||'';}
      if(initial)expanded=rowKey(selectedRows().find(row=>Number(row.balance)>0)||{userId:0,start:'',end:''});notice();render();
    }catch(error){notice(error.message,true);}finally{if(version===revision)$('payrollRefresh').disabled=false;}
  }
  for(const id of ['payrollStore','payrollPeriod']){const select=$(id);let previous=select.value;select.addEventListener('focus',()=>previous=select.value);select.onchange=()=>{if(pending||(dirty&&!window.confirm('Discard unsaved payment details?'))){select.value=previous;return;}previous=select.value;dirty=false;expanded='';notice();if(id==='payrollPeriod')load(false);else render();};}
  $('payrollRefresh').onclick=()=>{if(!pending&&(!dirty||window.confirm('Discard unsaved payment details?'))){dirty=false;load(false);}};
  window.addEventListener('beforeunload',event=>{if(dirty||pending){event.preventDefault();event.returnValue='';}});
  load(true);
})();
