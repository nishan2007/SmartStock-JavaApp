import { randomUUID } from 'node:crypto';
import { mkdir, open, readFile, rename, unlink } from 'node:fs/promises';
import { join } from 'node:path';
import { scannerSetup } from './scanner-setup.js';

export function scannerControls(stateRoot, scanners, connection) {
    const file = join(stateRoot, 'paused-scanners.json');
    const devices = () => new Map(scanners.map(scanner => [scanner.id, scanner]));
    let writes = Promise.resolve();
    async function paused() {
        try {
            const result = JSON.parse(await readFile(file, 'utf8'));
            if (!Array.isArray(result) || !result.every(id => devices().has(id))) {
                throw new Error('Invalid scanner processing settings.');
            }
            return new Set(result);
        } catch (error) {
            if (error.code === 'ENOENT') return new Set();
            throw error;
        }
    }
    return {
        async enabled(id) {
            const device = devices().get(id);
            return !!device && device.enabled !== false && !(await paused()).has(id);
        },
        async list(identity) {
            const stopped = await paused();
            return scanners.filter(scanner => identity.storeIds.includes(scanner.storeId)).map(scanner => ({
                id: scanner.id, store: scanner.storeId, paused: stopped.has(scanner.id),
                ...(connection ? { setup: scannerSetup(scanner, connection) } : {})
            }));
        },
        set(identity, id, pause) {
            const device = devices().get(id);
            if (!device || !identity.storeIds.includes(device.storeId) || typeof pause !== 'boolean') {
                throw new Error('Scanner unavailable.');
            }
            const operation = writes.then(async () => {
                await mkdir(stateRoot, { recursive: true });
                const values = await paused();
                if (pause) values.add(id); else values.delete(id);
                const temporary = file + '.' + randomUUID() + '.tmp';
                try {
                    const handle = await open(temporary, 'wx');
                    try { await handle.writeFile(JSON.stringify([...values])); await handle.sync(); }
                    finally { await handle.close(); }
                    await rename(temporary, file);
                } finally {
                    await unlink(temporary).catch(error => { if (error.code !== 'ENOENT') throw error; });
                }
            });
            writes = operation.catch(() => {});
            return operation;
        }
    };
}
