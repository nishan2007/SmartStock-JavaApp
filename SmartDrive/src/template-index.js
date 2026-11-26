import { lstat, open, readdir } from 'node:fs/promises';
import { extname } from 'node:path';
import { studioMetadata } from './studio-preview.js';
import { readFileIdentity } from './file-identity.js';

const kinds = new Set(['BADGE', 'BUSINESS_CARDS', 'LABELS', 'BILL_BOOKS']);
const keyOf = value => JSON.stringify([value.kind, value.storageStoreId, value.id]);
const pathKey = (store, parts) => JSON.stringify([store, ...parts]);
const validIdentity = value => value && typeof value.id === 'string' &&
    /^[a-f0-9]{8}(?:-[a-f0-9]{4}){3}-[a-f0-9]{12}$/u.test(value.id) &&
    typeof value.storageStoreId === 'string' && /^[1-9]\d{0,8}$/u.test(value.storageStoreId) && kinds.has(value.kind);

export function templateIndex({ roots, legacy, accessible, locate, safeName }) {
    const legacyPaths = new Map([...legacy.values()].map(record => [pathKey(record.store, record.parts), record]));
    let scans = 0;
    return async (identity, kind) => {
        if (!kinds.has(kind)) throw new Error('Invalid template category.');
        if (scans >= 2) throw new Error('SmartDrive template indexing is busy.');
        scans++;
        try {
        const found = new Map();
        let visited = 0;
        let bytesRead = 0;
        const deadline = Date.now() + 30000;
        function budget() {
            if (++visited > 20000 || bytesRead > 128 * 1024 * 1024 || Date.now() > deadline) {
                throw new Error('SmartDrive template indexing limit reached.');
            }
        }
        async function walk(store, parts) {
            if (!accessible(identity, store, parts)) return;
            budget();
            let path;
            let info;
            try {
                path = await locate(identity, [store, ...parts]);
                info = await lstat(path);
            } catch (error) {
                if (['ENOENT', 'ENOTDIR'].includes(error.code)) return;
                throw error;
            }
            if (info.isDirectory()) {
                for (const entry of await readdir(path, { withFileTypes: true })) {
                    if (!safeName(entry.name) || entry.name.startsWith('.') || entry.name.startsWith('~$') || entry.isSymbolicLink()) continue;
                    await walk(store, [...parts, entry.name]);
                }
                return;
            }
            const extension = extname(path).toLowerCase();
            if (!info.isFile() || info.nlink !== 1 || extension !== (kind === 'BADGE' ? '.sstbadge' : '.sspub')) return;
            const original = legacyPaths.get(pathKey(store, parts));
            let metadata;
            let fileIdentity;
            const handle = await open(path, 'r');
            try {
                const opened = await handle.stat();
                if (opened.ino !== info.ino || opened.dev !== info.dev || opened.nlink !== 1) return;
                try {
                    metadata = await studioMetadata({ read: async (...args) => {
                        bytesRead += args[2];
                        if (bytesRead > 128 * 1024 * 1024) throw new Error('Metadata read budget exceeded.');
                        return handle.read(...args);
                    } }, extension, opened.size);
                } catch {
                    // Malformed files still appear in ordinary browsing. A migrated
                    // path can retain its original identity until its first resave.
                    if (!original) return;
                }
                try { fileIdentity = await readFileIdentity(path); } catch { return; }
                const after = await handle.stat();
                const current = await lstat(path);
                if (after.size !== opened.size || after.mtimeMs !== opened.mtimeMs || current.ino !== opened.ino ||
                    current.dev !== opened.dev || current.nlink !== 1 || await locate(identity, [store, ...parts]) !== path) return;
                info = after;
            } finally { await handle.close(); }
            budget();
            const reference = fileIdentity ?? metadata?.smartDrive ?? original?.legacy;
            if (!validIdentity(reference) || reference.kind !== kind) return;
            const key = keyOf(reference);
            const binding = legacy.get(key);
            if (binding ? binding.store !== store : reference.storageStoreId !== store) return;
            const name = metadata?.name ?? metadata?.title ?? original?.metadata?.name ?? parts.at(-1);
            if (typeof name !== 'string' || !name.trim() || name.length > 512) return;
            const record = { store, parts, legacy: { ...reference }, name, size: info.size };
            // Never let a copied file silently take over another design's links.
            if (found.has(key)) found.set(key, null); else found.set(key, record);
        }
        for (const store of roots.keys()) await walk(store, ['Studio Jobs']);
        return found;
        } finally { scans--; }
    };
}
