import { win32 as path } from 'node:path';
import { resolveBranding } from './branding.js';

export function rebrandPlan(config, installationName, displayName = installationName) {
    const before = resolveBranding(config);
    const after = resolveBranding({ ...config, installationName, displayName });
    if (before.installationName !== after.installationName && before.installationName.toLowerCase() === after.installationName.toLowerCase()) {
        throw new Error('Choose a distinct installation name; case-only directory changes require manual review.');
    }
    const result = structuredClone(config);
    result.installationName = after.installationName;result.displayName = after.displayName;
    const changes = [];
    const inside = (root, target) => {
        const relative = path.relative(root, target);
        return !path.isAbsolute(relative) && relative !== '..' && !relative.startsWith('..' + path.sep);
    };
    function rebase(value, label, required = true) {
        if (typeof value !== 'string' || !path.isAbsolute(value)) throw new Error('Invalid configured path: ' + label);
        if (!inside(before.dataRoot, value)) {
            if (required) throw new Error('Keep managed storage under the installation data root: ' + label);
            return value;
        }
        const next = path.join(after.dataRoot, path.relative(before.dataRoot, value));
        if (next !== value) changes.push({ field: label, before: value, after: next });
        return next;
    }
    if (!config.storeRoots || !Object.keys(config.storeRoots).length) throw new Error('Configure stores before rebranding.');
    for (const [id, root] of Object.entries(config.storeRoots)) {
        if (!/^[1-9]\d{0,8}$/u.test(id) || path.normalize(root).toLowerCase() !== path.join(before.filesRoot, id).toLowerCase()) throw new Error('Store roots must use the installation Stores layout.');
        result.storeRoots[id] = rebase(root, 'storeRoots.' + id);
    }
    for (const key of ['enrollmentRoot', 'scanStateRoot', 'backupStateRoot', 'recoveryRoot']) {
        if (config[key] !== undefined) result[key] = rebase(config[key], key);
    }
    if (config.migrationCatalog) result.migrationCatalog = rebase(config.migrationCatalog, 'migrationCatalog', false);
    if (config.office) result.office.cacheRoot = rebase(config.office.cacheRoot, 'office.cacheRoot');
    if (config.originTls) for (const key of ['certificatePath', 'privateKeyPath']) result.originTls[key] = rebase(config.originTls[key], 'originTls.' + key, false);
    if (config.scanners !== undefined && !Array.isArray(config.scanners)) throw new Error('Invalid scanner configuration.');
    for (const [index, scanner] of (result.scanners ?? []).entries()) scanner.intake = rebase(scanner.intake, 'scanners.' + index + '.intake');
    return { version: 1, before, after, displayOnly: before.installationName === after.installationName,
        serverName: config.serverName, publicOrigin: config.publicOrigin, changes, config: result,
        workstation: { server: config.serverName, oldShare: before.shareName, newShare: after.shareName },
        retained: ['employee/store assignments', 'workstation and copier accounts', 'backup versions and OneDrive destination IDs',
            'template identities and submission links', 'audit history', 'Windows hostname and HTTPS origin'] };
}
