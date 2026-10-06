import test from 'node:test';
import assert from 'node:assert/strict';
import {mkdtemp,writeFile,rm} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import {webcrypto} from 'node:crypto';
import {releaseRequest,validArtifact,validUpload} from '../src/releases.js';
import {publishInstaller} from '../../../tools/publish-installer.mjs';
globalThis.crypto??=webcrypto;
const secret='x'.repeat(32);
const artifact=()=>{const a={platform:'windows',version:'1.0.229',buildNumber:100229,size:3,sha256:'a'.repeat(64),filename:'SmartStock.exe',etag:'etag'};a.key=`installers/windows/${a.version}/${a.sha256}/${a.filename}`;return a;};
const req=(route,a,method='POST',key=secret)=>new Request('https://downloads.deckers.gy/_release/'+route,{method,headers:{'X-SmartStock-Publish-Key':key},body:method==='POST'?JSON.stringify(a):undefined});
test('publisher rejects update ZIPs and mismatched version/build numbers',()=>{
  assert.equal(validArtifact(artifact()),true);assert.equal(validArtifact({...artifact(),filename:'update.zip'}),false);
  assert.equal(validArtifact({...artifact(),buildNumber:100230}),false);
});
test('separate update and Studio uploads cannot replace the public installer',async()=>{
  for(const [kind,prefix,filename] of [['update','updates','stock.zip'],['studio','smartstudio','studio.exe']]) {
    const a={...artifact(),kind,filename};a.key=`${prefix}/windows/${a.version}/${a.sha256}/${filename}`;
    assert.equal(validUpload(a),true);assert.equal(validArtifact(a),false);
    assert.equal(validUpload({...a,key:'installers/latest/windows.json'}),false);
    assert.equal((await releaseRequest(req('publish',a),{INSTALLER_PUBLISH_KEY:secret})).status,400);
    let created=false;
    assert.equal((await releaseRequest(req('create',a),{INSTALLER_PUBLISH_KEY:secret,UPDATE_BUCKET:{createMultipartUpload:async(key)=>{created=true;return{key,uploadId:'test'};}}})).status,200);
    assert.equal(created,true);
  }
});
test('release endpoints require the operator secret',async()=>{
  assert.equal((await releaseRequest(req('create',artifact(),'POST','wrong'),{INSTALLER_PUBLISH_KEY:secret})).status,403);
});

test('1.1.1 uses the shared cross-platform build number',()=>{
  for(const platform of ['windows','mac']) {
    const a={...artifact(),platform,version:'1.1.1',buildNumber:101001,filename:platform==='mac'?'SmartStock.dmg':'SmartStock.exe'};
    a.key=`installers/${platform}/${a.version}/${a.sha256}/${a.filename}`;
    assert.equal(validArtifact(a),true);assert.equal(validArtifact({...a,buildNumber:10101}),false);
  }
});

test('private model multipart uploads validate compatibility and cannot publish a public installer',async()=>{
  const model={quality:'BEST',version:'birefnet-general-1',sizeBytes:972666916,sha256:'b'.repeat(64),compatibility:'birefnet-1024-imagenet-v1'};
  model.objectKey=`models/best/${model.sha256}.onnx`;
  const a={kind:'model',model,filename:'birefnet-general.onnx',key:model.objectKey,size:model.sizeBytes,sha256:model.sha256};
  assert.equal(validUpload(a),true);assert.equal(validArtifact(a),false);
  assert.equal(validUpload({...a,model:{...model,compatibility:'unknown'}}),false);
  assert.equal(validUpload({...a,key:'models/catalogue-v1.json'}),false);
  assert.equal(validUpload({...a,size:1}),false);
  let created;
  const env={INSTALLER_PUBLISH_KEY:secret,UPDATE_BUCKET:{createMultipartUpload:async(key,options)=>{
    created={key,options};return{key,uploadId:'model-upload'};}}};
  assert.equal((await releaseRequest(req('create',a,'POST','wrong'),env)).status,403);
  assert.equal((await releaseRequest(req('create',a),env)).status,200);
  assert.equal(created.key,model.objectKey);assert.equal(created.options.customMetadata.compatibility,model.compatibility);
  assert.equal((await releaseRequest(req('publish',a),env)).status,400);
});

