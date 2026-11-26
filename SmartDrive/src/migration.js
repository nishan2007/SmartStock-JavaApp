import { createHash } from 'node:crypto';
import { constants, createReadStream } from 'node:fs';
import { copyFile, lstat, mkdir, readFile, readdir, writeFile } from 'node:fs/promises';
import { isAbsolute, join, relative, sep } from 'node:path';

async function hash(path) {
    const value = createHash('sha256');
    for await (const chunk of createReadStream(path)) value.update(chunk);
    return value.digest('hex');
}
async function directory(path) {
    const info = await lstat(path);
    if (!info.isDirectory() || info.isSymbolicLink()) throw new Error('Invalid library directory.');
}
const categories = [
    { folder: '', kind: 'BADGE', extension: '.sstbadge' },
    { folder: 'business_cards', kind: 'BUSINESS_CARDS', extension: '.sspub' },
    { folder: 'labels', kind: 'LABELS', extension: '.sspub' },
    { folder: 'bill_books', kind: 'BILL_BOOKS', extension: '.sspub' }
];

export async function exportStudioLibrary({ source, destination, dryRun = false }) {
    if (!isAbsolute(source) || !isAbsolute(destination)) throw new Error('Use absolute library and export paths.');
    const delta = relative(source, destination);
    if (!delta || (!delta.startsWith('..' + sep) && delta !== '..' && !isAbsolute(delta))) {
        throw new Error('Export outside the legacy library.');
    }
    await directory(source);
    if (!dryRun) await mkdir(destination);
    const records = [];
    const identities = new Set();
    for (const category of categories) {
        const root = join(source, category.folder);
        try { await directory(root); }
        catch (error) { if (error.code === 'ENOENT' && category.folder) continue; throw error; }
        for (const entry of await readdir(root, { withFileTypes: true })) {
            const match = /^store-([1-9]\d*)(?:-.*)?$/u.exec(entry.name);
            if (!match || !entry.isDirectory() || entry.isSymbolicLink()) continue;
            const store = match[1];
            const folder = join(root, entry.name);
            await directory(folder);
            for (const head of await readdir(folder)) {
                const id = /^([a-f0-9-]{36})\.head\.json$/iu.exec(head)?.[1];
                if (!id) continue;
                const identity = category.kind + '/' + store + '/' + id;
                if (identities.has(identity)) throw new Error('Duplicate legacy template identity.');
                identities.add(identity);
                const headPath = join(folder, head);
                const headInfo = await lstat(headPath);
                if (!headInfo.isFile() || headInfo.isSymbolicLink()) throw new Error('Invalid template metadata file.');
                const metadata = await readFile(headPath, 'utf8');
                const row = JSON.parse(metadata);
                if (row.id !== id || !/^[a-f0-9]{64}$/u.test(row.sha256) ||
                    (row.ownerStoreId !== undefined && (!Number.isSafeInteger(row.ownerStoreId) || row.ownerStoreId < 1)) ||
                    (row.sharedStoreIds !== undefined && (!Array.isArray(row.sharedStoreIds) ||
                        !row.sharedStoreIds.every(value => Number.isSafeInteger(value) && value > 0)))) {
                    throw new Error('Invalid template identity or store assignments.');
                }
                const revision = join(folder, row.sha256 + category.extension);
                const info = await lstat(revision);
                if (!info.isFile() || info.isSymbolicLink() || info.size !== row.size || await hash(revision) !== row.sha256) {
                    throw new Error('Legacy template checksum or size mismatch.');
                }
                const path = [category.kind, store, id + category.extension];
                if (!dryRun) {
                    const targetFolder = join(destination, category.kind, store);
                    await mkdir(targetFolder, { recursive: true });
                    const target = join(destination, ...path);
                    await copyFile(revision, target, constants.COPYFILE_EXCL);
                    if (await hash(target) !== row.sha256) throw new Error('Exported template checksum mismatch.');
                }
                if (metadata !== await readFile(headPath, 'utf8')) {
                    throw new Error('The legacy library changed during export. Freeze writes and rerun.');
                }
                records.push({ kind: category.kind, storageStoreId: store, path: path.join('/'), metadata: row });
            }
        }
    }
    const result = { formatVersion: 1, exportedAt: new Date().toISOString(), records };
    if (!dryRun) {
        // Written last. An export without its manifest is incomplete and cannot be cut over.
        await writeFile(join(destination, 'manifest.json'), JSON.stringify(result, null, 2), { flag: 'wx' });
    }
    return result;
}
