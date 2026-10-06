import test from 'node:test';
import assert from 'node:assert/strict';
import {mkdtemp, writeFile, rm} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import {createHash} from 'node:crypto';
import {validateAppEntries, verifyModelHosting} from './verify-app-update.mjs';

const entries = prefix => [`${prefix}inventory-management-1.1.1.jar`,
  `${prefix}dependency/onnxruntime-1.23.2.jar`,
  `${prefix}dependency/catalog-studio/catalog-studio-model-NOTICE.txt`,
  `${prefix}dependency/catalog-studio/birefnet-LICENSE.txt`];

test('accepts matching model-free Windows and macOS update layouts', () => {
  assert.equal(validateAppEntries(entries(''), '1.1.1'), 'inventory-management-1.1.1.jar');
  assert.equal(validateAppEntries(entries('SmartStock.app/Contents/app/'), '1.1.1'), 'SmartStock.app/Contents/app/inventory-management-1.1.1.jar');
});

test('rejects bundled weights, wrong versions, ambiguous JARs and missing runtime/licences', () => {
  const valid = entries('');
  assert.throws(() => validateAppEntries([...valid, 'dependency/catalog-studio/model.ONNX'], '1.1.1'));
  assert.throws(() => validateAppEntries(valid, '1.0.249'));
  assert.throws(() => validateAppEntries([...valid, ...entries('SmartStock.app/Contents/app/')], '1.1.1'));
  for (let i = 1; i < valid.length; i++) assert.throws(() => validateAppEntries(valid.filter((_, index) => index !== i), '1.1.1'));
  assert.throws(() => validateAppEntries([...valid, '../unsafe'], '1.1.1'));
});

test('requires the exact hosted immutable catalogue snapshot without downloading models', async () => {
  const work = await mkdtemp(join(tmpdir(), 'smartstock-catalogue-test-'));
  const bytes = Buffer.from(JSON.stringify({schemaVersion: 1, models: ['FAST', 'BEST'].map(quality => ({
    quality, version: 'fixture-1', sizeBytes: 1, sha256: 'a'.repeat(64),
    objectKey: `models/${quality.toLowerCase()}/${'a'.repeat(64)}.onnx`,
    compatibility: quality === 'FAST' ? 'isnet-1024-v1' : 'birefnet-1024-imagenet-v1'}))}));
  const hash = createHash('sha256').update(bytes).digest('hex');
  try {
    const calls = [];
    await verifyModelHosting(async args => {
      calls.push(args[3]); await writeFile(args[args.indexOf('--file') + 1], bytes);
    }, work);
    assert.deepEqual(calls, ['smartstock-updates/models/catalogue-v1.json', `smartstock-updates/models/catalogues/${hash}.json`]);
    await assert.rejects(verifyModelHosting(async args => {
      await writeFile(args[args.indexOf('--file') + 1], args[3].includes('/catalogues/') ? 'different bytes' : bytes);
    }, work));
    await assert.rejects(verifyModelHosting(async () => { throw Error('Not found'); }, work));
  } finally { await rm(work, {recursive: true, force: true}); }
});
