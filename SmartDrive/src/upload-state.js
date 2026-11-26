import { randomUUID } from 'node:crypto';
import { mkdir, open, readFile, rename, unlink } from 'node:fs/promises';
import { isAbsolute, join } from 'node:path';

// Upload URLs authorize transfers. Keep this state in the protected backup root,
// separate from the version records returned to the management dashboard.
export function uploadState(root) {
    if (!isAbsolute(root)) throw new Error('Use an absolute private upload-state directory.');
    function path(id) {
        if (!/^[a-f0-9-]{36}$/u.test(id)) throw new Error('Invalid upload identity.');
        return join(root, id + '.json');
    }
    return {
        async read(id) {
            try { return JSON.parse(await readFile(path(id), 'utf8')); }
            catch (error) { if (error.code === 'ENOENT') return null; throw error; }
        },
        async write(id, value) {
            await mkdir(root, { recursive: true });
            const target = path(id);
            const staged = target + '.' + randomUUID() + '.tmp';
            try {
                const file = await open(staged, 'wx', 0o600);
                try { await file.writeFile(JSON.stringify(value)); await file.sync(); }
                finally { await file.close(); }
                await rename(staged, target);
            } finally {
                await unlink(staged).catch(error => { if (error.code !== 'ENOENT') throw error; });
            }
        },
        async remove(id) {
            await unlink(path(id)).catch(error => { if (error.code !== 'ENOENT') throw error; });
        }
    };
}
