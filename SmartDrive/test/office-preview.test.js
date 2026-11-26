import assert from 'node:assert/strict';
import { mkdir, mkdtemp, open, readFile, readdir, rm, stat, utimes, writeFile } from 'node:fs/promises';
import { join } from 'node:path';
import { tmpdir } from 'node:os';
import { test } from 'node:test';
import { officePreviews } from '../src/office-preview.js';

test('caches Office previews by captured content and cleans private conversion jobs', async t => {
    const root = await mkdtemp(join(tmpdir(), 'smartdrive-office-'));
    t.after(() => rm(root, { recursive: true, force: true }));
    const cacheRoot = join(root, 'cache');
    const source = join(root, 'document.docx');
    let calls = 0;
    const preview = officePreviews({ cacheRoot, convert: async (input, output) => {
        calls++;
        await writeFile(join(output, 'document.pdf'), '%PDF-1.7\n' + await readFile(input, 'utf8'));
    } });
    async function render() {
        const handle = await open(source);
        try { return await preview(handle, '.docx', (await handle.stat()).size); }
        finally { await handle.close(); }
    }
    await writeFile(source, 'first');
    assert.match((await render()).toString(), /first/u);
    await render();
    assert.equal(calls, 1);
    await writeFile(source, 'second');
    assert.match((await render()).toString(), /second/u);
    assert.equal(calls, 2);
    assert.ok((await readdir(cacheRoot)).every(name => name.endsWith('.pdf')));
});

test('evicts least recently used revisions within the cache budget and leaves unrelated files alone', async t => {
    const root = await mkdtemp(join(tmpdir(), 'smartdrive-office-budget-'));
    t.after(() => rm(root, { recursive: true, force: true }));
    const cacheRoot = join(root, 'cache');
    await mkdir(cacheRoot);
    const old = join(cacheRoot, 'a'.repeat(64) + '.docx.pdf');
    const recent = join(cacheRoot, 'b'.repeat(64) + '.pptx.pdf');
    for (const file of [old, recent]) {
        const handle = await open(file, 'wx');
        try { await handle.truncate(16 * 1024 * 1024); } finally { await handle.close(); }
    }
    await utimes(old, new Date(1000), new Date(1000));
    await utimes(recent, new Date(2000), new Date(2000));
    await writeFile(join(cacheRoot, 'operator-note.txt'), 'keep');
    const source = join(root, 'input.docx');
    await writeFile(source, 'document');
    const preview = officePreviews({ cacheRoot, maxCacheBytes: 32 * 1024 * 1024,
        convert: async (input, output) => writeFile(join(output, 'document.pdf'), '%PDF-1.7\npreview') });
    const handle = await open(source);
    try { await preview(handle, '.docx', 8); } finally { await handle.close(); }
    await assert.rejects(stat(old), { code: 'ENOENT' });
    assert.equal((await stat(recent)).size, 16 * 1024 * 1024);
    assert.equal(await readFile(join(cacheRoot, 'operator-note.txt'), 'utf8'), 'keep');
    let total = 0;
    for (const name of await readdir(cacheRoot)) if (name.endsWith('.pdf')) total += (await stat(join(cacheRoot, name))).size;
    assert.ok(total <= 32 * 1024 * 1024);
});

test('bounds simultaneous captures and deduplicates conversion of the same revision', async t => {
    const root = await mkdtemp(join(tmpdir(), 'smartdrive-office-busy-'));
    t.after(() => rm(root, { recursive: true, force: true }));
    const source = join(root, 'input.docx');
    await writeFile(source, 'document');
    let release;
    const blocked = new Promise(resolve => { release = resolve; });
    let started;
    const ready = new Promise(resolve => { started = resolve; });
    let conversions = 0;
    const preview = officePreviews({ cacheRoot: join(root, 'cache'), convert: async (input, output) => {
        conversions++;
        started();
        await blocked;
        await writeFile(join(output, 'document.pdf'), '%PDF-1.7\npreview');
    } });
    const handles = await Promise.all([open(source), open(source), open(source)]);
    try {
        const first = preview(handles[0], '.docx', 8);
        await ready;
        assert.equal(preview.status().state, 'converting');
        assert.equal(preview.status().active, 1);
        const second = preview(handles[1], '.docx', 8);
        await assert.rejects(preview(handles[2], '.docx', 8), /busy/u);
        release();
        assert.deepEqual(await first, await second);
        assert.equal(conversions, 1);
        assert.equal(preview.status().completed, 1);
        assert.match((await preview(handles[2], '.docx', 8)).toString(), /PDF/u);
    } finally {
        release();
        await Promise.all(handles.map(handle => handle.close()));
    }
});

test('reports conversion failure without caching invalid output and recovers after a successful retry', async t => {
    const root = await mkdtemp(join(tmpdir(), 'smartdrive-office-health-'));
    t.after(() => rm(root, { recursive: true, force: true }));
    const source = join(root, 'input.docx');
    await writeFile(source, 'document');
    let valid = false;
    const preview = officePreviews({ cacheRoot: join(root, 'cache'), convert: async (input, output) => {
        await writeFile(join(output, 'document.pdf'), valid ? '%PDF-1.7\npreview' : 'conversion failed');
    } });
    async function render() {
        const handle = await open(source);
        try { return await preview(handle, '.docx', 8); } finally { await handle.close(); }
    }
    assert.equal(preview.status().state, 'idle');
    await assert.rejects(render(), /Invalid Office preview/u);
    assert.deepEqual(await readdir(join(root, 'cache')), []);
    assert.equal(preview.status().state, 'needs-attention');
    assert.equal(preview.status().failures, 1);
    assert.deepEqual(Object.keys(preview.status().lastResult).sort(), ['at', 'result']);
    valid = true;
    await render();
    assert.equal(preview.status().state, 'idle');
    assert.equal(preview.status().completed, 1);
    assert.equal(preview.status().failures, 1);
    assert.equal(preview.status().captures, 0);
});
