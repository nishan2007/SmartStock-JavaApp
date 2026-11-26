import assert from 'node:assert/strict';
import { mkdtemp, open, rm, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { test } from 'node:test';
import { studioMetadata, studioPreview } from '../src/studio-preview.js';

test('extracts a bounded badge preview and rejects files without one', async t => {
    const root = await mkdtemp(join(tmpdir(), 'smartdrive-preview-'));
    t.after(() => rm(root, { recursive: true, force: true }));
    const file = join(root, 'badge.sstbadge');
    const image = Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jh1sAAAAASUVORK5CYII=', 'base64');
    const identity = { id: '11111111-1111-4111-8111-111111111111', storageStoreId: '1', kind: 'BADGE' };
    const content = JSON.stringify({ previewPng: image.toString('base64'), smartDrive: identity });
    await writeFile(file, content);
    const handle = await open(file);
    try {
        assert.deepEqual(await studioPreview(handle, '.sstbadge', Buffer.byteLength(content)), image);
        assert.deepEqual((await studioMetadata(handle, '.sstbadge', Buffer.byteLength(content))).smartDrive, identity);
    } finally { await handle.close(); }
    await writeFile(file, '{}');
    const old = await open(file);
    try {
        await assert.rejects(studioPreview(old, '.sstbadge', 2), /updated SmartStudio/u);
    } finally { await old.close(); }
});
