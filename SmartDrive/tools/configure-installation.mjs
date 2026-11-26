import { readFile, open, unlink } from 'node:fs/promises';
import { installationConfig } from '../src/installation-config.js';

const [input, output] = process.argv.slice(2);
if (!input || !output) throw new Error('Usage: node tools/configure-installation.mjs <setup.json> <new-installation.json>');
const config = installationConfig(JSON.parse((await readFile(input, 'utf8')).replace(/^\uFEFF/u, '')));
const file = await open(output, 'wx', 0o600);
let complete = false;
try { await file.writeFile(JSON.stringify(config, null, 2) + '\n'); await file.sync(); complete = true; }
finally { await file.close(); if (!complete) await unlink(output); }
process.stdout.write('Installation configuration created. Review it before provisioning.\n');
