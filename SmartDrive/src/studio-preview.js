import { inflateRawSync } from 'node:zlib';

const maxJson = 16 * 1024 * 1024;

async function read(handle, offset, count) {
    const buffer = Buffer.alloc(count);
    const result = await handle.read(buffer, 0, count, offset);
    if (result.bytesRead !== count) throw new Error('Incomplete SmartStudio file.');
    return buffer;
}

export async function studioMetadata(handle, extension, size) {
    if (!['.sstbadge', '.sspub'].includes(extension) || !Number.isSafeInteger(size) || size < 1 || size > 5 * 1024 ** 3) {
        throw new Error('Invalid SmartStudio metadata request.');
    }
    let bytes;
    if (extension === '.sstbadge') {
        if (size > 8 * 1024 * 1024) throw new Error('Badge exceeds the preview limit.');
        bytes = await read(handle, 0, size);
    } else {
        const tailSize = Math.min(size, 65557);
        const tail = await read(handle, size - tailSize, tailSize);
        let end = tail.length - 22;
        while (end >= 0 && (tail.readUInt32LE(end) !== 0x06054b50 ||
            end + 22 + tail.readUInt16LE(end + 20) !== tail.length)) end--;
        if (end < 0) throw new Error('Invalid publication archive.');
        let directoryOffset = tail.readUInt32LE(end + 16);
        let count = tail.readUInt16LE(end + 10);
        if (directoryOffset === 0xffffffff || count === 0xffff) {
            const locator = await read(handle, size - tailSize + end - 20, 20);
            if (locator.readUInt32LE(0) !== 0x07064b50) throw new Error('Invalid ZIP64 locator.');
            const offset = Number(locator.readBigUInt64LE(8));
            if (!Number.isSafeInteger(offset) || offset < 0 || offset > size - 56) throw new Error('Invalid ZIP64 offset.');
            const record = await read(handle, offset, 56);
            if (record.readUInt32LE(0) !== 0x06064b50) throw new Error('Invalid ZIP64 record.');
            directoryOffset = Number(record.readBigUInt64LE(48));
            count = Number(record.readBigUInt64LE(32));
        }
        if (!Number.isSafeInteger(directoryOffset) || directoryOffset < 0 || directoryOffset >= size ||
            !Number.isInteger(count) || count > 10001) throw new Error('Invalid publication directory.');
        let cursor = directoryOffset;
        let found = false;
        for (let i = 0; i < count; i++) {
            const entry = await read(handle, cursor, 46);
            if (entry.readUInt32LE(0) !== 0x02014b50) throw new Error('Invalid publication entry.');
            const length = entry.readUInt16LE(28);
            if (length > 1024) throw new Error('Invalid publication name.');
            const name = (await read(handle, cursor + 46, length)).toString('utf8');
            cursor += 46 + length + entry.readUInt16LE(30) + entry.readUInt16LE(32);
            if (name !== 'publication.json') continue;
            if (found) throw new Error('Duplicate publication metadata.');
            found = true;
            const compressed = entry.readUInt32LE(20);
            const expanded = entry.readUInt32LE(24);
            const offset = entry.readUInt32LE(42);
            if (compressed > maxJson || expanded > maxJson || offset >= size || (entry.readUInt16LE(8) & 1)) {
                throw new Error('Publication metadata exceeds the preview limit.');
            }
            const local = await read(handle, offset, 30);
            if (local.readUInt32LE(0) !== 0x04034b50) throw new Error('Invalid publication header.');
            const start = offset + 30 + local.readUInt16LE(26) + local.readUInt16LE(28);
            const content = await read(handle, start, compressed);
            const method = entry.readUInt16LE(10);
            bytes = method === 0 ? content : method === 8
                ? inflateRawSync(content, { maxOutputLength: maxJson }) : null;
            if (!bytes || bytes.length !== expanded) throw new Error('Invalid publication compression.');
        }
        if (!found) throw new Error('Publication metadata is missing.');
    }
    return JSON.parse(bytes.toString('utf8'));
}

export async function studioPreview(handle, extension, size) {
    const encoded = (await studioMetadata(handle, extension, size)).previewPng;
    if (typeof encoded !== 'string' || encoded.length > 1400000 || !/^[A-Za-z0-9+/]+={0,2}$/u.test(encoded)) {
        throw new Error('Save the design in updated SmartStudio to create its preview.');
    }
    const png = Buffer.from(encoded, 'base64');
    if (png.subarray(0, 8).toString('hex') !== '89504e470d0a1a0a' || png.length < 24 ||
        png.readUInt32BE(16) > 2048 || png.readUInt32BE(20) > 2048) throw new Error('Invalid design preview.');
    return png;
}
