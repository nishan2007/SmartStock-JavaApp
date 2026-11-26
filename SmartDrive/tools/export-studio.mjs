import { exportStudioLibrary } from '../src/migration.js';

const [source, destination, mode] = process.argv.slice(2);
if (!source || !destination || (mode && mode !== '--dry-run')) {
    throw new Error('Usage: node tools/export-studio.mjs <legacy-studio-templates> <new-export-folder> [--dry-run]');
}
const result = await exportStudioLibrary({ source, destination, dryRun: mode === '--dry-run' });
process.stdout.write(JSON.stringify({
    templates: result.records.length,
    bytes: result.records.reduce((sum, record) => sum + record.metadata.size, 0),
    dryRun: mode === '--dry-run'
}, null, 2) + '\n');
