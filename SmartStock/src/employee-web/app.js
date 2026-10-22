'use strict';
const $=id=>document.getElementById(id);
let csrf='',viewer=null,target=null,preauth='',employeeOffset=0,sessionOffset=0,requestVersion=0;
let editing=null;
const editKeys=['clockIn','lunchStart','lunchEnd','breakStart','breakEnd','clockOut'];
const editInput=key=>$('edit'+key[0].toUpperCase()+key.slice(1));
const localTime=value=>value?value.replace(/(?:Z|[+-]\d{2}:\d{2})$/,''):null;
let activation=new URLSearchParams(location.hash.slice(1)).get('activate');
if(location.hash)history.replaceState(null,'',location.pathname+location.search);
function notice(text=''){$('notice').textContent=text;$('notice').hidden=!text;}
function resetPrivate(){window.dispatchEvent(new Event("employee-signed-out"));requestVersion++;viewer=null;target=null;csrf='';closeEditor();$('workspace').hidden=true;$('logout').hidden=true;$('identity').textContent='';$('employeeList').replaceChildren();$('sessions').replaceChildren();$('employeeName').textContent='Session history';$('employeeStatus').textContent='';}
function signedOut(){resetPrivate();$('loginPanel').hidden=false;$('loginForm').hidden=false;$('mfaForm').hidden=true;$('password').value='';$('code').value='';$('mfaQr').removeAttribute('src');$('mfaSecret').textContent='';preauth='';}
async function api(route,body={}){
  const response=await fetch(`./api/v1/${route}`,{method:'POST',credentials:'same-origin',headers:{'Content-Type':'application/json',...(csrf?{'X-CSRF-Token':csrf}:{})},body:JSON.stringify(body),cache:'no-store'});
  const result=await response.json();
  if(!response.ok||!result.ok){const error=new Error(result.error?.message||'Request failed. Please try again.');error.code=result.error?.code;
    if(response.status===401||response.status===403){signedOut();if(['DEVICE_REQUIRED','DEVICE_REVOKED'].includes(error.code))$('loginPanel').hidden=true;}
    throw error;
  }return result.data;
}
async function busy(button,work){button.disabled=true;try{notice();await work();}catch(e){notice(e.message||'Unable to connect. Please try again.');}finally{button.disabled=false;}}
function node(tag,text,className){const el=document.createElement(tag);if(text!==undefined)el.textContent=text;if(className)el.className=className;return el;}
async function enter(){
  const version=requestVersion,v=await api('bootstrap');if(version!==requestVersion)return;viewer=v;$('identity').textContent=v.name;$('loginPanel').hidden=true;$('activation').hidden=true;$('logout').hidden=false;$('workspace').hidden=false;
  window.dispatchEvent(new CustomEvent('employee-ready',{detail:v}));
  if(document.body.dataset.workspace==='overseas'){
    $('directory').hidden=true;$('history').hidden=true;$('back').hidden=true;
    if(!v.canOverseas)notice('Your account needs overseas-order access. Ask your administrator to assign the appropriate permission.');
    return;
  }
  $('directory').hidden=!v.canBrowse;$('history').hidden=true;$('back').hidden=!v.canBrowse;
  if(v.canBrowse)await employees(false);else await selectEmployee(v.userId);
}
async function employees(append){
  const version=++requestVersion,offset=append?employeeOffset:0;
  if(!append){$('employeeList').replaceChildren();$('moreEmployees').hidden=true;}
  const data=await api('employees',{search:$('search').value.trim(),includeInactive:$('includeInactive').checked,offset});
  if(version!==requestVersion||!viewer)return;
  for(const e of data.employees){const button=node('button',undefined,'employee');const info=node('span');info.append(node('strong',e.name),node('small',e.username));button.append(info,node('span',e.active?'Active':'Deactivated',`badge${e.active?'':' inactive'}`));button.addEventListener('click',()=>busy(button,()=>selectEmployee(e.userId)));$('employeeList').append(button);}
  if(!append&&!data.employees.length)$('employeeList').append(node('p','No employees match your search.','empty'));
  employeeOffset=offset+data.employees.length;$('moreEmployees').hidden=!data.hasMore;
}
async function selectEmployee(id){target=id;$('employeeName').textContent='Loading history…';$('employeeStatus').textContent='';$('directory').hidden=true;$('history').hidden=false;$('start').value='';$('end').value='';await sessions(false);}
function timestamp(value,timezone){if(!value)return '—';return new Intl.DateTimeFormat(undefined,{timeZone:timezone,month:'short',day:'numeric',year:'numeric',hour:'numeric',minute:'2-digit'}).format(new Date(value));}
function sessionCard(s){
  const card=node('article',undefined,'session'),top=node('div',undefined,'session-top'),heading=node('div');
  heading.append(node('h3',s.workDate),node('p',`${s.store||'Store unavailable'} · ${s.timezone}`,'muted'));top.append(heading);
  if(s.provisional)top.append(node('span','Open · provisional','badge inactive'));card.append(top);
  if(viewer?.canEdit){const edit=node('button','Edit','ghost');edit.setAttribute('aria-label',`Edit session ${s.workDate} #${s.clockId}`);edit.addEventListener('click',()=>openEditor(s));top.append(edit);}
  const times=node('dl',undefined,'timestamps');for(const [key,label]of [['clockIn','Clock in'],['lunchStart','Lunch start'],['lunchEnd','Lunch end'],['breakStart','Break start'],['breakEnd','Break end'],['clockOut','Clock out']]){const group=node('div');group.append(node('dt',label),node('dd',timestamp(s[key],s.timezone)));times.append(group);}card.append(times);
  const totals=node('div',undefined,'totals'),hours=node('div'),pay=node('div');hours.append(node('span','Worked hours'),node('strong',s.hours==null?'Unavailable':Number(s.hours).toFixed(2)));
  pay.append(node('span',s.provisional?'Calculated earnings · provisional':'Calculated earnings'),node('strong',s.calculatedPay==null?'Not allocated':new Intl.NumberFormat(undefined,{style:'currency',currency:'GYD'}).format(s.calculatedPay)));totals.append(hours,pay);card.append(totals);
  const flags=node('div',undefined,'flags');if(s.payNote)flags.append(node('p',s.payNote,'muted'));
  if(s.autoClockOut)flags.append(node('span',`Automatic clock-out${s.clockOutReview?' · '+s.clockOutReview.replaceAll('_',' ').toLowerCase():''}`,'flag'));
  if(s.autoBreakEnd)flags.append(node('span',`Automatic break end${s.breakEndReview?' · '+s.breakEndReview.replaceAll('_',' ').toLowerCase():''}`,'flag'));
  if(!s.clockIn)flags.append(node('span','Missing clock-in timestamp','flag'));
  if(s.lunchStart&&!s.lunchEnd)flags.append(node('span','Lunch end not recorded','flag'));
  if(s.lunchEnd&&!s.lunchStart)flags.append(node('span','Lunch start not recorded','flag'));
  if(s.breakStart&&!s.breakEnd)flags.append(node('span','Break end not recorded','flag'));
  if(s.breakEnd&&!s.breakStart)flags.append(node('span','Break start not recorded','flag'));
  if(flags.childNodes.length)card.append(flags);return card;
}
async function sessions(append){
  const version=++requestVersion,id=target,offset=append?sessionOffset:0,body={userId:id,offset};
  if($('start').value||$('end').value){body.start=$('start').value;body.end=$('end').value;}
  if(!append){$('sessions').replaceChildren();$('moreSessions').hidden=true;}
  const data=await api('history',body);if(version!==requestVersion||!viewer||id!==target)return;
  $('employeeName').textContent=data.employee.name;$('employeeStatus').textContent=`${data.employee.username}${data.employee.active?'':' · Deactivated'}`;
  $('start').value=data.start;$('end').value=data.end;
  for(const s of data.sessions)$('sessions').append(sessionCard(s));
  if(!append&&!data.sessions.length)$('sessions').append(node('p','No time-clock sessions in this period. Choose another date range to see older history.','empty'));
  sessionOffset=offset+data.sessions.length;$('moreSessions').hidden=!data.hasMore;
}
function closeEditor(){if($('sessionEditor').open)$('sessionEditor').close();$('editForm').reset();$('editError').hidden=true;editing=null;}
function openEditor(session){
  editing={session,initial:{}};
  for(const key of editKeys){const input=editInput(key);input.value=(localTime(session[key])||'').slice(0,19);editing.initial[key]=input.value;}
  $('editReason').value='';$('editError').hidden=true;$('editorZone').textContent=`${session.store||'Store'} · ${session.timezone}`;
  $('sessionEditor').showModal();editInput('clockIn').focus();
}
$('cancelEdit').addEventListener('click',closeEditor);
$('sessionEditor').addEventListener('cancel',event=>{if($('saveEdit').disabled)event.preventDefault();else closeEditor();});
$('editForm').addEventListener('submit',async event=>{
  event.preventDefault();if(!editing)return;
  const current=editing,correction={reason:$('editReason').value.trim()||'N/A'};
  // Preserve original subsecond precision for fields the user did not change.
  for(const key of editKeys){const value=editInput(key).value;correction[key]=value===current.initial[key]?localTime(current.session[key]):value||null;}
  $('saveEdit').disabled=true;$('cancelEdit').disabled=true;$('editError').hidden=true;
  try{
    await api('history/correct',{clockId:current.session.clockId,revision:current.session.revision,correction});
    closeEditor();await sessions(false);notice('Session corrected. Hours and calculated earnings refreshed.');
  }catch(e){
    if($('sessionEditor').open){$('editError').textContent=e.message;$('editError').hidden=false;if(e.code==='SESSION_CHANGED'){try{await sessions(false);}catch(refreshError){notice(refreshError.message);}}}
    else notice(e.message);
  }finally{$('saveEdit').disabled=false;$('cancelEdit').disabled=false;}
});
$('activate').addEventListener('click',()=>busy($('activate'),async()=>{await api('activate',{token:activation});activation=null;$('activation').hidden=true;signedOut();notice('Browser activated. Sign in with your employee account.');}));
$('loginForm').addEventListener('submit',event=>{event.preventDefault();busy($('loginForm').querySelector('button'),async()=>{
  const password=$('password').value;$('password').value='';const data=await api('auth/login',{identifier:$('identifier').value.trim(),password});
  if(data.mfaRequired){preauth=data.preauth;$('loginForm').hidden=true;$('mfaForm').hidden=false;$('mfaQr').hidden=!data.qrCode;if(data.qrCode)$('mfaQr').src=data.qrCode;$('mfaSecret').textContent=data.secret||'';$('code').focus();}
  else{csrf=data.csrfToken;await enter();}
});});
$('mfaForm').addEventListener('submit',event=>{event.preventDefault();busy($('mfaForm').querySelector('button'),async()=>{const code=$('code').value;$('code').value='';try{const data=await api('auth/verify',{preauth,code});csrf=data.csrfToken;preauth='';$('mfaQr').removeAttribute('src');$('mfaSecret').textContent='';await enter();}catch(e){signedOut();throw e;}});});
$('restartLogin').addEventListener('click',()=>signedOut());
$('logout').addEventListener('click',()=>busy($('logout'),async()=>{await api('auth/logout');signedOut();}));
$('searchForm').addEventListener('submit',event=>{event.preventDefault();busy($('searchForm').querySelector('button'),()=>employees(false));});
$('includeInactive').addEventListener('change',()=>busy($('searchForm').querySelector('button'),()=>employees(false)));
$('moreEmployees').addEventListener('click',()=>busy($('moreEmployees'),()=>employees(true)));
$('dates').addEventListener('submit',event=>{event.preventDefault();busy($('dates').querySelector('button'),()=>sessions(false));});
$('currentPeriod').addEventListener('click',()=>busy($('currentPeriod'),async()=>{$('start').value='';$('end').value='';await sessions(false);}));
$('moreSessions').addEventListener('click',()=>busy($('moreSessions'),()=>sessions(true)));
$('back').addEventListener('click',()=>{requestVersion++;target=null;$('sessions').replaceChildren();$('history').hidden=true;$('directory').hidden=false;});
window.addEventListener('hashchange',()=>{
  const token=new URLSearchParams(location.hash.slice(1)).get('activate');
  if(token){history.replaceState(null,'',location.pathname+location.search);signedOut();activation=token;$('loginPanel').hidden=true;$('activation').hidden=false;notice();}
});
(async()=>{try{
  if(document.body.dataset.workspace==='overseas'){
    const health=await fetch('./health',{cache:'no-store'});
    if(!health.ok){const result=await health.json();notice(result.error?.message||'The store gateway is unavailable.');return;}
  }
  if(activation){$('activation').hidden=false;return;}
  const data=await api('auth/session');csrf=data.csrfToken;await enter();
}catch(e){if(e.code==='SESSION_REQUIRED'||e.code==='SESSION_EXPIRED'){signedOut();notice();}else notice(e.message);}})();
