import assert from 'node:assert/strict';
import { test } from 'node:test';
import { execFile } from 'node:child_process';
import { promisify } from 'node:util';
import { link, mkdtemp, readFile, rm, symlink } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { request } from 'node:https';
import { once } from 'node:events';
import { originTlsSettings, loadOriginTls, canonicalAddress } from '../src/origin-tls.js';
import { driveServer } from '../src/server.js';

test('origin configuration rejects broad/public listeners, unbounded clients and unsafe options', () => {
    const valid = { listenAddress: '192.168.1.60', allowedAddresses: ['192.168.1.10'], certificatePath: 'C:\\Private\\origin.pem', privateKeyPath: 'C:\\Private\\origin.key' };
    assert.equal(originTlsSettings(valid).listenAddress, valid.listenAddress);
    assert.equal(canonicalAddress('::ffff:192.168.1.10'), '192.168.1.10');
    assert.equal(canonicalAddress('FD00:0:0:0:0:0:0:1'), 'fd00::1');
    for (const change of [{ listenAddress: '0.0.0.0' }, { listenAddress: '::' }, { listenAddress: '8.8.8.8' },
        { allowedAddresses: ['0.0.0.0/0'] }, { allowedAddresses: [] }, { allowedAddresses: ['192.168.1.10', '192.168.1.10'] },
        { privateKeyPath: '\\\\server\\share\\key.pem' }, { noTLSVerify: true }]) assert.throws(() => originTlsSettings({ ...valid, ...change }));
});

test('real HTTPS origin requires a trusted certificate and rejects unapproved peers before employee auth', async t => {
    const root = await mkdtemp(join(tmpdir(), 'smartdrive-origin-tls-'));
    t.after(() => rm(root, { recursive: true, force: true }));
    const cert = join(root, 'origin.pem'), key = join(root, 'origin.key');
    const openssl = process.platform === 'win32' ? 'C:\\Program Files\\Git\\usr\\bin\\openssl.exe' : 'openssl';
    await promisify(execFile)(openssl, ['req', '-x509', '-newkey', 'rsa:2048', '-nodes', '-days', '1',
        '-subj', '/CN=localhost', '-addext', 'subjectAltName=IP:127.0.0.1', '-keyout', key, '-out', cert], { windowsHide: true });
    const settings = { listenAddress: '127.0.0.1', allowedAddresses: ['127.0.0.1'], certificatePath: cert, privateKeyPath: key };
    const origin = await loadOriginTls(settings);
    let identities = 0;
    const server = driveServer({ ...origin, library: { stores: () => [] }, employeeSession: async () => { identities++;return { storeIds: [] }; } });
    server.listen(0, '127.0.0.1');await once(server, 'listening');
    t.after(() => new Promise(resolve => server.close(resolve)));
    const get = (port, ca) => new Promise((resolve, reject) => {
        const req = request({ hostname: '127.0.0.1', port, path: '/employees/drive/api/stores', ca, agent: false }, response => {
            response.resume();response.on('end', () => resolve(response.statusCode));
        });req.on('error', reject);req.end();
    });
    await assert.rejects(get(server.address().port));
    assert.equal(await get(server.address().port, await readFile(cert)), 200);assert.equal(identities, 1);
    const deniedOrigin = await loadOriginTls({ ...settings, allowedAddresses: ['192.168.1.10'] });
    const denied = driveServer({ ...deniedOrigin, library: {}, employeeSession: async () => { throw new Error('Peer rejection must precede employee auth.'); } });
    denied.listen(0, '127.0.0.1');await once(denied, 'listening');t.after(() => new Promise(resolve => denied.close(resolve)));
    assert.equal(await get(denied.address().port, await readFile(cert)), 403);
    await assert.rejects(loadOriginTls(settings, { 1: root }), /outside employee storage/u);
    const linked = join(root, 'linked-parent');await symlink(root, linked, 'junction');
    await assert.rejects(loadOriginTls({ ...settings, privateKeyPath: join(linked, 'origin.key') }), /Linked/u);
    const otherKey = join(root, 'other.key');
    await promisify(execFile)(openssl, ['genrsa', '-out', otherKey, '2048'], { windowsHide: true });
    await assert.rejects(loadOriginTls({ ...settings, privateKeyPath: otherKey }), /invalid or outside/u);
    await link(key, join(root, 'hard-linked-key'));
    await assert.rejects(loadOriginTls(settings), /Invalid origin TLS file/u);
});
