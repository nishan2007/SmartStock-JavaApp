import test from 'node:test';
import assert from 'node:assert/strict';
import {mkdtemp, writeFile, readFile, mkdir, rm} from 'node:fs/promises';
import {join} from 'node:path';
import {tmpdir} from 'node:os';
import {createHash} from 'node:crypto';
import {validateCatalogue, publishModels, uploadModel} from './publish-studio-models.mjs';

function entry(quality, body) {
  const sha256 = createHash('sha256').update(body).digest('hex');
  return {quality, version: 'fixture-1', objectKey: `models/${quality.toLowerCase()}/${sha256}.onnx`,
    sizeBytes: Buffer.byteLength(body), sha256,
    compatibility: quality === 'FAST' ? 'isnet-1024-v1' : 'birefnet-1024-imagenet-v1'};
}

test('rejects unsupported compatibility, duplicate qualities, and unsafe keys', () => {
  const valid = {schemaVersion: 1, models: [entry('FAST', 'fast'), entry('BEST', 'best')]};
  assert.equal(validateCatalogue(valid), valid);
  assert.throws(() => validateCatalogue({...valid, schemaVersion: 2}));
  assert.throws(() => validateCatalogue({...valid, models: [valid.models[0], valid.models[0]]}));
  assert.throws(() => validateCatalogue({...valid, models: [{...valid.models[0], compatibility: 'unknown'}, valid.models[1]]}));
  assert.throws(() => validateCatalogue({...valid, models: [{...valid.models[0], objectKey: '../private'}, valid.models[1]]}));
});

test('Best uses multipart staging above the Wrangler limit and missing staging fails closed', async () => {
  const large = {...entry('BEST', 'fixture'), sizeBytes: 972666916};
  let uploaded;
  await uploadModel(async () => { throw Error('Large models must not use Wrangler upload'); },
    async (file, model) => { uploaded = {file, model}; }, large, 'best.onnx');
  assert.equal(uploaded.model, large); assert.equal(uploaded.file, 'best.onnx');
  await assert.rejects(uploadModel(async () => {}, undefined, large, 'best.onnx'), /multipart/);
});

async function fixture(runTest) {
  const root = await mkdtemp(join(tmpdir(), 'smartstock-model-test-'));
  try {
    const models = join(root, 'models'); await mkdir(models);
    const catalogueFile = join(root, 'catalogue.json');
    const catalogue = {schemaVersion: 1, models: [entry('FAST', 'fast'), entry('BEST', 'best')]};
    await writeFile(catalogueFile, JSON.stringify(catalogue));
    await writeFile(join(models, 'isnet-general-use.onnx'), 'fast');
    await writeFile(join(models, 'birefnet-general.onnx'), 'best');
    await runTest({root, models, catalogueFile, catalogue});
  } finally { await rm(root, {recursive: true, force: true}); }
}

test('publishes catalogue only after both exact model downloads are verified', () => fixture(async f => {
  const objects = new Map(); const calls = [];
  const run = async args => {
    const operation = args[2], key = args[3], file = args[args.indexOf('--file') + 1];
    calls.push({operation, key});
    if (operation === 'put') objects.set(key, await readFile(file));
    else { if (!objects.has(key)) throw Error('Not found'); await writeFile(file, objects.get(key)); }
  };
  await publishModels({catalogueFile: f.catalogueFile, modelDirectory: f.models, workDirectory: f.root, run});
  const publication = calls.findIndex(c => c.operation === 'put' && c.key.endsWith('/catalogue-v1.json'));
  for (const model of f.catalogue.models) {
    const downloaded = calls.findLastIndex(c => c.operation === 'get' && c.key.endsWith(model.objectKey));
    assert.ok(downloaded < publication);
  }
  calls.length = 0;
  await publishModels({catalogueFile: f.catalogueFile, modelDirectory: f.models, workDirectory: f.root, run});
  assert.equal(calls.some(c => c.operation === 'put' && c.key.endsWith('.onnx')), false);
}));

test('corrupt downloaded artifact blocks live catalogue publication', () => fixture(async f => {
  let liveCataloguePublished = false;
  const run = async args => {
    const operation = args[2], key = args[3], file = args[args.indexOf('--file') + 1];
    if (operation === 'put' && key.endsWith('/catalogue-v1.json')) liveCataloguePublished = true;
    if (operation === 'get') await writeFile(file, 'corrupt');
  };
  await assert.rejects(publishModels({catalogueFile: f.catalogueFile, modelDirectory: f.models, workDirectory: f.root, run}));
  assert.equal(liveCataloguePublished, false);
}));

test('invalid supplied local model causes no remote writes', () => fixture(async f => {
  await writeFile(join(f.models, 'isnet-general-use.onnx'), 'corrupt');
  let calls = 0;
  await assert.rejects(publishModels({catalogueFile: f.catalogueFile, modelDirectory: f.models, workDirectory: f.root,
    run: async () => { calls++; }}));
  assert.equal(calls, 0);
}));
