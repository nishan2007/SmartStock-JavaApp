import { statfs } from 'node:fs/promises';

export function storageReserve(minimumFreeBytes = 5 * 1024 ** 3) {
    if (!Number.isSafeInteger(minimumFreeBytes) || minimumFreeBytes < 0) {
        throw new Error('Storage reserve must be a nonnegative integer byte count.');
    }
    return async (directory, requiredBytes) => {
        if (!Number.isSafeInteger(requiredBytes) || requiredBytes < 0) throw new Error('Invalid storage requirement.');
        const volume = await statfs(directory, { bigint: true });
        if (volume.bavail * volume.bsize < BigInt(minimumFreeBytes) + BigInt(requiredBytes)) {
            throw new Error('Insufficient free space: SmartDrive storage reserve would be exceeded.');
        }
    };
}
