// A single durable coordinator serializes encrypted identity revisions and phone claims.
// Password verification and decryption run exclusively on authenticated store servers.
const reply=(body,status=200)=>new Response(JSON.stringify(body),{status,headers:{'Content-Type':'application/json','Cache-Control':'no-store'}});
const digestKey=value=>typeof value==='string'&&/^[a-f0-9]{64}$/.test(value);
export async function identityOperation(storage,body,now=Date.now()){
  if(body?.action==='POLICY_READ')return reply(await storage.get('launch-policy')||{revision:0,allowUnreviewedLinks:true});
  if(body?.action==='POLICY_WRITE'){
    const old=await storage.get('launch-policy')||{revision:0,allowUnreviewedLinks:true};
    if(body.revision!==old.revision||typeof body.allowUnreviewedLinks!=='boolean')return reply({message:'Launch settings changed; refresh and retry'},409);
    const policy={revision:old.revision+1,allowUnreviewedLinks:body.allowUnreviewedLinks};await storage.put('launch-policy',policy);return reply(policy);
  }
  if(body?.action==='REVIEW_LIST'){
    const entries=await storage.list({prefix:'review:'});return reply({records:[...entries.values()].filter(v=>v.reviewPending).slice(0,200)});
  }
  if(!body||!digestKey(body.emailKey))return reply({message:'Invalid identity key'},400);
  const key='identity:'+body.emailKey;
  if(body.action==='RATE'){
    if(!digestKey(body.ipKey))return reply({message:'Invalid attempt key'},400);
    const bucket=Math.floor(now/600000),keys=['limit-email:'+body.emailKey,'limit-ip:'+body.ipKey];
    let allowed=true;for(const k of keys){const previous=await storage.get(k),count=(previous?.bucket===bucket?previous.count:0)+1;await storage.put(k,{bucket,count});if(count>20)allowed=false;}
    return reply({allowed});
  }
  const old=await storage.get(key)||{revision:0,payload:null,phoneKey:null};
  if(body.action==='READ')return reply(old);
  if(body.action!=='WRITE'||!Number.isSafeInteger(body.revision)||body.revision!==old.revision||typeof body.payload!=='string'||body.payload.length>32768||body.payload.length<40)return reply({message:'Identity changed; retry the request'},409);
  const phoneKey=body.phoneKey||null;
  if(phoneKey&&!digestKey(phoneKey))return reply({message:'Invalid phone key'},400);
  if(old.phoneKey&&phoneKey!==old.phoneKey&&!body.staffRelink)return reply({message:'The customer link requires an authorized employee'},409);
  let displaced;
  if(phoneKey){const owner=await storage.get('phone:'+phoneKey);if(owner&&owner!==body.emailKey){
    if(!body.staffRelink||!body.transfer||body.transfer.emailKey!==owner||!digestKey(owner))return reply({message:'This phone is already linked. Contact your store.'},409);
    const previous=await storage.get('identity:'+owner);
    if(!previous||previous.revision!==body.transfer.revision||typeof body.transfer.payload!=='string'||body.transfer.payload.length<40||body.transfer.payload.length>32768)return reply({message:'The previous customer link changed'},409);
    displaced={key:'identity:'+owner,value:{...previous,revision:previous.revision+1,payload:body.transfer.payload}};
  }}
  const revision=old.revision+1;
  if(body.launchClaim===true&&!(await storage.get('launch-policy')||{allowUnreviewedLinks:true}).allowUnreviewedLinks)return reply({message:'Self-service linking is closed. Contact your store.'},409);
  // Retired phone claims are deliberately retained to prevent reassignment through signup.
  const value={revision,payload:body.payload,phoneKey,emailKey:body.emailKey,reviewPending:typeof body.reviewPending==='boolean'?body.reviewPending:old.reviewPending===true};
  const updates={[key]:value};if(value.reviewPending||old.reviewPending)updates['review:'+body.emailKey]=value;if(phoneKey)updates['phone:'+phoneKey]=body.emailKey;if(displaced){updates[displaced.key]=displaced.value;updates['review:'+body.transfer.emailKey]={...displaced.value,reviewPending:false};}
  await storage.put(updates);return reply({revision});
}

export async function identityRoute(request,env,configured,stub,readBody){
  const url=new URL(request.url),origin=configured.find(o=>o.storeId===Number(url.searchParams.get('storeId')));
  const secret=origin&&JSON.parse(env.ORIGIN_KEYS_JSON||'{}')[origin.instanceId];
  if(request.method!=='POST'||!secret||request.headers.get('X-Storefront-Instance')!==origin.instanceId||request.headers.get('X-Storefront-Key')!==secret)return reply({message:'Forbidden'},403);
  let body;try{body=JSON.parse(await readBody(request,49152));}catch{return reply({message:'Invalid identity request'},400);}
  if(body.action==='LOOKUP'){
    if(typeof body.phone!=='string'||body.phone.length>32||typeof body.email!=='string'||body.email.length>254)return reply({message:'Invalid contacts'},400);
    const replies=await Promise.all(configured.map(async store=>{
      try{
        const key=JSON.parse(env.ORIGIN_KEYS_JSON||'{}')[store.instanceId];
        const response=await fetch(store.url+'/shop/internal/customer-auth-lookup',{method:'POST',headers:{'Content-Type':'application/json','X-Storefront-Key':key,'X-Storefront-Instance':store.instanceId},body:JSON.stringify({phone:body.phone,email:body.email}),signal:AbortSignal.timeout(5000),redirect:'manual'});
        if(!response.ok)throw Error();return await response.json();
      }catch{return null;}
    }));
    if(replies.some(value=>!value))return reply({message:'A store cannot confirm the customer directory. Please retry shortly.'},503);
    return reply({stores:replies});
  }
  const response=await stub(env,'customer-identities').fetch(new Request('https://coordinator/customer-identity',{method:'POST',body:JSON.stringify(body)}));
  return response;
}

export function rankIdentityOrigins(origins,health){
  return origins.filter(origin=>health[origin.storeId]?.ok).sort((a,b)=>health[a.storeId].latency-health[b.storeId].latency||a.storeId-b.storeId);
}
