import { createHash } from 'node:crypto';
import { constants, createReadStream } from 'node:fs';
import { copyFile, lstat, mkdir, readFile, writeFile } from 'node:fs/promises';
import { isAbsolute, join, relative, sep } from 'node:path';

async function digest(path) {
    const hash = createHash('sha256');
    for await (const bytes of createReadStream(path)) hash.update(bytes);
    return hash.digest('hex');
}
const formats = { BADGE: '.sstbadge', BUSINESS_CARDS: '.sspub', LABELS: '.sspub', BILL_BOOKS: '.sspub' };
const storeId = id => /^[1-9]\d{0,8}$/u.test(String(id));

export async function importStudioExport({ source, destination, configuredStores }) {
    if (!isAbsolute(source) || !isAbsolute(destination) || source === destination ||
        !Array.isArray(configuredStores) || !configuredStores.every(storeId)) throw new Error('Invalid import configuration.');
    const delta = relative(source, destination);
    const reverse = relative(destination, source);
    const nested = value => !value || !isAbsolute(value) && value !== '..' && !value.startsWith('..' + sep);
    if (nested(delta) || nested(reverse)) throw new Error('Use separate export and import directories.');
    const sourceInfo = await lstat(source);
    if (!sourceInfo.isDirectory() || sourceInfo.isSymbolicLink()) throw new Error('Invalid export directory.');
    const manifest = JSON.parse(await readFile(join(source, 'manifest.json'), 'utf8'));
    if (manifest.formatVersion !== 1 || !Array.isArray(manifest.records)) throw new Error('Invalid export manifest.');
    const planned = [];
    const identities = new Set();
    const sharedFolders = new Map();
    for (const record of manifest.records) {
        const metadata = record.metadata;
        const extension = formats[record.kind];
        if (!extension || !metadata || !/^[a-f0-9-]{36}$/u.test(metadata.id) || !storeId(record.storageStoreId) ||
            !/^[a-f0-9]{64}$/u.test(metadata.sha256) || !Number.isSafeInteger(metadata.size) || metadata.size < 0) {
            throw new Error('Invalid exported template.');
        }
        const identity = record.kind + '/' + record.storageStoreId + '/' + metadata.id;
        if (identities.has(identity)) throw new Error('Duplicate imported identity.');
        identities.add(identity);
        const expected = identity + extension;
        if (record.path !== expected) throw new Error('Invalid exported template path.');
        const owner = String(metadata.ownerStoreId ?? record.storageStoreId);
        const stores = [...new Set([owner, ...(metadata.sharedStoreIds ?? []).map(String)])].sort();
        if (!stores.every(id => storeId(id) && configuredStores.includes(id))) {
            throw new Error('Every owner and shared store must be configured before import.');
        }
        let revision = source;
        for (const part of record.path.split('/')) {
            revision = join(revision, part);
            if ((await lstat(revision)).isSymbolicLink()) throw new Error('Linked export path.');
        }
        const info = await lstat(revision);
        if (!info.isFile() || info.size !== metadata.size || await digest(revision) !== metadata.sha256) {
            throw new Error('Export checksum or size mismatch.');
        }
        const group = 'Access-' + stores.join('-');
        const folder = ['Studio Jobs', record.kind, group];
        const clean = String(metadata.name ?? 'Design').replace(/[<>:"/\\|?*\x00-\x1f]/gu, '_')
            .replace(/[ .]+$/u, '').slice(0, 70) || 'Design';
        const parts = [...folder, clean + '-' + metadata.id + extension];
        sharedFolders.set(owner + '/' + folder.join('/'), { store: owner, parts: folder, storeIds: stores });
        planned.push({ legacy: { kind: record.kind, storageStoreId: String(record.storageStoreId), id: metadata.id },
            store: owner, parts, sha256: metadata.sha256, metadata, revision });
    }
    await mkdir(destination);
    const records = [];
    for (const item of planned) {
        const parent = join(destination, 'Stores', item.store, ...item.parts.slice(0, -1));
        await mkdir(parent, { recursive: true });
        const target = join(parent, item.parts.at(-1));
        await copyFile(item.revision, target, constants.COPYFILE_EXCL);
        if (await digest(target) !== item.sha256) throw new Error('Imported template checksum mismatch.');
        const { revision, ...record } = item;
        records.push(record);
    }
    const catalog = { formatVersion: 1, importedAt: new Date().toISOString(), records,
        sharedFolders: [...sharedFolders.values()] };
    await writeFile(join(destination, 'catalog.json'), JSON.stringify(catalog, null, 2), { flag: 'wx' });
    return catalog;
}
