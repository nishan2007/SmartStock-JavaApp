import assert from 'node:assert/strict';
import { mkdir, mkdtemp, rm, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { test } from 'node:test';
import { managementStatus } from '../src/management.js';
import { once } from 'node:events';
import { driveServer } from '../src/server.js';

test('management storage and scanner results are scoped to the current employee stores', async t => {
    const root = await mkdtemp(join(tmpdir(), 'smartdrive-management-'));
    t.after(() => rm(root, { recursive: true, force: true }));
    const state = join(root, 'private');
    const scanner = join(state, 'Canon-C5040');
    await mkdir(join(scanner, 'quarantine'), { recursive: true });
    await writeFile(join(scanner, 'quarantine', 'scan.pdf'), 'rejected');
    await writeFile(join(scanner, 'status.json'), JSON.stringify({
        lastResult: 'published', at: '2026-11-07T12:00:00Z', file: 'private-filename.pdf'
    }));
    const status = managementStatus({
        storeRoots: { 1: root, 2: join(root, 'inaccessible') },
        scanStateRoot: state,
        scanners: [
            { id: 'Canon-C5040', storeId: '1' },
            { id: 'OtherStore', storeId: '2' }
        ]
    }, () => ({ state: 'needs-attention', active: 0, queued: 0 }));
    const result = await status({ storeIds: ['1'] });
    assert.deepEqual(result.previews, { state: 'needs-attention', active: 0, queued: 0 });
    assert.deepEqual(result.stores.map(store => store.store), ['1']);
    assert.ok(BigInt(result.stores[0].availableBytes) > 0n);
    assert.deepEqual(result.scanners, [{
        id: 'Canon-C5040', store: '1', enabled: true, quarantineCount: 1,
        lastResult: { result: 'published', at: '2026-11-07T12:00:00Z' }
    }]);
});

test('backup actions require management permission and write-session authorization', async t => {
    let restores = 0;
    const server = driveServer({
        library: {},
        audit: { action: async (identity, action, stores, details, operation) => operation() },
        employeeSession: async (request, write) => {
            if (write && request.headers['x-csrf-token'] !== 'valid') return null;
            return { storeIds: ['1'], permissions: request.headers.authorization === 'manager' ? ['MANAGE_SMARTDRIVE'] : [] };
        },
        backups: {
            versions: async () => [
                { id: '11111111-1111-4111-8111-111111111111', store: '1', parts: ['scan.pdf'], status: 'verified' },
                { id: '2', store: '2', parts: ['private.pdf'], status: 'verified' }
            ],
            restore: async (id, stores) => {
                assert.deepEqual(stores, ['1']);
                restores++;
                return { originalPath: 'scan.pdf' };
            }
        }
    });
    server.listen(0, '127.0.0.1');
    await once(server, 'listening');
    t.after(() => new Promise(resolve => server.close(resolve)));
    const origin = 'http://127.0.0.1:' + server.address().port + '/employees/drive/api/management/';
    const versions = await fetch(origin + 'versions', { headers: { authorization: 'manager' } });
    assert.deepEqual((await versions.json()).map(item => item.store), ['1']);
    const options = { method: 'POST', body: JSON.stringify({ id: '11111111-1111-4111-8111-111111111111' }),
        headers: { authorization: 'manager' } };
    assert.equal((await fetch(origin + 'restore', options)).status, 401);
    options.headers['x-csrf-token'] = 'valid';
    options.headers.authorization = 'employee';
    assert.equal((await fetch(origin + 'restore', options)).status, 403);
    options.headers.authorization = 'manager';
    assert.equal((await fetch(origin + 'restore', options)).status, 200);
    assert.equal(restores, 1);
});
