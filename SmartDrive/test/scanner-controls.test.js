import assert from 'node:assert/strict';
import { mkdtemp, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { test } from 'node:test';
import { scannerControls } from '../src/scanner-controls.js';

test('persists scanner pause state and rejects changes from another store', async t => {
    const root = await mkdtemp(join(tmpdir(), 'smartdrive-scanner-controls-'));
    t.after(() => rm(root, { recursive: true, force: true }));
    const devices = [{ id: 'Canon-C5040', storeId: '1' }, { id: 'Other', storeId: '2' }];
    const control = scannerControls(root, devices);
    const identity = { storeIds: ['1'] };
    assert.equal(await control.enabled('Canon-C5040'), true);
    await control.set(identity, 'Canon-C5040', true);
    assert.equal(await scannerControls(root, devices).enabled('Canon-C5040'), false);
    assert.throws(() => control.set(identity, 'Other', true), /unavailable/u);
    assert.deepEqual(await control.list(identity), [{ id: 'Canon-C5040', store: '1', paused: true }]);
    await control.set(identity, 'Canon-C5040', false);
    assert.equal(await control.enabled('Canon-C5040'), true);
});
