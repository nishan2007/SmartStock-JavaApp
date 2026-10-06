import {createReadStream} from 'node:fs';
import {stat,open} from 'node:fs/promises';
import {createHash} from 'node:crypto';
import {basename} from 'node:path';
import {pathToFileURL} from 'node:url';
import {validUpload} from '../cloudflare/smartstock-installer-portal/src/releases.js';

export async function fileHash(path) {
  const hash=createHash('sha256');for await(const chunk of createReadStream(path))hash.update(chunk);return hash.digest('hex');
}
export async function publishInstaller({artifact,version,buildNumber,platform,endpoint,secret,kind,model},fetcher=fetch) {
  const url=new URL(endpoint);
  if(url.protocol!=='https:'||url.username||url.password||url.pathname!=='/'||url.search||url.hash)throw Error('Publisher requires an HTTPS origin');
  if(!secret||secret.length<32)throw Error('Configure SMARTSTOCK_INSTALLER_PUBLISH_KEY');
  const size=(await stat(artifact)).size,sha256=await fileHash(artifact),filename=basename(artifact);
  const prefix=kind==='update'?'updates':kind==='studio'?'smartstudio':'installers';
  const manifest=kind==='model'?{kind,model,size,sha256,filename,key:model?.objectKey}
    :{platform,version,buildNumber,size,sha256,filename,key:`${prefix}/${platform}/${version}/${sha256}/${filename}`,...(kind?{kind}:{})};
  if(!validUpload(manifest))throw Error('Invalid release artifact or version/build number');
  const call=async(route,method,body)=>{
    const timeout=kind==='model'&&route.startsWith('verify?')?45*60*1000:300000;
    const r=await fetcher(new URL('/_release/'+route,url),{method,redirect:'error',headers:{'X-SmartStock-Publish-Key':secret,'Content-Type':Buffer.isBuffer(body)?'application/octet-stream':'application/json'},body:body===undefined?undefined:Buffer.isBuffer(body)?body:JSON.stringify(body),signal:AbortSignal.timeout(timeout)});
    if(!r.ok)throw Error(`Installer ${route.split('?')[0]} failed (HTTP ${r.status})`);return r;
  };
  console.log(`Uploading ${filename} (${size} bytes) in parts...`);
  let upload,completed=false;
  try {
    upload=await(await call('create','POST',manifest)).json();
    const handle=await open(artifact,'r'),parts=[];
    try {
      const partSize=8*1024*1024;
      const uploadPart=async(offset,partNumber)=>{
        const buffer=Buffer.alloc(Math.min(partSize,size-offset));let read=0;
        while(read<buffer.length){const result=await handle.read(buffer,read,buffer.length-read,offset+read);if(!result.bytesRead)throw Error('Installer changed during upload');read+=result.bytesRead;}
        const query=new URLSearchParams({key:upload.key,uploadId:upload.uploadId,partNumber:String(partNumber)});
        for(let attempt=0;;attempt++) {
          try { return await(await call('part?'+query,'PUT',buffer)).json(); }
          catch(error) {
            if(attempt>=3)throw error;
            await new Promise(resolve=>setTimeout(resolve,1000*2**attempt));
          }
        }
      };
      for(let offset=0;offset<size;offset+=partSize*4) {
        const batch=[];
        for(let lane=0;lane<4&&offset+lane*partSize<size;lane++)batch.push(uploadPart(offset+lane*partSize,Math.floor(offset/partSize)+lane+1));
        const results=await Promise.allSettled(batch);
        const failure=results.find(r=>r.status==='rejected');if(failure)throw failure.reason;
        parts.push(...results.map(r=>r.value));
        console.log(`Installer upload: ${Math.round(Math.min(offset+partSize*4,size)/size*100)}%`);
      }
    } finally {await handle.close();}
    const stored=await(await call('complete','POST',{...upload,parts})).json();completed=true;
    if(stored.key!==manifest.key||stored.size!==size||typeof stored.etag!=='string')throw Error('Stored installer size/key mismatch');
    console.log('Downloading the stored installer to verify its size and SHA-256...');
    const response=await call('verify?'+new URLSearchParams({key:manifest.key}),'GET');
    const hash=createHash('sha256');let downloaded=0,reported=0;
    for await(const bytes of response.body){hash.update(bytes);downloaded+=bytes.length;
      if(downloaded-reported>=128*1024*1024){reported=downloaded;console.log(`Installer verification download: ${Math.round(downloaded/size*100)}%`);}
    }
    if(downloaded!==size||hash.digest('hex')!==sha256)throw Error('Stored installer verification failed; latest selection was not changed');
    if(kind){console.log(`Verified staged ${kind} artifact: ${manifest.key}`);return manifest;}
    const published=await(await call('publish','POST',{...manifest,etag:stored.etag})).json();
    if(published.key!==manifest.key||published.sha256!==sha256)throw Error('Published installer did not match the verified artifact');
    console.log(`Published SmartStock ${version} for ${platform}: ${url.origin}/download/${platform}`);
    console.log(`SHA-256: ${sha256}`);return published;
  } catch(error) {
    if(upload&&!completed)try{await call('abort','POST',upload);}catch{}
    throw error;
  }
}
if(process.argv[1]&&import.meta.url===pathToFileURL(process.argv[1]).href) {
  const [artifact,version,build,platform]=process.argv.slice(2);
  if(!artifact||!version||!build||!platform){console.error('Usage: node publish-installer.mjs <installer> <version> <build-number> <windows|mac>');process.exitCode=2;}
  else try{await publishInstaller({artifact,version,buildNumber:Number(build),platform,endpoint:process.env.SMARTSTOCK_INSTALLER_PORTAL_URL||'https://downloads.deckers.gy',secret:process.env.SMARTSTOCK_INSTALLER_PUBLISH_KEY});}
  catch(error){console.error(error.message);process.exitCode=1;}
}
