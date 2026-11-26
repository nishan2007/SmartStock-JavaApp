const prefix = '/employees/drive';
const reads = new Set([
    '/', '/app.js', '/navigation.js', '/app.css', '/storage-icon.png', '/api/branding', '/api/session', '/api/stores',
    '/api/files', '/api/template', '/api/preview', '/api/management/status', '/api/management/versions',
    '/api/management/selections', '/api/management/activity', '/api/management/scanners', '/api/management/workstations', '/api/management/settings'
]);
const writes = new Set([
    '/api/account/activate', '/api/account/auth/login', '/api/account/auth/verify',
    '/api/account/auth/session', '/api/account/auth/logout', '/api/management/retry',
    '/api/management/restore', '/api/management/select', '/api/management/scanner-processing',
    '/api/management/scanner-disable', '/api/management/scanner-enable', '/api/management/scanner-rotate', '/api/management/scanner-enroll',
    '/api/management/workstations/enroll', '/api/management/workstations/revoke', '/api/enroll'
]);

function secure(response) {
    const result = new Response(response.body, response);
    const cookies = response.headers.getSetCookie?.() ?? response.headers.getAll?.('Set-Cookie') ?? [];
    result.headers.delete('Set-Cookie');
    for (const cookie of cookies) {
        if (/^ss_employee(?:_browser)?=/u.test(cookie)) result.headers.append('Set-Cookie', cookie);
    }
    result.headers.set('Cache-Control', 'private, no-store');
    result.headers.set('X-Content-Type-Options', 'nosniff');
    result.headers.set('Referrer-Policy', 'no-referrer');
    result.headers.set('X-Frame-Options', 'DENY');
    result.headers.set('Strict-Transport-Security', 'max-age=31536000');
    return result;
}
const error = (status, message) => secure(Response.json({ error: message }, { status }));

export function gateway(requestOrigin = fetch) {
    return {
        async fetch(request, env) {
            let publicOrigin, origin;
            try {
                publicOrigin = new URL(env.PUBLIC_ORIGIN);
                origin = new URL(env.DRIVE_ORIGIN);
                if (publicOrigin.protocol !== 'https:' || origin.protocol !== 'https:' ||
                    publicOrigin.href !== publicOrigin.origin + '/' || origin.href !== origin.origin + '/' ||
                    !env.DRIVE_ACCESS_CLIENT_ID || !env.DRIVE_ACCESS_CLIENT_SECRET) throw new Error();
            } catch { return error(503, 'Drive gateway configuration is incomplete.'); }
            const url = new URL(request.url);
            if (url.origin !== publicOrigin.origin) return error(404, 'Not found.');
            if (request.method === 'GET' && ['/', prefix].includes(url.pathname)) {
                return secure(Response.redirect(publicOrigin.origin + prefix + '/', 302));
            }
            if (!url.pathname.startsWith(prefix)) return error(404, 'Not found.');
            const route = url.pathname.slice(prefix.length);
            if (!reads.has(route) && !writes.has(route)) return error(404, 'Not found.');
            const method = writes.has(route) ? 'POST' : 'GET';
            if (request.method !== method) return error(405, method + ' is required.');
            if (method === 'POST' && request.headers.get('Origin') !== publicOrigin.origin) {
                return error(403, 'This web origin is not allowed.');
            }
            const target = new URL(url.pathname + url.search, origin);
            const headers = new Headers({
                'CF-Access-Client-Id': env.DRIVE_ACCESS_CLIENT_ID,
                'CF-Access-Client-Secret': env.DRIVE_ACCESS_CLIENT_SECRET
            });
            for (const name of ['Cookie', 'Content-Type', 'X-CSRF-Token', 'Accept']) {
                const value = request.headers.get(name);
                if (value) {
                    if (name === 'Cookie') {
                        headers.set(name, value.split(';').map(part => part.trim())
                            .filter(part => /^ss_employee(?:_browser)?=/u.test(part)).join('; '));
                    } else headers.set(name, value);
                }
            }
            if (method === 'POST') headers.set('Origin', publicOrigin.origin);
            let body;
            if (method === 'POST') {
                const reader = request.body?.getReader();
                const chunks = [];
                let size = 0;
                if (reader) {
                    while (true) {
                        const part = await reader.read();
                        if (part.done) break;
                        size += part.value.length;
                        if (size > 10000) { await reader.cancel(); return error(413, 'Request is too large.'); }
                        chunks.push(part.value);
                    }
                }
                body = new Uint8Array(size);
                let offset = 0;
                for (const chunk of chunks) { body.set(chunk, offset); offset += chunk.length; }
            }
            try {
                const response = await requestOrigin(target, {
                    method, headers, body, redirect: 'manual', signal: AbortSignal.timeout(120000),
                    cf: { cacheTtl: 0, cacheEverything: false }
                });
                if (response.status >= 300 && response.status < 400) {
                    return error(503, 'The drive origin requires attention.');
                }
                return secure(response);
            } catch { return error(503, 'DeckersDrive is temporarily unavailable.'); }
        }
    };
}
export default gateway();
