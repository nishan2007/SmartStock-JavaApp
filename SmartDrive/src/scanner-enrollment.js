import { randomBytes, randomUUID } from 'node:crypto';
import { mkdir, open, readFile, rename, unlink } from 'node:fs/promises';
import { isAbsolute, join } from 'node:path';
import { scannerMetadata, scannerSetup } from './scanner-setup.js';

// This registry is private service state, separate from the privileged broker's
// account ownership journal. Neither registry stores copier passwords.
export async function scannerEnrollment({ root, dataRoot, scanners, storeRoots, connection, broker }) {
    if (!isAbsolute(root) || !isAbsolute(dataRoot)) throw new Error('Private scanner enrollment paths are required.');
    const path = join(root, 'enrolled-scanners.json');
    let records = [];
    function scanner(input) {
        if (!input || typeof input.id !== 'string' || !/^[A-Za-z0-9][A-Za-z0-9_-]{0,47}$/u.test(input.id) ||
            /^(CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])$/iu.test(input.id) ||
            typeof input.storeId !== 'string' || !Object.hasOwn(storeRoots, input.storeId) ||
            typeof input.account !== 'string' || !/^sdscan_[a-z0-9_]{1,12}$/u.test(input.account)) throw new Error('Invalid scanner enrollment.');
        const value = { id: input.id, storeId: input.storeId, account: input.account, ...scannerMetadata(input),
            intake: join(dataRoot, 'Private', 'Intake', input.id) };
        scannerSetup(value, connection);
        return value;
    }
    try {
        const bytes = await readFile(path);
        if (bytes.length > 1024 * 1024) throw new Error('Scanner enrollment registry is too large.');
        const saved = JSON.parse(bytes);
        if (saved.version !== 1 || !Array.isArray(saved.records) || saved.records.length > 1000) throw new Error('Invalid scanner enrollment registry.');
        const ids = new Set(scanners.map(item => item.id.toLowerCase()));
        const accounts = new Set(scanners.map(item => item.account));
        records = saved.records.map(record => {
            if (!['provisioning', 'active', 'requires-review'].includes(record.phase)) throw new Error('Invalid scanner enrollment phase.');
            const value = scanner(record);
            if (ids.has(value.id.toLowerCase()) || accounts.has(value.account)) throw new Error('Duplicate scanner enrollment.');
            ids.add(value.id.toLowerCase());accounts.add(value.account);
            return { ...value, phase: record.phase === 'active' ? 'active' : 'requires-review' };
        });
        scanners.push(...records.filter(record => record.phase === 'active').map(scanner));
    } catch (error) { if (error.code !== 'ENOENT') throw error; }
    let tail = Promise.resolve();
    async function save() {
        await mkdir(root, { recursive: true });
        const staged = path + '.' + randomUUID() + '.tmp';
        try {
            const file = await open(staged, 'wx', 0o600);
            try { await file.writeFile(JSON.stringify({ version: 1, records }));await file.sync(); }
            finally { await file.close(); }
            await rename(staged, path);
        } finally { await unlink(staged).catch(error => { if (error.code !== 'ENOENT') throw error; }); }
    }
    return {
        pending(identity) {
            return records.filter(record => record.phase !== 'active' && identity.storeIds.includes(record.storeId))
                .map(record => ({ id: record.id, store: record.storeId, account: record.account, credentialState: 'requires-review', enrollmentPending: true }));
        },
        enroll(identity, input) {
            const operation = tail.then(async () => {
                if (!input || typeof input !== 'object' || Array.isArray(input) || Object.keys(input).some(key =>
                    !['id', 'storeId', 'displayName', 'model', 'reservedAddress'].includes(key))) throw new Error('Invalid scanner enrollment details.');
                const value = scanner({ ...input, account: 'sdscan_' + randomBytes(6).toString('hex') });
                if (!identity.storeIds.includes(value.storeId)) throw new Error('Scanner store unavailable.');
                if (records.length >= 1000 || [...scanners, ...records].some(item => item.id.toLowerCase() === value.id.toLowerCase() || item.account === value.account)) {
                    throw new Error('Scanner ID already exists or enrollment registry is full.');
                }
                const record = { ...value, phase: 'provisioning' };
                records.push(record);
                await save();
                const password = 'Sd!9' + randomBytes(24).toString('base64url');
                try {
                    await broker({ action: 'scanner-create', id: value.id, store: value.storeId, account: value.account, password });
                    record.phase = 'active';
                    await save();
                    scanners.push(value);
                    return { account: value.account, password, setup: scannerSetup(value, connection) };
                } catch (error) {
                    // A timeout can occur after Windows has created the account.
                    // Close that access when possible; never retry account creation.
                    await broker({ action: 'scanner-disable', account: value.account }).catch(() => {});
                    record.phase = 'requires-review';
                    await save();
                    throw error;
                }
            });
            tail = operation.catch(() => {});
            return operation;
        }
    };
}
