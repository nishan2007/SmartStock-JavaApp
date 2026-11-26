import assert from 'node:assert/strict';
import { mkdtemp, readFile, readdir, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { test } from 'node:test';
import { workstationEnrollment } from '../src/workstations.js';

test('redeems enrollment once and retains revocation across restart without storing passwords', async t => {
    const root = await mkdtemp(join(tmpdir(), 'smartdrive-enrollment-'));
    t.after(() => rm(root, { recursive: true, force: true }));
    const calls = [];
    const options = { root, server: 'DECKERSDRIVE', share: 'DeckersDrive',
        broker: async request => calls.push(request) };
    const manager = { storeIds: ['1'] };
    const enrollment = workstationEnrollment(options);
    const issued = await enrollment.issue(manager, 'Studio PC', ['1']);
    const attempts = await Promise.allSettled([enrollment.redeem(issued.code), enrollment.redeem(issued.code)]);
    assert.equal(attempts.filter(result => result.status === 'fulfilled').length, 1);
    assert.equal(calls.length, 1);
    const restarted = workstationEnrollment(options);
    assert.equal((await restarted.list(manager))[0].status, 'active');
    assert.deepEqual(await restarted.list({ storeIds: ['2'] }), []);
    for (const name of await readdir(join(root, 'workstations'))) {
        assert.ok(!(await readFile(join(root, 'workstations', name), 'utf8')).includes(calls[0].password));
    }
    await assert.rejects(restarted.revoke({ storeIds: ['2'] }, issued.id), /unavailable/u);
    await restarted.revoke(manager, issued.id);
    assert.equal(calls[1].action, 'revoke');
    assert.equal((await restarted.list(manager))[0].status, 'revoked');
});
