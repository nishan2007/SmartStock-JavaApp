import assert from 'node:assert/strict';
import { mkdtemp, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { test } from 'node:test';
import { managementAudit } from '../src/audit.js';

test('records intent and outcome durably and limits activity to assigned stores', async t => {
    const root = await mkdtemp(join(tmpdir(), 'smartdrive-audit-'));
    t.after(() => rm(root, { recursive: true, force: true }));
    const employee = { userId: 4, storeIds: ['1'] };
    const audit = managementAudit(root);
    assert.equal(await audit.action(employee, 'RESTORE_BACKUP', ['1'], {}, async () => 'done'), 'done');
    await assert.rejects(audit.action(employee, 'RETRY_BACKUPS', ['1'], {}, async () => {
        throw new Error('Unavailable.');
    }), /Unavailable/u);
    const restarted = managementAudit(root);
    assert.equal((await restarted.list(employee)).length, 4);
    assert.equal((await restarted.list({ storeIds: ['2'] })).length, 0);
    const entries = await restarted.list(employee);
    assert.deepEqual(entries.filter(item => item.action === 'RETRY_BACKUPS').map(item => item.result).sort(),
        ['failed', 'requested']);
});
