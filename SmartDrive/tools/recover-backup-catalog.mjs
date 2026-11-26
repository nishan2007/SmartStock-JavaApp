import { readFile } from 'node:fs/promises';
import { backupLibrary } from '../src/backup.js';
import { oneDriveBackup } from '../src/onedrive.js';

const [configPath, confirmation] = process.argv.slice(2);
if (!configPath || confirmation !== '--service-stopped') {
    throw new Error('Stop SmartDrive first. Usage: node tools/recover-backup-catalog.mjs <config.json> --service-stopped');
}
const config = JSON.parse((await readFile(configPath, 'utf8')).replace(/^\uFEFF/u, ''));
const cloud = oneDriveBackup({ ...config.oneDrive, clientSecret: process.env.SMARTDRIVE_ONEDRIVE_CLIENT_SECRET });
const library = backupLibrary({ ...config, stateRoot: config.backupStateRoot, cloud });
const result = await library.recoverCatalog();
process.stdout.write(JSON.stringify(result) + '\n');
