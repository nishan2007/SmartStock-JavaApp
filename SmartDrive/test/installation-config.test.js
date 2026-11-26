import assert from 'node:assert/strict';
import { test } from 'node:test';
import { installationConfig } from '../src/installation-config.js';
import { execFile as execFileCallback } from 'node:child_process';
import { promisify } from 'node:util';
import { randomUUID } from 'node:crypto';
import { mkdtemp, readFile, rm, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';
const execFile = promisify(execFileCallback);

const setup = { installationName: 'DeckersDrive', dataParent: 'D:\\Shares', storeIds: ['1', '2'],
    publicOrigin: 'https://drive.example.com', employeeOrigin: 'https://employees.example.com',
    employeeServer: 'https://smartstock.example.com',
    scanners: [{ id: 'Canon-C5040', storeId: '1', account: 'sdscan_c5040' }] };

test('setup carries explicit origin TLS policy and rejects disabled-verification settings', () => {
    const originTls = { listenAddress: '192.168.1.60', allowedAddresses: ['192.168.1.10'],
        certificatePath: 'C:\\ProgramData\\SmartDrive\\TLS\\origin.pem', privateKeyPath: 'C:\\ProgramData\\SmartDrive\\TLS\\origin.key' };
    assert.deepEqual(installationConfig({ ...setup, originTls }).originTls, originTls);
    assert.throws(() => installationConfig({ ...setup, originTls: { ...originTls, noTLSVerify: true } }));
});

test('generates every storage path from one installation name and leaves source input unchanged', () => {
    const first = installationConfig(setup);
    const renamed = installationConfig({ ...setup, installationName: 'AtlasDrive', displayName: 'Atlas Drive' });
    assert.equal(first.storeRoots['1'], 'D:\\Shares\\DeckersDrive\\Stores\\1');
    assert.equal(renamed.serverName, 'ATLASDRIVE');
    assert.equal(renamed.storeRoots['2'], 'D:\\Shares\\AtlasDrive\\Stores\\2');
    for (const path of [renamed.enrollmentRoot, renamed.scanStateRoot, renamed.backupStateRoot,
        renamed.recoveryRoot, renamed.office.cacheRoot, renamed.scanners[0].intake]) {
        assert.ok(path.startsWith('D:\\Shares\\AtlasDrive\\Private\\'));
    }
    assert.ok(!JSON.stringify(renamed).includes('DeckersDrive'));
    assert.equal(setup.installationName, 'DeckersDrive');
    assert.equal(setup.scanners[0].intake, undefined);
    assert.equal(renamed.displayName, 'Atlas Drive');
});

test('rejects stale path overrides, duplicate stores, invalid scanners and embedded OneDrive secrets', () => {
    for (const change of [{ storeRoots: { 1: 'D:\\OldCompany' } }, { storeIds: ['1', '1'] },
        { scanners: [{ id: '../Scanner', storeId: '1', account: 'sdscan_test' }] },
        { oneDrive: { tenantId: 'tenant', clientId: 'client', driveId: 'drive', folderId: 'folder', clientSecret: 'must-not-write' } },
        { publicOrigin: 'http://drive.example.com' }, { office: { cacheRoot: 'D:\\OldCompany' } }]) {
        assert.throws(() => installationConfig({ ...setup, ...change }));
    }
    assert.equal(installationConfig({ ...setup, office: false }).office, undefined);
});

test('writes a reviewable configuration without overwriting it and supports the branded shortcut dry run', async t => {
    const root = await mkdtemp(join(tmpdir(), 'smartdrive-config-'));
    t.after(() => rm(root, { recursive: true, force: true }));
    const name = 'TestDrive_' + randomUUID().slice(0, 8);
    const source = join(root, 'setup.json'), destination = join(root, 'installation.json');
    await writeFile(source, JSON.stringify({ ...setup, installationName: name }));
    const generator = fileURLToPath(new URL('../tools/configure-installation.mjs', import.meta.url));
    await execFile(process.execPath, [generator, source, destination]);
    const first = await readFile(destination, 'utf8');
    assert.equal(JSON.parse(first).installationName, name);
    await assert.rejects(execFile(process.execPath, [generator, source, destination]));
    assert.equal(await readFile(destination, 'utf8'), first);
    if (process.platform === 'win32') {
        const shortcut = fileURLToPath(new URL('../tools/Install-ManagementShortcut.ps1', import.meta.url));
        const result = await execFile('powershell.exe', ['-NoProfile', '-File', shortcut, '-ConfigPath', destination, '-WhatIf']);
        assert.ok(result.stdout.includes(name + ' Management.lnk'));
    }
});
