import assert from 'node:assert/strict';
import { copyFile, link, mkdir, mkdtemp, readFile, readdir, rm, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { test } from 'node:test';
import { backupLibrary } from '../src/backup.js';
import { createHash } from 'node:crypto';

test('queues healthy files when another entry fails and succeeds after the failing entry is removed', async t => {
    const root = await mkdtemp(join(tmpdir(), 'smartdrive-partial-backup-'));
    t.after(() => rm(root, { recursive: true, force: true }));
    const store = join(root, 'store');
    const selected = join(store, 'Selected');
    const remote = join(root, 'remote');
    await mkdir(selected, { recursive: true });await mkdir(remote);
    const outside = join(root, 'unselected');await writeFile(outside, 'must not capture');
    const bad = join(selected, 'a-linked');await link(outside, bad);
    await writeFile(join(selected, 'z-good.pdf'), 'healthy file');
    const backup = backupLibrary({ stateRoot: join(root, 'state'), recoveryRoot: join(root, 'recovery'), storeRoots: { 1: store },
        cloud: { upload: (id, source) => copyFile(source, join(remote, id)), download: (id, target) => copyFile(join(remote, id), target) } });
    await assert.rejects(backup.captureFolder('1', ['Selected']), error => error instanceof AggregateError && error.errors.length === 1);
    assert.deepEqual((await backup.versions()).map(record => record.parts), [['Selected', 'z-good.pdf']]);
    await backup.uploadPending();
    const record = (await backup.versions())[0];assert.equal(record.status, 'verified');
    assert.equal(await readFile((await backup.restore(record.id, ['1'])).path, 'utf8'), 'healthy file');
    await rm(bad);
    await backup.captureFolder('1', ['Selected']);
    assert.equal((await backup.versions()).length, 1);
});

test('keeps verified versions after source deletion and verifies restored bytes', async t => {
    const root = await mkdtemp(join(tmpdir(), 'smartdrive-backup-'));
    t.after(() => rm(root, { recursive: true, force: true }));
    const store = join(root, 'store');
    const remote = join(root, 'remote');
    await mkdir(store);
    await mkdir(remote);
    const options = {
        stateRoot: join(root, 'state'), recoveryRoot: join(root, 'recovery'), storeRoots: { 1: store },
        cloud: {
            upload: (id, path) => copyFile(path, join(remote, id)),
            download: (id, path) => copyFile(join(remote, id), path)
        }
    };
    await writeFile(join(store, 'scan.pdf'), 'first version');
    const backup = backupLibrary(options);
    const version = await backup.capture('1', ['scan.pdf']);
    assert.equal(await backup.capture('1', ['scan.pdf']), undefined);
    await writeFile(join(store, 'scan.pdf'), 'second version');
    const second = await backup.capture('1', ['scan.pdf']);
    await writeFile(join(store, 'scan.pdf'), 'first version');
    const reverted = await backup.capture('1', ['scan.pdf']);
    assert.ok(reverted.sequence > second.sequence);
    assert.equal(reverted.sha256, version.sha256);
    await backup.uploadPending();
    assert.deepEqual(await readdir(join(options.stateRoot, 'snapshots')), []);
    await rm(join(store, 'scan.pdf'));
    const restarted = backupLibrary(options);
    assert.equal((await restarted.versions()).length, 3);
    assert.ok((await restarted.versions()).every(item => item.status === 'verified'));
    await assert.rejects(restarted.restore(version.id, ['2']), /unavailable/u);
    const restored = await restarted.restore(version.id, ['1']);
    assert.equal(await readFile(restored.path, 'utf8'), 'first version');
    await writeFile(join(remote, version.id), 'corrupt data');
    await assert.rejects(restarted.restore(version.id, ['1']), /checksum/u);
});

test('rebuilds a lost catalog from verified cloud bytes without replacing live files', async t => {
    const root = await mkdtemp(join(tmpdir(), 'smartdrive-cloud-recovery-'));
    t.after(() => rm(root, { recursive: true, force: true }));
    const store = join(root, 'store');
    await mkdir(store);
    await writeFile(join(store, 'design.sstbadge'), 'live edits');
    const bytes = 'backed up version';
    const manifest = { id: '44444444-4444-4444-8444-444444444444', store: '1', parts: ['design.sstbadge'],
        size: Buffer.byteLength(bytes), sha256: createHash('sha256').update(bytes).digest('hex'),
        sequence: 1, createdAt: '2026-11-08T12:00:00Z', status: 'pending' };
    let content = bytes;
    const options = { stateRoot: join(root, 'state'), recoveryRoot: join(root, 'recovery'), storeRoots: { 1: store },
        cloud: { recoveryManifests: async function* () { yield manifest; },
            download: (id, target) => writeFile(target, content, { flag: 'wx' }) } };
    const backup = backupLibrary(options);
    content = 'corrupt';
    await assert.rejects(backup.recoverCatalog(), /checksum/u);
    assert.deepEqual(await backup.versions(), []);
    assert.deepEqual(await readdir(join(options.stateRoot, 'snapshots')), []);
    content = bytes;
    assert.deepEqual(await backup.recoverCatalog(), { recovered: 1, retained: 0 });
    const restarted = backupLibrary(options);
    assert.deepEqual(await restarted.recoverCatalog(), { recovered: 0, retained: 1 });
    const restored = await restarted.restore(manifest.id, ['1']);
    assert.equal(await readFile(restored.path, 'utf8'), bytes);
    assert.equal(await readFile(join(store, 'design.sstbadge'), 'utf8'), 'live edits');
    manifest.parts = ['..', 'other'];
    await assert.rejects(restarted.recoverCatalog(), /Invalid cloud/u);
    manifest.parts = ['other.sstbadge'];
    await assert.rejects(restarted.recoverCatalog(), /conflicts/u);
});

test('preserves pending snapshots during outages and refuses capture or restore below the configured reserve', async t => {
    const root = await mkdtemp(join(tmpdir(), 'smartdrive-backup-reserve-'));
    t.after(() => rm(root, { recursive: true, force: true }));
    const store = join(root, 'store');
    await mkdir(store);
    await writeFile(join(store, 'design.sstbadge'), '{}');
    const options = { stateRoot: join(root, 'state'), recoveryRoot: join(root, 'recovery'),
        storeRoots: { 1: store }, cloud: {
            upload: async () => { throw new Error('offline'); },
            download: async () => { throw new Error('Must not download'); }
        } };
    const normal = backupLibrary(options);
    const record = await normal.capture('1', ['design.sstbadge']);
    await normal.uploadPending();
    assert.equal((await normal.versions())[0].status, 'pending');
    assert.equal(await readFile(join(options.stateRoot, 'snapshots', record.id), 'utf8'), '{}');
    const full = backupLibrary({ ...options, minimumFreeBytes: Number.MAX_SAFE_INTEGER });
    await assert.rejects(full.capture('1', ['design.sstbadge']), /storage reserve/u);
    assert.equal((await full.versions()).length, 1);
    await writeFile(join(options.stateRoot, 'versions', record.id + '.json'), JSON.stringify({ ...record, status: 'verified' }));
    await assert.rejects(full.restore(record.id, ['1']), /storage reserve/u);
    assert.deepEqual(await readdir(options.recoveryRoot), []);
});
