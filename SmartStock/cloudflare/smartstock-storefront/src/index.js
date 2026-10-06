const json = (value, status = 200) => new Response(JSON.stringify(value), {status, headers:{'Content-Type':'application/json','Cache-Control':'no-store','X-Content-Type-Options':'nosniff'}});
const publicSnapshotKeys=['locationId','capturedAt','settings','unavailableServices','store','branding','tax','products','projects'];
const publicSyncSnapshot=snapshot=>{
  const selected=Object.fromEntries(publicSnapshotKeys.filter(key=>Object.hasOwn(snapshot,key)).map(key=>[key,snapshot[key]]));
  if(selected.settings)selected.settings={...selected.settings,enabled:false};
  return selected;
};
export function configuredOrigins(env){
  const list=JSON.parse(env.ORIGINS_JSON),seen=new Set(),instances=new Set();
  if(!Array.isArray(list)||!list.length||list.length>32)throw Error('Invalid storefront origin configuration');
  for(const o of list){
    const url=new URL(o.url);
    if(!Number.isInteger(o.storeId)||o.storeId<1||seen.has(o.storeId)||! /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/.test(o.instanceId||'')||instances.has(o.instanceId)
      ||url.protocol!=='https:'||url.username||url.password||url.search||url.hash||url.pathname!=='/')throw Error('Invalid storefront origin configuration');
    seen.add(o.storeId);instances.add(o.instanceId);o.url=url.origin;
  }
  return list;
}
const origins=configuredOrigins;
function originKey(env,origin){const key=JSON.parse(env.ORIGIN_KEYS_JSON||'{}')[origin.instanceId];if(typeof key!=='string'||key.length<32)throw Error('Storefront origin secret is missing');return key;}
function originHeaders(env,origin){return {'X-Storefront-Key':originKey(env,origin),'X-Storefront-Instance':origin.instanceId};}
const stub = (env,id) => env.STORES.get(env.STORES.idFromName(String(id)));
class RequestFailure extends Error {constructor(message,status){super(message);this.status=status;}}
export async function readBoundedBody(request,limit){
  if(Number(request.headers.get('Content-Length'))>limit)throw new RequestFailure('Request too large',413);
  if(!request.body)return '';
  const reader=request.body.getReader(),chunks=[];let size=0;
  try{while(true){const {done,value}=await reader.read();if(done)break;size+=value.byteLength;if(size>limit){await reader.cancel();throw new RequestFailure('Request too large',413);}chunks.push(value);}}
  finally{reader.releaseLock();}
  const bytes=new Uint8Array(size);let offset=0;for(const chunk of chunks){bytes.set(chunk,offset);offset+=chunk.byteLength;}
  try{return new TextDecoder('utf-8',{fatal:true}).decode(bytes);}catch{throw new RequestFailure('Invalid UTF-8 request',400);}
}
function parseObject(raw){try{const value=JSON.parse(raw);if(!value||typeof value!=='object'||Array.isArray(value))throw Error();return value;}catch{throw new RequestFailure('Invalid JSON object',400);}}
export function originHealth(origin,selected,response,latency,now=Date.now()){
  const freshness=Number(response.snapshots?.[selected]||0);
  const usable=Number.isFinite(freshness)&&freshness>0&&freshness<=now+60000;
  return {ok:response.ok===true&&response.version===1&&response.locationId===origin.storeId&&!!origin.instanceId&&response.instanceId===origin.instanceId&&(origin.storeId===selected||usable),freshness:usable?freshness:0,latency};
}
const digest = async s => [...new Uint8Array(await crypto.subtle.digest('SHA-256',new TextEncoder().encode(s)))].map(x=>x.toString(16).padStart(2,'0')).join('');
export function sessionCookie(header){return (header||'').split(';').map(x=>x.trim()).find(x=>x.startsWith('__Host-deckers='))||'';}
const canonical = value => JSON.stringify(value,(_,v)=>v&&typeof v==='object'&&!Array.isArray(v)?Object.fromEntries(Object.keys(v).sort().map(k=>[k,v[k]])):v);
export function sameOrderContract(a,b){return ['auth_id','location_id','customer_uuid','request_hash','source_server','total','quote'].every(key=>canonical(a[key])===canonical(b[key]));}
// KV values have a per-value limit even on SQLite-backed Durable Objects.
// Catalogs include private customer linking records and may exceed that limit.
async function readSnapshot(storage,prefix='snapshot'){
  const count=await storage.get(prefix+':parts');let snapshot;
  if(count===undefined)snapshot=await storage.get(prefix);
  else {let text='';for(let i=0;i<count;i++)text+=await storage.get(prefix+':part:'+i);snapshot=JSON.parse(text);}
  // Heartbeats update freshness without rewriting the large catalog. Checkout copies stay immutable.
  if(snapshot&&prefix==='snapshot'){const meta=await storage.get('snapshot:meta');if(meta)snapshot.capturedAt=meta.capturedAt;}
  return snapshot;
}
async function writeSnapshot(storage,snapshot,prefix='snapshot',meta){
  const text=JSON.stringify(snapshot),count=Math.ceil(text.length/24000);
  const persist=async tx=>{const oldCount=await tx.get(prefix+':parts')||0;
    for(let i=0;i<count;i++)await tx.put(prefix+':part:'+i,text.slice(i*24000,(i+1)*24000));
    for(let i=count;i<oldCount;i++)await tx.delete(prefix+':part:'+i);
    await tx.put({...(meta?{'snapshot:meta':meta}:{}),[prefix+':parts']:count,...(prefix==='snapshot'?{updated:Date.now()}:{})});await tx.delete(prefix);
  };
  if(storage.transaction)await storage.transaction(persist);else await persist(storage);
}
async function releaseCheckoutSnapshot(storage,id){
  const prefix='jobSnapshot:'+id;
  const remove=async tx=>{const count=await tx.get(prefix+':parts')||0;for(let i=0;i<count;i++)await tx.delete(prefix+':part:'+i);await tx.delete(prefix+':parts');await tx.delete(prefix);};
  if(storage.transaction)await storage.transaction(remove);else await remove(storage);
}
export function rankOrigins(list, selected, health) {
  return list.filter(x=>health[x.storeId]?.ok).sort((a,b)=>(b.storeId===selected)-(a.storeId===selected) || (health[b.storeId].freshness-health[a.storeId].freshness) || (health[a.storeId].latency-health[b.storeId].latency));
}
export function selectedStoreId(data,url,configured){
  const projectStore=/^\/shop\/projects\/([1-9]\d*)\/[0-9a-f-]{36}\/?$/i.exec(url.pathname)?.[1];
  return Number(data.storeId||url.searchParams.get('storeId')||projectStore||configured[0]?.storeId);
}
export function pendingQuantities(orders,snapshot) {
  const seen=new Set((snapshot.orders||[]).map(x=>x.id)), result={};
  for(const order of orders) if(!seen.has(order.order_id) && !['CANCELLED','EXPIRED'].includes(order.status))
    for(const line of order.quote.lines) result[line.id]=(result[line.id]||0)+line.quantity;
  return result;
}
async function healthy(env, selected) {
  const list=origins(env), health={};
  await Promise.all(list.map(async o=>{const start=Date.now();try{
    const r=await fetch(o.url+'/shop/health',{headers:originHeaders(env,o),signal:AbortSignal.timeout(2500)});
    const b=await r.json();health[o.storeId]=originHealth(o,selected,b,Date.now()-start);if(!r.ok)health[o.storeId].ok=false;
  }catch{health[o.storeId]={ok:false};}}));
  return rankOrigins(list,selected,health);
}
async function proxy(request, origin, env, body) {
  const url=new URL(request.url);const headers=new Headers(request.headers);
  if(url.pathname==='/shop/api/v1/account'){
    const sessionUrl=new URL(url);sessionUrl.pathname='/shop/api/v1/auth/session';
    const session=await proxy(new Request(sessionUrl,{method:request.method,headers:request.headers}),origin,env,body);
    if(!session.ok)return session;
  }
  for(const name of [...headers.keys()]) if(name.startsWith('x-storefront-')&&!['x-storefront-request'].includes(name))headers.delete(name);
  for(const [name,value] of Object.entries(originHeaders(env,origin)))headers.set(name,value);headers.set('X-Real-IP',request.headers.get('CF-Connecting-IP')||'unknown');
  headers.delete('Host');
  const response=await fetch(origin.url+url.pathname+url.search,{method:request.method,headers,body:request.method==='GET'?undefined:body??await request.text(),redirect:'manual',signal:AbortSignal.timeout(20000)});
  if(response.ok&&url.pathname.startsWith('/shop/api/v1/auth/')){
    const route=url.pathname.split('/auth/')[1],registry=stub(env,'session-revocations');
    const cookieHash=await digest(sessionCookie(request.headers.get('Cookie')));
    if(route==='logout')await registry.fetch('https://coordinator/revoke',{method:'POST',body:JSON.stringify({cookieHash})});
    if(['reset','verify'].includes(route)){
      const session=await response.clone().json();
      await registry.fetch('https://coordinator/revoke',{method:'POST',body:JSON.stringify({id:session.id,before:session.issuedAt})});
    }
    if(route==='session'){
      const session=await response.clone().json();
      const check=await registry.fetch('https://coordinator/revoked',{method:'POST',body:JSON.stringify({cookieHash,id:session.id,issuedAt:session.issuedAt})});
      if((await check.json()).revoked)return json({message:'Sign in again to continue.'},401);
    }
  }
  return response;
}
export default {
  async fetch(request,env) {
    try {
      const url=new URL(request.url);
      const browseOnly=env.BROWSE_ONLY!=='false';
      if(url.protocol==='http:'||(env.REDIRECT_HOST&&url.hostname===env.REDIRECT_HOST&&env.CANONICAL_HOST)){
        // Redirect navigation only; never forward account/order POST bodies across hosts.
        if(!['GET','HEAD'].includes(request.method))return json({message:'Use the main website address.'},421);
        const destination=new URL('https://'+(env.CANONICAL_HOST||url.host));
        destination.pathname=url.pathname;destination.search=url.search;
        return Response.redirect(destination.toString(),308);
      }
      const configured=origins(env);
      if(url.pathname==='/shop/internal/sync'){
        const id=Number(url.searchParams.get('storeId')),origin=configured.find(x=>x.storeId===id);
        if(!origin||request.method!=='POST'||request.headers.get('X-Storefront-Instance')!==origin.instanceId||request.headers.get('X-Storefront-Key')!==originKey(env,origin))return json({message:'Forbidden'},403);
        // Keep large JSON processing outside the free Worker's 10 ms CPU budget.
        return stub(env,'sync:'+id).fetch(request);
      }
      // Internal origin endpoints are never available through the public proxy.
      if(url.pathname.startsWith('/shop/internal/'))return json({message:'Not found'},404);
      if(!url.pathname.startsWith('/shop'))return Response.redirect(url.origin+'/shop/',302);
      if(browseOnly&&url.pathname.startsWith('/shop/api/v1/')&&!['/shop/api/v1/stores','/shop/api/v1/catalog','/shop/api/v1/custom-order-private'].includes(url.pathname))return json({message:'Online accounts and requests are not available yet. Browse the Deckers website or contact a store.'},503);
      if(url.pathname==='/shop/api/v1/stores'){
        const stores=[];for(const o of configured){const meta=await(await stub(env,o.storeId).fetch('https://coordinator/meta')).json();if(meta.store)stores.push({...meta.store,capturedAt:meta.capturedAt});}return json({stores});
      }
      if(request.method==='POST' && (request.headers.get('Origin')!==url.origin || request.headers.get('X-Storefront-Request')!=='same-origin'))return json({message:'Invalid origin'},403);
      let body=request.method==='POST'?await readBoundedBody(request,url.pathname==='/shop/api/v1/custom-quote-file'?6100000:url.pathname==='/shop/api/v1/custom-order-private'?1500000:65536):undefined;
      let data=body?parseObject(body):{};for(const key of Object.keys(data))if(key.startsWith('_'))delete data[key];
      const selected=selectedStoreId(data,url,configured);
      if(!configured.some(x=>x.storeId===selected))return json({message:'Choose a store'},400);
      if(['/shop/api/v1/custom-quote','/shop/api/v1/custom-quote-file','/shop/api/v1/quote-template','/shop/api/v1/proof-file','/shop/api/v1/proof-decision','/shop/api/v1/favorite','/shop/api/v1/favorite-state','/shop/api/v1/custom-order-private'].includes(url.pathname)){
        const primary=configured.find(x=>x.storeId===selected);
        for(const origin of await healthy(env,selected))if(origin.storeId===primary.storeId){
          try{return await proxy(request,origin,env,JSON.stringify(data));}catch{}
        }
        return json({message:'This store is temporarily unavailable for custom requests. Please retry shortly.'},503);
      }
      if(url.pathname.endsWith('/checkout')||url.pathname.endsWith('/quote'))return stub(env,selected).fetch(new Request(request,{body:JSON.stringify(data)}));
      for(const origin of await healthy(env,selected)){try{const response=await proxy(request,origin,env,body?JSON.stringify(data):undefined);if(response.status<500){if(browseOnly&&url.pathname==='/shop/api/v1/catalog'&&response.ok){const catalog=await response.json();catalog.browseOnly=true;if(catalog.settings)catalog.settings.enabled=false;catalog.products=(catalog.products||[]).map(product=>({...product,canOrder:false}));if(catalog.campaign?.topic==='3D Printing')catalog.campaign={...catalog.campaign,description:'Discover what Deckers can create with 3D printing. Ask your store about current availability.',steps:'Bring your idea · Discuss the details · Plan the print',primary:'Find your store',primaryAction:'START'};return json(catalog);}return response;}}catch{}}
      return json({message:'Stores are temporarily unavailable. Your bag is saved.'},503);
    }catch(error){if(error instanceof RequestFailure)return json({message:error.message},error.status);return json({message:'The request could not be completed. Please retry.'},503);}
  }
};

