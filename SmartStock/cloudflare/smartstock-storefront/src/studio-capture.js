import {capturePage} from './studio-capture-page.js';
const json=(body,status=200)=>Response.json(body,{status,headers:{'Cache-Control':'no-store','X-Content-Type-Options':'nosniff','Referrer-Policy':'no-referrer'}});
export const sha256=async value=>[...new Uint8Array(await crypto.subtle.digest('SHA-256',new TextEncoder().encode(value)))].map(x=>x.toString(16).padStart(2,'0')).join('');
export async function bounded(request,limit){
  if(Number(request.headers.get('Content-Length'))>limit)throw Object.assign(Error('Photo is too large.'),{status:413});
  const reader=request.body?.getReader();if(!reader)return new Uint8Array();
  const chunks=[];let size=0;
  try{while(true){const {done,value}=await reader.read();if(done)break;size+=value.length;if(size>limit){await reader.cancel();throw Object.assign(Error('Photo is too large.'),{status:413});}chunks.push(value);}}finally{reader.releaseLock();}
  const bytes=new Uint8Array(size);let offset=0;for(const chunk of chunks){bytes.set(chunk,offset);offset+=chunk.length;}return bytes;
}
export async function captureRoute(request,env,configured){
  const url=new URL(request.url),internal=url.pathname==='/shop/internal/studio-capture';
  if(url.pathname==='/shop/studio-capture'&&request.method==='GET')return new Response(capturePage,{headers:{'Content-Type':'text/html; charset=utf-8','Cache-Control':'no-store','Referrer-Policy':'no-referrer','X-Content-Type-Options':'nosniff','Content-Security-Policy':"default-src 'none'; script-src 'unsafe-inline'; style-src 'unsafe-inline'; img-src blob:; connect-src 'self'; base-uri 'none'; frame-ancestors 'none'; form-action 'none'",'Permissions-Policy':'camera=(self), microphone=(), geolocation=()'}});
  if(request.method!=='POST')return json({message:'Use POST.'},405);
  if(internal){
    const store=configured.find(o=>o.instanceId===request.headers.get('X-Storefront-Instance'));
    const key=store&&JSON.parse(env.ORIGIN_KEYS_JSON||'{}')[store.instanceId];
    if(!key||key.length<32||key!==request.headers.get('X-Storefront-Key'))return json({message:'Forbidden'},403);
    const body=JSON.parse(new TextDecoder().decode(await bounded(request,4096)));
    if(!/^[a-f0-9-]{36}$/.test(body.id||'')||typeof body.owner!=='string'||body.owner.length>160||!body.owner)return json({message:'Invalid capture request.'},400);
    const object=env.CAPTURES.get(env.CAPTURES.idFromName(store.instanceId+':'+body.id));
    return object.fetch(new Request('https://capture/internal',{method:'POST',body:JSON.stringify(body)}));
  }
  const id=url.searchParams.get('id'),instance=url.searchParams.get('instance');
  if(!/^[a-f0-9-]{36}$/.test(id||'')||!configured.some(o=>o.instanceId===instance))return json({message:'This photo link is invalid.'},404);
  if(request.headers.get('Origin')!==url.origin)return json({message:'Open the photo link in your browser.'},403);
  return env.CAPTURES.get(env.CAPTURES.idFromName(instance+':'+id)).fetch(new Request('https://capture/upload',{method:'POST',headers:request.headers,body:request.body,duplex:'half'}));
}

/** A separate object per capture; credentials and photos never enter the public catalog. */
export class StudioCapture {
  constructor(state){this.state=state;this.tail=Promise.resolve();}
  alarm(){const run=this.tail.then(()=>this.state.storage.deleteAll());this.tail=run.catch(()=>{});return run;}
  fetch(request){const run=this.tail.then(()=>this.handle(request)).catch(e=>json({message:e.status?e.message:'Photo transfer failed. Try a new link.'},e.status||503));this.tail=run.then(()=>{});return run;}
  async handle(request){
    const storage=this.state.storage,path=new URL(request.url).pathname;
    let session=await storage.get('session');
    if(session&&session.expires<=Date.now()){await storage.deleteAll();return json({message:'This photo link expired. Create a new link on the computer.'},410);}
    if(path==='/internal'){
      const body=await request.json();
      if(body.action==='CREATE'){
        if(session)return json({message:'Photo request already exists.'},409);
        if(!/^[a-f0-9]{64}$/.test(body.tokenHash||''))return json({message:'Invalid photo request.'},400);
        session={owner:body.owner,tokenHash:body.tokenHash,label:String(body.label||'Passport photo').slice(0,100),expires:Date.now()+15*60*1000,uploaded:false};
        await storage.transaction(async tx=>{await tx.setAlarm(session.expires);await tx.put('session',session);});
        return json({expiresAt:session.expires});
      }
      if(!session)return json({message:'This photo link is no longer active.'},410);
      if(body.owner!==session.owner)return json({message:'This photo request belongs to another workspace session.'},403);
      if(body.action==='CANCEL'||body.action==='ACK'){await storage.deleteAll();return json({closed:true});}
      if(body.action!=='POLL')return json({message:'Invalid photo action.'},400);
      const response={uploaded:session.uploaded,expiresAt:session.expires};
      if(session.uploaded){let bytes='';for(let i=0;i<session.parts;i++)bytes+=await storage.get('photo:'+i);response.bytesBase64=bytes;}
      return json(response);
    }
    if(!session)return json({message:'This photo link is no longer active.'},410);
    const token=(request.headers.get('Authorization')||'').replace(/^Bearer /,'');
    if(!/^[A-Za-z0-9_-]{43}$/.test(token)||await sha256(token)!==session.tokenHash)return json({message:'This photo link is invalid.'},403);
    if(session.uploaded)return json({message:'This photo has already been sent. Create a new link for a retake.'},409);
    if(request.headers.get('Content-Type')!=='image/jpeg')return json({message:'Send a JPEG photo.'},415);
    const bytes=await bounded(request,2*1024*1024);
    if(session.expires<=Date.now()){await storage.deleteAll();return json({message:'This photo link expired. Create a new link.'},410);}
    if(bytes.length<4||bytes[0]!==255||bytes[1]!==216||bytes.at(-2)!==255||bytes.at(-1)!==217)return json({message:'Invalid JPEG photo.'},400);
    let raw='';for(let i=0;i<bytes.length;i+=8192)raw+=String.fromCharCode(...bytes.subarray(i,i+8192));const base64=btoa(raw);
    session.uploaded=true;session.parts=Math.ceil(base64.length/24000);
    await storage.transaction(async tx=>{for(let i=0;i<session.parts;i++)await tx.put('photo:'+i,base64.slice(i*24000,(i+1)*24000));await tx.put('session',session);});
    return json({uploaded:true});
  }
}
