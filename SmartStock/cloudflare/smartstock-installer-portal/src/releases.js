const json=(value,status=200)=>Response.json(value,{status,headers:{'Cache-Control':'private, no-store','X-Content-Type-Options':'nosniff'}});
export function validArtifact(a) {
  return a && ['windows','mac'].includes(a.platform) && /^\d+\.\d+\.\d+$/.test(a.version)
    && Number.isSafeInteger(a.buildNumber) && a.buildNumber>0
    && a.buildNumber===a.version.split('.').reduce((n,v,i)=>n+Number(v)*[100000,1000,1][i],0)
    && Number.isSafeInteger(a.size) && a.size>0 && a.size<=5*1024**3
    && /^[a-f0-9]{64}$/.test(a.sha256) && typeof a.filename==='string' && /^[a-zA-Z0-9._-]+$/.test(a.filename)
    && (a.platform==='windows'?a.filename.endsWith('.exe'):/\.(dmg|pkg)$/.test(a.filename))
    && a.key===`installers/${a.platform}/${a.version}/${a.sha256}/${a.filename}`;
}
// Multipart staging for large update ZIPs and the separate SmartStudio channel.
// These uploads never change the public SmartStock installer selection.
export function validUpload(a) {
  if(a?.kind==='model') {
    const m=a.model,compatibility={FAST:'isnet-1024-v1',BEST:'birefnet-1024-imagenet-v1'};
    return !!m && Object.hasOwn(compatibility,m.quality)
      && /^[A-Za-z0-9._-]{1,64}$/.test(m.version||'') && m.compatibility===compatibility[m.quality]
      && Number.isSafeInteger(a.size) && a.size>0 && a.size<=2_000_000_000 && m.sizeBytes===a.size
      && /^[a-f0-9]{64}$/.test(a.sha256||'') && m.sha256===a.sha256
      && typeof a.filename==='string' && /^[a-zA-Z0-9._-]+\.onnx$/.test(a.filename)
      && a.key===`models/${m.quality.toLowerCase()}/${a.sha256}.onnx` && m.objectKey===a.key;
  }
  if(validArtifact(a))return true;
  if(!a||!['update','studio'].includes(a.kind))return false;
  const filename=a.kind==='update'?'verified.exe':a.filename;
  const standard={...a,filename,key:`installers/${a.platform}/${a.version}/${a.sha256}/${filename}`};
  const prefix=a.kind==='update'?'updates':'smartstudio';
  return validArtifact(standard) && a.platform==='windows'
    && /^[a-zA-Z0-9._-]+$/.test(a.filename)
    && a.filename.endsWith(a.kind==='update'?'.zip':'.exe')
    && a.key===`${prefix}/${a.platform}/${a.version}/${a.sha256}/${a.filename}`;
}
const uploadKey=key=>typeof key==='string'&&(/^(installers|updates|smartstudio)\/(windows|mac)\/\d+\.\d+\.\d+\/[a-f0-9]{64}\/[a-zA-Z0-9._-]+$/.test(key)
  || /^models\/(fast|best)\/[a-f0-9]{64}\.onnx$/.test(key));
