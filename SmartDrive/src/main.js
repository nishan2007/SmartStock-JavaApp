import { readFile, realpath } from 'node:fs/promises';
import { isAbsolute, join, relative, sep } from 'node:path';
import { smartStockEmployees } from './employee-session.js';
import { resolveBranding } from './branding.js';
import { fileLibrary } from './files.js';
import { driveServer } from './server.js';
import { scanIntake } from './scans.js';
import { windowsScanOperations } from './windows-scans.js';
import { managementStatus } from './management.js';
import { backupLibrary } from './backup.js';
import { oneDriveBackup } from './onedrive.js';
import { uploadState } from './upload-state.js';
import { backupSettings } from './backup-settings.js';
import { managementAudit } from './audit.js';
import { scannerControls } from './scanner-controls.js';
import { scannerCredentials } from './scanner-credentials.js';
import { scannerEnrollment } from './scanner-enrollment.js';
import { windowsAccounts } from './windows-accounts.js';
import { serviceHealth } from './service-health.js';
import { effectiveSettings } from './management-overview.js';
import { loadOriginTls } from './origin-tls.js';
import { workstationEnrollment } from './workstations.js';
import { officePreviews } from './office-preview.js';
import { windowsOffice } from './windows-office.js';

const [configPath] = process.argv.slice(2);
if (!configPath) throw new Error('Usage: node src/main.js <installation-config.json>');
const config = JSON.parse((await readFile(configPath, 'utf8')).replace(/^\uFEFF/u, ''));
let sharedFolders = [];
let migrationRecords = [];
if (config.migrationCatalog) {
    const catalogPath = await realpath(config.migrationCatalog);
    for (const root of Object.values(config.storeRoots)) {
        const inside = relative(await realpath(root), catalogPath);
        if (!inside || !isAbsolute(inside) && inside !== '..' && !inside.startsWith('..' + sep)) {
            throw new Error('Keep migration access metadata outside staff storage.');
        }
    }
    const catalog = JSON.parse(await readFile(catalogPath, 'utf8'));
    if (catalog.formatVersion !== 1 || !Array.isArray(catalog.sharedFolders)) throw new Error('Invalid migration catalog.');
    sharedFolders = catalog.sharedFolders;
    if (!Array.isArray(catalog.records)) throw new Error('Invalid migration identities.');
    migrationRecords = catalog.records;
}
const key = process.env.SMARTDRIVE_FILE_ID_KEY ?? '';
if (!/^[a-f0-9]{64}$/iu.test(key)) throw new Error('Set SMARTDRIVE_FILE_ID_KEY to 32 random bytes in hex.');
const officePreview = config.office ? officePreviews({
    cacheRoot: config.office.cacheRoot, maxCacheBytes: config.office.maxCacheBytes,
    minimumFreeBytes: config.minimumFreeBytes,
    convert: windowsOffice(config.office.converter)
}) : undefined;
const library = fileLibrary({ storeRoots: config.storeRoots, fileIdKey: Buffer.from(key, 'hex'), officePreview, sharedFolders, migrationRecords });
const employees = smartStockEmployees(config);
const branding = resolveBranding(config);
const origin = Object.hasOwn(config, 'originTls') ? await loadOriginTls(config.originTls, config.storeRoots) : { host: '127.0.0.1' };
const accountBroker = config.enrollmentRoot || config.scanStateRoot ? windowsAccounts() : undefined;
const port = config.port ?? 4178;
const workstations = config.enrollmentRoot ? workstationEnrollment({
    root: config.enrollmentRoot, server: config.serverName, share: branding.shareName, broker: accountBroker,
    activity: accounts => accountBroker({ action: 'workstation-activity', accounts })
}) : undefined;
if (workstations) {
    let observing = false;
    setInterval(async () => {
        if (observing) return;
        observing = true;
        try { await workstations.refreshActivity(); }
        catch { process.stderr.write('Workstation activity observation needs attention.\n'); }
        finally { observing = false; }
    }, 30000);
}
if (!Number.isInteger(port) || port < 1 || port > 65535) throw new Error('Invalid SmartDrive port.');
let scannerControl;
let scannerCredentialControl;
let scannerEnrollmentControl;
if (config.scanStateRoot) {
    config.scanners ??= [];
    scannerEnrollmentControl = await scannerEnrollment({ root: config.scanStateRoot, dataRoot: branding.dataRoot,
        scanners: config.scanners, storeRoots: config.storeRoots,
        connection: { serverName: config.serverName, shareName: branding.shareName }, broker: accountBroker });
    scannerControl = scannerControls(config.scanStateRoot, config.scanners, { serverName: config.serverName, shareName: branding.shareName });
    scannerCredentialControl = scannerCredentials({ root: join(config.scanStateRoot, 'Credentials'), scanners: config.scanners, broker: accountBroker });
    const poll = scanIntake({
        scanners: config.scanners, storeRoots: config.storeRoots, stateRoot: config.scanStateRoot,
        ...windowsScanOperations(config.defenderScanner),
        isEnabled: scannerControl.enabled
    });
    setInterval(async () => {
        const result = await poll();
        if (result.some(task => task.status === 'rejected')) {
            process.stderr.write('SmartDrive scanner intake could not process one or more uploads.\n');
        }
    }, 10000);
}
const status = managementStatus(config, officePreview?.status,
    accountBroker ? serviceHealth({ accounts: () => accountBroker({ action: 'health' }) }) : undefined);
let backups;
let selections;
const audit = config.backupStateRoot ? managementAudit(config.backupStateRoot) : undefined;
if (config.oneDrive) {
    const cloud = oneDriveBackup({ ...config.oneDrive,
        uploadState: uploadState(join(config.backupStateRoot, 'Uploads')),
        clientSecret: process.env.SMARTDRIVE_ONEDRIVE_CLIENT_SECRET });
    backups = backupLibrary({ ...config, stateRoot: config.backupStateRoot, cloud });
    selections = backupSettings(config.backupStateRoot, config.backupSelections);
    let collecting = false;
    setInterval(async () => {
        if (collecting) return;
        collecting = true;
        try {
            for (const selection of await selections.read()) {
                try { await backups.captureFolder(selection.store, selection.parts); }
                catch { process.stderr.write('A selected backup folder could not be captured.\n'); }
            }
            await backups.uploadPending();
        } catch {
            process.stderr.write('SmartDrive backup requires attention. Pending versions are retained.\n');
        } finally { collecting = false; }
    }, 60000);
}
driveServer({ library, employeeSession: employees.session, auth: employees.auth, branding, status, backups, selections, audit, scannerControl, scannerCredentialControl, scannerEnrollmentControl,
    tls: origin.tls, originPeer: origin.originPeer,
    settings: identity => effectiveSettings(config, identity),
    studioBridge: process.env.SMARTDRIVE_STUDIO_BRIDGE_TOKEN ? {
        token: process.env.SMARTDRIVE_STUDIO_BRIDGE_TOKEN, server: config.serverName, share: branding.shareName
    } : undefined,
    workstations, publicOrigin: config.publicOrigin }).listen(port, origin.host, () => {
    process.stdout.write(`SmartDrive listening on ${origin.host}:${port} (${origin.tls ? 'HTTPS' : 'local HTTP'})\n`);
});
