import assert from 'node:assert/strict';
import { link, mkdir, mkdtemp, rm, symlink, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { test } from 'node:test';
import { fileLibrary } from '../src/files.js';

test('file metadata includes unsupported files but excludes linked files and unauthorized stores', async t => {
    const root = await mkdtemp(join(tmpdir(), 'smartdrive-file-details-'));
    t.after(() => rm(root, { recursive: true, force: true }));
    const store = join(root, 'store'), outside = join(root, 'outside');await mkdir(store);await mkdir(outside);
    await mkdir(join(store, 'Folder'));await writeFile(join(store, 'Unsupported.xyz'), 'original');
    await writeFile(join(outside, 'private.pdf'), 'private data');
    await link(join(outside, 'private.pdf'), join(store, 'Linked.pdf'));
    await symlink(outside, join(store, 'Junction'), 'junction');
    const library = fileLibrary({ storeRoots: { 1: store, 2: outside }, fileIdKey: Buffer.alloc(32, 8) });
    const identity = { storeIds: ['1'] }, id = library.stores(identity)[0].id;
    const files = await library.list(identity, id);
    assert.deepEqual(files.map(file => file.name), ['Folder', 'Unsupported.xyz']);
    assert.equal(files[0].sizeBytes, null);assert.equal(files[0].fileType, 'Folder');
    assert.equal(files[1].fileType, 'XYZ');assert.equal(files[1].sizeBytes, 8);assert.equal(files[1].preview, false);
    await assert.rejects(library.list({ storeIds: ['2'] }, id));
});
