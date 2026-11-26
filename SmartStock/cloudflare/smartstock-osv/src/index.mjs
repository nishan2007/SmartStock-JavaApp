import { bounded } from '../../smartstock-storefront/src/studio-capture.js';

const origin = 'https://osv.deckers.gy';
const security = {
  'Cache-Control': 'no-store',
  'Referrer-Policy': 'no-referrer',
  'X-Content-Type-Options': 'nosniff',
  'X-Robots-Tag': 'noindex, nofollow',
  'Content-Security-Policy': "default-src 'none'; script-src 'self'; style-src 'self'; img-src 'self' blob:; connect-src 'self'; base-uri 'none'; frame-ancestors 'none'; form-action 'none'",
  'Permissions-Policy': 'camera=(self), microphone=(), geolocation=()',
};
const answer = (message, status) => Response.json({ message }, { status, headers: security });
const actions = new Set(['INFO', 'SETUP', 'LOGIN', 'FORM', 'SUBMIT', 'REMOVE_BACKGROUND']);
const uuid = /^[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}$/;

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    if (url.hostname !== 'osv.deckers.gy') return answer('Not found.', 404);
    if (url.protocol !== 'https:') {
      return ['GET', 'HEAD'].includes(request.method)
        ? Response.redirect(origin + url.pathname + url.search, 308)
        : answer('Use the secure permit website.', 403);
    }
    if (url.pathname !== '/api') {
      if (!['GET', 'HEAD'].includes(request.method)) return answer('Use GET.', 405);
      const asset = await env.ASSETS.fetch(request);
      const headers = new Headers(asset.headers);
      for (const [name, value] of Object.entries(security)) headers.set(name, value);
      return new Response(asset.body, { status: asset.status, headers });
    }
    if (request.method !== 'POST') return answer('Use POST.', 405);
    if (request.headers.get('Origin') !== origin) return answer('Open the permit website directly.', 403);
    if (!/^application\/json(?:\s*;|$)/i.test(request.headers.get('Content-Type') || '')) return answer('Send JSON.', 415);
    const instance = url.searchParams.get('instance');
    if (!uuid.test(instance || '')) return answer('Open the complete permit invitation.', 400);
    let input;
    try {
      const bytes = await bounded(request, 3 * 1024 * 1024);
      input = JSON.parse(new TextDecoder().decode(bytes));
      if (!input || !actions.has(input.action) || !/^[A-Za-z0-9_-]{43}$/.test(input.token || '')) {
        return answer('Open the complete permit invitation.', 400);
      }
    } catch (error) {
      return error.status === 413 ? answer('The photo is too large.', 413) : answer('Check the form and try again.', 400);
    }
    const destination = new URL('/shop/template-submit/api', 'https://www.deckers.gy');
    destination.searchParams.set('instance', instance);
    try {
      const headers = { 'Content-Type': 'application/json', Origin: destination.origin };
      const peer = request.headers.get('CF-Connecting-IP');
      if (peer) headers['CF-Connecting-IP'] = peer;
      const result = await env.STOREFRONT.fetch(destination, {
        method: 'POST', headers, redirect: 'manual',
        body: JSON.stringify({ ...input, portal: 'OREALLA' }),
      });
      if (![200, 400, 403, 409, 413, 429].includes(result.status)) return answer('The store is unavailable. Keep your entries and try again.', 503);
      return new Response(result.body, { status: result.status, headers: { ...security, 'Content-Type': 'application/json' } });
    } catch {
      return answer('The store is unavailable. Keep your entries and try again.', 503);
    }
  },
};
