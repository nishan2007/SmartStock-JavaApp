import {releaseRequest,latest} from './releases.js';
const headers = {
  'Cache-Control':'private, no-store', 'X-Content-Type-Options':'nosniff',
  'Referrer-Policy':'no-referrer', 'X-Frame-Options':'DENY',
  'Content-Security-Policy':"default-src 'none'; style-src 'unsafe-inline'; base-uri 'none'; frame-ancestors 'none'; form-action 'none'",
  'Strict-Transport-Security':'max-age=31536000'
};
const reply = (body,status=200,extra={}) => new Response(body,{status,headers:{...headers,...extra}});
export function configuration(env) {
  const installers=JSON.parse(env.INSTALLERS_JSON||'{}');
  for(const [platform,a] of Object.entries(installers)) {
    if(!['windows','mac'].includes(platform)||typeof a.key!=='string'||!a.key||a.key.split('/').some(p=>!p||p==='.'||p==='..')||/[\\\x00-\x1f]/.test(a.key)||!Number.isSafeInteger(a.size)||a.size<1||!/^[a-f0-9]{64}$/.test(a.sha256)||typeof a.filename!=='string'||!/^[a-zA-Z0-9._-]+$/.test(a.filename)) throw Error('Invalid installer');
  }
  return {installers};
}
export default {
  async fetch(request,env) {
    const url=new URL(request.url);
    if(url.protocol!=='https:') return reply('HTTPS required.',400);
    if(url.pathname.startsWith('/_release/')) return releaseRequest(request,env);
    if(!['GET','HEAD'].includes(request.method)) return reply('Method not allowed.',405,{'Allow':'GET, HEAD'});
    if(url.pathname!=='/' && !/^\/download\/(windows|mac)$/.test(url.pathname)) return reply('Not found.',404);
    let config;
    // Installers are public downloads, independent of store availability.
    // Operator publication remains authenticated in releaseRequest above.
    try { config=configuration(env); }
    catch { return reply('Installer selection is being configured. Please try again later.',503); }
    try {
      for(const platform of ['windows','mac']) {
        const published=await latest(env.UPDATE_BUCKET,platform);
        if(published)config.installers[platform]=published;
      }
    } catch {return reply('Installer selection unavailable.',503);}
    if(url.pathname==='/') {
      const links=Object.keys(config.installers).map(p=>`<li><a href="/download/${p}">${p==='windows'?'Windows':'Mac'} installer</a></li>`).join('');
      return reply(request.method==='HEAD'?null:`<!doctype html><html lang="en"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>SmartStock installers</title><body><h1>SmartStock installers</h1><p>Download the current installer for your computer.</p><ul>${links}</ul>${links?'':'<p>No verified installer is available yet.</p>'}</body></html>`,200,{'Content-Type':'text/html; charset=utf-8'});
    }
    const artifact=config.installers[url.pathname.split('/')[2]];
    if(!artifact) return reply('Installer not available yet.',404);
    const object=request.method==='HEAD'?await env.UPDATE_BUCKET.head(artifact.key):await env.UPDATE_BUCKET.get(artifact.key);
    // Objects are uploaded and verified by the release operator before they are selected.
    if(!object || object.size!==artifact.size || object.customMetadata?.sha256!==artifact.sha256 || (artifact.etag && object.etag!==artifact.etag)) return reply('Installer verification unavailable.',503);
    return reply(request.method==='HEAD'?null:object.body,200,{
      'Content-Type':'application/octet-stream','Content-Length':String(object.size),
      'Content-Disposition':`attachment; filename="${artifact.filename}"`
    });
  }
};
