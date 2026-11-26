import test from 'node:test';
import assert from 'node:assert/strict';
import worker from '../src/index.mjs';

const origin = 'https://osv.deckers.gy';
const instance = '23fcb44f-ce9b-4dbf-b19b-aec7a90d418c';
const token = 'a'.repeat(43);
const endpoint = origin + '/api?instance=' + instance;

test('serves the protected entry page without proxying another hostname', async () => {
  const env = { ASSETS: { fetch: () => new Response('entry') } };
  const page = await worker.fetch(new Request(origin), env);
  assert.equal(await page.text(), 'entry');
  assert.match(page.headers.get('Content-Security-Policy'), /frame-ancestors 'none'/);
  assert.equal((await worker.fetch(new Request('https://unrelated.example/'), env)).status, 404);
});

test('proxies only the permit operation and preserves the store-assigned number', async () => {
  const request = new Request(endpoint, {
    method: 'POST', headers: { Origin: origin, 'Content-Type': 'application/json' },
    body: JSON.stringify({ action: 'SUBMIT', token, portal: 'unrelated', payload: { values: {} } }),
  });
  const result = await worker.fetch(request, { STOREFRONT: { fetch(url, options) {
    assert.equal(url.toString(), 'https://www.deckers.gy/shop/template-submit/api?instance=' + instance);
    assert.equal(options.headers.Origin, 'https://www.deckers.gy');
    assert.equal(JSON.parse(options.body).portal, 'OREALLA');
    return Response.json({ permitNumber: 'OSV-0001' });
  } } });
  assert.equal(result.headers.get('Cache-Control'), 'no-store');
  assert.deepEqual(await result.json(), { permitNumber: 'OSV-0001' });
});

test('rejects cross-origin posts, staff operations, and oversized uploads before the store binding', async () => {
  const post = (value, caller = origin) => new Request(endpoint, {
    method: 'POST', headers: { Origin: caller, 'Content-Type': 'application/json' }, body: JSON.stringify(value),
  });
  const denied = { STOREFRONT: { fetch() { assert.fail('Rejected requests cannot reach the storefront'); } } };
  assert.equal((await worker.fetch(post({ action: 'INFO', token }, 'https://unrelated.example'), denied)).status, 403);
  assert.equal((await worker.fetch(post({ action: 'QUEUE_LIST', token }), denied)).status, 400);
  assert.equal((await worker.fetch(post({ action: 'SUBMIT', token, photo: 'a'.repeat(3 * 1024 * 1024) }), denied)).status, 413);
});