export class StoreCoordinator {
  constructor(state,env){this.state=state;this.env=env;this.tail=Promise.resolve();}
  alarm(){const run=this.tail.then(async()=>{try{await this.reconcile();}catch{await this.state.storage.setAlarm(Date.now()+30000);}});this.tail=run.catch(()=>{});return run;}
  async reconcile(){
    const storage=this.state.storage;let pending=false;
    if(this.env.BROWSE_ONLY!=='false'){
      for(const [,job] of await storage.list({prefix:'job:'}))if(!job.result&&!job.failure){await storage.setAlarm(Date.now()+300000);break;}
      return;
    }
    for(const [key,job] of await storage.list({prefix:'job:'})){
      if(job.result||job.failure){await releaseCheckoutSnapshot(storage,job.body.orderId);continue;}
      // Absence alone is insufficient: each attempted origin must durably fence late
      // requests before a checkout can be declared unaccepted and another allowed.
      let rejected=0,accepted;
      for(const attempt of job.attempted||[]){
        const origin=origins(this.env).find(o=>o.storeId===attempt.storeId&&o.instanceId===attempt.instanceId);if(!origin)continue;
        try{
          const response=await fetch(origin.url+'/shop/internal/resolve-command',{method:'POST',headers:{'Content-Type':'application/json',...originHeaders(this.env,origin)},body:JSON.stringify({orderId:job.body.orderId,storeId:job.body.storeId}),signal:AbortSignal.timeout(15000)});
          if(!response.ok)continue;const result=await response.json();
          if(result.order&&result.order.order_id===job.body.orderId&&result.order.location_id===job.body.storeId){accepted=result.order;break;}if(result.rejected===true)rejected++;
        }catch{}
      }
      if(accepted){job.result=accepted;await storage.put({[key]:job,['order:'+accepted.order_id]:accepted});await releaseCheckoutSnapshot(storage,job.body.orderId);}
      else if(rejected===(job.attempted||[]).length){job.failure={message:'The interrupted checkout was not accepted. Review your bag and confirm a new order.',checkoutClosed:true};await storage.put(key,job);await releaseCheckoutSnapshot(storage,job.body.orderId);}
      else pending=true;
    }
    if(pending)await storage.setAlarm(Date.now()+30000);
  }
  fetch(request){if(['/meta','/state'].includes(new URL(request.url).pathname))return this.handle(request);const run=this.tail.then(()=>this.handle(request));this.tail=run.catch(()=>{});return run;}
  async handle(request){
    const url=new URL(request.url),storage=this.state.storage;
    if(url.pathname==='/shop/internal/sync')return synchronize(request,this.env);
    if(url.pathname==='/meta')return snapshotMeta(storage);
    if(url.pathname==='/revoke'){
      const body=await request.json();
      if(body.cookieHash)await storage.put('revoked-cookie:'+body.cookieHash,Date.now()+3600000);
      if(body.id&&Number.isFinite(body.before))await storage.put('revoked-user:'+body.id,Math.max(body.before,await storage.get('revoked-user:'+body.id)||0));
      return json({ok:true});
    }
    if(url.pathname==='/revoked'){
      const body=await request.json(),until=await storage.get('revoked-cookie:'+body.cookieHash),before=await storage.get('revoked-user:'+body.id);
      return json({revoked:!!(until>Date.now()||before&&(!body.issuedAt||body.issuedAt<before))});
    }
    if(url.pathname==='/snapshot')return acceptSnapshot(storage,request);
    if(url.pathname==='/enrollment'){
      const {source,enrollment}=await request.json(),key='enrollment:'+enrollment.enrollment_id,old=await storage.get(key);
      if(!old&&source!==enrollment.source_server&&source!==enrollment.location_id)return json({message:'Invalid enrolling server.'},403);
      if(old&&['auth_id','location_id','email'].some(k=>old[k]!==enrollment[k]))return json({message:'Account enrollment identity changed.'},409);
      if(!old||source===enrollment.location_id&&enrollment.revision>old.revision)await storage.put(key,enrollment);
      return json({ok:true});
    }
    if(url.pathname==='/event'){
      const {source,order}=await request.json(),old=await storage.get('order:'+order.order_id);
      if(!old&&source!==order.source_server&&source!==order.location_id)return json({message:'Invalid accepting server.'},403);
      if(old&&!sameOrderContract(old,order))return json({message:'Confirmed order identity and quote are immutable.'},409);
      // After first acceptance, only the fulfillment store may advance lifecycle revisions.
      if(!old || source===order.location_id&&order.revision>old.revision)await storage.put('order:'+order.order_id,order);
      const job=await storage.get('job:'+order.order_id);
      if(job&&!job.result){job.result=await storage.get('order:'+order.order_id);await storage.put('job:'+order.order_id,job);await releaseCheckoutSnapshot(storage,order.order_id);}
      return json({ok:true});
    }
    const snapshot=await readSnapshot(storage);
    if(url.pathname==='/state'){
      if(url.searchParams.get('public')==='1')return json({snapshot:snapshot?publicSyncSnapshot(snapshot):undefined,orders:[],enrollments:[]});
      return json({snapshot,orders:[...(await storage.list({prefix:'order:'})).values()],enrollments:[...(await storage.list({prefix:'enrollment:'})).values()]});
    }
    if(!snapshot||!snapshot.settings.enabled)return json({message:'This store is not accepting online orders yet.'},503);
    const body=await request.json(),isCheckout=url.pathname.endsWith('/checkout');
    if(!request.headers.get('Cookie')||!request.headers.get('X-CSRF-Token'))return json({message:'Sign in to continue.'},401);
    const candidates=await healthy(this.env,body.storeId);
    const sessionUrl=new URL(request.url);sessionUrl.pathname='/shop/api/v1/auth/session';let identity;
    for(const origin of candidates){try{
      const checked=await proxy(new Request(sessionUrl,{method:request.method,headers:request.headers}),origin,this.env,JSON.stringify({storeId:body.storeId}));
      if(checked.status>=500)continue;if(!checked.ok)return checked;
      const session=await checked.json();if(!session.id||session.csrf!==request.headers.get('X-CSRF-Token'))return json({message:'Refresh the page before continuing.'},403);
      identity=session.id;break;
    }catch{}}
    if(!identity)return json({message:'Sign-in verification is temporarily unavailable.'},503);
    let job;
    if(isCheckout){
      if(!/^[0-9a-f-]{36}$/i.test(body.orderId||''))return json({message:'Invalid order identifier.'},400);
      const hash=await digest(JSON.stringify({identity,storeId:body.storeId,lines:body.lines,expectedTotal:body.expectedTotal}));
      job=await storage.get('job:'+body.orderId);
      if(job&&job.hash!==hash)return json({message:'Checkout changed. Refresh your bag before retrying.'},409);
      if(!job){
        const unresolved=[...(await storage.list({prefix:'job:'})).values()].some(value=>!value.result&&!value.failure);
        if(unresolved)return json({message:'A previous checkout is still being reconciled. Please retry shortly.'},503);
      }
      if(job?.result)return json(job.result);
      if(job?.failure)return json(job.failure,409);
      if(job?.uncertain||job?.attempted?.length)return json({message:'Your checkout is being reconciled. Your bag is saved; retry shortly.'},503);
      if(!job){job={hash,attempted:[],body:{...body,_pending:pendingQuantities([...(await storage.list({prefix:'order:'})).values()],snapshot)}};await writeSnapshot(storage,{...snapshot,receipts:[]},'jobSnapshot:'+body.orderId);await storage.setAlarm(Date.now()+30000);await storage.put('job:'+body.orderId,job);}
    }
    const payload=job?{...job.body,_snapshot:await readSnapshot(storage,'jobSnapshot:'+body.orderId)}:{...body,_snapshot:{...snapshot,receipts:[]},_pending:pendingQuantities([...(await storage.list({prefix:'order:'})).values()],snapshot)};
    for(const origin of candidates){try{
      if(isCheckout&&!(job.attempted||=[]).some(x=>x.storeId===origin.storeId&&x.instanceId===origin.instanceId)){job.attempted.push({storeId:origin.storeId,instanceId:origin.instanceId});await storage.put('job:'+body.orderId,job);}
      const response=await proxy(request,origin,this.env,JSON.stringify(payload));
      if(response.status>=500){if(isCheckout){job.uncertain=true;await storage.put('job:'+body.orderId,job);return json({message:'Your checkout is being reconciled. Your bag is saved; retry shortly.'},503);}continue;}
      if(isCheckout&&response.ok){const order=await response.json();job.result=order;await storage.put({['job:'+body.orderId]:job,['order:'+order.order_id]:order});await releaseCheckoutSnapshot(storage,body.orderId);return json(order);}
      if(isCheckout&&response.status<500){
        if(job.uncertain)return json({message:'Your earlier checkout is being reconciled. Keep this order identifier and retry shortly.'},503);
        await storage.delete('job:'+body.orderId);
        await releaseCheckoutSnapshot(storage,body.orderId);
      }
      return response;
    }catch{if(isCheckout){job.uncertain=true;await storage.put('job:'+body.orderId,job);return json({message:'Your checkout is being reconciled. Your bag is saved; retry shortly.'},503);}}}
    return json({message:'Checkout is temporarily unavailable. Retry with the same bag; your order will not be duplicated.'},503);
  }
}


