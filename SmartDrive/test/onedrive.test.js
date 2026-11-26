import assert from 'node:assert/strict';
import { mkdtemp, readFile, rm, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { test } from 'node:test';
import { oneDriveBackup } from '../src/onedrive.js';
import { uploadState } from '../src/upload-state.js';

test('uses Graph authorization only for Graph and verifies upload acknowledgement size', async t => {
    const root = await mkdtemp(join(tmpdir(), 'smartdrive-onedrive-'));
    t.after(() => rm(root, { recursive: true, force: true }));
    const file = join(root, 'snapshot');
    await writeFile(file, 'backup');
    const id = '11111111-1111-4111-8111-111111111111';
    const seen = [];
    const cloud = oneDriveBackup({
        tenantId: 'tenant', clientId: 'client', clientSecret: 'private',
        driveId: 'drive', folderId: 'backup-folder'
    }, async (value, options = {}) => {
        const url = new URL(value);
        seen.push(url.hostname);
        if (url.hostname === 'login.microsoftonline.com') {
            return Response.json({ access_token: 'token', expires_in: 3600 });
        }
        if (url.hostname === 'graph.microsoft.com') {
            assert.equal(options.headers.Authorization, 'Bearer token');
            if (url.pathname.endsWith('/createUploadSession')) {
                return Response.json({ uploadUrl: 'https://transfer.example/upload' });
            }
            if (options.method === 'PUT') return Response.json({ size: 6 });
            return new Response(null, { status: 302, headers: { Location: 'https://transfer.example/download' } });
        }
        assert.equal(options.headers?.Authorization, undefined);
        if (options.method === 'PUT') {
            assert.equal(options.headers['Content-Range'], 'bytes 0-5/6');
            assert.equal(options.body.toString(), 'backup');
            return Response.json({ size: 6 });
        }
        return new Response('backup');
    });
    await cloud.upload(id, file, { id });
    const restored = join(root, 'restored');
    await cloud.download(id, restored);
    assert.equal(await readFile(restored, 'utf8'), 'backup');
    assert.equal(seen.filter(host => host === 'login.microsoftonline.com').length, 1);
});

test('resumes after process restart using server progress and retries only the manifest after completion', async t => {
    const root = await mkdtemp(join(tmpdir(), 'smartdrive-resume-'));
    t.after(() => rm(root, { recursive: true, force: true }));
    const file = join(root, 'snapshot');
    const block = 327680 * 16;
    const size = block + 100;
    await writeFile(file, Buffer.alloc(size, 8));
    const id = '22222222-2222-4222-8222-222222222222';
    const record = { id, sha256: 'b'.repeat(64) };
    const config = { tenantId: 'tenant', clientId: 'client', clientSecret: 'private',
        driveId: 'drive', folderId: 'folder' };
    let sessions = 0;
    let progress = 0;
    let interrupted = false;
    let manifestFailed = false;
    const puts = [];
    const request = async (value, options = {}) => {
        const url = new URL(value);
        if (url.hostname === 'login.microsoftonline.com') return Response.json({ access_token: 'token', expires_in: 3600 });
        if (url.hostname === 'graph.microsoft.com') {
            if (url.pathname.endsWith('/createUploadSession')) {
                sessions++;
                return Response.json({ uploadUrl: 'https://transfer.example/secret-session' });
            }
            if (!manifestFailed) { manifestFailed = true; return new Response(null, { status: 503 }); }
            return Response.json({});
        }
        assert.equal(options.headers?.Authorization, undefined);
        if (options.method === 'GET') return Response.json({ nextExpectedRanges: [progress + '-'] });
        puts.push(options.headers['Content-Range']);
        if (progress === block && !interrupted) { interrupted = true; throw new Error('Connection lost'); }
        progress += options.body.length;
        return progress === size ? Response.json({ size }, { status: 201 }) :
            Response.json({ nextExpectedRanges: [progress + '-'] }, { status: 202 });
    };
    const restart = () => oneDriveBackup({ ...config, uploadState: uploadState(join(root, 'private')) }, request);
    await assert.rejects(restart().upload(id, file, record), /Connection lost/u);
    await assert.rejects(restart().upload(id, file, record), /recovery manifest/u);
    assert.equal((await uploadState(join(root, 'private')).read(id)).complete, true);
    await restart().upload(id, file, record);
    assert.equal(sessions, 1);
    assert.deepEqual(puts, [`bytes 0-${block - 1}/${size}`, `bytes ${block}-${size - 1}/${size}`,
        `bytes ${block}-${size - 1}/${size}`]);
    assert.equal(await uploadState(join(root, 'private')).read(id), null);
});

test('replaces expired upload sessions and rejects mismatched snapshot state', async t => {
    const root = await mkdtemp(join(tmpdir(), 'smartdrive-expired-upload-'));
    t.after(() => rm(root, { recursive: true, force: true }));
    const file = join(root, 'snapshot');
    await writeFile(file, 'backup');
    const state = uploadState(join(root, 'private'));
    const id = '33333333-3333-4333-8333-333333333333';
    const endpoint = `https://graph.microsoft.com/v1.0/drives/drive/items/folder:/${id}.bin:`;
    await state.write(id, { endpoint, size: 7, sha256: 'a', uploadUrl: 'https://transfer.example/expired' });
    let sessions = 0;
    const cloud = oneDriveBackup({ tenantId: 'tenant', clientId: 'client', clientSecret: 'secret',
        driveId: 'drive', folderId: 'folder', uploadState: state }, async (value, options = {}) => {
        const url = new URL(value);
        if (url.hostname === 'login.microsoftonline.com') return Response.json({ access_token: 'token', expires_in: 3600 });
        if (url.pathname === '/expired') return new Response(null, { status: 404 });
        if (url.pathname.endsWith('/createUploadSession')) {
            sessions++;
            return Response.json({ uploadUrl: 'https://transfer.example/new' });
        }
        return Response.json({ size: 6 });
    });
    await assert.rejects(cloud.upload(id, file, { sha256: 'a' }), /does not match/u);
    await state.write(id, { endpoint, size: 6, sha256: 'a', uploadUrl: 'https://transfer.example/expired' });
    await cloud.upload(id, file, { sha256: 'a' });
    assert.equal(sessions, 1);
    assert.equal(await state.read(id), null);
});

test('discovers paginated recovery manifests without sending bearer tokens to transfer URLs', async () => {
    const id = '55555555-5555-4555-8555-555555555555';
    let badNext = false;
    const cloud = oneDriveBackup({ tenantId: 'tenant', clientId: 'client', clientSecret: 'secret',
        driveId: 'drive', folderId: 'folder' }, async (value, options = {}) => {
        const url = new URL(value);
        if (url.hostname === 'login.microsoftonline.com') return Response.json({ access_token: 'token', expires_in: 3600 });
        if (url.hostname === 'transfer.example') {
            assert.equal(options.headers?.Authorization, undefined);
            return Response.json({ id, store: '1' });
        }
        assert.equal(options.headers.Authorization, 'Bearer token');
        if (url.pathname.endsWith('/children')) {
            if (url.searchParams.has('page')) return Response.json({ value: [{ name: id + '.json', size: 100, file: {} }] });
            return Response.json({ value: [{ name: 'unrelated.txt', size: 100, file: {} }],
                '@odata.nextLink': badNext ? 'https://untrusted.example/steal-token' :
                    'https://graph.microsoft.com/v1.0/drives/drive/items/folder/children?page=2' });
        }
        return new Response(null, { status: 302, headers: { Location: 'https://transfer.example/manifest' } });
    });
    const collect = async () => {
        const records = [];
        for await (const record of cloud.recoveryManifests()) records.push(record);
        return records;
    };
    assert.deepEqual(await collect(), [{ id, store: '1' }]);
    badNext = true;
    await assert.rejects(collect(), /pagination/u);
});
