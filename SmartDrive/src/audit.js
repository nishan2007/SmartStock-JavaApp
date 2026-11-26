import { randomUUID } from 'node:crypto';
import { mkdir, open, readFile, readdir } from 'node:fs/promises';
import { isAbsolute, join } from 'node:path';

export function managementAudit(root) {
    if (!isAbsolute(root)) throw new Error('Audit storage requires an absolute private directory.');
    const folder = join(root, 'audit');
    async function append(event) {
        await mkdir(folder, { recursive: true });
        const id = randomUUID();
        const handle = await open(join(folder, id + '.json'), 'wx');
        try {
            await handle.writeFile(JSON.stringify({ ...event, id, at: new Date().toISOString() }));
            await handle.sync();
        } finally { await handle.close(); }
    }
    return {
        async action(identity, action, stores, details, operation) {
            if (!Number.isSafeInteger(identity.userId) || !stores.length ||
                !stores.every(store => identity.storeIds.includes(store))) {
                throw new Error('Invalid management audit identity.');
            }
            const operationId = randomUUID();
            const event = { operationId, userId: identity.userId, action, stores, details };
            await append({ ...event, result: 'requested' });
            try {
                const result = await operation();
                await append({ ...event, result: 'completed' });
                return result;
            } catch (error) {
                await append({ ...event, result: 'failed' });
                throw error;
            }
        },
        async list(identity) {
            await mkdir(folder, { recursive: true });
            const events = [];
            for (const name of await readdir(folder)) {
                if (!/^[a-f0-9-]{36}\.json$/u.test(name)) continue;
                let event;
                try { event = JSON.parse(await readFile(join(folder, name), 'utf8')); }
                catch { throw new Error('Management activity could not be read.'); }
                if (Array.isArray(event.stores) && event.stores.every(store => identity.storeIds.includes(store))) {
                    events.push(event);
                }
            }
            return events.sort((a, b) => b.at.localeCompare(a.at)).slice(0, 500);
        }
    };
}