// Internal binding only: recheck the registered instance before reading any body.
async function synchronize(request,env){
 try{const url=new URL(request.url),configured=origins(env),browseOnly=env.BROWSE_ONLY!=='false';

    const id=Number(url.searchParams.get('storeId')),origin=configured.find(x=>x.storeId===id);
    if(!origin||request.method!=='POST'||request.headers.get('X-Storefront-Instance')!==origin.instanceId||request.headers.get('X-Storefront-Key')!==originKey(env,origin))return json({message:'Forbidden'},403);
    const raw=await readBoundedBody(request,16000000);
    const body=parseObject(raw);if(body.snapshot?.locationId!==id)return json({message:'Store mismatch'},400);
    if(browseOnly&&(!Array.isArray(body.events||[])||!Array.isArray(body.enrollments||[])||(body.events||[]).length||(body.enrollments||[]).length
      ||Object.keys(body.snapshot).some(key=>!publicSnapshotKeys.includes(key))))return json({message:'Customer synchronization is disabled during browse-only launch.'},409);
    await stub(env,id).fetch('https://coordinator/snapshot',{method:'POST',body:JSON.stringify(browseOnly?publicSyncSnapshot(body.snapshot):body.snapshot)});
    for(const event of body.events||[]){const order=event.payload;if(!configured.some(x=>x.storeId===order.location_id))return json({message:'Unknown fulfillment store'},400);
      const accepted=await stub(env,order.location_id).fetch('https://coordinator/event',{method:'POST',body:JSON.stringify({source:id,order})});
      if(!accepted.ok)return json({message:'Order handoff was rejected. Reconcile this order before acknowledging events.'},409);}
    for(const enrollment of body.enrollments||[]){
      if(!configured.some(x=>x.storeId===enrollment.location_id))return json({message:'Unknown account store'},400);
      const accepted=await stub(env,enrollment.location_id).fetch('https://coordinator/enrollment',{method:'POST',body:JSON.stringify({source:id,enrollment})});
      if(!accepted.ok)return json({message:'Account handoff was rejected.'},409);
    }
    const snapshots=[], orders=[], enrollments=[];
    for(const store of configured){const state=await (await stub(env,store.storeId).fetch('https://coordinator/state'+(browseOnly?'?public=1':''))).json();if(state.snapshot)snapshots.push(browseOnly?publicSyncSnapshot(state.snapshot):state.snapshot);if(!browseOnly){orders.push(...state.orders);enrollments.push(...state.enrollments);}}
    return json({snapshots,orders,enrollments});
 }catch(error){if(error instanceof RequestFailure)return json({message:error.message},error.status);return json({message:'The request could not be completed. Please retry.'},503);}
}
async function acceptSnapshot(storage,request){
  const snapshot=await request.json();
  const meta=await storage.get('snapshot:meta');
  const previous=meta||await readSnapshot(storage);
  if(previous&&!(snapshot.capturedAt>=previous.capturedAt))return json({ok:true});
  const revision=await digest(JSON.stringify({...snapshot,capturedAt:undefined}));
  const nextMeta={revision,capturedAt:snapshot.capturedAt,store:snapshot.store,enabled:snapshot.settings?.enabled,updated:Date.now()};
  if(!meta||revision!==meta.revision)await writeSnapshot(storage,snapshot,'snapshot',nextMeta);
  else if(snapshot.capturedAt!==meta.capturedAt)await storage.put('snapshot:meta',nextMeta);
  return json({ok:true});
}
async function snapshotMeta(storage){
  const meta=await storage.get('snapshot:meta');
  if(meta){const {revision,...publicMeta}=meta;return json(publicMeta);}
  const snapshot=await readSnapshot(storage);
  return json({store:snapshot?.store,enabled:snapshot?.settings?.enabled,capturedAt:snapshot?.capturedAt,updated:await storage.get('updated')});
}
