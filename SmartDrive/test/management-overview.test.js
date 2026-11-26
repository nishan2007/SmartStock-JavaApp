import assert from 'node:assert/strict';
import { once } from 'node:events';
import { test } from 'node:test';
import { backupOverview, effectiveSettings } from '../src/management-overview.js';
import { driveServer } from '../src/server.js';

test('backup overview scopes counts, failures, selections and verification timestamps to assigned stores', async () => {
    const backups = { versions: async () => [
        { store: '1', status: 'verified', verifiedAt: '2026-11-01T12:00:00Z' },
        { store: '1', status: 'pending', attempts: 0 },
        { store: '1', status: 'pending', attempts: 2 },
        { store: '2', status: 'verified', verifiedAt: '2026-11-08T12:00:00Z' },
        { store: '2', status: 'pending', attempts: 10 }
    ] };
    const selections = { read: async () => [{ store: '1' }, { store: '2' }] };
    const result = await backupOverview({ storeIds: ['1'] }, backups, selections);
    assert.deepEqual(result, { state: 'needs-attention', selectedFolders: 1, verified: 1, pending: 2, failed: 1, lastVerifiedAt: '2026-11-01T12:00:00Z' });
    assert.equal((await backupOverview({ storeIds: ['3'] }, backups, selections)).state, 'no-folders-selected');
    assert.equal((await backupOverview({ storeIds: ['1'] })).state, 'not-configured');
    const failed = await backupOverview({ storeIds: ['1'] }, { versions: async () => { throw new Error('private credentials'); } });
    assert.deepEqual(failed, { state: 'unavailable' });
});

test('settings and status require a manager and whitelist effective configuration without credentials', async t => {
    const config = { installationName: 'DeckersDrive', displayName: 'Deckers Shared Files', dataParent: 'D:\\Shares',
        serverName: 'DECKERSDRIVE', publicOrigin: 'https://drive.example', storeRoots: { 1: 'private-store-path', 2: 'other-private-path' },
        oneDrive: { driveId: 'drive', folderId: 'folder', clientSecret: 'never-expose', tenantId: 'tenant-not-needed' },
        environment: { privateKey: 'never-expose' } };
    const server = driveServer({ library: {}, status: async () => ({ service: 'running' }),
        settings: identity => effectiveSettings(config, identity),
        backups: { versions: async () => [{ store: '1', status: 'verified', verifiedAt: '2026-11-01T12:00:00Z' }] },
        employeeSession: async request => request.headers.authorization ? { storeIds: ['1'],
            permissions: request.headers.authorization === 'manager' ? ['MANAGE_SMARTDRIVE'] : [] } : null });
    server.listen(0, '127.0.0.1');await once(server, 'listening');
    t.after(() => new Promise(resolve => server.close(resolve)));
    const base = `http://127.0.0.1:${server.address().port}/employees/drive/api/management/`;
    for (const route of ['settings', 'status']) {
        assert.equal((await fetch(base + route)).status, 401);
        assert.equal((await fetch(base + route, { headers: { authorization: 'employee' } })).status, 403);
    }
    const response = await fetch(base + 'settings', { headers: { authorization: 'manager' } });
    assert.equal(response.status, 200);assert.equal(response.headers.get('cache-control'), 'private, no-store');
    const settings = await response.json();assert.deepEqual(settings.stores, ['1']);assert.equal(settings.share, '\\\\DECKERSDRIVE\\DeckersDrive');
    assert.equal(settings.backup.folderId, 'folder');
    for (const secret of ['never-expose', 'private-store-path', 'tenant-not-needed']) assert.ok(!JSON.stringify(settings).includes(secret));
    const status = await (await fetch(base + 'status', { headers: { authorization: 'manager' } })).json();
    assert.equal(status.backups.verified, 1);assert.equal(status.service, 'running');
});
