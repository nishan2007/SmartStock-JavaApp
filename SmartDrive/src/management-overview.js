import { resolveBranding } from './branding.js';

export async function backupOverview(identity, backups, selections) {
    if (!backups) return { state: 'not-configured', selectedFolders: 0, verified: 0, pending: 0, failed: 0, lastVerifiedAt: null };
    try {
        const versions = (await backups.versions()).filter(record => identity.storeIds.includes(record.store));
        const folders = selections ? (await selections.read()).filter(record => identity.storeIds.includes(record.store)) : [];
        const pending = versions.filter(record => record.status !== 'verified');
        const failed = pending.filter(record => record.attempts > 0);
        const verified = versions.filter(record => record.status === 'verified');
        const dates = verified.map(record => record.verifiedAt).filter(value => typeof value === 'string' && Number.isFinite(Date.parse(value))).sort((a, b) => Date.parse(b) - Date.parse(a));
        return { state: failed.length ? 'needs-attention' : pending.length ? 'pending' : folders.length ? 'ready' : 'no-folders-selected',
            selectedFolders: folders.length, verified: verified.length, pending: pending.length, failed: failed.length,
            lastVerifiedAt: dates[0] ?? null };
    } catch { return { state: 'unavailable' }; }
}

export function effectiveSettings(config, identity) {
    const brand = resolveBranding(config);
    return { productName: brand.productName, installationName: brand.installationName, displayName: brand.displayName,
        serverName: config.serverName, share: '\\\\' + config.serverName + '\\' + brand.shareName,
        managementUrl: config.publicOrigin.replace(/\/$/u, '') + '/employees/drive/',
        origin: { transport: config.originTls ? 'HTTPS' : 'Loopback HTTP', listenAddress: config.originTls?.listenAddress ?? '127.0.0.1',
            port: config.port ?? 4178, allowedAddresses: config.originTls?.allowedAddresses ?? ['127.0.0.1'] },
        stores: Object.keys(config.storeRoots).filter(id => identity.storeIds.includes(id)),
        webAccess: 'Read-only employee previews', backup: config.oneDrive ? {
            configured: true, provider: 'Microsoft OneDrive', driveId: config.oneDrive.driveId, folderId: config.oneDrive.folderId,
            mode: 'One-way versioned backup; local deletions retain backed-up versions', restore: 'Separate recovery folder'
        } : { configured: false } };
}
