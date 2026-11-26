import { randomUUID } from 'node:crypto';
import { mkdir, open, readFile, rename, unlink } from 'node:fs/promises';
import { join } from 'node:path';

export function backupSettings(stateRoot, defaults = []) {
    const file = join(stateRoot, 'selections.json');
    let writes = Promise.resolve();
    async function read() {
        try {
            const value = JSON.parse(await readFile(file, 'utf8'));
            if (!Array.isArray(value)) throw new Error('Invalid backup folder selections.');
            return value;
        } catch (error) {
            if (error.code === 'ENOENT') return structuredClone(defaults);
            throw error;
        }
    }
    return {
        read,
        set(selection, enabled) {
            const operation = writes.then(async () => {
                await mkdir(stateRoot, { recursive: true });
                const values = (await read()).filter(item => item.store !== selection.store ||
                    JSON.stringify(item.parts) !== JSON.stringify(selection.parts));
                if (enabled) values.push(selection);
                const temporary = file + '.' + randomUUID() + '.tmp';
                try {
                    const handle = await open(temporary, 'wx');
                    try { await handle.writeFile(JSON.stringify(values)); await handle.sync(); }
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
