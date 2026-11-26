import assert from 'node:assert/strict';
import { copyFile, mkdir, mkdtemp, readFile, readdir, rm, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { test } from 'node:test';
import { scanIntake } from '../src/scans.js';

test('publishes stable uploads once, retries a closed-file failure, and quarantines invalid scans', async t => {
    const root = await mkdtemp(join(tmpdir(), 'smartdrive-scans-'));
    t.after(() => rm(root, { recursive: true, force: true }));
    const intake = join(root, 'intake');
    const store = join(root, 'store');
    await mkdir(intake);
    await mkdir(store);
    let locked = true;
    let enabled = true;
    const options = {
        scanners: [{ id: 'Canon-C5040', storeId: '1', intake }],
        storeRoots: { 1: store },
        stateRoot: join(root, 'state'),
        async copyClosed(source, destination) {
            if (locked) throw new Error('File is still being written.');
            await copyFile(source, destination);
        },
        malwareCheck: async () => true,
        resetAcl: async () => {},
        isEnabled: async () => enabled
    };
    const poll = scanIntake(options);
    const pdf = '%PDF-1.7\ntest\n%%EOF\n';
    await writeFile(join(intake, 'scan.pdf'), pdf);
    assert.equal((await poll())[0].status, 'fulfilled');
    assert.equal((await poll())[0].status, 'rejected');
    const destination = join(store, 'Scans', 'Canon-C5040');
    assert.deepEqual(await readdir(destination), []);
    locked = false;
    // Recreate the worker to prove claimed inputs survive service restarts.
    const restarted = scanIntake(options);
    assert.equal((await restarted())[0].status, 'fulfilled');
    const names = await readdir(destination);
    assert.equal(names.length, 1);
    assert.equal(await readFile(join(destination, names[0]), 'utf8'), pdf);
    await restarted();
    assert.deepEqual(await readdir(destination), names);
    await writeFile(join(intake, 'bad.pdf'), 'invalid scan');
    enabled = false;
    await restarted();
    await restarted();
    assert.deepEqual(await readdir(intake), ['bad.pdf']);
    enabled = true;
    await restarted();
    await restarted();
    assert.equal((await readdir(join(options.stateRoot, 'Canon-C5040', 'quarantine'))).length, 1);
    assert.deepEqual(await readdir(destination), names);
});
