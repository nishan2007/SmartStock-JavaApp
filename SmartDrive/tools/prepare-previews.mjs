import { execFile } from 'node:child_process';
import { promisify } from 'node:util';
import { readFile } from 'node:fs/promises';
import { prepareLegacyPreviews } from '../src/prepare-previews.js';

const [configPath, javaPath, classpath, recoveryRoot, maintenance] = process.argv.slice(2);
if (!configPath || !javaPath || !classpath || !recoveryRoot || maintenance !== '--maintenance-window' || process.argv.length !== 7) {
    throw new Error('Usage: node tools/prepare-previews.mjs <installation-config.json> <java.exe> <SmartStudio-jar-and-lib-classpath> <new-private-recovery-folder> --maintenance-window');
}
const config = JSON.parse((await readFile(configPath, 'utf8')).replace(/^\uFEFF/u, ''));
const catalog = JSON.parse((await readFile(config.migrationCatalog, 'utf8')).replace(/^\uFEFF/u, ''));
const run = promisify(execFile);
const result = await prepareLegacyPreviews({ storeRoots: config.storeRoots, catalog, recoveryRoot, maintenanceWindow: true,
    async prepare(source, recovery, expected) {
        const output = await run(javaPath, ['-Djava.awt.headless=true', '-Xmx512m', '-cp', classpath,
            'gy.deckers.studio.StudioPreviewPreparation', source, recovery, expected], { windowsHide: true, timeout: 180000, maxBuffer: 65536 });
        return JSON.parse(output.stdout.trim());
    } });
process.stdout.write(JSON.stringify(result) + '\n');
