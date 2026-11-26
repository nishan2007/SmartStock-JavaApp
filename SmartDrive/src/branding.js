import { readFileSync } from 'node:fs';
import { win32 as path } from 'node:path';
import { pathToFileURL } from 'node:url';

export const productName = 'SmartDrive';

export function resolveBranding(config) {
    const installationName = config.installationName ?? productName;
    const displayName = config.displayName ?? installationName;
    if (typeof displayName !== 'string' || !displayName.trim() ||
        displayName.length > 80 || /[\u0000-\u001f\u007f]/u.test(displayName)) {
        throw new Error('displayName must be a name of 1 to 80 characters.');
    }

    if (typeof installationName !== 'string' ||
        !/^[A-Za-z][A-Za-z0-9_-]{0,39}$/u.test(installationName) ||
        /^(CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])$/iu.test(installationName) ||
        /[\r\n]/u.test(installationName)) {
        throw new Error('installationName must be a Windows-safe name of 1 to 40 characters.');
    }

    const dataParent = config.dataParent;
    if (typeof dataParent !== 'string' || !/^[A-Za-z]:[\\/]/u.test(dataParent)) {
        throw new Error('dataParent must be an absolute path on a local Windows drive.');
    }

    const dataRoot = path.join(dataParent, installationName);
    return Object.freeze({
        productName,
        displayName: displayName.trim(),
        installationName,
        shareName: installationName,
        dataRoot,
        filesRoot: path.join(dataRoot, 'Stores'),
        recoveryRoot: path.join(dataRoot, 'Private', 'Recovery'),
        oneDriveFolder: installationName
    });
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
    try {
        if (process.argv.length !== 3) {
            throw new Error('Usage: npm run branding -- <installation-config.json>');
        }
        const config = JSON.parse(readFileSync(process.argv[2], 'utf8').replace(/^\uFEFF/u, ''));
        process.stdout.write(JSON.stringify(resolveBranding(config), null, 2) + '\n');
    } catch (error) {
        process.stderr.write(error.message + '\n');
        process.exitCode = 1;
    }
}
