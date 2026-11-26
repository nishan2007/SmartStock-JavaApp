import assert from 'node:assert/strict';
import { test } from 'node:test';
import { smartStockEmployees } from '../src/employee-session.js';

test('validates the employee session with SmartStock and forwards only its two cookies', async () => {
    const employee = 'ss_employee=' + 'a'.repeat(40);
    const browser = 'ss_employee_browser=' + 'b'.repeat(40);
    let calls = 0;
    const identity = { userId: 7, storeIds: ['2'], permissions: [] };
    const { session: check } = smartStockEmployees({
        employeeServer: 'https://stock.internal',
        employeeOrigin: 'https://drive.example'
    }, async (url, options) => {
        calls++;
        assert.equal(url.href, 'https://stock.internal/employees/api/v1/auth/drive-session');
        assert.equal(options.headers.Origin, 'https://drive.example');
        assert.equal(options.headers.Cookie, employee + '; ' + browser);
        assert.equal(options.redirect, 'error');
        return calls === 1 ? Response.json({ ok: true, data: identity }) : new Response(null, { status: 403 });
    });
    assert.equal(await check({ headers: { cookie: employee } }), null);
    const request = { headers: { cookie: employee + '; unrelated=private; ' + browser } };
    assert.deepEqual(await check(request), identity);
    assert.equal(await check(request), null);
    assert.equal(calls, 2);
});

test('allows employee sign-in only from the configured browser origin and only for auth routes', async () => {
    let calls = 0;
    const { auth } = smartStockEmployees({
        employeeServer: 'https://stock.internal',
        employeeOrigin: 'https://employee.example',
        publicOrigin: 'https://drive.example'
    }, async (url, options) => {
        calls++;
        assert.equal(url.href, 'https://stock.internal/employees/api/v1/auth/drive-login');
        assert.equal(options.headers.Origin, 'https://employee.example');
        assert.equal(options.body, '{}');
        return Response.json({ ok: true });
    });
    assert.equal((await auth({ headers: { origin: 'https://other.example' } }, 'auth/login', '{}')).status, 403);
    const request = { headers: { origin: 'https://drive.example' } };
    assert.equal((await auth(request, 'history', '{}')).status, 404);
    assert.equal((await auth(request, 'auth/login', '{}')).status, 200);
    assert.equal(calls, 1);
});
