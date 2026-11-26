import assert from 'node:assert/strict';
import { mkdtemp, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { test } from 'node:test';
import { backupSettings } from '../src/backup-settings.js';

test('persists concurrent folder changes and preserves selections from other stores', async t => {
    const root = await mkdtemp(join(tmpdir(), 'smartdrive-settings-'));
    t.after(() => rm(root, { recursive: true, force: true }));
    const settings = backupSettings(root);
    const first = { store: '1', parts: ['Studio Jobs'] };
    const second = { store: '2', parts: ['Scans'] };
    await Promise.all([settings.set(first, true), settings.set(second, true)]);
    assert.deepEqual(await backupSettings(root).read(), [first, second]);
    await settings.set(first, false);
    assert.deepEqual(await settings.read(), [second]);
});