async function body(request) {
  if(!request.body)throw Error('Body required');
  const reader=request.body.getReader();let text='',size=0;const decoder=new TextDecoder('utf-8',{fatal:true});
  try{while(true){const {done,value}=await reader.read();if(done)break;size+=value.byteLength;if(size>65536){await reader.cancel();throw Error('Request too large');}text+=decoder.decode(value,{stream:true});}text+=decoder.decode();}
  finally{reader.releaseLock();}
  return JSON.parse(text);
}
async function secretMatches(actual,expected) {
  if(typeof actual!=='string'||actual.length<32||typeof expected!=='string'||expected.length>256)return false;
  const hash=async s=>new Uint8Array(await crypto.subtle.digest('SHA-256',new TextEncoder().encode(s)));
  const [a,b]=await Promise.all([hash(actual),hash(expected)]);let difference=0;
  for(let i=0;i<a.length;i++)difference|=a[i]^b[i];return difference===0;
}
export async function latest(bucket,platform) {
  const object=await bucket.get(`installers/latest/${platform}.json`);
  if(!object)return null;
  if(object.size>16384)throw Error('Invalid release manifest');
  const manifest=await object.json();if(!validArtifact(manifest)||manifest.platform!==platform||typeof manifest.etag!=='string')throw Error('Invalid release manifest');
  return manifest;
}
// Only the release operator can call these paths; user accounts cannot publish.
export async function releaseRequest(request,env) {
  if(!await secretMatches(env.INSTALLER_PUBLISH_KEY,request.headers.get('X-SmartStock-Publish-Key')))return json({message:'Access denied'},403);
  const url=new URL(request.url),route=url.pathname.slice('/_release/'.length),bucket=env.UPDATE_BUCKET;
  try {
    if(route==='create'&&request.method==='POST') {
      const a=await body(request);if(!validUpload(a))return json({message:'Invalid release upload'},400);
      const metadata=a.kind==='model'?{sha256:a.sha256,version:a.model.version,quality:a.model.quality,compatibility:a.model.compatibility,filename:a.filename,size:String(a.size)}
        :{sha256:a.sha256,version:a.version,buildNumber:String(a.buildNumber),platform:a.platform,filename:a.filename,size:String(a.size)};
      const upload=await bucket.createMultipartUpload(a.key,{httpMetadata:{contentType:'application/octet-stream',contentDisposition:`attachment; filename="${a.filename}"`},customMetadata:metadata});
      return json({key:upload.key,uploadId:upload.uploadId});
    }
    if(route==='part'&&request.method==='PUT') {
      const key=url.searchParams.get('key'),uploadId=url.searchParams.get('uploadId'),partNumber=Number(url.searchParams.get('partNumber'));
      if(!uploadKey(key)||!uploadId||!Number.isInteger(partNumber)||partNumber<1||partNumber>10000)return json({message:'Invalid part'},400);
      return json(await bucket.resumeMultipartUpload(key,uploadId).uploadPart(partNumber,request.body));
    }
    if(route==='complete'&&request.method==='POST') {
      const a=await body(request);if(!uploadKey(a.key)||!a.uploadId||!Array.isArray(a.parts))return json({message:'Invalid upload'},400);
      const object=await bucket.resumeMultipartUpload(a.key,a.uploadId).complete(a.parts);
      return json({key:object.key,size:object.size,etag:object.etag});
    }
    if(route==='abort'&&request.method==='POST') {
      const a=await body(request);if(!uploadKey(a.key)||!a.uploadId)return json({message:'Invalid upload'},400);
      await bucket.resumeMultipartUpload(a.key,a.uploadId).abort();return json({aborted:true});
    }
    if(route==='verify'&&request.method==='GET') {
      const key=url.searchParams.get('key');if(!uploadKey(key))return json({message:'Invalid object'},400);
      const object=await bucket.get(key);if(!object)return json({message:'Not found'},404);
      return new Response(object.body,{headers:{'Content-Type':'application/octet-stream','Content-Length':String(object.size),'Cache-Control':'private, no-store'}});
    }
    if(route==='publish'&&request.method==='POST') {
      const a=await body(request);if(a.kind||!validArtifact(a)||typeof a.etag!=='string')return json({message:'Invalid verified installer'},400);
      const object=await bucket.head(a.key);
      if(!object||object.size!==a.size||object.etag!==a.etag||object.customMetadata?.sha256!==a.sha256)return json({message:'Installer changed'},409);
      const path=`installers/latest/${a.platform}.json`,previous=await bucket.get(path);
      if(previous) {
        const old=await previous.json();
        if(old.buildNumber>a.buildNumber)return json({message:'A newer installer is already published'},409);
        if(old.buildNumber===a.buildNumber) {
          if(old.sha256!==a.sha256)return json({message:'This build already has different bytes'},409);
          return json(old);
        }
      }
      const manifest={...a,publishedAt:new Date().toISOString()};
      const saved=await bucket.put(path,JSON.stringify(manifest),{
        httpMetadata:{contentType:'application/json',cacheControl:'no-store'},
        onlyIf:previous?{etagMatches:previous.etag}:{etagDoesNotMatch:'*'}
      });
      if(!saved)return json({message:'Another release changed the latest installer; retry publication'},409);
      return json(manifest);
    }
    return json({message:'Not found'},404);
  } catch {return json({message:'Release operation failed'},400);}
}
