import assert from 'node:assert/strict';
import { mkdtemp, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { test } from 'node:test';
import { scannerMetadata, scannerSetup } from '../src/scanner-setup.js';
import { scannerControls } from '../src/scanner-controls.js';
import { installationConfig } from '../src/installation-config.js';

const copier = { id: 'Canon-C5040', storeId: '1', account: 'sdscan_c5040', displayName: 'Front desk Canon',
    model: 'Canon imageRUNNER ADVANCE C5040', reservedAddress: '192.168.20.50' };
const connection = { serverName: 'DECKERSDRIVE', shareName: 'DeckersDrive' };

test('derives machine-specific SMB setup and published-folder instructions without storing a password', () => {
    const setup = scannerSetup(copier, connection);
    assert.equal(setup.protocol, 'SMB2/3');
    assert.equal(setup.destination, '\\\\DECKERSDRIVE\\ScanIntake\\Canon-C5040');
    assert.equal(setup.publishedFolder, '\\\\DECKERSDRIVE\\DeckersDrive\\1\\Scans\\Canon-C5040');
    assert.equal(setup.username, 'DECKERSDRIVE\\sdscan_c5040');
    assert.equal(setup.reservedAddress, copier.reservedAddress);assert.equal(setup.displayName, copier.displayName);
    assert.equal(setup.password, undefined);
    assert.ok(setup.steps.some(step => step.includes('one-page test PDF')));
    assert.ok(setup.steps.some(step => step.includes('Do not enable SMB1')));
    for (const bad of [{ reservedAddress: 'https://copier.example' }, { reservedAddress: '192.168.20.999' },
        { displayName: 'Bad\nName' }, { id: '../OtherCopier' }]) assert.throws(() => scannerSetup({ ...copier, ...bad }, connection));
    assert.throws(() => scannerSetup(copier, { shareName: 'DeckersDrive' }));
    assert.equal(scannerMetadata({ id: copier.id }).reservedAddress, null);
});

test('keeps setup metadata scoped to the employee stores and accepts it through the setup generator', async t => {
    const root = await mkdtemp(join(tmpdir(), 'smartdrive-scanner-setup-'));
    t.after(() => rm(root, { recursive: true, force: true }));
    const other = { ...copier, id: 'Other-Copier', storeId: '2', account: 'sdscan_other', reservedAddress: '192.168.30.50' };
    const controls = scannerControls(root, [copier, other], connection);
    const list = await controls.list({ storeIds: ['1'] });assert.equal(list.length, 1);
    assert.ok(!JSON.stringify(list).includes(other.reservedAddress));
    const config = installationConfig({ installationName: 'AtlasDrive', dataParent: 'D:\\Shares', storeIds: ['1'],
        publicOrigin: 'https://drive.example.com', employeeOrigin: 'https://employees.example.com', employeeServer: 'https://stock.example.com', scanners: [copier] });
    assert.equal(config.scanners[0].reservedAddress, copier.reservedAddress);
    assert.equal(config.scanners[0].displayName, copier.displayName);
    assert.ok(config.scanners[0].intake.startsWith('D:\\Shares\\AtlasDrive\\Private\\Intake\\'));
});
