# Deckers storefront deployment and acceptance

## Current boundary

This feature is developed in the source checkout. Do not update the running
SmartStock installation, Windows services, live database, cloud Auth settings,
DNS, or Cloudflare configuration during development verification.

The frontend compiles to `src/storefront-web` and is packaged by Maven. Installed
servers do not need Node. The dedicated listener binds only to loopback HTTPS
port 8449. The public register API on 8443 must never be tunneled.

## Build and isolated verification

From `SmartStock/website`, run `pnpm install --frozen-lockfile`, `pnpm build`,
and `pnpm test`. The generated frontend is versioned with the Java sources.
The build writes `src/storefront-web/bundle-manifest.json` with SHA-256 hashes
of its source inputs and compiled assets. Maven's `StorefrontBundleTest` rejects
missing, extra, changed, or stale files and tells the developer to rebuild.
Source line endings are normalized so Windows and macOS checkouts agree.
Run `node --test SmartStock/cloudflare/smartstock-storefront/test/*.test.mjs`
from the repository root, followed by the normal Maven/security/diff gates.

For a synthetic browser preview, run `node test/preview-server.mjs` from
`SmartStock/website`, then open `http://127.0.0.1:5180/shop/`. This server
uses in-memory sample products and a fake signed-in customer. It never sends
email, talks to Supabase, or writes to a store database. Never deploy it.

The opt-in `StorefrontDatabaseIntegrationTest` requires a dedicated loopback
PostgreSQL cluster on port 55439 with the test-only `storefront_test` role.
Invoke Maven with `-Dstorefront.test.admin=jdbc:postgresql://127.0.0.1:55439/postgres`.
It creates two randomly named `smartstock_dev_sf_*` databases, installs the
canonical schema, exercises handoff, replay, deadlines and shortages, and drops
only those exact test databases. It does not load saved SmartStock credentials.

## Future production configuration

Configure these values in the store service environment, outside the repository:

| Variable | Purpose |
| --- | --- |
| `SMARTSTOCK_STOREFRONT_ORIGIN` | Exact public HTTPS origin, without a path |
| `SMARTSTOCK_STOREFRONT_EDGE_KEY` | Unique secret for this server instance, at least 32 random characters |
| `SMARTSTOCK_STOREFRONT_SESSION_KEY` | 32 random bytes encoded as Base64; same value on participating servers |
| `SMARTSTOCK_STOREFRONT_BROWSE_ONLY` | Defaults to browse-only; only the explicit value `false` permits customer API routes after separate transaction acceptance |

Windows builds can also read the existing CurrentUser DPAPI-protected website
settings at `.smartstock/storefront-tunnel/website-config.dpapi` when no origin
environment variable is set. That compatibility path keeps the settings out of
the scheduled-task command line and survives an updater replacing the former
launcher wrapper. A failed decrypt leaves the website listener unavailable
while the LAN server continues. The task must run under the same Windows user
that protected the settings; verify 8449 after each installed update.

Both the origin environment variable and protected settings file must be absent
to keep the public listener disabled. The new local `storefront` schema is
additive and installed separately from the established
`public` schema fingerprint. Its canonical script is
`database/storefront/001_schema.sql`; the matching dated migration is provided
for controlled upgrades. Full database backups must include this schema.
The installer recognizes schema versions 1 and 2; version 1 upgrades with
`database/storefront/002_enrollment.sql`, preserving existing orders.

The Cloudflare Worker uses `ORIGIN_KEYS_JSON` as a secret JSON object mapping each
registered server-instance UUID to that instance's unique edge secret. Do not reuse
one instance's secret on another server. `ORIGINS_JSON` is a list of
`{"storeId":1,"instanceId":"REGISTERED-SERVER-UUID","url":"https://store-origin.example.com"}` entries. Each origin
must lead through its own named Tunnel to that store's loopback listener.
The listener reads its identity from SmartStock's existing server registry; it
cannot start without registration. Both sides check the instance ID, and sync is
authorized with the secret belonging to the claimed store and instance. The older
shared Worker `EDGE_KEY` configuration is no longer accepted.
Configure certificate validation using the store's trusted TLS identity; do not
disable TLS verification. Public traffic uses `/shop/`; synchronization uses
the authenticated `/shop/internal/sync` endpoint.

