import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { configuredOrigins } from '../src/index.js';

try {
  const path = process.argv[2] ?? fileURLToPath(new URL('../wrangler.production.jsonc', import.meta.url));
  const config = JSON.parse(readFileSync(path, 'utf8').replace(/^\s*\/\/.*$/gm, ''));
  const domains = (config.routes ?? [])
    .filter(route => route.custom_domain === true)
    .map(route => route.pattern)
    .sort();

  if (config.name !== 'smartstock-storefront'
      || config.workers_dev !== false
      || config.preview_urls !== false
      || JSON.stringify(domains) !== JSON.stringify(['deckers.gy', 'www.deckers.gy'])) {
    throw new Error('Production Worker target or domains do not match the reviewed launch.');
  }
  if (config.vars?.BROWSE_ONLY !== 'true') {
    throw new Error('Production Worker must have BROWSE_ONLY set to true.');
  }
  if ('ORIGIN_KEYS_JSON' in (config.vars ?? {})) {
    throw new Error('Origin keys must be configured as a Worker secret, not a public variable.');
  }

  const configured = JSON.parse(config.vars?.ORIGINS_JSON ?? 'null');
  if (!Array.isArray(configured) || configured.length === 0) {
    throw new Error('ORIGINS_JSON has no verified production store origin; deployment is blocked.');
  }
  const origins = configuredOrigins(config.vars);
  console.log(`Production storefront preflight passed: ${origins.length} registered origin(s), browse-only enabled.`);
} catch (error) {
  console.error(`Production storefront preflight failed: ${error.message}`);
  process.exitCode = 1;
}
