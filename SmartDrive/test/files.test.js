import assert from 'node:assert/strict';
import { once } from 'node:events';
import { mkdir, mkdtemp, rm, symlink, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { test } from 'node:test';
import { fileLibrary } from '../src/files.js';
import { driveServer } from '../src/server.js';

test('authorizes folder and preview access against the current employee stores', async t => {
    const root = await mkdtemp(join(tmpdir(), 'smartdrive-files-'));
    t.after(() => rm(root, { recursive: true, force: true }));
    const store = join(root, 'store');
    const outside = join(root, 'outside');
    await mkdir(store);
    await mkdir(outside);
    await writeFile(join(store, 'Proof.pdf'), 'test preview');
    await writeFile(join(store, 'Artwork.sspub'), 'test document');
    await writeFile(join(outside, 'Private.pdf'), 'private document');
    await symlink(outside, join(store, 'Link'), 'junction');

    const server = driveServer({
        library: fileLibrary({ storeRoots: { 1: store, 2: outside }, fileIdKey: Buffer.alloc(32, 5) }),
        employeeSession: async request => {
            const authorization = request.headers.authorization;
            return authorization ? { storeIds: [authorization] } : null;
        }
    });
    server.listen(0, '127.0.0.1');
    await once(server, 'listening');
    t.after(() => new Promise(resolve => server.close(resolve)));
    const origin = 'http://127.0.0.1:' + server.address().port;
    const get = (path, storeId) => fetch(origin + '/employees/drive' + path, {
        headers: storeId ? { authorization: storeId } : {}
    });

    const home = await get('/');
    assert.equal(home.status, 200);
    assert.match(await home.text(), /Employee sign in/u);
    assert.match(home.headers.get('content-security-policy'), /frame-ancestors 'none'/u);
    assert.equal((await get('/app.js')).status, 200);
    assert.match((await get('/navigation.js')).headers.get('content-type'), /^application\/javascript(?:;|$)/u);
    const icon = await get('/storage-icon.png');
    assert.equal(icon.headers.get('content-type'), 'image/png');
    assert.equal(Buffer.from(await icon.arrayBuffer()).subarray(0,8).toString('hex'), '89504e470d0a1a0a');
    assert.equal((await get('/api/stores')).status, 401);
    assert.equal((await get('/api/management/status', '1')).status, 403);
    assert.deepEqual(await (await get('/api/session', '1')).json(), { canManage: false });
    const stores = await (await get('/api/stores', '1')).json();
    assert.deepEqual(stores.map(item => item.store), ['1']);
    const files = '/api/files?id=' + stores[0].id;
    assert.equal((await get(files, '2')).status, 404);
    const entries = await (await get(files, '1')).json();
    assert.deepEqual(entries.map(item => [item.name, item.preview]), [
        ['Artwork.sspub', true], ['Proof.pdf', true]
    ]);
    assert.equal(entries[1].fileType, 'PDF');assert.equal(entries[1].sizeBytes, Buffer.byteLength('test preview'));
    assert.ok(Number.isFinite(Date.parse(entries[1].modifiedAt)));
    const path = '/api/preview?id=' + entries[1].id;
    assert.equal((await get(path, '2')).status, 404);
    assert.equal((await get(path + 'x', '1')).status, 404);
    const response = await get(path, '1');
    assert.equal(response.headers.get('content-type'), 'application/pdf');
    assert.equal(response.headers.get('cache-control'), 'private, no-store');
    assert.equal(await response.text(), 'test preview');
});