test('model uploader fully verifies bytes and never selects an installer',async()=>{
  const dir=await mkdtemp(join(tmpdir(),'smartstock-model-multipart-')),path=join(dir,'model.onnx');
  await writeFile(path,'zip');
  try {
    const {fileHash}=await import('../../../tools/publish-installer.mjs');
    const sha256=await fileHash(path),model={quality:'FAST',version:'fixture-1',sizeBytes:3,sha256,compatibility:'isnet-1024-v1',objectKey:`models/fast/${sha256}.onnx`};
    for(const corrupt of [false,true]) {
      const calls=[];let manifest;
      const fetcher=async(url,options)=>{
        const route=new URL(url).pathname.split('/').pop();calls.push(route);
        if(route==='create'){manifest=JSON.parse(options.body);return Response.json({key:manifest.key,uploadId:'model'});}
        if(route==='part')return Response.json({partNumber:1,etag:'part'});
        if(route==='complete')return Response.json({key:manifest.key,size:3,etag:'etag'});
        if(route==='verify')return new Response(corrupt?'bad':'zip');
        throw Error('Models must not change an installer selection');
      };
      const task=publishInstaller({artifact:path,kind:'model',model,endpoint:'https://downloads.deckers.gy',secret},fetcher);
      if(corrupt)await assert.rejects(task,/verification failed/);else assert.equal((await task).key,model.objectKey);
      assert.deepEqual(calls,['create','part','complete','verify']);
    }
  }finally{await rm(dir,{recursive:true,force:true});}
});
test('multipart upload retries a disconnected part before verification',async()=>{
  const dir=await mkdtemp(join(tmpdir(),'smartstock-upload-retry-')),path=join(dir,'SmartStock.exe');
  await writeFile(path,'zip');
  try {
    let manifest,attempts=0;
    const fetcher=async(url,options)=>{
      const route=new URL(url).pathname.split('/').pop();
      if(route==='create'){manifest=JSON.parse(options.body);return Response.json({key:manifest.key,uploadId:'retry'});}
      if(route==='part'){if(++attempts===1)throw new TypeError('fetch failed');return Response.json({partNumber:1,etag:'part'});}
      if(route==='complete')return Response.json({key:manifest.key,size:3,etag:'etag'});
      if(route==='verify')return new Response('zip');
      if(route==='publish')return Response.json(JSON.parse(options.body));
      throw Error('Unexpected route');
    };
    await publishInstaller({artifact:path,version:'1.1.1',buildNumber:101001,platform:'windows',endpoint:'https://downloads.deckers.gy',secret},fetcher);
    assert.equal(attempts,2);
  }finally{await rm(dir,{recursive:true,force:true});}
});
test('publication rejects changed bytes and old builds',async()=>{
  const a=artifact(),env={INSTALLER_PUBLISH_KEY:secret,UPDATE_BUCKET:{head:async()=>({size:4,etag:a.etag,customMetadata:{sha256:a.sha256}})}};
  assert.equal((await releaseRequest(req('publish',a),env)).status,409);
  env.UPDATE_BUCKET.head=async()=>({size:a.size,etag:a.etag,customMetadata:{sha256:a.sha256}});
  env.UPDATE_BUCKET.get=async()=>({json:async()=>({...a,buildNumber:100230}),etag:'old'});
  assert.equal((await releaseRequest(req('publish',a),env)).status,409);
});
test('latest selection uses an atomic condition and preserves other platforms',async()=>{
  let saved;
  const a=artifact(),env={INSTALLER_PUBLISH_KEY:secret,UPDATE_BUCKET:{head:async()=>({size:a.size,etag:a.etag,customMetadata:{sha256:a.sha256}}),get:async()=>null,put:async(...args)=>{saved=args;return {};}}};
  assert.equal((await releaseRequest(req('publish',a),env)).status,200);
  assert.equal(saved[0],'installers/latest/windows.json');assert.deepEqual(saved[2].onlyIf,{etagDoesNotMatch:'*'});
  env.UPDATE_BUCKET.put=async()=>null;assert.equal((await releaseRequest(req('publish',a),env)).status,409);
});
test('publisher verifies the uploaded download before changing latest',async()=>{
  const dir=await mkdtemp(join(tmpdir(),'smartstock-publish-test-')),path=join(dir,'SmartStock.exe');
  await writeFile(path,'zip');
  try {
    for(const corrupt of [false,true]) {
      const calls=[];let storedManifest;
      const fetcher=async(url,options)=>{
        const route=new URL(url).pathname.split('/').pop();calls.push(route);
        if(route==='create'){storedManifest=JSON.parse(options.body);return Response.json({key:storedManifest.key,uploadId:'upload'});}
        if(route==='part')return Response.json({partNumber:1,etag:'part'});
        if(route==='complete')return Response.json({key:storedManifest.key,size:3,etag:'etag'});
        if(route==='verify')return new Response(corrupt?'bad':'zip');
        if(route==='publish')return Response.json(JSON.parse(options.body));
        throw Error('Unexpected route');
      };
      const task=publishInstaller({artifact:path,version:'1.0.229',buildNumber:100229,platform:'windows',endpoint:'https://downloads.deckers.gy',secret},fetcher);
      if(corrupt){await assert.rejects(task,/verification failed/);assert.ok(!calls.includes('publish'));}
      else{await task;assert.deepEqual(calls,['create','part','complete','verify','publish']);}
    }
  } finally{await rm(dir,{recursive:true,force:true});}
});
