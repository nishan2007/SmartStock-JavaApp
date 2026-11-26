import { readFile } from 'node:fs/promises';
import { rebrandPlan } from '../src/rebrand-plan.js';
const [configPath, name, displayName] = process.argv.slice(2);
if (!configPath || !name || process.argv.length > 5) throw new Error('Usage: node tools/rebrand-plan.mjs <config.json> <installation-name> [display-name]');
const config = JSON.parse((await readFile(configPath, 'utf8')).replace(/^\uFEFF/u, ''));
process.stdout.write(JSON.stringify(rebrandPlan(config, name, displayName)) + '\n');
