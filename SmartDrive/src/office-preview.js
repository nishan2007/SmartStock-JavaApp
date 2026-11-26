import { createHash } from 'node:crypto';
import { createWriteStream } from 'node:fs';
import { lstat, mkdir, mkdtemp, open, readFile, readdir, rename, rm, unlink, utimes } from 'node:fs/promises';
import { dirname, isAbsolute, join, resolve } from 'node:path';
import { pipeline } from 'node:stream/promises';
import { Transform } from 'node:stream';
import { storageReserve } from './storage-reserve.js';

export function officePreviews({ cacheRoot, convert, maxCacheBytes = 1024 * 1024 * 1024, minimumFreeBytes }) {
    const requireSpace = storageReserve(minimumFreeBytes);
    if (!isAbsolute(cacheRoot)) throw new Error('Office preview cache requires a private absolute path.');
    if (!Number.isSafeInteger(maxCacheBytes) || maxCacheBytes < 32 * 1024 * 1024) {
        throw new Error('Office preview cache budget must be at least 32 MiB.');
    }
    cacheRoot = resolve(cacheRoot);
    const active = new Map();
    const readers = new Map();
    let captures = 0;
    let converting = 0;
    let completed = 0;
    let failures = 0;
    let lastResult = null;
    let tail = Promise.resolve();
    async function makeRoom(required) {
        const entries = [];
        let total = 0;
        for (const name of await readdir(cacheRoot)) {
            if (!/^[a-f0-9]{64}\.(?:docx|pptx)\.pdf$/u.test(name)) continue;
            const path = join(cacheRoot, name);
            const info = await lstat(path);
            if (!info.isFile() || info.isSymbolicLink() || info.nlink !== 1) throw new Error('Invalid Office preview cache.');
            total += info.size;
            entries.push({ path, key: name.slice(0, -4), ...info });
        }
        entries.sort((a, b) => a.mtimeMs - b.mtimeMs);
        for (const entry of entries) {
            if (total + required <= maxCacheBytes) return;
            if (readers.has(entry.key) || active.has(entry.key)) continue;
            await unlink(entry.path);
            total -= entry.size;
        }
        if (total + required > maxCacheBytes) throw new Error('Office preview cache is busy or full. Try again.');
    }
    const preview = async (handle, extension, size) => {
        if (!['.docx', '.pptx'].includes(extension) || size < 1 || size > 64 * 1024 * 1024) {
            throw new Error('Document exceeds the Office preview limit.');
        }
        if (captures >= 2) throw new Error('Office preview conversion is busy. Try again.');
        captures++;
        let job;
        let readerKey;
        try {
            await mkdir(cacheRoot, { recursive: true });
            await requireSpace(cacheRoot, size + 32 * 1024 * 1024);
            job = await mkdtemp(join(cacheRoot, 'job-'));
            const input = join(job, 'document' + extension);
            const hash = createHash('sha256');
            let captured = 0;
            const meter = new Transform({
                transform(chunk, encoding, callback) {
                    captured += chunk.length;
                    if (captured > 64 * 1024 * 1024) return callback(new Error('Office document exceeds the preview limit.'));
                    hash.update(chunk);
                    callback(null, chunk);
                }
            });
            await pipeline(handle.createReadStream({ autoClose: false, start: 0 }), meter,
                createWriteStream(input, { flags: 'wx' }));
            const key = hash.digest('hex') + extension;
            readerKey = key;
            readers.set(key, (readers.get(key) ?? 0) + 1);
            const cached = join(cacheRoot, key + '.pdf');
            async function output() {
                const info = await lstat(cached);
                if (!info.isFile() || info.isSymbolicLink() || info.nlink !== 1 || info.size > 32 * 1024 * 1024) {
                    throw new Error('Invalid Office preview cache.');
                }
                const bytes = await readFile(cached);
                if (bytes.subarray(0, 5).toString() !== '%PDF-') throw new Error('Invalid Office preview output.');
                const now = new Date();
                await utimes(cached, now, now);
                return bytes;
            }
            try { return await output(); }
            catch (error) { if (error.code !== 'ENOENT') throw error; }
            if (active.has(key)) {
                await active.get(key);
                return await output();
            }
            if (active.size >= 2) throw new Error('Office preview conversion is busy. Try again.');
            const task = tail.then(async () => {
                converting++;
                try {
                const destination = join(job, 'output');
                await mkdir(destination);
                await convert(input, destination, join(job, 'profile'));
                const result = join(destination, 'document.pdf');
                const info = await lstat(result);
                if (!info.isFile() || info.isSymbolicLink() || info.nlink !== 1 || info.size > 32 * 1024 * 1024) {
                    throw new Error('Office preview conversion failed.');
                }
                const generated = await open(result, 'r');
                try {
                    const header = Buffer.alloc(5);
                    const read = await generated.read(header, 0, 5, 0);
                    if (read.bytesRead !== 5 || header.toString() !== '%PDF-') throw new Error('Invalid Office preview output.');
                } finally { await generated.close(); }
                await makeRoom(info.size);
                await rename(result, cached);
                completed++;
                lastResult = { result: 'completed', at: new Date().toISOString() };
                } catch (error) {
                    failures++;
                    lastResult = { result: 'failed', at: new Date().toISOString() };
                    throw error;
                } finally { converting--; }
            });
            tail = task.catch(() => {});
            active.set(key, task);
            try { await task; } finally { active.delete(key); }
            return await output();
        } finally {
            captures--;
            if (readerKey) {
                const count = readers.get(readerKey) - 1;
                if (count) readers.set(readerKey, count); else readers.delete(readerKey);
            }
            if (job) {
                if (dirname(job) !== cacheRoot) throw new Error('Invalid preview workspace.');
                await rm(job, { recursive: true, force: true });
            }
        }
    };
    preview.status = () => ({
        state: converting ? 'converting' : active.size ? 'queued' : lastResult?.result === 'failed' ? 'needs-attention' : 'idle',
        active: converting, queued: Math.max(0, active.size - converting), captures,
        completed, failures, maxCacheBytes, lastResult: lastResult ? { ...lastResult } : null
    });
    return preview;
}
