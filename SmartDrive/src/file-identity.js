import { open } from 'node:fs/promises';

export function validFileIdentity(value) {
    return value && typeof value.id === 'string' && /^[a-f0-9]{8}(?:-[a-f0-9]{4}){3}-[a-f0-9]{12}$/u.test(value.id) &&
        typeof value.storageStoreId === 'string' && /^[1-9]\d{0,8}$/u.test(value.storageStoreId) &&
        ['BADGE', 'BUSINESS_CARDS', 'LABELS', 'BILL_BOOKS'].includes(value.kind);
}
export async function readFileIdentity(path) {
    if (process.platform !== 'win32') return null;
    let stream;
    try { stream = await open(path + ':SmartDriveIdentity', 'r'); }
    catch (error) { if (error.code === 'ENOENT') return null; throw error; }
    try {
        const size = (await stream.stat()).size;
        if (size < 1 || size > 4096) throw new Error('Invalid file identity metadata.');
        const bytes = Buffer.alloc(size);
        const read = await stream.read(bytes, 0, size, 0);
        if (read.bytesRead !== size) throw new Error('Incomplete file identity metadata.');
        const value = JSON.parse(bytes.toString('utf8').replace(/^\uFEFF/u, ''));
        if (!validFileIdentity(value)) throw new Error('Invalid file identity metadata.');
        return { id: value.id, storageStoreId: value.storageStoreId, kind: value.kind };
    } finally { await stream.close(); }
}
export async function writeFileIdentity(path, value) {
    if (process.platform !== 'win32' || !validFileIdentity(value)) throw new Error('Valid Windows file identity metadata is required.');
    const stream = await open(path + ':SmartDriveIdentity', 'w');
    try { await stream.writeFile(JSON.stringify({ id: value.id, storageStoreId: value.storageStoreId, kind: value.kind })); await stream.sync(); }
    finally { await stream.close(); }
}
