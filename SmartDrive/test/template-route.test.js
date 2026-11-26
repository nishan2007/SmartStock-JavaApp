import assert from 'node:assert/strict';
import { once } from 'node:events';
import { mkdir, mkdtemp, rm, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { test } from 'node:test';
import { fileLibrary } from '../src/files.js';
import { driveServer } from '../src/server.js';

test('resolves migrated identities over HTTP only for signed-in employees with current store access', async t => {
    const root = await mkdtemp(join(tmpdir(), 'smartdrive-template-route-'));
    t.after(() => rm(root, { recursive: true, force: true }));
    const owner = join(root, '1');
    const parts = ['Studio Jobs', 'BADGE', 'Access-1-2', 'Badge.sstbadge'];
    await mkdir(join(owner, ...parts.slice(0, -1)), { recursive: true });
    await writeFile(join(owner, ...parts), '{}');
    const template = '11111111-1111-4111-8111-111111111111';
    const server = driveServer({
        library: fileLibrary({ storeRoots: { 1: owner, 2: join(root, '2') },
            fileIdKey: Buffer.alloc(32, 3),
            sharedFolders: [{ store: '1', parts: parts.slice(0, -1), storeIds: ['1', '2'] }],
            migrationRecords: [{ legacy: { kind: 'BADGE', storageStoreId: '1', id: template }, store: '1', parts }] }),
        employeeSession: async request => request.headers.authorization ? {
            storeIds: [request.headers.authorization], permissions: []
        } : null
    });
    server.listen(0, '127.0.0.1');
    await once(server, 'listening');
    t.after(() => new Promise(resolve => server.close(resolve)));
    const route = `http://127.0.0.1:${server.address().port}/employees/drive/api/template?kind=BADGE&store=1&template=${template}`;
    assert.equal((await fetch(route)).status, 401);
    const shared = await fetch(route, { headers: { authorization: '2' } });
    assert.equal(shared.status, 200);
    assert.equal(shared.headers.get('cache-control'), 'private, no-store');
    const body = await shared.json();
    assert.deepEqual(Object.keys(body).sort(), ['id', 'name']);
    assert.equal(body.name, 'Badge.sstbadge');
    assert.equal((await fetch(route, { method: 'POST' })).status, 405);
    const denied = await fetch(route, { headers: { authorization: '3' } });
    const missing = await fetch(route.replace(template, 'missing'), { headers: { authorization: '2' } });
    assert.equal(denied.status, 404);
    assert.equal(missing.status, 404);
    assert.deepEqual(await denied.json(), await missing.json());
    await rm(join(owner, ...parts));
    assert.equal((await fetch(route, { headers: { authorization: '2' } })).status, 404);
});

test('desktop bridge requires its service credential and validates employee scope before returning native paths', async t => {
    let calls = 0;
    const token = 'a'.repeat(64);
    const server = driveServer({ library: { desktopTemplates: async (identity, kind) => {
        calls++;
        assert.deepEqual(identity.storeIds, ['2']);
        assert.equal(kind, 'BADGE');
        return [{ id: 'design-id', ownerStoreId: '1', parts: ['Studio Jobs', 'BADGE', 'Access-1-2', 'Badge.sstbadge'] }];
    } }, employeeSession: async () => { throw new Error('Bridge must not use browser identity'); },
    studioBridge: { token, server: 'DECKERSDRIVE', share: 'DeckersDrive' } });
    server.listen(0, '127.0.0.1');await once(server, 'listening');
    t.after(() => new Promise(resolve => server.close(resolve)));
    const url = `http://127.0.0.1:${server.address().port}/internal/studio/catalog`;
    const options = { method: 'POST', headers: { 'X-SmartDrive-Service': token }, body: JSON.stringify({ storeIds: ['2'], kind: 'BADGE' }) };
    assert.equal((await fetch(url, { ...options, headers: {} })).status, 404);
    assert.equal((await fetch(url, { ...options, headers: { 'X-SmartDrive-Service': 'b'.repeat(64) } })).status, 404);
    assert.equal((await fetch(url, { ...options, body: JSON.stringify({ storeIds: ['../1'], kind: 'BADGE' }) })).status, 400);
    assert.equal(calls, 0);
    const response = await fetch(url, options);
    assert.equal(response.status, 200);
    const body = await response.json();
    assert.equal(body.networkRoot, '\\\\DECKERSDRIVE\\DeckersDrive');
    assert.equal(body.templates[0].networkPath, '\\\\DECKERSDRIVE\\DeckersDrive\\1\\Studio Jobs\\BADGE\\Access-1-2\\Badge.sstbadge');
    assert.equal(body.templates[0].parts, undefined);
    assert.equal(calls, 1);
});
