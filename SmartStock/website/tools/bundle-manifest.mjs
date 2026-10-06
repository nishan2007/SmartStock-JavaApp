import {createHash} from 'node:crypto';
import {readFile,writeFile,readdir} from 'node:fs/promises';
import {fileURLToPath} from 'node:url';
import path from 'node:path';

const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..');
const output=path.resolve(root,'../src/storefront-web');
async function files(directory,prefix=''){
  const result=[];
  let entries;try{entries=await readdir(directory,{withFileTypes:true});}catch(error){if(error.code==='ENOENT')return result;throw error;}
  for(const entry of entries){
    const name=prefix+entry.name;
    if(entry.isDirectory())result.push(...await files(path.join(directory,entry.name),name+'/'));
    else if(entry.isFile())result.push(name);
    else throw new Error('Unsupported bundle entry: '+name);
  }
  return result.sort();
}
async function hashes(base,names,normalize=false){
  const result={};
  for(const name of names){let bytes=await readFile(path.join(base,name));if(normalize&&!/\.(?:png|jpe?g|webp|gif|ico|woff2?|ttf|otf|mp4|webm|pdf)$/i.test(name))bytes=Buffer.from(bytes.toString('utf8').replaceAll('\r\n','\n'));result[name]=createHash('sha256').update(bytes).digest('hex');}
  return result;
}
const inputs=['package.json','pnpm-lock.yaml','pnpm-workspace.yaml','tsconfig.json','vite.config.ts','index.html',
  ...(await files(path.join(root,'src'))).map(x=>'src/'+x),
  ...(await files(path.join(root,'public'))).map(x=>'public/'+x),
  'tools/bundle-manifest.mjs'].sort();
const assets=(await files(output)).filter(x=>x!=='bundle-manifest.json');
await writeFile(path.join(output,'bundle-manifest.json'),JSON.stringify({version:1,inputs:await hashes(root,inputs,true),assets:await hashes(output,assets)},null,2)+'\n');
console.log('Recorded storefront source and asset hashes.');
