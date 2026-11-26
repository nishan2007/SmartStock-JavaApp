import assert from 'node:assert/strict';
import { createHash, randomBytes, randomUUID } from 'node:crypto';
import { createReadStream } from 'node:fs';
import { copyFile, mkdir, readFile, readdir, writeFile } from 'node:fs/promises';
import { join } from 'node:path';
import { execFile } from 'node:child_process';
import { promisify } from 'node:util';
import { exportStudioLibrary } from '../src/migration.js';
import { importStudioExport } from '../src/import-library.js';
import { writeFileIdentity, readFileIdentity } from '../src/file-identity.js';
import { prepareLegacyPreviews } from '../src/prepare-previews.js';
import { fileLibrary } from '../src/files.js';
import { backupLibrary } from '../src/backup.js';

const [source, root, java, classpath, mode] = process.argv.slice(2);
const verifyPrepared = mode === '--verify-prepared-copy';
if (![6, 7].includes(process.argv.length) || mode && !verifyPrepared || process.platform !== 'win32') throw new Error('Usage on Windows: node tools/rehearse-library.mjs <legacy-library> <protected-working-root> <java.exe> <SmartStudio-classpath> [--verify-prepared-copy]');
if (!verifyPrepared && (await readdir(root)).length) throw new Error('Use an empty, privately protected working directory.');
const digest = async path => { const hash = createHash('sha256');for await (const bytes of createReadStream(path)) hash.update(bytes);return hash.digest('hex'); };
const fingerprint = manifest => createHash('sha256').update(JSON.stringify(manifest.records)).digest('hex');
const report = verifyPrepared ? JSON.parse(await readFile(join(root, 'rehearsal-report.json'), 'utf8')) : { startedAt: new Date().toISOString(), phase: 'exporting', liveCutover: false,
    limits: ['No SMB ACL/service acceptance', 'No real customer-link database verification', 'Local backup transport fixture only; company OneDrive not tested', 'Only stores present in this local library'] };
