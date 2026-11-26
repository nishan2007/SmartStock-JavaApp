import assert from 'node:assert/strict';
import { mkdir, mkdtemp, rename, rm, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { test } from 'node:test';
import { fileLibrary } from '../src/files.js';

test('finds renamed and newly identified designs, preserves store scope and refuses ambiguous identities', async t => {
    const root = await mkdtemp(join(tmpdir(), 'smartdrive-index-'));
    t.after(() => rm(root, { recursive: true, force: true }));
    const store = join(root, '1');
    const parts = ['Studio Jobs', 'BADGE', 'Access-1-2', 'Original.sstbadge'];
    const folder = join(store, ...parts.slice(0, -1));
    await mkdir(folder, { recursive: true });
    const identity = { id: '11111111-1111-4111-8111-111111111111', storageStoreId: '1', kind: 'BADGE' };
    const original = join(store, ...parts);
    await writeFile(original, JSON.stringify({ name: 'Original', smartDrive: identity }));
    const config = { storeRoots: { 1: store, 2: join(root, '2') }, fileIdKey: Buffer.alloc(32, 9),
        sharedFolders: [{ store: '1', parts: parts.slice(0, -1), storeIds: ['1', '2'] }],
        migrationRecords: [{ store: '1', parts, legacy: identity }] };
    const shared = { storeIds: ['2'] };
    const before = await fileLibrary(config).template(shared, 'BADGE', '1', identity.id);
    const renamed = join(folder, 'Renamed.sstbadge');
    await rename(original, renamed);
    const restarted = fileLibrary(config);
    const after = await restarted.template(shared, 'BADGE', '1', identity.id);
    assert.equal(after.name, 'Renamed.sstbadge');
    assert.notEqual(after.id, before.id);
    assert.equal((await restarted.desktopTemplates(shared, 'BADGE'))[0].parts.at(-1), 'Renamed.sstbadge');
    const fresh = { ...identity, id: '22222222-2222-4222-8222-222222222222' };
    await writeFile(join(folder, 'Fresh.sstbadge'), JSON.stringify({ name: 'Fresh', smartDrive: fresh }));
    assert.equal((await restarted.desktopTemplates(shared, 'BADGE')).length, 2);
    await assert.rejects(restarted.template({ storeIds: ['3'] }, 'BADGE', '1', identity.id), /unavailable/u);
    await writeFile(join(folder, 'Duplicate.sstbadge'), JSON.stringify({ name: 'Duplicate', smartDrive: identity }));
    await assert.rejects(restarted.template(shared, 'BADGE', '1', identity.id), /unavailable/u);
    assert.equal((await restarted.desktopTemplates(shared, 'BADGE')).length, 1);
    await rm(join(folder, 'Duplicate.sstbadge'));
    const privateFolder = join(store, 'Studio Jobs', 'BADGE', 'Private');
    await mkdir(privateFolder);
    await rename(renamed, join(privateFolder, 'Renamed.sstbadge'));
    await assert.rejects(restarted.template(shared, 'BADGE', '1', identity.id), /unavailable/u);
    assert.equal((await restarted.template({ storeIds: ['1'] }, 'BADGE', '1', identity.id)).name, 'Renamed.sstbadge');
});
