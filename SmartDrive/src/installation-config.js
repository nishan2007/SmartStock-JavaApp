import { win32 as path } from 'node:path';
import { resolveBranding } from './branding.js';
import { scannerMetadata } from './scanner-setup.js';
import { originTlsSettings } from './origin-tls.js';

export function installationConfig(input) {
    const allowed = new Set(['installationName', 'displayName', 'dataParent', 'serverName', 'storeIds',
        'publicOrigin', 'employeeOrigin', 'employeeServer', 'minimumFreeBytes', 'port', 'office', 'scanners', 'defenderScanner', 'oneDrive', 'originTls']);
    if (!input || typeof input !== 'object' || Array.isArray(input) || Object.keys(input).some(key => !allowed.has(key))) {
        throw new Error('Use setup inputs, without previously generated storage paths.');
    }
    const brand = resolveBranding(input);
    if (!Array.isArray(input.storeIds) || !input.storeIds.length || input.storeIds.length > 1000 ||
        !input.storeIds.every(id => typeof id === 'string' && /^[1-9]\d{0,8}$/u.test(id)) ||
        new Set(input.storeIds).size !== input.storeIds.length) throw new Error('Supply unique store IDs as strings.');
    const serverName = input.serverName ?? brand.installationName.replace(/_/gu, '-').slice(0, 15).replace(/-+$/u, '').toUpperCase();
    if (typeof serverName !== 'string' || !/^[A-Za-z][A-Za-z0-9-]{0,14}$/u.test(serverName)) throw new Error('Use a Windows hostname of 1 to 15 characters.');
    function origin(value) {
        const url = new URL(value);
        if (url.protocol !== 'https:' || url.username || url.password || url.pathname !== '/' || url.search || url.hash) {
            throw new Error('Supply HTTPS origins without paths, credentials, queries or fragments.');
        }
        return url.origin;
    }
    const privateRoot = path.join(brand.dataRoot, 'Private');
    const config = { installationName: brand.installationName,
        ...(input.displayName === undefined ? {} : { displayName: brand.displayName }),
        dataParent: input.dataParent, serverName,
        publicOrigin: origin(input.publicOrigin), employeeOrigin: origin(input.employeeOrigin), employeeServer: origin(input.employeeServer),
        port: input.port ?? 4178, minimumFreeBytes: input.minimumFreeBytes ?? 5 * 1024 ** 3,
        storeRoots: Object.fromEntries(input.storeIds.map(id => [id, path.join(brand.filesRoot, id)])),
        enrollmentRoot: path.join(privateRoot, 'Workstations'), scanStateRoot: path.join(privateRoot, 'ScannerState'),
        backupStateRoot: path.join(privateRoot, 'BackupState'), recoveryRoot: brand.recoveryRoot,
        defenderScanner: input.defenderScanner ?? 'C:\\Program Files\\Windows Defender\\MpCmdRun.exe' };
    if (!Number.isInteger(config.port) || config.port < 1 || config.port > 65535 ||
        !Number.isSafeInteger(config.minimumFreeBytes) || config.minimumFreeBytes < 0) throw new Error('Invalid service port or storage reserve.');
    if (input.originTls !== undefined) config.originTls = originTlsSettings(input.originTls);
    if (input.office !== false) {
        const office = input.office ?? {};
        if (!office || typeof office !== 'object' || Array.isArray(office) || Object.keys(office).some(key => !['converter', 'maxCacheBytes'].includes(key))) {
            throw new Error('Office setup accepts converter and maxCacheBytes only.');
        }
        config.office = { converter: office.converter ?? 'C:\\Program Files\\LibreOffice\\program\\soffice.bin',
            maxCacheBytes: office.maxCacheBytes ?? 1024 ** 3, cacheRoot: path.join(privateRoot, 'OfficePreviews') };
        if (!Number.isSafeInteger(config.office.maxCacheBytes) || config.office.maxCacheBytes < 32 * 1024 ** 2) throw new Error('Invalid Office preview cache budget.');
        if (typeof config.office.converter !== 'string' || !/^[A-Za-z]:[\\/]/u.test(config.office.converter)) throw new Error('Use an absolute local Office converter path.');
    }
    if (input.scanners !== undefined && !Array.isArray(input.scanners)) throw new Error('Scanners must be an array.');
    config.scanners = (input.scanners ?? []).map(scanner => {
        if (!scanner || typeof scanner.id !== 'string' || typeof scanner.account !== 'string' ||
            Object.keys(scanner).some(key => !['id', 'storeId', 'account', 'displayName', 'model', 'reservedAddress'].includes(key)) ||
            !/^[A-Za-z0-9][A-Za-z0-9_-]{0,47}$/u.test(scanner.id) ||
            !/^sdscan_[a-z0-9_]{1,12}$/u.test(scanner.account) || !input.storeIds.includes(scanner.storeId)) throw new Error('Invalid scanner setup.');
        return { ...scanner, ...scannerMetadata(scanner), intake: path.join(privateRoot, 'Intake', scanner.id) };
    });
    if (new Set(config.scanners.map(scanner => scanner.id.toLowerCase())).size !== config.scanners.length ||
        new Set(config.scanners.map(scanner => scanner.account)).size !== config.scanners.length) throw new Error('Scanner IDs and accounts must be unique.');
    if (input.oneDrive) {
        if (Object.keys(input.oneDrive).some(key => !['tenantId', 'clientId', 'driveId', 'folderId'].includes(key)) ||
            !['tenantId', 'clientId', 'driveId', 'folderId'].every(key => typeof input.oneDrive[key] === 'string' && input.oneDrive[key].length)) {
            throw new Error('OneDrive setup requires IDs only; configure its secret separately.');
        }
        config.oneDrive = { ...input.oneDrive };
    }
    return config;
}
