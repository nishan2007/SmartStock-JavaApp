import {execFile} from 'node:child_process';
import {promisify} from 'node:util';
import {mkdtemp, readFile, rm, stat} from 'node:fs/promises';
import {createHash} from 'node:crypto';
import {tmpdir} from 'node:os';
import {dirname, join, resolve} from 'node:path';
import {fileURLToPath, pathToFileURL} from 'node:url';
import {validateCatalogue} from './publish-studio-models.mjs';
const execute = promisify(execFile);

export function validateAppEntries(entries, version) {
  if (!/^\d+\.\d+\.\d+$/.test(version)) throw Error('Use the shared Maven release version');
  if (entries.some(name => /\.onnx$/i.test(name))) throw Error('AI models must be published separately; the application ZIP contains model weights');
  if (entries.some(name => name.includes('\\') || name.split('/').some(part => part === '..' || part === '.') || name.startsWith('/')))
    throw Error('Application ZIP contains unsafe or non-portable paths');
  const jars = entries.filter(name => /^(?:SmartStock\.app\/Contents\/app\/)?inventory-management-[^/]+\.jar$/.test(name));
  if (jars.length !== 1 || !jars[0].endsWith(`inventory-management-${version}.jar`)) throw Error('Application ZIP must contain exactly one application JAR matching the release version');
  const prefix = jars[0].slice(0, -`inventory-management-${version}.jar`.length);
  for (const name of ['catalog-studio-model-NOTICE.txt', 'birefnet-LICENSE.txt'])
    if (!entries.includes(`${prefix}dependency/catalog-studio/${name}`)) throw Error('Application ZIP is missing the AI model notice or licence');
  if (!entries.some(name => name.startsWith(`${prefix}dependency/onnxruntime-`) && name.endsWith('.jar')))
    throw Error('Application ZIP is missing the AI inference runtime');
  return jars[0];
}

export async function verifyAppUpdate(artifact, version) {
  const zip = resolve(artifact);
  const listing = await execute('jar', ['tf', zip], {maxBuffer: 10 * 1024 * 1024, windowsHide: true});
  const jarName = validateAppEntries(listing.stdout.split(/\r?\n/).filter(Boolean), version);
  const work = await mkdtemp(join(tmpdir(), 'smartstock-update-check-'));
  try {
    await execute('jar', ['xf', zip, jarName], {cwd: work, windowsHide: true});
    const nested = await execute('jar', ['tf', join(work, jarName)], {maxBuffer: 10 * 1024 * 1024, windowsHide: true});
    const entries = new Set(nested.stdout.split(/\r?\n/));
    for (const name of ['app/StudioModelMigration.class', 'services/StudioModelService.class', 'models/catalogue-v1.json'])
      if (!entries.has(name)) throw Error('Application JAR does not support independent AI models; rebuild from the current source');
  } finally { await rm(work, {recursive: true, force: true}); }
}

export async function verifyModelHosting(run, work) {
  const latest = join(work, 'catalogue.json');
  await run(['r2', 'object', 'get', 'smartstock-updates/models/catalogue-v1.json', '--remote', '--file', latest]);
  if ((await stat(latest)).size > 65536) throw Error('Hosted model catalogue is too large');
  const bytes = await readFile(latest); validateCatalogue(JSON.parse(bytes.toString('utf8')));
  const hash = createHash('sha256').update(bytes).digest('hex');
  const snapshot = join(work, 'verified-catalogue.json');
  await run(['r2', 'object', 'get', `smartstock-updates/models/catalogues/${hash}.json`, '--remote', '--file', snapshot]);
  if (!(await readFile(snapshot)).equals(bytes)) throw Error('Hosted model catalogue does not match its verified immutable snapshot');
}

export async function verifyHostedModelCatalogue() {
  const worker = resolve(dirname(fileURLToPath(import.meta.url)), '../cloudflare/smartstock-update-download');
  const wrangler = join(worker, 'node_modules/wrangler/bin/wrangler.js'); await stat(wrangler);
  const work = await mkdtemp(join(tmpdir(), 'smartstock-hosting-check-'));
  try {
    await verifyModelHosting(args => execute(process.execPath, [wrangler, ...args], {cwd: worker, windowsHide: true}), work);
  } catch { throw Error('Publish and verify the independent AI models and catalogue before publishing this application release; check Cloudflare operator access'); }
  finally { await rm(work, {recursive: true, force: true}); }
}

if (process.argv[1] && import.meta.url === pathToFileURL(resolve(process.argv[1])).href) {
  const [artifact, version, flag] = process.argv.slice(2);
  (async () => {
    if (!artifact || !version || (flag && flag !== '--check-model-hosting') || process.argv.length > 5)
      throw Error('Usage: node tools/verify-app-update.mjs <application.zip> <version> [--check-model-hosting]');
    await verifyAppUpdate(artifact, version);
    if (flag) await verifyHostedModelCatalogue();
    console.log(`SmartStock ${version} model-free update package verified${flag ? '; hosted verified model catalogue is ready' : ''}.`);
  })().catch(error => { console.error(error.message); process.exitCode = 1; });
}
