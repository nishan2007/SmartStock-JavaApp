# Deckers storefront testing handoff

This is a development test candidate. Do not install it over the running store
application or point it at a live database. Production enablement remains blocked
by the open items in `storefront-readiness.md`.

September 26: editable frontend source now lives in `website`. Company branding
comes from existing Company Preferences and selected-store contact fields; the
synthetic preview uses labeled sample preferences and a bundled sample logo.
Desktop and 390px mobile branding were visually checked. Full Maven tests,
including the isolated two-store rehearsal, frontend build/tests and the security
check passed after this change. The September 25 installer below is older than
these branding changes and must be rebuilt before testing this version installed.

## Local visual and shopping rehearsal

From `SmartStock/website`, run:

```powershell
pnpm build
node test/preview-server.mjs
```

Open `http://127.0.0.1:5180/shop/`. This binds only to loopback, serves the compiled
website and uses synthetic products, a synthetic customer, and in-memory orders.
It never sends email, takes payment, or writes SmartStock records. Restarting the
preview clears orders; browser-local bags persist. Stop it with Ctrl+C.

Test on desktop and a narrow mobile viewport:

1. Browse home and products; search, filter categories, and open product details.
2. Add items, change quantities, remove items, and check the empty bag state.
3. Switch pickup stores and verify each store retains its own bag.
4. Review the total, confirm pickup, and check confirmation and order history.
5. Inspect the sample receipt and balances; refresh and use browser Back.
6. Repeat navigation using the keyboard, including product dialogs and checkout.

The preview deliberately does not simulate successful signup, verification or
password recovery. Real authentication must be tested against staging Auth.
It also does not prove inventory reservations, discounts, tax configuration,
server failover, staff payment, email, or production product photography.

## Source checks

### Bulk publication and product lifecycle

The Products tab includes **Publish all active products** for the selected store.
It publishes currently active inventory products, preserves descriptions and
featured selections, and excludes archived products and services. It does not
automatically publish products created later. Company Preferences permission is
enforced on the server. Archive and restore both clear publication and featured
flags for the product at every store; restoring requires explicit publication.
Publication locks product rows so an overlapping archive cannot leave the item
published. Public catalog snapshots also exclude inactive products. Offline
servers can retain an older snapshot until synchronization resumes.

The isolated database rehearsal covers bulk retries, permissions, exclusions,
preserved presentation, archive unpublication and explicit publication after
restore. Verify the new Swing button in an updated test installation before
release; source changes do not update an already-installed application.

From the repository root:

```powershell
mvn -q -f SmartStock/pom.xml test
node --test SmartStock/website/test/*.test.mjs
node --test SmartStock/cloudflare/smartstock-storefront/test/*.test.mjs
git diff --check
```

Run `SmartStock/tools/security-check.sh` through Git Bash. The two-database test
is opt-in and requires the disposable loopback PostgreSQL cluster on port 55439:

```powershell
mvn -q -f SmartStock/pom.xml '-Dtest=StorefrontDatabaseIntegrationTest' '-Dstorefront.test.admin=jdbc:postgresql://127.0.0.1:55439/postgres' test
```

It creates random `smartstock_dev_sf_a_*` and `smartstock_dev_sf_b_*` databases,
then drops only those databases. It uses the isolated `storefront_test` role and
does not read the installed application's database credentials. Check the
Surefire report for zero skipped tests; ordinary Maven runs skip this rehearsal.

## Staging integration

Prepare these before testing the full integration:

| Component | Required isolation | Evidence before proceeding |
| --- | --- | --- |
| Two Windows test machines or VMs | Separate installations and PostgreSQL databases | Test store names and distinct registered server-instance UUIDs |
| Supabase project | Staging Auth and server registry, synthetic customers only | Test verification/recovery email reaches a controlled recipient |
| Cloudflare | Separate Worker, Durable Objects namespace, hostname and two named tunnels | Each origin identifies its configured test instance |
| Origin secrets | One random secret per test instance; shared staging session-encryption key | Wrong instance/secret requests are rejected |
| Email sender | Test sender and recipient mailboxes | Confirmation and READY notifications arrive |
| Product images | Staging provider configuration and synthetic publication records | Images render from each server and its verified cache |

Do not configure a second installation on the production machine as a shortcut:
the application uses machine/profile settings, server registration and service
resources that need independent isolation. A different browser port alone does
not isolate the Java application or its database.

Use two separate test installations, independent databases, staging Supabase Auth,
test email recipients, separate named tunnels, and a separate Worker/DO namespace.
Follow `storefront-deployment.md`, including per-instance origin credentials.
Do not copy production secrets or production customer data into test fixtures.

Test verified activation, ambiguous contacts, recovery, cross-customer denial,
price changes, customer discounts, READY expiry, cancellation, collection, and
exactly one sale. Disconnect the selected store and verify backup acceptance
keeps the pickup store unchanged. Restore it and verify reconciliation and any
shortage flags. Retry after a lost checkout response and verify one order.

Record expected versus observed behavior, browser/device, store and instance IDs,
order IDs, timestamps, and sanitized logs. Do not attach tokens, passwords, or
customer records to a bug report. Installed service restarts, real email delivery,
cellular access, receipt printing and cash-drawer behavior require separate checks.

## Known limits to target during testing

### Local browser rehearsal, 2026-09-25

Verified the compiled preview at 390px mobile and 1440px desktop widths. A
synthetic cotton-tee order reviewed at $2,400 plus $336 tax, confirmed for the
selected preview store at $2,736, cleared the bag, and appeared in account
history. Keyboard checkout, desktop product navigation, mobile bag/account
pointer navigation, and the empty-bag state were exercised. The freshness notice
and account balances rendered at both widths. This is preview evidence only;
authentication, pricing rules and persistence still require staging validation.

The Windows test candidate is under
`target/storefront-test-candidate-0008dc1af7a54be4895cb98539982197`.
Its `verification.json` records artifact sizes, SHA-256 hashes and packaged
frontend/schema comparisons. The installer was built, but was not executed.
The candidate includes concurrent workspace changes and is for isolated testing.

### Outstanding acceptance

- Automated generation/lease validation and replacement-server recovery remain
  incomplete; do not promote this candidate for unattended production failover.
- Large-catalog/customer synchronization and journal/history pagination remain
  incomplete. Start with a small synthetic catalog, then run explicit volume tests.
- Complete browser acceptance and isolated Swing verification must cover all
  staff controls and error paths; the preview rehearsal above is a smoke test.
- Real cloud image providers, email and Auth delivery need staging credentials.
- A final installer built from the exact accepted source still needs isolated
  installation verification before rollout.
