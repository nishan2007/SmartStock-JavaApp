import assert from 'node:assert/strict';
import { test } from 'node:test';
import { resolveBranding } from '../src/branding.js';

const dataParent = 'D:\\Shares';

test('uses SmartDrive as the default brand', () => {
    const brand = resolveBranding({ dataParent });
    assert.equal(brand.displayName, 'SmartDrive');
    assert.equal(brand.filesRoot, 'D:\\Shares\\SmartDrive\\Stores');
});

test('uses one company installation name for the share and storage paths', () => {
    const brand = resolveBranding({
        dataParent, installationName: 'DeckersDrive'
    });
    assert.deepEqual(brand, {
        productName: 'SmartDrive',
        displayName: 'DeckersDrive',
        installationName: 'DeckersDrive',
        shareName: 'DeckersDrive',
        dataRoot: 'D:\\Shares\\DeckersDrive',
        filesRoot: 'D:\\Shares\\DeckersDrive\\Stores',
        recoveryRoot: 'D:\\Shares\\DeckersDrive\\Private\\Recovery',
        oneDriveFolder: 'DeckersDrive'
    });
});

test('changing only the displayed name keeps storage and mapping names stable', () => {
    const brand = resolveBranding({ dataParent, displayName: 'Deckers Drive' });
    assert.equal(brand.displayName, 'Deckers Drive');
    assert.equal(brand.shareName, 'SmartDrive');
});

test('rejects invalid installation names and nonlocal roots', () => {
    for (const installationName of ['..', '../Other', 'A/B', 'A:B', 'A.', 'NUL', 'Name\n']) {
        assert.throws(() => resolveBranding({ dataParent, installationName }), /Name/u);
    }
    for (const root of ['D:Shares', 'Shares', '\\\\host\\share']) {
        assert.throws(() => resolveBranding({ dataParent: root }), /dataParent/u);
    }
});
