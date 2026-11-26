import assert from 'node:assert/strict';
import { mkdtemp, readFile, readdir, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { test } from 'node:test';
import { scannerCredentials } from '../src/scanner-credentials.js';

test('rotates configured scanner credentials without persisting passwords and retains disablement across restart', async t => {
    const root = await mkdtemp(join(tmpdir(), 'smartdrive-scanner-creds-'));
    t.after(() => rm(root, { recursive: true, force: true }));
    const calls = [];
    const config = { root, scanners: [{ id: 'Canon-C5040', storeId: '1', account: 'sdscan_c5040' }],
        broker: async request => { calls.push(request); } };
    const credentials = scannerCredentials(config), manager = { storeIds: ['1'] };
    assert.throws(() => credentials.rotate({ storeIds: ['2'] }, 'Canon-C5040'), /unavailable/u);
    assert.equal(calls.length, 0);
    const first = await credentials.rotate(manager, 'Canon-C5040');
    assert.match(first.password, /^[A-Za-z0-9!_-]{32,100}$/u);
    assert.equal(calls[0].action, 'scanner-rotate');assert.equal(calls[0].account, 'sdscan_c5040');
    await credentials.disable(manager, 'Canon-C5040');
    const restarted = scannerCredentials(config);
    const second = await restarted.rotate(manager, 'Canon-C5040');
    assert.notEqual(first.password, second.password);assert.equal(second.disabled, true);
    assert.equal(calls.at(-1).disabled, true);
    assert.equal((await restarted.list(manager))[0].credentialState, 'disabled');
    await restarted.enable(manager, 'Canon-C5040');
    assert.equal(calls.at(-1).action, 'scanner-enable');
    assert.equal(calls.at(-1).password, undefined);
    assert.equal((await restarted.list(manager))[0].credentialState, 'active');
    assert.deepEqual(await restarted.list({ storeIds: ['2'] }), []);
    for (const file of await readdir(root)) {
        const text = await readFile(join(root, file), 'utf8');
        assert.ok(!text.includes(first.password));assert.ok(!text.includes(second.password));
    }
});

test('records ambiguous broker failures for review and permits an explicit retry', async t => {
    const root = await mkdtemp(join(tmpdir(), 'smartdrive-scanner-creds-failure-'));
    t.after(() => rm(root, { recursive: true, force: true }));
    let fails = true;
    const config = { root, scanners: [{ id: 'Canon-C5040', storeId: '1', account: 'sdscan_c5040' }],
        broker: async () => { if (fails) throw new Error('Synthetic disconnect'); } };
    const manager = { storeIds: ['1'] };
    await assert.rejects(scannerCredentials(config).disable(manager, 'Canon-C5040'), /disconnect/u);
    const restarted = scannerCredentials(config);
    assert.equal((await restarted.list(manager))[0].credentialState, 'requires-review');
    fails = false;await restarted.disable(manager, 'Canon-C5040');
    assert.equal((await restarted.list(manager))[0].credentialState, 'disabled');
});
