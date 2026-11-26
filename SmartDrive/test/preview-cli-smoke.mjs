// Run after `mvn -f SmartStudio/pom.xml package`; synthetic files only.
import assert from 'node:assert/strict';
import { mkdtemp, mkdir, writeFile, readFile, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { delimiter, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { createHash } from 'node:crypto';
import { execFile } from 'node:child_process';
import { promisify } from 'node:util';

const repository = fileURLToPath(new URL('../../', import.meta.url));
const root = await mkdtemp(join(tmpdir(), 'smartdrive-real-preview-'));
try {
    const store = join(root, 'Stores', '1');
    const id = '11111111-1111-4111-8111-111111111111';
    const parts = ['Studio Jobs', 'BADGE', 'Access-1', 'Legacy-' + id + '.sstbadge'];
    await mkdir(join(store, ...parts.slice(0, -1)), { recursive: true });
    const bytes = JSON.stringify({ formatVersion: 1, name: 'Legacy fixture', widthMm: 85.6, heightMm: 54, elements: [], futureSetting: 'retain' });
    await writeFile(join(store, ...parts), bytes);
    const catalogPath = join(root, 'catalog.json');
    await writeFile(catalogPath, JSON.stringify({ formatVersion: 1, records: [{ store: '1', parts,
        sha256: createHash('sha256').update(bytes).digest('hex'), legacy: { id, storageStoreId: '1', kind: 'BADGE' } }] }));
    const config = join(root, 'config.json');
    await writeFile(config, JSON.stringify({ storeRoots: { 1: store }, migrationCatalog: catalogPath }));
    const run = promisify(execFile);
    const cp = join(repository, 'SmartStudio', 'target', 'classes') + delimiter + join(repository, 'SmartStudio', 'target', 'lib', '*');
    const java = process.env.JAVA_HOME ? join(process.env.JAVA_HOME, 'bin', process.platform === 'win32' ? 'java.exe' : 'java') : 'java';
    for (const name of ['Recovery1', 'Recovery2']) {
        const recovery = join(root, name);
        let output;
        try { output = await run(process.execPath, [join(repository, 'SmartDrive', 'tools', 'prepare-previews.mjs'), config, java, cp, recovery, '--maintenance-window']); }
        catch (error) {
            const journal = await readFile(join(recovery, 'preparation.jsonl'), 'utf8').catch(() => 'No journal was created.');
            throw new Error('Synthetic preview CLI failed: ' + journal, { cause: error });
        }
        const result = JSON.parse(output.stdout);
        assert.equal(name === 'Recovery1' ? result.prepared : result.existing, 1);
    }
    const prepared = JSON.parse(await readFile(join(store, ...parts), 'utf8'));
    assert.ok(prepared.previewPng);assert.equal(prepared.futureSetting, 'retain');
    process.stdout.write('Real Node CLI to Java renderer passed; second run skipped the prepared file.\n');
} finally { await rm(root, { recursive: true, force: true }); }
