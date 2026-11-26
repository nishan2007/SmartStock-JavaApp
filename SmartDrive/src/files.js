import { createHash, createHmac, timingSafeEqual } from 'node:crypto';
import { lstat, open, readdir, realpath } from 'node:fs/promises';
import { basename, extname, isAbsolute, join, relative, sep } from 'node:path';
import { studioPreview } from './studio-preview.js';
import { templateIndex } from './template-index.js';

const types = new Map([
    ['.pdf', 'application/pdf'],
    ['.png', 'image/png'],
    ['.jpg', 'image/jpeg'],
    ['.jpeg', 'image/jpeg'],
    ['.gif', 'image/gif'],
    ['.webp', 'image/webp'],
    ['.sspub', 'image/png'],
    ['.sstbadge', 'image/png']
]);
const safeName = name => typeof name === 'string' && name.length > 0 &&
    !/[\\/:*?"<>|\x00-\x1f]/u.test(name) && !/[ .]$/u.test(name) &&
    !/^(?:con|prn|aux|nul|com[1-9]|lpt[1-9])(?:\.|$)/iu.test(name);

export class FileUnavailable extends Error {}

export function fileLibrary({ storeRoots, fileIdKey, officePreview, sharedFolders = [], migrationRecords = [] }) {
    if (!Buffer.isBuffer(fileIdKey) || fileIdKey.length < 32) {
        throw new Error('fileIdKey must contain at least 32 bytes.');
    }
    const roots = new Map(Object.entries(storeRoots));
    for (const [id, root] of roots) {
        if (!/^[1-9]\d*$/u.test(id) || typeof root !== 'string' || !isAbsolute(root)) {
            throw new Error('Store roots must map numeric store IDs to absolute directories.');
        }
    }
    for (const folder of sharedFolders) {
        if (!roots.has(folder.store) || !Array.isArray(folder.parts) || !folder.parts.length ||
            !folder.parts.every(safeName) || !Array.isArray(folder.storeIds) ||
            !folder.storeIds.length || !folder.storeIds.every(id => roots.has(id))) {
            throw new Error('Invalid shared library folder assignment.');
        }
    }
    const legacy = new Map();
    for (const record of migrationRecords) {
        const key = record.legacy;
        if (!key || !['BADGE', 'BUSINESS_CARDS', 'LABELS', 'BILL_BOOKS'].includes(key.kind) ||
            !/^[1-9]\d*$/u.test(key.storageStoreId) || !/^[a-f0-9-]{36}$/u.test(key.id) ||
            !roots.has(record.store) || !Array.isArray(record.parts) || !record.parts.length ||
            !record.parts.every(safeName)) throw new Error('Invalid migrated template identity.');
        const identity = JSON.stringify([key.kind, key.storageStoreId, key.id]);
        if (legacy.has(identity)) throw new Error('Duplicate migrated template identity.');
        legacy.set(identity, record);
    }
    function accessible(identity, store, parts) {
        if (identity.storeIds.includes(store)) return true;
        return sharedFolders.some(folder => folder.store === store &&
            folder.storeIds.some(id => identity.storeIds.includes(id)) &&
            parts.slice(0, Math.min(parts.length, folder.parts.length))
                .every((part, index) => part === folder.parts[index]));
    }

    function sign(payload) {
        return createHmac('sha256', fileIdKey).update(payload).digest();
    }

    function encode(store, parts) {
        const payload = Buffer.from(JSON.stringify([store, ...parts])).toString('base64url');
        return payload + '.' + sign(payload).toString('base64url');
    }

    function decode(id) {
        if (typeof id !== 'string' || id.length > 8192 || !/^[\w-]+\.[\w-]{43}$/u.test(id)) {
            throw new FileUnavailable('File unavailable.');
        }
        const [payload, signature] = id.split('.');
        if (!timingSafeEqual(sign(payload), Buffer.from(signature, 'base64url'))) {
            throw new FileUnavailable('File unavailable.');
        }
        return JSON.parse(Buffer.from(payload, 'base64url'));
    }

    async function locate(identity, [store, ...parts]) {
        const root = roots.get(store);
        if (!root || !parts.every(safeName) || !accessible(identity, store, parts)) {
            throw new FileUnavailable('File unavailable.');
        }
        const realRoot = await realpath(root);
        let path = realRoot;
        for (const part of parts) {
            path = join(path, part);
            const info = await lstat(path);
            if (info.isSymbolicLink()) throw new FileUnavailable('File unavailable.');
        }
        const resolved = await realpath(path);
        const within = relative(realRoot, resolved);
        if (within === '..' || within.startsWith('..' + sep) || isAbsolute(within)) {
            throw new FileUnavailable('File unavailable.');
        }
        return resolved;
    }

    const indexTemplates = templateIndex({ roots, legacy, accessible, locate, safeName });
    async function templateHead(identity, storageStoreId, templateId) {
        const record = (await indexTemplates(identity, 'BADGE')).get(JSON.stringify(['BADGE', storageStoreId, templateId]));
        if (!record) throw new FileUnavailable('File unavailable.');
        const path = await locate(identity, [record.store, ...record.parts]);
        const expected = await lstat(path);
        if (!expected.isFile() || expected.nlink !== 1 || expected.size > 8 * 1024 * 1024) throw new FileUnavailable('File unavailable.');
        const handle = await open(path, 'r');
        try {
            const before = await handle.stat();
            if (before.ino !== expected.ino || before.dev !== expected.dev || before.nlink !== 1 || before.size !== expected.size) throw new FileUnavailable('File unavailable.');
            const bytes = Buffer.alloc(before.size);
            let offset = 0;
            while (offset < bytes.length) {
                const read = await handle.read(bytes, offset, bytes.length - offset, offset);
                if (!read.bytesRead) throw new FileUnavailable('File changed while reading.');
                offset += read.bytesRead;
            }
            const after = await handle.stat();
            const current = await lstat(path);
            if (before.size !== after.size || before.mtimeMs !== after.mtimeMs || before.ctimeMs !== after.ctimeMs ||
                current.ino !== before.ino || current.dev !== before.dev || current.nlink !== 1 ||
                await locate(identity, [record.store, ...record.parts]) !== path) throw new FileUnavailable('File changed while reading.');
            const sharedStoreIds = [...new Set(sharedFolders.filter(folder => folder.store === record.store &&
                folder.parts.every((part, index) => record.parts[index] === part)).flatMap(folder => folder.storeIds))]
                .filter(store => store !== record.store).map(Number);
            return { id: templateId, storageStoreId: Number(storageStoreId), ownerStoreId: Number(record.store),
                sharedStoreIds, name: record.name, sha256: createHash('sha256').update(bytes).digest('hex'),
                content: bytes.toString('utf8') };
        } finally { await handle.close(); }
    }
    return {
        templateHead,
        linkedTemplate(storageStoreId, templateId) {
            // Only the authenticated SmartStock bridge can invoke this after it
            // has resolved an enabled customer link in its authoritative database.
            return templateHead({ storeIds: [...roots.keys()] }, storageStoreId, templateId);
        },
        async desktopTemplates(identity, kind) {
            if (!['BADGE', 'BUSINESS_CARDS', 'LABELS', 'BILL_BOOKS'].includes(kind)) throw new FileUnavailable('Invalid template category.');
            const result = [];
            for (const record of (await indexTemplates(identity, kind)).values()) {
                if (!record) continue;
                try {
                    const path = await locate(identity, [record.store, ...record.parts]);
                    const info = await lstat(path);
                    if (!info.isFile() || info.nlink !== 1) continue;
                    result.push({ kind, id: record.legacy.id, storageStoreId: record.legacy.storageStoreId,
                        ownerStoreId: record.store, name: record.name,
                        parts: [...record.parts], size: info.size });
                } catch (error) {
                    if (!['ENOENT', 'ENOTDIR'].includes(error.code)) throw error;
                }
                if (result.length > 10000) throw new Error('Desktop library exceeds 10000 designs.');
            }
            return result.sort((a, b) => a.name.localeCompare(b.name));
        },
        async template(identity, kind, storageStoreId, templateId) {
            if (!['BADGE', 'BUSINESS_CARDS', 'LABELS', 'BILL_BOOKS'].includes(kind)) throw new FileUnavailable('File unavailable.');
            const record = (await indexTemplates(identity, kind)).get(JSON.stringify([kind, storageStoreId, templateId]));
            if (!record) throw new FileUnavailable('File unavailable.');
            const path = await locate(identity, [record.store, ...record.parts]);
            const info = await lstat(path);
            if (!info.isFile() || info.nlink !== 1) throw new FileUnavailable('File unavailable.');
            return { id: encode(record.store, record.parts), name: basename(path) };
        },
        async folder(identity, id) {
            const [store, ...parts] = decode(id);
            if (!identity.storeIds.includes(store)) throw new FileUnavailable('Only the owner store can manage this backup folder.');
            const path = await locate(identity, [store, ...parts]);
            if (!(await lstat(path)).isDirectory()) throw new FileUnavailable('Folder unavailable.');
            return { store, parts };
        },
        stores(identity) {
            return [...roots.keys()].filter(store => accessible(identity, store, [])).map(store => ({
                store, id: encode(store, []), manage: identity.storeIds.includes(store)
            }));
        },

        async list(identity, id) {
            const [store, ...parts] = decode(id);
            const path = await locate(identity, [store, ...parts]);
            const entries = await readdir(path, { withFileTypes: true });
            const visible = entries.filter(entry => safeName(entry.name) && !entry.name.startsWith('~$') &&
                !entry.name.endsWith('.smartstudio-lock') &&
                accessible(identity, store, [...parts, entry.name]) &&
                (entry.isDirectory() || entry.isFile()));
            const details = await Promise.all(visible.map(async entry => {
                try {
                    const child = [store, ...parts, entry.name];
                    const target = await locate(identity, child);
                    const info = await lstat(target);
                    if (info.isSymbolicLink() || !info.isDirectory() && (!info.isFile() || info.nlink !== 1)) return null;
                    const extension = extname(entry.name).toLowerCase();
                    return { name: entry.name, id: encode(store, [...parts, entry.name]), folder: info.isDirectory(),
                        fileType: info.isDirectory() ? 'Folder' : extension.slice(1).toUpperCase() || 'File',
                        sizeBytes: info.isFile() ? info.size : null, modifiedAt: info.mtime.toISOString(),
                        preview: info.isFile() && (types.has(extension) || !!officePreview && ['.docx', '.pptx'].includes(extension)) };
                } catch (error) {
                    if (error instanceof FileUnavailable || ['ENOENT', 'ENOTDIR', 'EACCES', 'EPERM'].includes(error.code)) return null;
                    throw error;
                }
            }));
            return details.filter(Boolean).sort((a, b) => Number(b.folder) - Number(a.folder) || a.name.localeCompare(b.name));
        },

        async preview(identity, id) {
            const entry = decode(id);
            const path = await locate(identity, entry);
            const office = ['.docx', '.pptx'].includes(extname(path).toLowerCase()) && officePreview;
            const contentType = office ? 'application/pdf' : types.get(extname(path).toLowerCase());
            if (!contentType) throw new FileUnavailable('Preview unavailable.');
            const expected = await lstat(path);
            if (!expected.isFile() || expected.nlink !== 1) {
                throw new FileUnavailable('Preview unavailable.');
            }
            const handle = await open(path, 'r');
            try {
                const info = await handle.stat();
                if (!info.isFile() || info.nlink !== 1 || info.ino !== expected.ino ||
                    info.dev !== expected.dev || await locate(identity, entry) !== path) {
                    throw new FileUnavailable('Preview unavailable.');
                }
                const extension = extname(path).toLowerCase();
                if (office) {
                    const bytes = await officePreview(handle, extension, info.size);
                    return { handle, bytes, size: bytes.length, contentType, name: basename(path) };
                }
                if (extension === '.sspub' || extension === '.sstbadge') {
                    let bytes;
                    try { bytes = await studioPreview(handle, extension, info.size); }
                    catch { throw new FileUnavailable('Design preview unavailable.'); }
                    return { handle, bytes, size: bytes.length, contentType, name: basename(path) };
                }
                return { handle, size: info.size, contentType, name: basename(path) };
            } catch (error) {
                await handle.close();
                throw error;
            }
        }
    };
}