Interrupted checkouts are recovered by a Durable Object alarm, independently
of the shopper's browser. The coordinator asks every attempted origin for a
durable resolution through `/shop/internal/resolve-command`. A server returns
an accepted order or writes a rejection under the same transaction lock as
checkout. That rejection prevents delayed HTTP requests from creating an order
after the gateway has certified non-acceptance. Preserve `rejected_commands`
in backups; do not clear it as a cache. If an attempted origin is unreachable,
new checkout remains temporarily unavailable until acceptance or durable
non-acceptance is established. Public requests cannot proxy internal routes.
An uncertain submission is not retried against a second server: its first server
may already have committed. This differs from ordinary pre-submission failover,
where an unavailable primary is excluded and a healthy backup is selected.
See [Cloudflare alarms](https://developers.cloudflare.com/durable-objects/api/alarms/)
for the retry and scheduling contract.

Customers use the existing Supabase project for Auth only. Configure branded
email OTP and recovery templates that show the verification code. Verify actual
delivery and reset/session-revocation behavior in an isolated staging Auth
project before enabling production. Do not put server/service-role keys in the
browser, register configuration, or frontend bundle.
New Free projects using default Supabase SMTP may not allow custom OTP templates;
configure staging/production SMTP as required by the
[email-template change](https://supabase.com/changelog/46599-changes-to-email-template-customisation-on-free-tier).

Verified login durably enrolls the account at its selected store before returning
a session. The selected store creates or links the customer immediately. A backup
records a pending enrollment and hands it to the selected store on reconnection;
it does not create a customer in another store's authoritative ledger. Duplicate
contacts enter Customer access → Resolve selected customer. Staff choose a
matching record after server-side Auth verification; records, balances, and past
sales remain separate. Website account history shows store names and snapshot
times alongside balances and itemized receipts.

In SmartStock Company Preferences → Online Store, configure currency and pickup
expiry, then explicitly publish selected active inventory products. Prices and
taxes come from the store. Product photography comes from existing image assets.
Start with ordering disabled until the end-to-end acceptance checks pass.

## Acceptance before enabling orders

- Verify fresh install and upgrade with a restorable database backup including
  `storefront`; rehearse rollback without overwriting new business transactions.
- Verify two installed test servers, permanent tunnels, restart behavior, and
  the public website over cellular data.
- Verify activation, ambiguous customer matches, inactive customers, password
  recovery, logout, revocation, and denial of another customer's orders.
- Verify publication and withdrawal, product image retrieval, variants, stock,
  price changes, tax, discounts, and rounding against the register.
- Verify collection creates one sale and one inventory deduction, including
  retry after a lost response, cash drawer restrictions, and receipt handling.
- Disconnect the fulfillment server, accept an order at a backup, reconnect,
  and verify exact-once handoff and staff handling of any stock shortage.
- Interrupt submission and close the shopper's browser. Verify background
  resolution, including delayed requests after a negative acknowledgement,
  and a fresh checkout after all attempted origins reject the interrupted one.
- Verify expiry begins at READY, uses the saved deadline, and releases stock;
  verify email delivery and retry visibility.
- Verify desktop and narrow mobile layouts with real product photography and
  keyboard-only navigation.

Automated checks and synthetic previews are not evidence of production email,
installed-service, tunnel, receipt-printer, or cash-drawer acceptance. Deployment
and those live checks remain separate from source implementation.

Storefront version 2 validates its PostgreSQL catalog against the canonical
PostgreSQL 17 schema before enabling the public listener. Missing/changed columns,
constraints or indexes, altered precision, and PUBLIC schema access are rejected.
Do not repair drift by changing only the recorded schema version. Restore the
expected schema or apply a reviewed migration, then repeat the isolated checks.
Other PostgreSQL major versions require catalog compatibility verification before
deployment; their catalog formatting may differ.

Company backups include the public and storefront schemas together. Restore them
only to an isolated compatible SmartStock database during rehearsal; verify locked
orders, reservations, customer links, rejected-checkout fences, sale references,
and a new audit entry after restore. Cloud replacement recovery is a separate path
and must not be assumed equivalent to a company backup restore.

Configure the store sender in its existing email settings and verify Gmail sender
authorization before launch. If the address is missing, storefront notifications
remain in Email Outbox as FAILED with `STOREFRONT_SENDER_REQUIRED`; they do not
consume attempts and resume automatically after the address is configured. Other
delivery failures use the existing outbox retry workflow. The online-store staff
screen displays an email-attention message when failed notifications exist.

Sales staff can open **Point of Sale → Online pickup orders** from the main menu
with `MAKE_SALE` permission. This fulfillment view does not expose merchandising,
company settings, customer-link records, or pending account activations. Those
administrative workflows remain in Company Preferences. Validate preparation,
payment, and receipt handling using a restricted pickup-clerk account in staging.

Published product snapshots carry private image manifests. Every eligible backup
needs access to the configured product-image provider; credentials remain server
configuration and are never included in snapshots. Replica images are cached by
SHA-256 under the configured image store's `storefront` directory. They do not
modify the backup's authoritative image registry. A previously uncached image
still needs cloud access. Rehearse primary-store failure and provider failure
separately, including legacy URLs and OneDrive-backed products. Missing image
manifests or checksums require image synchronization before backup delivery works.
Storefront images must be at most 12 MiB. Larger files are rejected during local
reads or streaming downloads; optimize product photographs before publishing them.
Replica images are prefetched in rotating batches of four on a separate worker
every 15 seconds after the previous batch completes. Order handoff does not wait
for image downloads. The disposable replica cache has a 512 MiB budget; older
copies are evicted as new images arrive. A cached, verified image can be served
without cloud access, but an evicted or not-yet-prefetched image cannot. Include
the actual published catalog size and backup disk capacity in outage acceptance.
