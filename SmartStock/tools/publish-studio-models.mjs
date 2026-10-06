import {createReadStream} from 'node:fs';
import {mkdtemp, readFile, stat, rm, writeFile} from 'node:fs/promises';
import {createHash} from 'node:crypto';
import {tmpdir} from 'node:os';
import {dirname, join, resolve} from 'node:path';
import {fileURLToPath, pathToFileURL} from 'node:url';
import {spawn} from 'node:child_process';
import {publishInstaller} from './publish-installer.mjs';

const compatibility = {FAST: 'isnet-1024-v1', BEST: 'birefnet-1024-imagenet-v1'};

export function validateCatalogue(value) {
  if (value?.schemaVersion !== 1 || !Array.isArray(value.models) || value.models.length !== 2)
    throw Error('Unsupported AI model catalogue');
  const qualities = new Set();
  for (const model of value.models) {
    if (!model || !Object.hasOwn(compatibility, model.quality) || qualities.has(model.quality)
        || !/^[A-Za-z0-9._-]{1,64}$/.test(model.version || '')
        || !/^[a-f0-9]{64}$/.test(model.sha256 || '')
        || !Number.isSafeInteger(model.sizeBytes) || model.sizeBytes <= 0 || model.sizeBytes > 2_000_000_000
        || model.compatibility !== compatibility[model.quality]
        || model.objectKey !== `models/${model.quality.toLowerCase()}/${model.sha256}.onnx`)
      throw Error('Invalid or incompatible AI model entry');
    qualities.add(model.quality);
  }
  return value;
}

export async function digestFile(file) {
  const hash = createHash('sha256');
  for await (const chunk of createReadStream(file)) hash.update(chunk);
  return {sizeBytes: (await stat(file)).size, sha256: hash.digest('hex')};
}

export async function verifyFile(file, expected) {
  const actual = await digestFile(file);
  if (actual.sizeBytes !== expected.sizeBytes || actual.sha256 !== expected.sha256)
    throw Error('AI model size or SHA-256 verification failed');
}

export async function uploadModel(run, stageModel, model, file) {
  if (model.sizeBytes > 300 * 1024 * 1024) {
    if (!stageModel) throw Error('This model exceeds Wrangler\'s upload limit; use the protected multipart model publisher');
    await stageModel(file, model);
  } else {
    await run(['r2', 'object', 'put', `smartstock-updates/${model.objectKey}`, '--remote', '--file', file, '--content-type', 'application/octet-stream', '--force']);
  }
}

export async function publishModels({catalogueFile, modelDirectory, run, workDirectory, stageModel}) {
  const catalogueBytes = await readFile(catalogueFile);
  const catalogue = validateCatalogue(JSON.parse(catalogueBytes.toString('utf8')));
  // Validate all supplied files before any upload. Unchanged entries may exist only in R2.
  const localFiles = new Map();
  for (const model of catalogue.models) {
    const file = join(modelDirectory, model.quality === 'FAST' ? 'isnet-general-use.onnx' : 'birefnet-general.onnx');
    try { await stat(file); }
    catch (e) { if (e.code === 'ENOENT') continue; throw e; }
    await verifyFile(file, model); localFiles.set(model.quality, file);
  }
  for (const model of catalogue.models) {
    const object = `smartstock-updates/${model.objectKey}`;
    const check = join(workDirectory, `${model.quality}.onnx`);
    let existing = false;
    try { await run(['r2', 'object', 'get', object, '--remote', '--file', check]); await verifyFile(check, model); existing = true; }
    catch { /* A supplied verified file can repair a missing or corrupt object. */ }
    if (!existing) {
      const file = localFiles.get(model.quality);
      if (!file) throw Error(`Supply the ${model.quality} model; its remote object could not be verified`);
      await uploadModel(run, stageModel, model, file);
      await run(['r2', 'object', 'get', object, '--remote', '--file', check]);
      await verifyFile(check, model);
    }
    // Avoid holding both downloaded models on disk during publication.
    await rm(check, {force: true});
  }
  const stagedCatalogue = join(workDirectory, 'catalogue.json');
  await writeFile(stagedCatalogue, catalogueBytes);
  const catalogueDigest = await digestFile(stagedCatalogue);
  const immutable = `smartstock-updates/models/catalogues/${catalogueDigest.sha256}.json`;
  const checkCatalogue = join(workDirectory, 'verified-catalogue.json');
  await run(['r2', 'object', 'put', immutable, '--remote', '--file', stagedCatalogue, '--content-type', 'application/json', '--force']);
  await run(['r2', 'object', 'get', immutable, '--remote', '--file', checkCatalogue]);
  await verifyFile(checkCatalogue, catalogueDigest);
  // Publish the live catalogue only after every exact artifact has passed download verification.
  await run(['r2', 'object', 'put', 'smartstock-updates/models/catalogue-v1.json', '--remote', '--file', stagedCatalogue, '--content-type', 'application/json', '--force']);
  await run(['r2', 'object', 'get', 'smartstock-updates/models/catalogue-v1.json', '--remote', '--file', checkCatalogue]);
  await verifyFile(checkCatalogue, catalogueDigest);
}

async function main() {
  const [catalogueFile, modelDirectory] = process.argv.slice(2);
  if (!catalogueFile || !modelDirectory || process.argv.length !== 4)
    throw Error('Usage: node tools/publish-studio-models.mjs <catalogue.json> <model-directory>');
  const worker = resolve(dirname(fileURLToPath(import.meta.url)), '../cloudflare/smartstock-update-download');
  const wrangler = join(worker, 'node_modules/wrangler/bin/wrangler.js');
  await stat(wrangler);
  const workDirectory = await mkdtemp(join(tmpdir(), 'smartstock-model-publish-'));
  try {
    const run = args => new Promise((accept, reject) => {
      const child = spawn(process.execPath, [wrangler, ...args], {cwd: worker, stdio: 'inherit', windowsHide: true});
      child.on('error', reject); child.on('exit', code => code === 0 ? accept() : reject(Error('R2 operation failed')));
    });
    const stageModel = (artifact, model) => publishInstaller({artifact, model, kind: 'model',
      endpoint: process.env.SMARTSTOCK_INSTALLER_PORTAL_URL || 'https://downloads.deckers.gy',
      secret: process.env.SMARTSTOCK_INSTALLER_PUBLISH_KEY});
    await publishModels({catalogueFile: resolve(catalogueFile), modelDirectory: resolve(modelDirectory), run, workDirectory, stageModel});
    console.log('Published independently verified AI models and catalogue. No application release metadata was changed.');
  } finally { await rm(workDirectory, {recursive: true, force: true}); }
}

if (process.argv[1] && import.meta.url === pathToFileURL(resolve(process.argv[1])).href)
  main().catch(error => { console.error(error.message); process.exitCode = 1; });
