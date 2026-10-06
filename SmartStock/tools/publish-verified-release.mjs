import {publishInstaller} from './publish-installer.mjs';
import {readFile} from 'node:fs/promises';
import {verifyAppUpdate,verifyHostedModelCatalogue} from './verify-app-update.mjs';
const [artifact,version,build,kind,notes]=process.argv.slice(2);
if(!['update','studio'].includes(kind))throw Error('Choose update or studio');
if(kind==='update'){await verifyAppUpdate(artifact,version);await verifyHostedModelCatalogue();}
const url=process.env.SUPABASE_URL,key=process.env.SUPABASE_SECRET_KEY;
if(!url?.startsWith('https://')||!key)throw Error('Protected server publisher configuration is required');
const manifest=await publishInstaller({artifact,version,buildNumber:Number(build),platform:'windows',kind,
  endpoint:process.env.SMARTSTOCK_INSTALLER_PORTAL_URL||'https://downloads.deckers.gy',secret:process.env.SMARTSTOCK_INSTALLER_PUBLISH_KEY});
const row={version,build_number:Number(build),platform:'windows',artifact_bucket:'r2:smartstock-updates',artifact_path:manifest.key,
  sha256:manifest.sha256,file_size_bytes:manifest.size,release_notes:await readFile(notes,'utf8'),required:false,published:true,published_at:new Date().toISOString()};
const headers={apikey:key,'Content-Type':'application/json','Prefer':'return=representation','User-Agent':'SmartStockReleasePublisher/1.0'};
if(!key.startsWith('sb_secret_'))headers.Authorization=`Bearer ${key}`;
const response=await fetch(`${url.replace(/\/$/,'')}/rest/v1/app_releases`,{method:'POST',headers,body:JSON.stringify(row)});
if(!response.ok)throw Error(`Verified artifact is staged, but metadata publication failed (HTTP ${response.status})`);
const result=await response.json();
if(result.length!==1||result[0].artifact_path!==manifest.key||result[0].sha256!==manifest.sha256)throw Error('Published release did not match verified artifact');
console.log(`Published ${kind} ${version}: ${manifest.size} bytes, SHA-256 ${manifest.sha256}`);
