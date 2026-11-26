import assert from 'node:assert/strict';
import { execFile as execFileCallback, spawn } from 'node:child_process';
import { once } from 'node:events';
import { createServer } from 'node:net';
import { promisify } from 'node:util';
import { mkdir, mkdtemp, readFile, rm, stat, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { test } from 'node:test';
const execFile = promisify(execFileCallback);

test('installed payload retains module metadata and operational tool dependencies without copying local configuration', { skip: process.platform !== 'win32' }, async t => {
    const root = await mkdtemp(join(tmpdir(), 'smartdrive-payload-'));
    t.after(() => rm(root, { recursive: true, force: true }));
    const source = fileURLToPath(new URL('..', import.meta.url));
    const copier = join(source, 'installer', 'windows', 'Copy-AppPayload.ps1');
    const destination = join(root, 'installed');
    const args = ['-NoProfile', '-File', copier, '-SourceRoot', source, '-Destination', destination];
    await execFile('powershell.exe', [...args, '-ValidateOnly']);
    await assert.rejects(stat(destination), { code: 'ENOENT' });
    await execFile('powershell.exe', args);
    assert.equal(JSON.parse(await readFile(join(destination, 'package.json'), 'utf8')).type, 'module');
    for (const file of ['src/main.js', 'web/app.js', 'tools/WindowsDriveCredential.cs',
        'installer/windows/Publish-StudioImport.ps1', 'installer/windows/IdentityStream.cs', 'installer/windows/Provision-Scanner.ps1']) {
        assert.ok((await stat(join(destination, file))).isFile());
    }
    await assert.rejects(stat(join(destination, 'tools', 'Test-SubmissionDatabase.ps1')), { code: 'ENOENT' });
    await assert.rejects(stat(join(destination, 'installation.json')), { code: 'ENOENT' });
    const setup = join(root, 'setup.json'), config = join(root, 'generated.json');
    await writeFile(setup, await readFile(join(destination, 'config.setup.example.json')));
    await execFile(process.execPath, [join(destination, 'tools', 'configure-installation.mjs'), setup, config]);
    assert.equal(JSON.parse(await readFile(config, 'utf8')).installationName, 'DeckersDrive');
    const probe = createServer();probe.listen(0, '127.0.0.1');await once(probe, 'listening');
    const port = probe.address().port;await new Promise(resolve => probe.close(resolve));
    const parent = join(root, 'data'), store = join(parent, 'PayloadDrive', 'Stores', '1');await mkdir(store, { recursive: true });
    const runtimeConfig = join(root, 'runtime.json');
    await writeFile(runtimeConfig, JSON.stringify({ installationName: 'PayloadDrive', dataParent: parent, port,
        publicOrigin: 'https://drive.example.com', employeeOrigin: 'https://employees.example.com',
        employeeServer: 'https://stock.example.com', storeRoots: { 1: store } }));
    const environment = { ...process.env };
    for (const key of Object.keys(environment)) if (key.startsWith('SMARTDRIVE_')) delete environment[key];
    environment.SMARTDRIVE_FILE_ID_KEY = 'a'.repeat(64);
    const child = spawn(process.execPath, [join(destination, 'src', 'main.js'), runtimeConfig], {
        env: environment, windowsHide: true, stdio: ['ignore', 'pipe', 'pipe']
    });
    const exited = once(child, 'exit');
    let timer;
    try {
        await new Promise((resolve, reject) => {
            let output = '';
            child.stdout.on('data', data => { output += data.toString();if (output.includes('SmartDrive listening')) resolve(); });
            child.once('error', reject);child.once('exit', () => reject(new Error('Copied payload exited before startup.')));
            timer = setTimeout(() => reject(new Error('Copied payload startup timed out.')), 10000);
        });
        const origin = `http://127.0.0.1:${port}/employees/drive/`;
        assert.deepEqual(await (await fetch(origin + 'api/branding')).json(), { name: 'PayloadDrive' });
        assert.equal((await fetch(origin + 'app.js')).status, 200);
    } finally { clearTimeout(timer);child.kill();await exited; }
    await assert.rejects(execFile('powershell.exe', args));
    assert.equal(JSON.parse(await readFile(join(destination, 'package.json'), 'utf8')).type, 'module');
});
