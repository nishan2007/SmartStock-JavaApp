import { createHash } from 'node:crypto';
import { createReadStream } from 'node:fs';
import { lstat, mkdir, open } from 'node:fs/promises';
import { dirname, isAbsolute, join, relative, sep } from 'node:path';

const inside = (root, path) => {
    const part = relative(root, path);
    return !part || !isAbsolute(part) && part !== '..' && !part.startsWith('..' + sep);
};
async function plain(path, directory) {
    const info = await lstat(path);
    if (info.isSymbolicLink() || (directory ? !info.isDirectory() : !info.isFile() || info.nlink !== 1)) throw new Error('Linked or invalid preview preparation path.');
    for (let parent = dirname(path); ; parent = dirname(parent)) {
        const item = await lstat(parent);
        if (!item.isDirectory() || item.isSymbolicLink()) throw new Error('Linked preview preparation ancestor.');
        if (dirname(parent) === parent) break;
    }
}
async function hash(path) {
    const digest = createHash('sha256');
    for await (const bytes of createReadStream(path)) digest.update(bytes);
    return digest.digest('hex');
}

export async function prepareLegacyPreviews({ storeRoots, catalog, recoveryRoot, maintenanceWindow, prepare }) {
    if (maintenanceWindow !== true) throw new Error('Freeze employee file access and stop SmartDrive before preparing previews.');
    if (!isAbsolute(recoveryRoot) || Object.values(storeRoots).some(root => !isAbsolute(root) || inside(root, recoveryRoot) || inside(recoveryRoot, root)) ||
        catalog?.formatVersion !== 1 || !Array.isArray(catalog.records) || catalog.records.length > 20000) throw new Error('Use a valid migration catalog and separate private recovery folder.');
    const plans = [], seen = new Set();
    for (const record of catalog.records) {
        const root = storeRoots[record.store], parts = record.parts, legacy = record.legacy;
        if (!root || !Array.isArray(parts) || parts.length < 4 || parts[0] !== 'Studio Jobs' ||
            !legacy || parts[1] !== legacy.kind || !['BADGE', 'BUSINESS_CARDS', 'LABELS', 'BILL_BOOKS'].includes(legacy.kind) ||
            !/^[a-f0-9-]{36}$/u.test(legacy.id) || !/^[1-9]\d{0,8}$/u.test(legacy.storageStoreId) ||
            !parts.every(part => typeof part === 'string' && part && !/[<>:"/\\|?*\x00-\x1f]/u.test(part) && !/[ .]$/u.test(part) && part !== '..') ||
            !/^[a-f0-9]{64}$/u.test(record.sha256)) throw new Error('Invalid migrated preview record.');
        const extension = legacy.kind === 'BADGE' ? '.sstbadge' : '.sspub';
        if (!parts.at(-1).endsWith('-' + legacy.id + extension)) throw new Error('Preview target does not match its migrated identity.');
        let path = root;await plain(path, true);
        for (let index = 0; index < parts.length; index++) { path = join(path, parts[index]);await plain(path, index < parts.length - 1); }
        const key = path.toLowerCase();if (seen.has(key)) throw new Error('Duplicate preview target.');seen.add(key);
        plans.push({ path, expected: record.sha256 });
    }
    // Exclusive directory creation makes every run's originals and journal independent.
    await plain(dirname(recoveryRoot), true);
    await mkdir(recoveryRoot);
    const journal = await open(join(recoveryRoot, 'preparation.jsonl'), 'wx', 0o600);
    let failed = 0, prepared = 0, existing = 0;
    async function record(value) { await journal.writeFile(JSON.stringify({ at: new Date().toISOString(), ...value }) + '\n');await journal.sync(); }
    try {
        for (const plan of plans) {
            await record({ file: plan.path, status: 'started', expectedSha256: plan.expected });
            try {
                const result = await prepare(plan.path, recoveryRoot, plan.expected);
                if (!['prepared', 'already-prepared'].includes(result.status) || !/^[a-f0-9]{64}$/u.test(result.afterSha256) ||
                    await hash(plan.path) !== result.afterSha256) throw new Error('Prepared file verification failed.');
                if (result.status === 'prepared') {
                    if (result.beforeSha256 !== plan.expected || typeof result.backup !== 'string' || !inside(recoveryRoot, result.backup) || result.backup === recoveryRoot) throw new Error('Invalid recovery copy result.');
                    await plain(result.backup, false);
                    if (await hash(result.backup) !== plan.expected) throw new Error('Recovery checksum verification failed.');
                    prepared++;
                } else existing++;
                await record({ file: plan.path, ...result });
            } catch (error) { failed++;await record({ file: plan.path, status: 'failed', error: error.message }); }
        }
    } finally { await journal.close(); }
    if (failed) throw new Error(`${failed} preview preparation(s) failed. Keep writes frozen and review the private journal and recovery copies.`);
    return { prepared, existing, recoveryRoot };
}
