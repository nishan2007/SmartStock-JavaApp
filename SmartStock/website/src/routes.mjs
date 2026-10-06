import {serviceFromPath as matchService} from './services.mjs';
const routes = new Set(['home','services','business','about','create','made','project','product','shop','bag','checkout','account','login','stores','confirmation','help']);
export function editorialFromPath(pathname){
  const match=/^\/shop\/(services|business|about)\/?$/.exec(pathname);
  return match?.[1]||'';
}
export function productFromPath(pathname) {
  const match=/^\/shop\/products\/([1-9]\d*)\/([1-9]\d*)\/?$/.exec(pathname);
  return match?{storeId:Number(match[1]),id:Number(match[2])}:null;
}
export function projectFromPath(pathname) {
  const match = /^\/shop\/projects\/([1-9]\d*)\/([0-9a-f-]{36})\/?$/i.exec(pathname);
  return match ? {storeId:Number(match[1]),id:match[2].toLowerCase()} : null;
}
export function serviceFromPath(pathname) {
  return matchService(pathname)?.name||'';
}
export function routeFromLocation(pathname,hash,transient=false) {
  return projectFromPath(pathname)?'project':productFromPath(pathname)?'product':serviceFromPath(pathname)?'create':editorialFromPath(pathname)||routeFromHash(hash,transient);
}
export function projectFromHash(hash) {
  const match = /^#project\/([0-9a-f-]{36})$/i.exec(hash);
  return match ? match[1].toLowerCase() : '';
}
export function createProjectFromHash(hash) {
  const match = /^#create\/project\/([0-9a-f-]{36})$/i.exec(hash);
  return match ? match[1].toLowerCase() : '';
}
export function topicFromHash(hash) {
  const match = /^#create\/([^?#]+)$/.exec(hash);
  if (!match) return '3D Printing';
  try { return decodeURIComponent(match[1]).slice(0, 100) || '3D Printing'; }
  catch { return '3D Printing'; }
}
export function routeFromHash(hash, transient = false) {
  const route = hash.replace(/^#/, '').split('/')[0];
  if (!routes.has(route)) return 'home';
  if (!transient && route === 'checkout') return 'bag';
  if (!transient && route === 'confirmation') return 'account';
  return route;
}
