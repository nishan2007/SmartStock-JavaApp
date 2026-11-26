import { readFile } from 'node:fs/promises';
import { loadOriginTls, originTlsSettings } from '../src/origin-tls.js';
const [path] = process.argv.slice(2);
if (!path || process.argv.length !== 3) throw new Error('Usage: node tools/validate-origin.mjs <installation-config.json>');
const config = JSON.parse((await readFile(path, 'utf8')).replace(/^\uFEFF/u, ''));
const port = config.port ?? 4178;
if (!Number.isInteger(port) || port < 1 || port > 65535) throw new Error('Use a valid SmartDrive origin port.');
if (!Object.hasOwn(config, 'originTls')) process.stdout.write(JSON.stringify({ tls: false, listenAddress: '127.0.0.1' }) + '\n');
else {
    await loadOriginTls(config.originTls, config.storeRoots);
    process.stdout.write(JSON.stringify({ tls: true, ...originTlsSettings(config.originTls), port }) + '\n');
}
