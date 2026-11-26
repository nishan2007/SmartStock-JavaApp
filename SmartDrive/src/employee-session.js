export function smartStockEmployees({ employeeOrigin, employeeServer, publicOrigin = employeeOrigin }, fetchRequest = fetch) {
    const endpoint = new URL('/employees/api/v1/', employeeServer);
    const origin = new URL(employeeOrigin);
    const browserOrigin = new URL(publicOrigin);
    if (endpoint.protocol !== 'https:' || origin.protocol !== 'https:' ||
        browserOrigin.protocol !== 'https:' || browserOrigin.href !== browserOrigin.origin + '/' ||
        endpoint.username || endpoint.password || origin.href !== origin.origin + '/') {
        throw new Error('Employee session settings require an HTTPS server and origin.');
    }
    function cookies(request) {
        return (request.headers.cookie ?? '').split(';').map(value => value.trim()).filter(
            value => /^ss_employee(?:_browser)?=[A-Za-z0-9_-]{16,256}$/u.test(value)
        );
    }
    function send(request, route, body) {
        const headers = { Origin: origin.origin, Cookie: cookies(request).join('; '), 'Content-Type': 'application/json' };
        if (typeof request.headers['x-csrf-token'] === 'string') {
            headers['X-CSRF-Token'] = request.headers['x-csrf-token'];
        }
        return fetchRequest(new URL(route, endpoint), {
            method: 'POST', headers, body, redirect: 'error', signal: AbortSignal.timeout(10000)
        });
    }
    async function session(request, write = false) {
        if (write && request.headers.origin !== browserOrigin.origin) return null;
        const values = cookies(request);
        if (values.length !== 2 || new Set(values.map(cookie => cookie.split('=')[0])).size !== 2) {
            return null;
        }
        const response = await send(request, write ? 'auth/drive-authorize' : 'auth/drive-session', '{}');
        if (response.status === 401 || response.status === 403) return null;
        if (!response.ok) throw new Error('SmartStock employee session check failed.');
        const { ok, data } = await response.json();
        if (!ok || !Number.isSafeInteger(data?.userId) || !Array.isArray(data?.storeIds) ||
            !data.storeIds.every(id => typeof id === 'string' && /^[1-9]\d*$/u.test(id))) {
            throw new Error('SmartStock returned an invalid employee session.');
        }
        return data;
    }
    return {
        session,
        async auth(request, route, body) {
            if (request.headers.origin !== browserOrigin.origin) {
                return Response.json({ error: { message: 'Sign-in origin is not allowed.' } }, { status: 403 });
            }
            if (!['activate', 'auth/login', 'auth/verify', 'auth/session', 'auth/logout'].includes(route)) {
                return Response.json({ error: { message: 'Not found.' } }, { status: 404 });
            }
            if (route === 'auth/login') return send(request, 'auth/drive-login', body);
            if (route === 'auth/session' || route === 'auth/logout') {
                let value;
                try { value = JSON.parse(body.toString()); }
                catch { return Response.json({ error: { message: 'Invalid account request.' } }, { status: 400 }); }
                return send(request, route, JSON.stringify({ ...value, application: 'smartdrive' }));
            }
            return send(request, route, body);
        }
    };
}
