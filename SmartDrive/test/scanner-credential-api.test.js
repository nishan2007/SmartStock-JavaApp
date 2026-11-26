import assert from 'node:assert/strict';
import { once } from 'node:events';
import { mkdtemp, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { test } from 'node:test';
import { scannerCredentials } from '../src/scanner-credentials.js';
import { scannerControls } from '../src/scanner-controls.js';
import { managementAudit } from '../src/audit.js';
import { driveServer } from '../src/server.js';

test('scanner credential actions enforce manager, CSRF and store scope without auditing passwords', async t => {
    const root = await mkdtemp(join(tmpdir(), 'smartdrive-scanner-api-'));
    t.after(() => rm(root, { recursive: true, force: true }));
    const scanners = [{ id: 'Canon-C5040', storeId: '1', account: 'sdscan_c5040' }];
    const calls = [];
    const audit = managementAudit(root);
    const server = driveServer({ library: {}, audit, scannerControl: scannerControls(root, scanners),
        scannerCredentialControl: scannerCredentials({ root: join(root, 'credentials'), scanners, broker: async request => calls.push(request) }),
        employeeSession: async (request, write) => {
            if (write && request.headers['x-csrf-token'] !== 'valid') return null;
            return { userId: 7, storeIds: [request.headers['x-store'] ?? '1'],
                permissions: request.headers.authorization === 'manager' ? ['MANAGE_SMARTDRIVE'] : [] };
        } });
    server.listen(0, '127.0.0.1');await once(server, 'listening');
    t.after(() => new Promise(resolve => server.close(resolve)));
    const base = `http://127.0.0.1:${server.address().port}/employees/drive/api/management/`;
    const options = { method: 'POST', body: JSON.stringify({ id: 'Canon-C5040' }), headers: { authorization: 'manager', 'x-csrf-token': 'valid' } };
    assert.equal((await fetch(base + 'scanner-rotate', { ...options, headers: { authorization: 'manager' } })).status, 401);
    assert.equal((await fetch(base + 'scanner-rotate', { ...options, headers: { ...options.headers, authorization: 'employee' } })).status, 403);
    assert.equal((await fetch(base + 'scanner-rotate', { ...options, headers: { ...options.headers, 'x-store': '2' } })).status, 404);
    assert.equal(calls.length, 0);
    const response = await fetch(base + 'scanner-rotate', options);
    assert.equal(response.status, 200);assert.equal(response.headers.get('cache-control'), 'private, no-store');
    const secret = await response.json();assert.match(secret.password, /^[A-Za-z0-9!_-]{32,100}$/u);
    assert.equal(calls[0].action, 'scanner-rotate');
    const events = await audit.list({ storeIds: ['1'] });
    assert.equal(events.length, 2);assert.ok(!JSON.stringify(events).includes(secret.password));
    const listing = await (await fetch(base + 'scanners', { headers: options.headers })).json();
    assert.equal(listing[0].credentialState, 'active');assert.ok(!JSON.stringify(listing).includes(secret.password));
    assert.equal((await fetch(base + 'scanner-disable', options)).status, 200);
    assert.equal(calls.at(-1).action, 'scanner-disable');
    assert.equal((await (await fetch(base + 'scanners', { headers: options.headers })).json())[0].credentialState, 'disabled');
    assert.equal((await fetch(base + 'scanner-enable', { ...options, headers: { ...options.headers, authorization: 'employee' } })).status, 403);
    assert.equal((await fetch(base + 'scanner-enable', options)).status, 200);
    assert.equal(calls.at(-1).action, 'scanner-enable');
    assert.equal((await (await fetch(base + 'scanners', { headers: options.headers })).json())[0].credentialState, 'active');
});
