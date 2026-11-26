import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { mkdir, mkdtemp, readFile, rm, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { test } from 'node:test';
import { exportStudioLibrary } from '../src/migration.js';
import { importStudioExport } from '../src/import-library.js';
import { fileLibrary } from '../src/files.js';

test('exports verified designs with identities and store sharing and rejects corrupt legacy bytes', async t => {
    const root = await mkdtemp(join(tmpdir(), 'smartdrive-migration-'));
    t.after(() => rm(root, { recursive: true, force: true }));
    const source = join(root, 'legacy');
    const folder = join(source, 'store-1-Georgetown');
    await mkdir(folder, { recursive: true });
    const id = '11111111-1111-4111-8111-111111111111';
    const content = '{"name":"Badge"}';
    const sha256 = createHash('sha256').update(content).digest('hex');
    const row = { id, name: 'Badge', sha256, size: Buffer.byteLength(content), ownerStoreId: 1,
        sharedStoreIds: [2], assignmentVersion: 'original-assignment' };
    const revision = join(folder, sha256 + '.sstbadge');
    await writeFile(revision, content);
    await writeFile(join(folder, id + '.head.json'), JSON.stringify(row));
    const destination = join(root, 'export');
    const result = await exportStudioLibrary({ source, destination });
    assert.deepEqual(result.records[0].metadata, row);
    assert.equal(await readFile(join(destination, ...result.records[0].path.split('/')), 'utf8'), content);
    assert.equal(JSON.parse(await readFile(join(destination, 'manifest.json'), 'utf8')).records.length, 1);
    const imported = join(root, 'import');
    const catalog = await importStudioExport({ source: destination, destination: imported, configuredStores: ['1', '2'] });
    assert.equal(catalog.records.length, 1);
    assert.deepEqual(catalog.records[0].legacy, { kind: 'BADGE', storageStoreId: '1', id });
    assert.deepEqual(catalog.sharedFolders[0].storeIds, ['1', '2']);
    const ownerRoot = join(imported, 'Stores', '1');
    await writeFile(join(ownerRoot, 'Private.pdf'), 'private');
    const files = fileLibrary({ storeRoots: { 1: ownerRoot, 2: join(imported, 'Stores', '2') },
        sharedFolders: catalog.sharedFolders, migrationRecords: catalog.records, fileIdKey: Buffer.alloc(32, 8) });
    const shared = { storeIds: ['2'] };
    const desktop = await files.desktopTemplates(shared, 'BADGE');
    assert.equal(desktop.length, 1);
    assert.equal(desktop[0].id, id);
    assert.deepEqual(desktop[0].parts, catalog.records[0].parts);
    assert.deepEqual(await files.desktopTemplates({ storeIds: ['3'] }, 'BADGE'), []);
    const resolved = await files.template(shared, 'BADGE', '1', id);
    assert.equal(resolved.name, catalog.records[0].parts.at(-1));
    await assert.rejects(files.template({ storeIds: ['3'] }, 'BADGE', '1', id), /unavailable/u);
    await assert.rejects(files.template(shared, 'BADGE', '2', id), /unavailable/u);
    assert.throws(() => fileLibrary({ storeRoots: { 1: ownerRoot, 2: join(imported, 'Stores', '2') },
        migrationRecords: [catalog.records[0], catalog.records[0]], fileIdKey: Buffer.alloc(32, 8) }), /Duplicate/u);
    let folderId = files.stores(shared).find(store => store.store === '1').id;
    for (const part of catalog.records[0].parts.slice(0, -1)) {
        const entries = await files.list(shared, folderId);
        assert.deepEqual(entries.map(entry => entry.name), [part]);
        folderId = entries[0].id;
    }
    assert.equal((await files.list(shared, folderId))[0].name, catalog.records[0].parts.at(-1));
    assert.equal((await files.list(shared, folderId))[0].id, resolved.id);
    await assert.rejects(files.list({ storeIds: ['3'] }, folderId), /unavailable/u);
    await writeFile(revision, 'corrupt');
    await assert.rejects(exportStudioLibrary({ source, destination: join(root, 'bad'), dryRun: true }), /checksum/u);
});
