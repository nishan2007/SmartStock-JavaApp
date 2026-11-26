import assert from 'node:assert/strict';
import { copyFile, mkdir, mkdtemp, readFile, rename, rm, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { test } from 'node:test';
import { readFileIdentity, writeFileIdentity } from '../src/file-identity.js';
import { fileLibrary } from '../src/files.js';
import { backupLibrary } from '../src/backup.js';

test('Windows identity streams preserve original bytes, survive first rename and round-trip through backup', { skip: process.platform !== 'win32' }, async t => {
    const root = await mkdtemp(join(tmpdir(), 'smartdrive-file-identity-'));
    t.after(() => rm(root, { recursive: true, force: true }));
    const store = join(root, 'store');
    const folder = join(store, 'Studio Jobs', 'BADGE');
    const remote = join(root, 'remote');
    await mkdir(folder, { recursive: true });await mkdir(remote);
    const source = join(folder, 'Original.sstbadge');
    const content = '{"name":"Migrated badge"}';
    await writeFile(source, content);
    const identity = { id: '11111111-1111-4111-8111-111111111111', storageStoreId: '1', kind: 'BADGE' };
    await writeFileIdentity(source, identity);
    assert.equal(await readFile(source, 'utf8'), content);
    assert.deepEqual(await readFileIdentity(source), identity);
    const renamed = join(folder, 'Renamed.sstbadge');await rename(source, renamed);
    const library = fileLibrary({ storeRoots: { 1: store }, fileIdKey: Buffer.alloc(32, 9),
        migrationRecords: [{ store: '1', parts: ['Studio Jobs', 'BADGE', 'Original.sstbadge'], legacy: identity }] });
    assert.equal((await library.template({ storeIds: ['1'] }, 'BADGE', '1', identity.id)).name, 'Renamed.sstbadge');
    const backup = backupLibrary({ storeRoots: { 1: store }, stateRoot: join(root, 'state'), recoveryRoot: join(root, 'recovery'),
        cloud: { upload: async (id, source) => writeFile(join(remote, id), await readFile(source)),
            download: (id, target) => copyFile(join(remote, id), target) } });
    const version = await backup.capture('1', ['Studio Jobs', 'BADGE', 'Renamed.sstbadge']);
    assert.deepEqual(version.fileIdentity, identity);
    await backup.uploadPending();
    const restored = await backup.restore(version.id, ['1']);
    assert.deepEqual(await readFileIdentity(restored.path), identity);
    assert.equal(await readFile(restored.path, 'utf8'), content);
    await writeFileIdentity(renamed, { ...identity, id: '22222222-2222-4222-8222-222222222222' });
    const reassigned = await backup.capture('1', ['Studio Jobs', 'BADGE', 'Renamed.sstbadge']);
    assert.ok(reassigned);
    assert.equal(reassigned.sha256, version.sha256);
    assert.notEqual(reassigned.fileIdentity.id, version.fileIdentity.id);
});
