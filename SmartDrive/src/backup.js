import { createHash, randomUUID } from 'node:crypto';
import { createReadStream } from 'node:fs';
import { copyFile, link, lstat, mkdir, open, readFile, readdir, rename, unlink } from 'node:fs/promises';
import { isAbsolute, join, relative, sep } from 'node:path';
import { storageReserve } from './storage-reserve.js';
import { readFileIdentity, validFileIdentity, writeFileIdentity } from './file-identity.js';

async function digest(path) {
    const hash = createHash('sha256');
    for await (const chunk of createReadStream(path)) hash.update(chunk);
    return hash.digest('hex');
}

async function durableJson(path, value) {
    const staged = path + '.' + randomUUID() + '.tmp';
    try {
        const file = await open(staged, 'wx');
        try { await file.writeFile(JSON.stringify(value)); await file.sync(); }
        finally { await file.close(); }
        await rename(staged, path);
    } finally {
        await unlink(staged).catch(error => { if (error.code !== 'ENOENT') throw error; });
    }
}

function within(root, path) {
    const part = relative(root, path);
    return !isAbsolute(part) && part !== '..' && !part.startsWith('..' + sep);
}

export function backupLibrary({ stateRoot, recoveryRoot, storeRoots, cloud, minimumFreeBytes }) {
    const requireSpace = storageReserve(minimumFreeBytes);
    if (![stateRoot, recoveryRoot, ...Object.values(storeRoots)].every(isAbsolute) ||
        Object.values(storeRoots).some(root => within(root, stateRoot) || within(root, recoveryRoot))) {
        throw new Error('Backup and recovery directories must be outside staff storage.');
    }
    const jobs = join(stateRoot, 'versions');
    const snapshots = join(stateRoot, 'snapshots');
    let running = false;

    async function initialize() {
        await mkdir(jobs, { recursive: true });
        await mkdir(snapshots, { recursive: true });
        await mkdir(recoveryRoot, { recursive: true });
    }
    async function versions() {
        await initialize();
        const result = [];
        for (const name of await readdir(jobs)) {
            if (!/^[a-f0-9-]{36}\.json$/u.test(name)) continue;
            const item = JSON.parse(await readFile(join(jobs, name), 'utf8'));
            if (name !== item.id + '.json' || !/^[a-f0-9]{64}$/u.test(item.sha256) ||
                !Number.isSafeInteger(item.size) || item.size < 0) throw new Error('Invalid backup catalog.');
            result.push(item);
        }
        return result;
    }

    async function snapshot(store, parts) {
        const root = storeRoots[store];
        if (!root || !parts.length || !parts.every(part => typeof part === 'string' &&
            part !== '.' && part !== '..' && !/[\\/\x00-\x1f:]/u.test(part))) {
            throw new Error('Invalid backup source.');
        }
        let source = root;
        for (const part of parts) {
            source = join(source, part);
            const info = await lstat(source);
            if (info.isSymbolicLink() || info.nlink > 1 && info.isFile()) throw new Error('Linked backup source.');
        }
        const before = await lstat(source);
        const fileIdentity = await readFileIdentity(source);
        if (!before.isFile()) throw new Error('Choose a file to back up.');
        const id = randomUUID();
        const staged = join(snapshots, id + '.tmp');
        const path = join(snapshots, id);
        try {
            await requireSpace(snapshots, before.size);
            await copyFile(source, staged);
            const hash = await digest(staged);
            const after = await lstat(source);
            if (JSON.stringify(fileIdentity) !== JSON.stringify(await readFileIdentity(source)) ||
                before.size !== after.size || before.mtimeMs !== after.mtimeMs ||
                before.ino !== after.ino || after.isSymbolicLink() || after.nlink !== 1 ||
                hash !== await digest(source)) throw new Error('Source changed during backup.');
            const catalog = await versions();
            const previous = catalog.filter(item => item.store === store &&
                JSON.stringify(item.parts) === JSON.stringify(parts)).sort((a, b) =>
                (b.sequence ?? 0) - (a.sequence ?? 0) || b.createdAt.localeCompare(a.createdAt))[0];
            if (previous?.sha256 === hash && JSON.stringify(previous.fileIdentity ?? null) === JSON.stringify(fileIdentity)) return;
            const handle = await open(staged, 'r+');
            try { await handle.sync(); } finally { await handle.close(); }
            await rename(staged, path);
            const record = { id, store, parts, size: before.size, sha256: hash,
                sequence: catalog.reduce((value, item) => Math.max(value, item.sequence ?? 0), 0) + 1,
                createdAt: new Date().toISOString(), status: 'pending', attempts: 0 };
            if (fileIdentity) record.fileIdentity = fileIdentity;
            await durableJson(join(jobs, id + '.json'), record);
            return record;
        } finally {
            await unlink(staged).catch(error => { if (error.code !== 'ENOENT') throw error; });
        }
    }

    return {
        versions,
        async recoverCatalog() {
            if (running) throw new Error('Wait for active backup work before rebuilding the catalog.');
            if (typeof cloud.recoveryManifests !== 'function') throw new Error('Cloud catalog recovery is unavailable.');
            running = true;
            let recovered = 0;
            let retained = 0;
            try {
                await initialize();
                const existing = new Map((await versions()).map(record => [record.id, record]));
                const seen = new Set();
                for await (const candidate of cloud.recoveryManifests()) {
                    if (!candidate || typeof candidate.id !== 'string' || typeof candidate.store !== 'string' ||
                        !/^[a-f0-9-]{36}$/u.test(candidate.id) || seen.has(candidate.id) ||
                        !Object.hasOwn(storeRoots, candidate.store) || !Array.isArray(candidate.parts) ||
                        !candidate.parts.length || candidate.parts.length > 128 || !candidate.parts.every(part =>
                            typeof part === 'string' && part.length && part !== '.' && part !== '..' &&
                            !/[\\/:*?"<>|\x00-\x1f]/u.test(part)) ||
                        !/^[a-f0-9]{64}$/u.test(candidate.sha256) || !Number.isSafeInteger(candidate.size) || candidate.size < 0 ||
                        !Number.isSafeInteger(candidate.sequence) || candidate.sequence < 1 ||
                        typeof candidate.createdAt !== 'string' || !Number.isFinite(Date.parse(candidate.createdAt)) ||
                        candidate.fileIdentity && !validFileIdentity(candidate.fileIdentity)) {
                        throw new Error('Invalid cloud recovery manifest or unconfigured store.');
                    }
                    seen.add(candidate.id);
                    const previous = existing.get(candidate.id);
                    if (previous) {
                        if (previous.sha256 !== candidate.sha256 || previous.size !== candidate.size ||
                            previous.store !== candidate.store || JSON.stringify(previous.parts) !== JSON.stringify(candidate.parts) ||
                            JSON.stringify(previous.fileIdentity ?? null) !== JSON.stringify(candidate.fileIdentity ?? null)) {
                            throw new Error('Cloud recovery conflicts with the local version catalog.');
                        }
                        if (previous.status === 'verified') { retained++; continue; }
                    }
                    const check = join(snapshots, candidate.id + '.recovery-' + randomUUID());
                    try {
                        await requireSpace(snapshots, candidate.size);
                        await cloud.download(candidate.id, check);
                        const info = await lstat(check);
                        if (!info.isFile() || info.isSymbolicLink() || info.nlink !== 1 ||
                            info.size !== candidate.size || await digest(check) !== candidate.sha256) {
                            throw new Error('Cloud recovery version failed checksum verification.');
                        }
                        const record = { id: candidate.id, store: candidate.store, parts: candidate.parts,
                            size: candidate.size, sha256: candidate.sha256, sequence: candidate.sequence,
                            createdAt: candidate.createdAt, status: 'verified', attempts: 0,
                            verifiedAt: new Date().toISOString(), catalogRecoveredAt: new Date().toISOString() };
                        if (candidate.fileIdentity) record.fileIdentity = candidate.fileIdentity;
                        await durableJson(join(jobs, record.id + '.json'), record);
                        recovered++;
                    } finally {
                        await unlink(check).catch(error => { if (error.code !== 'ENOENT') throw error; });
                    }
                }
                return { recovered, retained };
            } finally { running = false; }
        },
        async retry(storeIds) {
            await this.uploadPending(storeIds);
        },
        async captureFolder(store, parts) {
            await initialize();
            if (!Object.hasOwn(storeRoots, store) || !Array.isArray(parts) || !parts.every(part =>
                typeof part === 'string' && part.length && part !== '.' && part !== '..' &&
                !/[\\/\x00-\x1f:]/u.test(part))) throw new Error('Invalid backup folder.');
            let failed = 0;
            const failures = [];
            async function walk(path, names) {
                const info = await lstat(path);
                if (info.isSymbolicLink()) throw new Error('Linked backup folder.');
                if (info.isFile()) {
                    if (info.nlink !== 1) throw new Error('Linked backup file.');
                    await snapshot(store, names);
                    return;
                }
                if (!info.isDirectory()) return;
                for (const entry of await readdir(path, { withFileTypes: true })) {
                    if (entry.name.startsWith('.') || entry.name.startsWith('~$')) continue;
                    try { await walk(join(path, entry.name), [...names, entry.name]); }
                    catch (error) {
                        failed++;
                        if (failures.length < 20) failures.push(error);
                    }
                }
            }
            let parent = storeRoots[store];
            for (const part of parts) {
                parent = join(parent, part);
                if ((await lstat(parent)).isSymbolicLink()) throw new Error('Linked backup folder.');
            }
            await walk(parent, parts);
            if (failed) throw new AggregateError(failures, failed + ' backup entries could not be captured; other files were queued.');
        },
        async capture(store, parts) {
            await initialize();
            return snapshot(store, parts);
        },
        async uploadPending(retryStores = null) {
            if (running) return;
            running = true;
            try {
                for (const record of await versions()) {
                    if (retryStores && !retryStores.includes(record.store)) continue;
                    if (record.status === 'verified') {
                        // The durable manifest and verified remote version are the
                        // recovery authority; local upload snapshots are disposable.
                        await unlink(join(snapshots, record.id)).catch(error => { if (error.code !== 'ENOENT') throw error; });
                        continue;
                    }
                    if (!retryStores && Date.parse(record.retryAt ?? '') > Date.now()) continue;
                    record.attempts++;
                    const source = join(snapshots, record.id);
                    const check = join(snapshots, record.id + '.check');
                    try {
                        if (await digest(source) !== record.sha256) throw new Error('Snapshot checksum changed.');
                        await requireSpace(snapshots, record.size);
                        await cloud.upload(record.id, source, record);
                        await cloud.download(record.id, check);
                        const info = await lstat(check);
                        if (info.size !== record.size || await digest(check) !== record.sha256) {
                            throw new Error('Backup verification failed.');
                        }
                        record.status = 'verified';
                        record.verifiedAt = new Date().toISOString();
                        delete record.retryAt;
                    } catch {
                        record.status = 'pending';
                        record.retryAt = new Date(Date.now() + Math.min(3600000, 1000 * 2 ** Math.min(record.attempts, 12))).toISOString();
                    } finally {
                        await unlink(check).catch(error => { if (error.code !== 'ENOENT') throw error; });
                        await durableJson(join(jobs, record.id + '.json'), record);
                        if (record.status === 'verified') {
                            await unlink(source).catch(error => { if (error.code !== 'ENOENT') throw error; });
                        }
                    }
                }
            } finally { running = false; }
        },
        async restore(id, storeIds) {
            const record = (await versions()).find(item => item.id === id && storeIds.includes(item.store));
            if (!record || record.status !== 'verified') throw new Error('Verified backup is unavailable.');
            const staged = join(recoveryRoot, '.' + randomUUID() + '.tmp');
            const destination = join(recoveryRoot, record.id + '-' + randomUUID());
            try {
                await requireSpace(recoveryRoot, record.size);
                await cloud.download(id, staged);
                const info = await lstat(staged);
                if (info.size !== record.size || await digest(staged) !== record.sha256) {
                    throw new Error('Restored backup checksum does not match.');
                }
                if (record.fileIdentity) await writeFileIdentity(staged, record.fileIdentity);
                await link(staged, destination);
                await durableJson(destination + '.json', { ...record, recoveredAt: new Date().toISOString() });
                return { path: destination, originalPath: record.parts.join('/'), sha256: record.sha256 };
            } finally {
                await unlink(staged).catch(error => { if (error.code !== 'ENOENT') throw error; });
            }
        }
    };
}
