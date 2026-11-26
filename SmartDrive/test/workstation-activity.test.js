import assert from 'node:assert/strict';
import { mkdtemp, readFile, readdir, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { test } from 'node:test';
import { workstationEnrollment } from '../src/workstations.js';

test('workstation activity is scoped, persisted and retained when disconnected or the broker is unavailable', async t => {
    const root = await mkdtemp(join(tmpdir(), 'smartdrive-workstation-activity-'));
    t.after(() => rm(root, { recursive: true, force: true }));
    let mode = 'connected', observed;
    const requests = [], managers = { storeIds: ['1', '2'] };
    const options = { root, server: 'DECKERSDRIVE', share: 'DeckersDrive', broker: async () => {},
        activity: async accounts => {
            requests.push(accounts);
            if (mode === 'unavailable') throw new Error('private Windows detail');
            if (mode === 'disconnected') return [];
            return accounts.map(account => ({ account, lastActiveAt: observed }));
        } };
    const enrollment = workstationEnrollment(options);
    for (const store of ['1', '2']) { const issued = await enrollment.issue(managers, 'Store ' + store, [store]);await enrollment.redeem(issued.code); }
    observed = new Date(Date.now() - 1000).toISOString();
    const first = await enrollment.list({ storeIds: ['1'] });
    assert.equal(first.length, 1);assert.equal(requests[0].length, 1);assert.equal(first[0].activityState, 'connected');
    assert.equal(first[0].lastActiveAt, observed);
    const restarted = workstationEnrollment(options);mode = 'disconnected';
    const disconnected = (await restarted.list({ storeIds: ['1'] }))[0];
    assert.equal(disconnected.activityState, 'disconnected');assert.equal(disconnected.lastActiveAt, observed);
    mode = 'unavailable';const unavailable = (await restarted.list({ storeIds: ['1'] }))[0];
    assert.equal(unavailable.activityState, 'unavailable');assert.equal(unavailable.lastActiveAt, observed);
    for (const file of await readdir(join(root, 'workstations'))) assert.ok(!(await readFile(join(root, 'workstations', file), 'utf8')).includes('private Windows detail'));
});

test('activity rejects out-of-scope accounts and cannot reactivate a revoked workstation', async t => {
    const root = await mkdtemp(join(tmpdir(), 'smartdrive-workstation-activity-policy-'));
    t.after(() => rm(root, { recursive: true, force: true }));
    let calls = 0;
    const options = { root, server: 'DECKERSDRIVE', share: 'DeckersDrive', broker: async () => {},
        activity: async () => { calls++;return [{ account: 'sdpc_ffffffffffff', lastActiveAt: new Date().toISOString() }]; } };
    const manager = { storeIds: ['1'] }, enrollment = workstationEnrollment(options);
    const issued = await enrollment.issue(manager, 'Studio PC', ['1']);await enrollment.redeem(issued.code);
    const invalid = (await enrollment.list(manager))[0];assert.equal(invalid.activityState, 'unavailable');assert.equal(invalid.lastActiveAt, undefined);
    await enrollment.revoke(manager, issued.id);
    await enrollment.refreshActivity();assert.equal(calls, 1);
    assert.equal((await enrollment.list(manager))[0].status, 'revoked');assert.equal(calls, 1);
});
