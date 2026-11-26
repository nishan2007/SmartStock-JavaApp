import { readFile, readdir, statfs } from 'node:fs/promises';
import { join } from 'node:path';

export function managementStatus({ storeRoots, scanners = [], scanStateRoot, minimumFreeBytes = 5 * 1024 ** 3 }, previewStatus, dependencyStatus) {
    if (!Number.isSafeInteger(minimumFreeBytes) || minimumFreeBytes < 0) throw new Error('Invalid storage reserve.');
    return async identity => {
        const stores = await Promise.all(Object.entries(storeRoots)
            .filter(([id]) => identity.storeIds.includes(id)).map(async ([id, root]) => {
                const volume = await statfs(root, { bigint: true });
                return { store: id, capacityBytes: String(volume.blocks * volume.bsize),
                    availableBytes: String(volume.bavail * volume.bsize),
                    lowSpace: volume.bavail * volume.bsize < BigInt(minimumFreeBytes) };
            }));
        const devices = await Promise.all(scanners.filter(scanner => identity.storeIds.includes(scanner.storeId))
            .map(async scanner => {
                const folder = join(scanStateRoot, scanner.id);
                let lastResult = null;
                try {
                    const saved = JSON.parse(await readFile(join(folder, 'status.json'), 'utf8'));
                    lastResult = { result: saved.lastResult, at: saved.at };
                } catch (error) {
                    if (error.code !== 'ENOENT') throw error;
                }
                let quarantineCount = 0;
                try { quarantineCount = (await readdir(join(folder, 'quarantine'))).length; }
                catch (error) { if (error.code !== 'ENOENT') throw error; }
                return { id: scanner.id, store: scanner.storeId, enabled: scanner.enabled !== false,
                    lastResult, quarantineCount };
            }));
        return { service: 'running', stores, scanners: devices,
            ...(dependencyStatus ? { dependencies: await dependencyStatus() } : {}),
            previews: previewStatus ? previewStatus() : { state: 'not-configured' } };
    };
}
