import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { copyFile, mkdir, mkdtemp, readFile, rm, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { test } from 'node:test';
import { prepareLegacyPreviews } from '../src/prepare-previews.js';

const hash = bytes => createHash('sha256').update(bytes).digest('hex');
async function fixture(t) {
    const root = await mkdtemp(join(tmpdir(), 'smartdrive-preview-prepare-'));
    t.after(() => rm(root, { recursive: true, force: true }));
    const stores = { 1: join(root, 'Stores', '1') }, records = [];
    for (const number of [1, 2]) {
        const id = `11111111-1111-4111-8111-${String(number).padStart(12, '0')}`;
        const parts = ['Studio Jobs', 'BADGE', 'Access-1', 'Badge-' + id + '.sstbadge'];
        await mkdir(join(stores[1], ...parts.slice(0, -1)), { recursive: true });
        const bytes = JSON.stringify({ name: 'Legacy ' + number });
        await writeFile(join(stores[1], ...parts), bytes);
        records.push({ store: '1', parts, sha256: hash(bytes), legacy: { id, storageStoreId: '1', kind: 'BADGE' } });
    }
    return { storeRoots: stores, catalog: { formatVersion: 1, records }, recoveryRoot: join(root, 'Recovery'), maintenanceWindow: true };
}

test('bulk preview preparation verifies original recovery copies and records each transformed revision', async t => {
    const options = await fixture(t);let calls = 0;
    const result = await prepareLegacyPreviews({ ...options, async prepare(source, recovery, expected) {
        const backup = join(recovery, `original-${calls++}.sstbadge`);await copyFile(source, backup);
        const next = JSON.stringify({ name: 'Legacy', previewPng: 'generated' });await writeFile(source, next);
        return { status: 'prepared', beforeSha256: expected, afterSha256: hash(next), backup };
    } });
    assert.equal(result.prepared, 2);
    const journal = (await readFile(join(options.recoveryRoot, 'preparation.jsonl'), 'utf8')).trim().split('\n').map(JSON.parse);
    assert.deepEqual(journal.map(record => record.status), ['started', 'prepared', 'started', 'prepared']);
    for (const record of journal.filter(record => record.backup)) assert.equal(hash(await readFile(record.backup)), record.beforeSha256);
});

test('bulk preview preparation requires maintenance, rejects traversal before mutation and journals independent failures', async t => {
    const options = await fixture(t);let calls = 0;
    const prepare = async () => { calls++;throw new Error('Synthetic renderer failure'); };
    await assert.rejects(prepareLegacyPreviews({ ...options, maintenanceWindow: false, prepare }), /Freeze/u);
    const invalid = structuredClone(options.catalog);invalid.records[0].parts[2] = '..';
    await assert.rejects(prepareLegacyPreviews({ ...options, catalog: invalid, prepare }), /Invalid/u);
    assert.equal(calls, 0);
    await assert.rejects(prepareLegacyPreviews({ ...options, prepare }), /2 preview preparation/u);
    assert.equal(calls, 2);
    const journal = await readFile(join(options.recoveryRoot, 'preparation.jsonl'), 'utf8');
    assert.equal(journal.split('Synthetic renderer failure').length - 1, 2);
    await assert.rejects(prepareLegacyPreviews({ ...options, prepare }), /exist/u);
});
