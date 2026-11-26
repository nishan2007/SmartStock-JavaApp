import { isIP } from 'node:net';
import { isAbsolute, dirname, resolve, win32 } from 'node:path';
import { lstat, open } from 'node:fs/promises';
import { X509Certificate, createPrivateKey } from 'node:crypto';
import { createSecureContext } from 'node:tls';

export function canonicalAddress(value) {
    if (typeof value !== 'string') throw new Error('Use literal origin IP addresses.');
    if (value.startsWith('::ffff:') && isIP(value.slice(7)) === 4) value = value.slice(7);
    const family = isIP(value);
    if (!family) throw new Error('Use literal origin IP addresses.');
    return family === 6 ? new URL('http://[' + value + ']/').hostname.slice(1, -1) : value;
}
function privateAddress(value) {
    const address = canonicalAddress(value);
    if (isIP(address) === 6) return address === '::1' || /^(fc|fd)/u.test(address);
    const parts = address.split('.').map(Number);
    return parts[0] === 10 || parts[0] === 127 || parts[0] === 192 && parts[1] === 168 || parts[0] === 172 && parts[1] >= 16 && parts[1] <= 31;
}
export function originTlsSettings(value) {
    if (!value || typeof value !== 'object' || Array.isArray(value) || Object.keys(value).some(key =>
        !['listenAddress', 'allowedAddresses', 'certificatePath', 'privateKeyPath'].includes(key))) throw new Error('Invalid HTTPS origin settings.');
    const listenAddress = canonicalAddress(value.listenAddress);
    if (!privateAddress(listenAddress) || !Array.isArray(value.allowedAddresses) || !value.allowedAddresses.length || value.allowedAddresses.length > 16) throw new Error('Use a private origin address and explicit connector/client IP addresses.');
    const allowedAddresses = value.allowedAddresses.map(canonicalAddress);
    if (!allowedAddresses.every(privateAddress) || new Set(allowedAddresses).size !== allowedAddresses.length) throw new Error('Use unique private connector/client IP addresses.');
    for (const key of ['certificatePath', 'privateKeyPath']) {
        if (typeof value[key] !== 'string' || !(/^[A-Za-z]:[\\/]/u.test(value[key]) || process.platform !== 'win32' && isAbsolute(value[key]) && !value[key].startsWith('//'))) throw new Error('Use absolute local origin certificate/key paths.');
    }
    if (value.certificatePath.toLowerCase() === value.privateKeyPath.toLowerCase()) throw new Error('Store the certificate and private key in separate files.');
    return { listenAddress, allowedAddresses, certificatePath: value.certificatePath, privateKeyPath: value.privateKeyPath };
}
async function readPrivateFile(path, maximum, storeRoots) {
    if (!isAbsolute(path)) throw new Error('Origin TLS files require local absolute paths.');
    const normalized = resolve(path);
    const compare = value => process.platform === 'win32' ? value.toLowerCase() : value;
    for (const root of Object.values(storeRoots)) {
        const base = compare(resolve(root));const target = compare(normalized);
        if (target === base || target.startsWith(base + (process.platform === 'win32' ? win32.sep : '/'))) throw new Error('Keep origin TLS files outside employee storage.');
    }
    for (let current = normalized; ; current = dirname(current)) {
        const info = await lstat(current);
        if (info.isSymbolicLink()) throw new Error('Linked origin TLS path.');
        if (dirname(current) === current) break;
    }
    const before = await lstat(normalized);
    if (!before.isFile() || before.nlink !== 1 || before.size < 1 || before.size > maximum) throw new Error('Invalid origin TLS file.');
    const handle = await open(normalized, 'r');
    try {
        const info = await handle.stat();
        if (info.ino !== before.ino || info.dev !== before.dev || info.size !== before.size || info.nlink !== 1) throw new Error('Origin TLS file changed during load.');
        const bytes = Buffer.alloc(info.size);const read = await handle.read(bytes, 0, bytes.length, 0);
        if (read.bytesRead !== bytes.length) throw new Error('Incomplete origin TLS file.');
        return bytes;
    } finally { await handle.close(); }
}
export async function loadOriginTls(value, storeRoots = {}) {
    const settings = originTlsSettings(value);
    const cert = await readPrivateFile(settings.certificatePath, 1024 * 1024, storeRoots);
    const key = await readPrivateFile(settings.privateKeyPath, 65536, storeRoots);
    try {
        const leaf = new X509Certificate(cert);
        if (Date.parse(leaf.validFrom) > Date.now() || Date.parse(leaf.validTo) <= Date.now() || !leaf.checkPrivateKey(createPrivateKey(key))) throw new Error('Invalid certificate validity or key pair.');
        createSecureContext({ cert, key, minVersion: 'TLSv1.2' });
    } catch { throw new Error('Origin certificate/key pair is invalid or outside its validity period.'); }
    const allowed = new Set(settings.allowedAddresses);
    return { tls: { cert, key, minVersion: 'TLSv1.2' }, host: settings.listenAddress,
        originPeer: address => { try { return allowed.has(canonicalAddress(address)); } catch { return false; } } };
}
