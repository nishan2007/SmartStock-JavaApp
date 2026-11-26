import assert from 'node:assert/strict';
import { test } from 'node:test';
import { resumeBrowsing } from '../web/navigation.js';

test('restores a requested location using fresh names, permission state and file metadata', async () => {
    const responses = {
        stores: [{ store: '1', id: 'root', manage: false }],
        'files?id=root': [{ id: 'jobs', name: 'Studio Jobs', folder: true }],
        'files?id=jobs': [{ id: 'file', name: 'Current badge.sstbadge', folder: false, preview: true, sizeBytes: 123 }]
    };
    const result = await resumeBrowsing({ folders: [{ id: 'root', manage: true, name: 'stale private label' },
        { id: 'jobs', name: 'old label', folder: true }], fileId: 'file' }, async path => responses[path]);
    assert.equal(result.folders[0].manage, false);assert.equal(result.folders[0].name, 'Shared from store 1');
    assert.equal(result.folders[1].name, 'Studio Jobs');assert.equal(result.file.name, 'Current badge.sstbadge');
    assert.ok(!JSON.stringify(result).includes('stale private label'));
});

test('does not restore a folder or preview when the current employee lacks store access', async () => {
    const calls = [];
    const result = await resumeBrowsing({ folders: [{ id: 'previous-root' }], fileId: 'previous-file' }, async path => { calls.push(path);return []; });
    assert.equal(result, null);assert.deepEqual(calls, ['stores']);
    const missing = await resumeBrowsing({ folders: [{ id: 'root' }, { id: 'deleted-folder' }], fileId: 'file' },
        async path => path === 'stores' ? [{ id: 'root', store: '1' }] : []);
    assert.equal(missing, null);
    await assert.rejects(resumeBrowsing({ folders: [{ id: 'root' }] }, async () => { throw new Error('Session expired'); }), /expired/u);
});
