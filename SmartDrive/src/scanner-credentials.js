import { randomBytes, randomUUID } from 'node:crypto';
import { mkdir, open, readFile, rename, unlink } from 'node:fs/promises';
import { isAbsolute, join } from 'node:path';

export function scannerCredentials({ root, scanners, broker }) {
    if (!isAbsolute(root)) throw new Error('Scanner credentials require private absolute state storage.');
    function devices() {
        const result = new Map();
        const accounts = new Set();
        const ids = new Set();
        for (const scanner of scanners) {
            if (typeof scanner.id !== 'string' || typeof scanner.account !== 'string' || typeof scanner.storeId !== 'string' ||
                !/^[A-Za-z0-9][A-Za-z0-9_-]{0,47}$/u.test(scanner.id) ||
                !/^sdscan_[a-z0-9_]{1,12}$/u.test(scanner.account) || !/^[1-9]\d{0,8}$/u.test(scanner.storeId) || ids.has(scanner.id.toLowerCase()) || accounts.has(scanner.account)) {
                throw new Error('Invalid scanner credential configuration.');
            }
            result.set(scanner.id, scanner);
            accounts.add(scanner.account);
            ids.add(scanner.id.toLowerCase());
        }
        return result;
    }
    devices();
    let tail = Promise.resolve();
    function path(id) { return join(root, id + '.json'); }
    async function state(id) {
        try {
            const saved = JSON.parse(await readFile(path(id), 'utf8'));
            if (typeof saved.disabled !== 'boolean' || !['active', 'disabled', 'rotating', 'disabling', 'enabling', 'requires-review'].includes(saved.status)) throw new Error('Invalid scanner credential state.');
            return saved;
        } catch (error) { if (error.code === 'ENOENT') return { disabled: false, status: 'active' };throw error; }
    }
    async function save(id, value) {
        await mkdir(root, { recursive: true });
        const staged = path(id) + '.' + randomUUID() + '.tmp';
        try {
            const file = await open(staged, 'wx', 0o600);
            try { await file.writeFile(JSON.stringify({ ...value, updatedAt: new Date().toISOString() }));await file.sync(); }
            finally { await file.close(); }
            await rename(staged, path(id));
        } finally { await unlink(staged).catch(error => { if (error.code !== 'ENOENT') throw error; }); }
    }
    function device(identity, id) {
        const scanner = devices().get(id);
        if (!scanner || !identity.storeIds.includes(scanner.storeId)) throw new Error('Scanner unavailable.');
        return scanner;
    }
    function change(identity, id, action) {
        const scanner = device(identity, id);
        const operation = tail.then(async () => {
            const previous = await state(id);
            const rotate = action === 'rotate';
            const disabled = rotate ? previous.disabled : action === 'disable';
            await save(id, { disabled, status: rotate ? 'rotating' : disabled ? 'disabling' : 'enabling' });
            const password = rotate ? 'Sd!9' + randomBytes(24).toString('base64url') : undefined;
            try {
                await broker({ action: 'scanner-' + action, account: scanner.account,
                    ...(rotate ? { password, disabled } : {}) });
                await save(id, { disabled, status: disabled ? 'disabled' : 'active' });
                return rotate ? { account: scanner.account, password, disabled } : { disabled };
            } catch (error) {
                await save(id, { disabled, status: 'requires-review' });throw error;
            }
        });
        tail = operation.catch(() => {});return operation;
    }
    return {
        async list(identity) {
            return Promise.all([...devices().values()].filter(scanner => identity.storeIds.includes(scanner.storeId)).map(async scanner => {
                const saved = await state(scanner.id);
                return { id: scanner.id, store: scanner.storeId, account: scanner.account,
                    disabled: saved.disabled, credentialState: ['rotating', 'disabling', 'enabling'].includes(saved.status) ? 'requires-review' : saved.status };
            }));
        },
        disable: (identity, id) => change(identity, id, 'disable'),
        enable: (identity, id) => change(identity, id, 'enable'),
        rotate: (identity, id) => change(identity, id, 'rotate')
    };
}
