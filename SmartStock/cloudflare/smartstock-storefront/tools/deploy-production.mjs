import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

const config = process.argv[2] ?? fileURLToPath(new URL('../wrangler.production.jsonc', import.meta.url));
const flags = process.argv.slice(3);
const check = spawnSync(process.execPath, [fileURLToPath(new URL('./check-production-config.mjs', import.meta.url)), config], {stdio:'inherit'});
if (check.error) throw check.error;
if (check.status !== 0) process.exit(check.status ?? 1);

const wrangler = fileURLToPath(new URL('../node_modules/wrangler/bin/wrangler.js', import.meta.url));
const listed = spawnSync(process.execPath, [wrangler, 'secret', 'list', '--config', config],
  {encoding:'utf8', maxBuffer:1024*1024});
if (listed.error || listed.status !== 0) {
  console.error('Production storefront preflight failed: Cloudflare origin secret could not be verified.');
  process.exit(1);
}
let secrets;
try { secrets = JSON.parse(listed.stdout); }
catch {
  console.error('Production storefront preflight failed: Cloudflare secret list was unreadable.');
  process.exit(1);
}
if (!Array.isArray(secrets) || !secrets.some(secret => secret.name === 'ORIGIN_KEYS_JSON' && secret.type === 'secret_text')) {
  console.error('Production storefront preflight failed: ORIGIN_KEYS_JSON Worker secret is missing.');
  process.exit(1);
}
console.log('Cloudflare origin secret binding verified.');
const deploy = spawnSync(process.execPath, [wrangler, 'deploy', '--config', config, ...flags], {stdio:'inherit'});
if (deploy.error) throw deploy.error;
process.exit(deploy.status ?? 1);
