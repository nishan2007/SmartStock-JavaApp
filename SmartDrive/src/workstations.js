import { createHash, randomBytes, randomUUID } from 'node:crypto';
import { mkdir, open, readFile, readdir, rename, unlink } from 'node:fs/promises';
import { isAbsolute, join } from 'node:path';

export function workstationEnrollment({ root, server, share, broker, activity }) {
    if (!isAbsolute(root) || !/^[A-Za-z0-9][A-Za-z0-9.-]{0,252}$/u.test(server) ||
        !/^[A-Za-z][A-Za-z0-9_-]{0,39}$/u.test(share)) throw new Error('Invalid workstation enrollment configuration.');
    const records = join(root, 'workstations');
    const codes = join(root, 'enrollment');
    let writes = Promise.resolve();
    function mutate(operation) {
        const result = writes.then(operation);
        writes = result.catch(() => {});
        return result;
    }
    async function save(row) {
        await mkdir(records, { recursive: true });
        const file = join(records, row.id + '.json');
        const temporary = file + '.' + randomUUID() + '.tmp';
        try {
            const handle = await open(temporary, 'wx');
            try { await handle.writeFile(JSON.stringify(row)); await handle.sync(); }
            finally { await handle.close(); }
            await rename(temporary, file);
        } finally {
            await unlink(temporary).catch(error => { if (error.code !== 'ENOENT') throw error; });
        }
    }
    async function record(id) {
        if (!/^[a-f0-9-]{36}$/u.test(id)) throw new Error('Workstation unavailable.');
        return JSON.parse(await readFile(join(records, id + '.json'), 'utf8'));
    }
    function allowed(identity, row) {
        return row.stores.every(store => identity.storeIds.includes(store));
    }
    async function rows(identity) {
        await mkdir(records, { recursive: true });
        const result = [];
        for (const file of await readdir(records)) {
            if (!/^[a-f0-9-]{36}\.json$/u.test(file)) continue;
            const row = await record(file.slice(0, -5));
            if (!identity || allowed(identity, row)) result.push(row);
        }
        return result;
    }
    async function observe(identity) {
        const result = await rows(identity);
        if (!activity) return result;
        const active = result.filter(row => row.status === 'active');
        for (let offset = 0; offset < active.length; offset += 128) {
            const batch = active.slice(offset, offset + 128), accounts = batch.map(row => row.account);
            let sessions;
            try {
                sessions = await activity(accounts);
                if (!Array.isArray(sessions) || sessions.length > accounts.length || !sessions.every(session =>
                    accounts.includes(session.account) && typeof session.lastActiveAt === 'string' &&
                    Number.isFinite(Date.parse(session.lastActiveAt)) && Date.parse(session.lastActiveAt) <= Date.now() + 5000) ||
                    new Set(sessions.map(session => session.account)).size !== sessions.length) throw new Error('Invalid activity response.');
            } catch { for (const row of batch) row.activityState = 'unavailable';continue; }
            for (const row of batch) {
                const session = sessions.find(session => session.account === row.account);
                if (session && (!row.lastActiveAt || Date.parse(session.lastActiveAt) > Date.parse(row.lastActiveAt))) {
                    row.lastActiveAt = session.lastActiveAt;await save(row);
                }
                row.activityState = session ? 'connected' : 'disconnected';
            }
        }
        return result;
    }
    return {
        list: identity => mutate(() => observe(identity)),
        refreshActivity: () => mutate(() => observe()),
        issue(identity, name, stores) {
            return mutate(async () => {
                if (typeof name !== 'string' || !name.trim() || name.length > 80 ||
                    /[\x00-\x1f]/u.test(name) || !Array.isArray(stores) || !stores.length ||
                    !stores.every(store => /^[1-9]\d{0,8}$/u.test(store) && identity.storeIds.includes(store))) {
                    throw new Error('Choose a workstation name and assigned stores.');
                }
                await mkdir(codes, { recursive: true });
                const row = { id: randomUUID(), name: name.trim(), account: 'sdpc_' + randomBytes(6).toString('hex'),
                    stores: [...new Set(stores)].sort(), status: 'issued', createdAt: new Date().toISOString(),
                    expiresAt: new Date(Date.now() + 15 * 60000).toISOString() };
                const code = randomBytes(24).toString('base64url');
                const hash = createHash('sha256').update(code).digest('hex');
                await save(row);
                const handle = await open(join(codes, hash), 'wx');
                try { await handle.writeFile(row.id); await handle.sync(); }
                finally { await handle.close(); }
                return { id: row.id, code, expiresAt: row.expiresAt };
            });
        },
        redeem(code) {
            return mutate(async () => {
                if (typeof code !== 'string' || !/^[A-Za-z0-9_-]{32}$/u.test(code)) throw new Error('Invalid enrollment code.');
                const hash = createHash('sha256').update(code).digest('hex');
                const path = join(codes, hash);
                const used = path + '.used';
                await rename(path, used);
                const row = await record(await readFile(used, 'utf8'));
                if (row.status !== 'issued' || Date.parse(row.expiresAt) <= Date.now()) throw new Error('Enrollment code expired.');
                row.status = 'provisioning';
                await save(row);
                const password = 'Sd!9' + randomBytes(30).toString('base64url');
                try {
                    await broker({ action: 'create', account: row.account, password, stores: row.stores });
                    row.status = 'active';
                    row.enrolledAt = new Date().toISOString();
                    await save(row);
                    return { server, share, username: server.split('.')[0] + '\\' + row.account, password };
                } catch (error) {
                    row.status = 'requires-review';
                    await save(row);
                    throw error;
                }
            });
        },
        revoke(identity, id) {
            return mutate(async () => {
                const row = await record(id);
                if (!allowed(identity, row)) throw new Error('Workstation unavailable.');
                if (row.status !== 'issued' && row.status !== 'revoked') {
                    await broker({ action: 'revoke', account: row.account });
                }
                row.status = 'revoked';
                row.revokedAt = new Date().toISOString();
                await save(row);
            });
        }
    };
}
