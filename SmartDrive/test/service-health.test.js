import assert from 'node:assert/strict';
import { test } from 'node:test';
import { serviceHealth } from '../src/service-health.js';
import { managementStatus } from '../src/management.js';

test('health probes coalesce, cache results and never expose private failure messages', async () => {
    let calls = 0, release;
    const probe = serviceHealth({ accounts: async () => { calls++;await new Promise(resolve => { release = resolve; }); } });
    const first = probe(), second = probe();await Promise.resolve();release();
    const results = await Promise.all([first, second]);assert.equal(calls, 1);assert.equal(results[0].accounts.state, 'ready');
    results[0].accounts.state = 'modified';assert.equal((await probe()).accounts.state, 'ready');
    const failed = serviceHealth({ accounts: async () => { throw new Error('private account password and server path'); } });
    const unavailable = await failed();assert.equal(unavailable.accounts.state, 'unavailable');assert.ok(!JSON.stringify(unavailable).includes('password'));
});

test('health timeout leaves management responsive and reports recovery on a later check', async () => {
    let ready = false;
    const probe = serviceHealth({ accounts: () => ready ? Promise.resolve() : new Promise(() => {}) }, { timeoutMs: 15, cacheMs: 0 });
    const status = managementStatus({ storeRoots: {} }, undefined, probe);
    assert.equal((await status({ storeIds: [] })).dependencies.accounts.state, 'unavailable');
    ready = true;assert.equal((await status({ storeIds: [] })).dependencies.accounts.state, 'ready');
});