if (verifyPrepared) {
    if (report.liveCutover !== false || !/^[a-f0-9]{64}$/u.test(report.sourceFingerprint) || !(report.templates > 0) ||
        report.previewPreparation?.prepared + report.previewPreparation?.existing !== report.templates || report.verifiedWebPreviews !== report.templates) throw new Error('Only a retained copy with completed preview preparation and delivery checks can be reverified.');
    await copyFile(join(root, 'rehearsal-report.json'), join(root, 'rehearsal-report.previous-' + randomUUID() + '.json'));
}
async function save() { await writeFile(join(root, 'rehearsal-report.json'), JSON.stringify(report, null, 2)); }
try {
    await save();
    const exported = await exportStudioLibrary({ source, destination: join(root, verifyPrepared ? 'UnusedDryRun' : 'Export'), dryRun: verifyPrepared });
    if (verifyPrepared) assert.equal(fingerprint(exported), report.sourceFingerprint, 'Source changed since the retained rehearsal copy. Review before retrying.');
    if (!exported.records.length) throw new Error('No designs found; migration rehearsal cannot pass an empty library.');
    const stores = [...new Set(exported.records.flatMap(record => [String(record.storageStoreId), String(record.metadata.ownerStoreId ?? record.storageStoreId), ...(record.metadata.sharedStoreIds ?? []).map(String)]))];
    report.templates = exported.records.length;report.stores = stores.length;
    report.originalBytes = exported.records.reduce((total, record) => total + record.metadata.size, 0);
    report.sourceFingerprint = fingerprint(exported);report.phase = 'importing';await save();
    process.stdout.write(`Export verified: ${report.templates} designs, ${report.stores} local stores.\n`);
    const importedRoot = join(root, 'Imported');
    const reference = await importStudioExport({ source: join(root, 'Export'), destination: verifyPrepared ? join(root, 'ImportValidation-' + randomUUID()) : importedRoot, configuredStores: stores });
    const catalog = verifyPrepared ? JSON.parse(await readFile(join(importedRoot, 'catalog.json'), 'utf8')) : reference;
    assert.deepEqual(catalog.records, reference.records);assert.deepEqual(catalog.sharedFolders, reference.sharedFolders);
    const storeRoots = Object.fromEntries(stores.map(store => [store, join(importedRoot, 'Stores', store)]));
    for (const path of Object.values(storeRoots)) await mkdir(path, { recursive: true });
    if (!verifyPrepared) for (const record of catalog.records) {
        const path = join(storeRoots[record.store], ...record.parts);
        await writeFileIdentity(path, record.legacy);
        assert.equal(await digest(path), record.sha256);
        assert.deepEqual(await readFileIdentity(path), record.legacy);
    }
    if (!verifyPrepared) {
    report.phase = 'preparing-previews';await save();process.stdout.write('Import hashes and identity streams verified. Preparing previews on the copy.\n');
    const run = promisify(execFile);let completed = 0;
    report.previewPreparation = await prepareLegacyPreviews({ storeRoots, catalog, recoveryRoot: join(root, 'PreviewOriginals'), maintenanceWindow: true,
        async prepare(path, recovery, expected) {
            const result = await run(java, ['-Djava.awt.headless=true', '-Xmx512m', '-cp', classpath,
                'gy.deckers.studio.StudioPreviewPreparation', path, recovery, expected], { windowsHide: true, timeout: 180000, maxBuffer: 65536 });
            if (++completed % 20 === 0) process.stdout.write(`Preview preparation: ${completed}/${report.templates}.\n`);
            return JSON.parse(result.stdout.trim());
        } });
    }
    report.phase = 'verifying-access-and-previews';await save();
    const library = fileLibrary({ storeRoots, fileIdKey: randomBytes(32), sharedFolders: catalog.sharedFolders, migrationRecords: catalog.records });
    let catalogChecks = 0;
    for (const store of stores) for (const kind of ['BADGE', 'BUSINESS_CARDS', 'LABELS', 'BILL_BOOKS']) {
        const expected = catalog.records.filter(record => record.legacy.kind === kind && (record.store === store || record.metadata.sharedStoreIds?.map(String).includes(store)));
        const actual = await library.desktopTemplates({ storeIds: [store] }, kind);
        assert.deepEqual(actual.map(record => record.storageStoreId + '/' + record.id).sort(), expected.map(record => record.legacy.storageStoreId + '/' + record.legacy.id).sort());
        catalogChecks += expected.length;
    }
    const folders = new Map();let previews = 0;
    for (const record of catalog.records) {
        const identity = { storeIds: [record.store] };
        let id = library.stores(identity).find(store => store.store === record.store).id;
        for (let index = 0; index < record.parts.length; index++) {
            const key = JSON.stringify([record.store, ...record.parts.slice(0, index)]);
            if (!folders.has(key)) folders.set(key, await library.list(identity, id));
            id = folders.get(key).find(entry => entry.name === record.parts[index])?.id;
            assert.ok(id, 'Migrated file missing from the authorized web listing.');
        }
        const preview = await library.preview(identity, id);
        try { assert.equal(preview.contentType, 'image/png');assert.ok(preview.bytes?.length > 24);previews++; }
        finally { await preview.handle.close(); }
        await assert.rejects(library.preview({ storeIds: [] }, id));
    }
    report.catalogAccessChecks = catalogChecks;report.verifiedWebPreviews = previews;
    const badge = catalog.records.find(record => record.legacy.kind === 'BADGE');
    if (badge) {
        const head = await library.linkedTemplate(badge.legacy.storageStoreId, badge.legacy.id);
        assert.equal(head.id, badge.legacy.id);assert.equal(head.sha256, await digest(join(storeRoots[badge.store], ...badge.parts)));
        report.badgeLinkContentResolution = 'verified-file-bridge-only';
    }
    report.phase = 'verifying-local-transport-recovery';await save();
    const runTag = randomUUID();
    const cloudRoot = join(root, 'LocalTransportFixture-' + runTag);await mkdir(cloudRoot);
    const backups = backupLibrary({ storeRoots, stateRoot: join(root, 'BackupState-' + runTag), recoveryRoot: join(root, 'Restored-' + runTag),
        cloud: { upload: (id, path) => copyFile(path, join(cloudRoot, id)), download: (id, path) => copyFile(join(cloudRoot, id), path) } });
    const samples = [...new Set(catalog.records.map(record => record.legacy.kind))].map(kind => catalog.records.find(record => record.legacy.kind === kind));
    for (const sample of samples) await backups.capture(sample.store, sample.parts);
    await backups.uploadPending();
    const versions = await backups.versions();assert.equal(versions.length, samples.length);
    for (const version of versions) {
        assert.equal(version.status, 'verified');const restored = await backups.restore(version.id, [version.store]);
        assert.equal(await digest(restored.path), version.sha256);assert.deepEqual(await readFileIdentity(restored.path), version.fileIdentity ?? null);
    }
    report.localTransportRestores = versions.length;
    const unchanged = await exportStudioLibrary({ source, destination: join(root, 'UnusedDryRun'), dryRun: true });
    assert.equal(fingerprint(unchanged), report.sourceFingerprint, 'Live library metadata/revisions changed during rehearsal; review the frozen-copy procedure.');
    report.phase = 'completed';report.completedAt = new Date().toISOString();delete report.error;delete report.failedDuring;await save();
    process.stdout.write(JSON.stringify({ templates: report.templates, stores: report.stores, previews, localTransportRestores: versions.length, sourceUnchanged: true, liveCutover: false }) + '\n');
} catch (error) {
    report.failedDuring = report.phase;report.phase = 'failed';report.error = error.message;await save();
    process.stderr.write('Library rehearsal failed. Review the private report, recovery copies and preview journal. Live library was not modified by this tool.\n');process.exitCode = 1;
}
