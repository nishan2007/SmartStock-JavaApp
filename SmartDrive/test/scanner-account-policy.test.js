import assert from 'node:assert/strict';
import { execFile as execFileCallback } from 'node:child_process';
import { promisify } from 'node:util';
import { mkdtemp, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { test } from 'node:test';
const execFile = promisify(execFileCallback);

test('native account broker rejects unconfigured accounts and malformed scanner actions before OS mutations', { skip: process.platform !== 'win32' }, async t => {
    const root = await mkdtemp(join(tmpdir(), 'smartdrive-native-policy-'));
    t.after(() => rm(root, { recursive: true, force: true }));
    const compiler = join(process.env.SystemRoot, 'Microsoft.NET', 'Framework64', 'v4.0.30319', 'csc.exe');
    const output = join(root, 'AccountPolicy.exe');
    const service = fileURLToPath(new URL('../installer/windows/SmartDriveAccounts.cs', import.meta.url));
    const smoke = fileURLToPath(new URL('./ScannerAccountPolicySmoke.cs', import.meta.url));
    await execFile(compiler, ['/nologo', '/target:exe', '/main:ScannerAccountPolicySmoke',
        '/reference:System.ServiceProcess.dll', '/reference:System.Web.Extensions.dll', '/out:' + output, service, smoke], { windowsHide: true });
    const result = await execFile(output, [], { windowsHide: true });
    assert.match(result.stdout, /rejected all invalid requests/u);
});
