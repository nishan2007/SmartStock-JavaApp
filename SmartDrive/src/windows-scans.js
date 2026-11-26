import { execFile } from 'node:child_process';
import { isAbsolute } from 'node:path';
import { fileURLToPath } from 'node:url';
import { promisify } from 'node:util';

const run = promisify(execFile);
const script = fileURLToPath(new URL('../tools/Copy-ClosedScan.ps1', import.meta.url));
const options = { windowsHide: true, timeout: 120000 };

export function windowsScanOperations(defender) {
    if (process.platform !== 'win32' || typeof defender !== 'string' || !isAbsolute(defender)) {
        throw new Error('Scanner intake requires Windows and an absolute Defender MpCmdRun.exe path.');
    }
    return {
        copyClosed: (source, destination) => run('powershell.exe', [
            '-NoProfile', '-NonInteractive', '-File', script, '-Source', source, '-Destination', destination
        ], options),
        async malwareCheck(path) {
            try {
                await run(defender, ['-Scan', '-ScanType', '3', '-File', path, '-DisableRemediation'], options);
                return true;
            } catch (error) {
                if (error.code === 2) return false;
                throw error;
            }
        },
        resetAcl: path => run('icacls.exe', [path, '/reset'], options)
    };
}
