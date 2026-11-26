import assert from 'node:assert/strict';
import { test } from 'node:test';
import { gateway } from '../cloudflare/src/index.mjs';

test('permits only drive routes and forwards employee cookies through authenticated origin access', async () => {
    const env = { PUBLIC_ORIGIN: 'https://drive.example', DRIVE_ORIGIN: 'https://origin.example',
        DRIVE_ACCESS_CLIENT_ID: 'client', DRIVE_ACCESS_CLIENT_SECRET: 'secret' };
    let calls = 0;
    const worker = gateway(async (url, options) => {
        calls++;
        assert.equal(url.origin, env.DRIVE_ORIGIN);
        assert.equal(options.headers.get('CF-Access-Client-Secret'), 'secret');
        assert.equal(options.headers.get('Forwarded'), null);
        assert.equal(options.headers.get('Cookie'), 'ss_employee=session');
        const response = Response.json({ ok: true });
        response.headers.append('Set-Cookie', 'CF_Authorization=private-origin; Secure');
        response.headers.append('Set-Cookie', 'ss_employee=new-session; Path=/employees; Secure; HttpOnly');
        return response;
    });
    assert.equal((await worker.fetch(new Request('https://drive.example/employees/api/v1/history'), env)).status, 404);
    const endpoint = 'https://drive.example/employees/drive/api/management/restore';
    assert.equal((await worker.fetch(new Request(endpoint, { method: 'POST' }), env)).status, 403);
    const response = await worker.fetch(new Request(endpoint, { method: 'POST', body: '{}',
        headers: { Origin: env.PUBLIC_ORIGIN, Cookie: 'ss_employee=session', Forwarded: 'host=evil' } }), env);
    assert.equal(response.status, 200);
    assert.equal(response.headers.get('cache-control'), 'private, no-store');
    assert.deepEqual(response.headers.getSetCookie(), ['ss_employee=new-session; Path=/employees; Secure; HttpOnly']);
    assert.equal(calls, 1);
    const enrollment = 'https://drive.example/employees/drive/api/management/scanner-enroll';
    assert.equal((await worker.fetch(new Request(enrollment, { method: 'POST' }), env)).status, 403);
    assert.equal((await worker.fetch(new Request(enrollment, { method: 'POST', body: '{}',
        headers: { Origin: env.PUBLIC_ORIGIN, Cookie: 'ss_employee=session' } }), env)).status, 200);
    assert.equal(calls, 2);
    assert.equal((await worker.fetch(new Request('https://drive.example/employees/drive/navigation.js', {
        headers: { Cookie: 'ss_employee=session' }
    }), env)).status, 200);
    assert.equal(calls, 3);
});
