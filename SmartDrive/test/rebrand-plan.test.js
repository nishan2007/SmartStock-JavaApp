import assert from 'node:assert/strict';
import { test } from 'node:test';
import { installationConfig } from '../src/installation-config.js';
import { rebrandPlan } from '../src/rebrand-plan.js';

function config() {
    const value = installationConfig({ installationName: 'DeckersDrive', dataParent: 'D:\\Shares', storeIds: ['1', '2'],
        publicOrigin: 'https://drive.example.com', employeeOrigin: 'https://employees.example.com', employeeServer: 'https://server.example.com',
        scanners: [{ id: 'Canon-C5040', account: 'sdscan_c5040', storeId: '1' }] });
    value.migrationCatalog = 'D:\\Shares\\DeckersDrive\\Private\\catalog.json';
    value.oneDrive = { tenantId: 'tenant', clientId: 'client', driveId: 'drive', folderId: 'folder' };
    value.originTls = { listenAddress: '192.168.1.60', allowedAddresses: ['192.168.1.10'],
        certificatePath: 'D:\\Shares\\DeckersDrive\\Private\\TLS\\origin.pem', privateKeyPath: 'D:\\Shares\\DeckersDrive\\Private\\TLS\\origin.key' };
    return value;
}
test('rebranding updates managed paths together without changing identities or external endpoints', () => {
    const original = config(), snapshot = structuredClone(original);
    const plan = rebrandPlan(original, 'ExampleDrive', 'Example Storage');
    assert.equal(plan.after.dataRoot, 'D:\\Shares\\ExampleDrive');
    assert.equal(plan.config.scanners[0].intake, 'D:\\Shares\\ExampleDrive\\Private\\Intake\\Canon-C5040');
    assert.equal(plan.config.migrationCatalog, 'D:\\Shares\\ExampleDrive\\Private\\catalog.json');
    assert.equal(plan.config.office.cacheRoot, 'D:\\Shares\\ExampleDrive\\Private\\OfficePreviews');
    assert.equal(plan.config.originTls.privateKeyPath, 'D:\\Shares\\ExampleDrive\\Private\\TLS\\origin.key');
    assert.deepEqual(plan.config.oneDrive, original.oneDrive);assert.equal(plan.config.serverName, original.serverName);
    assert.equal(plan.config.publicOrigin, original.publicOrigin);assert.equal(plan.config.scanners[0].account, original.scanners[0].account);
    assert.deepEqual(original, snapshot);assert.equal(plan.displayOnly, false);assert.ok(plan.changes.length >= 8);
});
test('display-only changes preserve every storage path; unmanaged external catalog remains external', () => {
    const original = config();original.migrationCatalog = 'E:\\Recovery\\catalog.json';
    const plan = rebrandPlan(original, 'DeckersDrive', 'Deckers Shared Files');
    assert.equal(plan.displayOnly, true);assert.deepEqual(plan.changes, []);
    assert.equal(plan.config.migrationCatalog, original.migrationCatalog);
    assert.deepEqual(plan.config.storeRoots, original.storeRoots);
});
test('rejects unsafe names, case-only moves and inconsistent storage layouts', () => {
    assert.throws(() => rebrandPlan(config(), '../escape'));
    assert.throws(() => rebrandPlan(config(), 'CON'));
    assert.throws(() => rebrandPlan(config(), 'deckersdrive'), /case-only/u);
    const wrongStore = config();wrongStore.storeRoots[1] = 'D:\\OtherStore';
    assert.throws(() => rebrandPlan(wrongStore, 'ExampleDrive'), /Stores layout/u);
    const wrongState = config();wrongState.scanStateRoot = 'D:\\Shares\\DeckersDriveOther\\Private';
    assert.throws(() => rebrandPlan(wrongState, 'ExampleDrive'), /managed storage/u);
});
