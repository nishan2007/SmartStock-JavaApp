import { importStudioExport } from '../src/import-library.js';

const [source, destination, ...configuredStores] = process.argv.slice(2);
if (!source || !destination || !configuredStores.length) {
    throw new Error('Usage: node tools/import-studio.mjs <export-folder> <new-import-folder> <store-id> [...]');
}
const catalog = await importStudioExport({ source, destination, configuredStores });
process.stdout.write(JSON.stringify({ designs: catalog.records.length, accessFolders: catalog.sharedFolders.length }) + '\n');
