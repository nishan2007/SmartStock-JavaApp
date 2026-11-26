import assert from 'node:assert/strict';
import { copyFile, mkdir, mkdtemp, readFile, readdir, rm, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { once } from 'node:events';
import { test } from 'node:test';
import { scannerEnrollment } from '../src/scanner-enrollment.js';
import { scannerControls } from '../src/scanner-controls.js';
import { scannerCredentials } from '../src/scanner-credentials.js';
import { scanIntake } from '../src/scans.js';
import { driveServer } from '../src/server.js';
import { managementAudit } from '../src/audit.js';

async function fixture(t, broker) {
    const dataRoot = await mkdtemp(join(tmpdir(), 'smartdrive-enroll-scanner-'));
    t.after(() => rm(dataRoot, { recursive: true, force: true }));
    const storeRoots = { 1: join(dataRoot, 'Stores', '1') };
    await mkdir(storeRoots[1], { recursive: true });
    const options = { root: join(dataRoot, 'Private', 'ScanState'), dataRoot, scanners: [], storeRoots,
        connection: { serverName: 'DECKERSDRIVE', shareName: 'DeckersDrive' }, broker };
    return { options, registry: await scannerEnrollment(options) };
}
const manager = { userId: 7, storeIds: ['1'], permissions: ['MANAGE_SMARTDRIVE'] };
const input = { id: 'Canon-C5040', storeId: '1', displayName: 'Office copier', model: 'Canon imageRUNNER ADVANCE C5040' };

test('enrolled copier joins existing controls, credentials and intake without restart; registry survives restart without passwords', async t => {
    const calls = [];
    const { options, registry } = await fixture(t, async request => {
        calls.push(request);
        if (request.action === 'scanner-create') await mkdir(join(options.dataRoot, 'Private', 'Intake', request.id), { recursive: true });
    });
    const controls = scannerControls(options.root, options.scanners, options.connection);
    const credentials = scannerCredentials({ root: join(options.root, 'Credentials'), scanners: options.scanners, broker: options.broker });
    const poll = scanIntake({ scanners: options.scanners, storeRoots: options.storeRoots, stateRoot: options.root,
        copyClosed: copyFile, malwareCheck: async () => true, resetAcl: async () => {}, isEnabled: controls.enabled });
    assert.deepEqual(await poll(), []);
    const result = await registry.enroll(manager, input);
    assert.match(result.password, /^[A-Za-z0-9!_-]{32,100}$/u);
    assert.equal(calls[0].action, 'scanner-create');assert.equal(calls[0].store, '1');
    assert.equal(result.setup.destination, '\\\\DECKERSDRIVE\\ScanIntake\\Canon-C5040');
    assert.equal((await controls.list(manager))[0].setup.model, input.model);
    assert.equal((await credentials.list(manager))[0].account, result.account);
    const intake = options.scanners[0].intake;
    const pdf = '%PDF-1.7\nscanner enrollment test\n%%EOF\n';
    await writeFile(join(intake, 'test.pdf'), pdf);
    assert.equal((await poll())[0].status, 'fulfilled');
    assert.equal((await poll())[0].status, 'fulfilled');
    const published = join(options.storeRoots[1], 'Scans', input.id);
    const names = await readdir(published);assert.equal(names.length, 1);
    assert.equal(await readFile(join(published, names[0]), 'utf8'), pdf);
    const saved = await readFile(join(options.root, 'enrolled-scanners.json'), 'utf8');
    assert.ok(!saved.includes(result.password));
    const restarted = { ...options, scanners: [] };
    await scannerEnrollment(restarted);
    assert.equal(restarted.scanners.length, 1);assert.equal(restarted.scanners[0].intake, intake);
    await credentials.disable(manager, input.id);assert.equal(calls.at(-1).action, 'scanner-disable');
});

test('enrollment serializes duplicate IDs and retains failed setup for review across restart', async t => {
    const calls = [];
    const { options, registry } = await fixture(t, async request => { calls.push(request);throw new Error('Synthetic account service timeout'); });
    const results = await Promise.allSettled([registry.enroll(manager, input), registry.enroll(manager, { ...input, id: input.id.toLowerCase() })]);
    assert.ok(results.every(result => result.status === 'rejected'));
    assert.equal(calls.filter(request => request.action === 'scanner-create').length, 1);
    assert.equal(calls.at(-1).action, 'scanner-disable');assert.equal(options.scanners.length, 0);
    assert.equal(registry.pending(manager)[0].enrollmentPending, true);
    assert.deepEqual(registry.pending({ storeIds: ['2'] }), []);
    const restarted = await scannerEnrollment({ ...options, scanners: [] });
    assert.equal(restarted.pending(manager)[0].credentialState, 'requires-review');
    for (const invalid of [{ ...input, storeId: '2' }, { ...input, id: '../escape' }, { ...input, id: 'CON' },
        { ...input, reservedAddress: 'example.com' }, { ...input, intake: 'C:\\unsafe' }]) {
        await assert.rejects(restarted.enroll(manager, invalid));
    }
    assert.equal(calls.length, 2);
});

test('scanner enrollment API enforces manager, CSRF, assigned store and audit without exposing passwords in listings', async t => {
    const calls = [];
    const { options, registry } = await fixture(t, async request => calls.push(request));
    const audit = managementAudit(join(options.root, 'Audit'));
    const server = driveServer({ library: {}, audit, scannerEnrollmentControl: registry,
        scannerControl: scannerControls(options.root, options.scanners, options.connection),
        employeeSession: async (request, write) => write && request.headers['x-csrf-token'] !== 'valid' ? null :
            { ...manager, permissions: request.headers.authorization === 'manager' ? manager.permissions : [] } });
    server.listen(0, '127.0.0.1');await once(server, 'listening');
    t.after(() => new Promise(resolve => server.close(resolve)));
    const base = `http://127.0.0.1:${server.address().port}/employees/drive/api/management/`;
    const post = { method: 'POST', body: JSON.stringify(input), headers: { authorization: 'manager', 'x-csrf-token': 'valid' } };
    assert.equal((await fetch(base + 'scanner-enroll', { ...post, headers: { authorization: 'manager' } })).status, 401);
    assert.equal((await fetch(base + 'scanner-enroll', { ...post, headers: { ...post.headers, authorization: 'employee' } })).status, 403);
    assert.equal((await fetch(base + 'scanner-enroll', { ...post, body: JSON.stringify({ ...input, storeId: '2' }) })).status, 400);
    assert.equal(calls.length, 0);
    const response = await fetch(base + 'scanner-enroll', post);assert.equal(response.status, 200);
    assert.equal(response.headers.get('cache-control'), 'private, no-store');
    const result = await response.json();assert.ok(result.password);
    const listing = await (await fetch(base + 'scanners', { headers: post.headers })).json();assert.equal(listing[0].id, input.id);
    const events = await audit.list(manager);assert.equal(events.length, 2);
    assert.ok(!JSON.stringify({ listing, events }).includes(result.password));
});
