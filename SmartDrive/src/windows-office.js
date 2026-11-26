import { execFile } from 'node:child_process';
import { isAbsolute } from 'node:path';
import { fileURLToPath } from 'node:url';
import { promisify } from 'node:util';

const run = promisify(execFile);
export function windowsOffice(converter) {
    if (process.platform !== 'win32' || !isAbsolute(converter) || !/[\\/]soffice\.bin$/iu.test(converter)) {
        throw new Error('Configure the guest LibreOffice soffice.bin for isolated previews.');
    }
    const executable = fileURLToPath(new URL('../OfficePreview.exe', import.meta.url));
    return (input, output, profile) => run(executable, [converter, input, output, profile], {
        windowsHide: true, timeout: 70000, maxBuffer: 8192
    });
}
