# Browse-only launch review — 2026-09-28

**Status: browse-only gateway and Windows update published; public site incomplete.**
On 2026-09-28, the user authorized packaging and publication without further
copy edits. The production Worker was deployed with `BROWSE_ONLY=true` to
`www.deckers.gy` and `deckers.gy` (version
`e320539d-a6f7-4b93-8ec0-e9c350a0a474`). All eight customer route probes
returned the exact browse-only rejection. The live Skeldon ordering setting
was changed from `true` to `false`; a read-back found zero enabled stores.
The verified Windows 1.0.222 ZIP was uploaded to R2, downloaded again, and
matched at 296,442,033 bytes and SHA-256
`d7ef5f7dba41f2ca13f103e0af4f226136082b0fa71e0bb650b0c71ac52f82f8`.
Only after that check was build 100222 published in Supabase. The local Windows
installer and server task still run 1.0.221: this session lacks administrator
rights for the installed server upgrade. The public home and Skeldon catalog
still return HTTP 503 because loopback listener 8449 is absent. The public
site has **not** passed launch acceptance and should not be announced as live.

## Proposed public surface

The `www.deckers.gy/shop/` website would show the Deckers branding, store selector,
published products with public prices and availability, product detail pages,
published Made at Deckers projects, services, business and about pages, search,
and store contact information. The apex hostname would redirect to `www`.
Only items and projects explicitly published for the selected store enter its
public catalog. Product and project images and editorial concept illustrations
would be public. The current local artwork includes illustrative 3D printing
and apparel images; it is not evidence of completed customer work.

The production Worker configuration now declares `BROWSE_ONLY=true`. The gateway
also defaults to browse-only if that setting is missing or invalid; a later
transaction rollout requires the explicit value `false`. In browse-only mode,
the gateway rejects every `/shop/api/v1/` customer route except `stores`
and `catalog`, including authentication, account, favorites, custom requests,
artwork uploads, proofs, quotes, and checkout. The catalog response marks
`browseOnly=true` and sets `canOrder=false`; the website shows browse messaging
and hides account, bag, favorite, and quote submission controls. Store ordering
must also remain disabled in SmartStock. Removing the Worker flag alone must
not be treated as approval to take orders.

Both reviewed public catalog layers also report `settings.enabled=false` in
browse-only mode, even if a stale internal snapshot still has ordering enabled.
This public projection does not change the live SmartStock setting; the local
production audit will continue to fail until that setting is disabled there.

The reviewed Java origin also defaults to browse-only. It rejects customer API
routes even when reached through a trusted gateway and marks catalog products
non-orderable. Enabling transactions later requires an explicit `false` setting
at both gateway and origin, plus the separate SmartStock ordering control.
The reviewed background sync uses a public-field allowlist in browse-only
mode. It sends no customer, account-link, order, receipt, event, or enrollment
records to the gateway, and it does not expire orders. The gateway rejects
private sync fields and nonempty events/enrollments while browse-only, stores
only public snapshot fields, and returns no persisted order/enrollment records
to the origin. Existing private coordinator records, if any, are not deleted
by this change; review their retention before enabling transactions.
The origin also rejects internal delayed-order resolution while browse-only.
The gateway pauses pending checkout reconciliation without contacting origins
and schedules a later check; it resumes only when its transaction setting is
explicitly `false`. These guards leave any old pending jobs unresolved during
the browse-only period, so inspect the coordinator before a transaction rollout.

## Read-only production checks

The Cloudflare Worker currently deployed at the public domains is still the
2026-09-27 version; its bindings include one Skeldon origin and an
`ORIGIN_KEYS_JSON` secret, but no `BROWSE_ONLY` binding. The deployed origin's
instance ID matches this server's protected registry identity. An ignored local
review configuration in `target/storefront-production-review.jsonc` combines
that verified non-secret origin mapping with the proposed `BROWSE_ONLY=true`
Worker configuration. Its production preflight passed and Wrangler completed a
dry run against both intended domains. The resulting local `index.js` is
28,234 bytes, SHA-256
`b3691d09186c7fd60f687c92b17ae2480b8a577c550137ffeb4467e861bee283`.
This is a deployment candidate only; the public Worker was not changed. The
checked-in production config deliberately retains `ORIGINS_JSON=[]`, so its
guarded deploy command still fails until the reviewed local mapping is used.
The guarded `pnpm run deploy:production <review-config>` command now checks
and deploys the same config file. It also checks Cloudflare's secret list for
the existing `ORIGIN_KEYS_JSON` secret binding without reading its value.
Its default empty config failed closed, and
its explicit local review config completed a dry run with the identical
28,234-byte Worker bundle and SHA-256 above. It has not been run without
`--dry-run`.

| Check | Result |
| --- | --- |
| `https://www.deckers.gy/shop/` | HTTP 503 |
| `https://deckers.gy/shop/` | HTTP 503 |
| Public `stores` API, same-origin POST | HTTP 200; one store, Skeldon, Lot 1 & 2 #81; snapshot captured 2026-09-28 14:13:10 UTC |
| Public Skeldon `catalog` API, same-origin POST | HTTP 503 |
| Windows background server task | Running |
| Dedicated storefront tunnel task | Ready and logon-triggered; two `cloudflared` processes with storefront arguments are running |
| Loopback HTTPS 8449 | No listener found |

A later read-only check found the register (8443), scheduler (8446), and careers
(8448) listeners active. The running server process started at 2026-09-28
10:13 local time. Its scheduled-task action now launches `javaw.exe` directly
for `--sync-service`, without the storefront settings wrapper described in
`storefront-activation-2026-09-27.md`. No storefront startup failure was logged.
This strongly points to the installed task action being replaced by the later
installation, leaving the website origin unset for the current process. Process
environment values were not read or exposed. The tunnel task is logon-triggered
and Ready; that state is compatible with its launcher exiting after starting
background connector processes. Two storefront connector processes were found,
but their end-to-end health is unverified while 8449 is absent. The reviewed
source now reads the existing user-protected website settings directly at server
startup, so a later task-action replacement should no longer silently disable
the listener. A disposable DPAPI round-trip test passed. An explicit read-only
test also decrypted the existing protected file and validated the public origin
and key lengths without printing their values; the task user and test user SIDs
match. The installed service still contains the older code. Restoring it before activating
the new browse-only gateway would expose its older public customer routes, so
the gateway gate must be deployed and verified first.

An explicit read-only connection to the local production PostgreSQL server
found **storefront schema version 2**. The reviewed origin code expects later
website price, project, campaign, and service availability migrations; those
tables/columns are absent here. This database cannot serve the reviewed catalog
as built. No migration was applied during this audit.

The local production rows propose exactly one published product at Skeldon:
product 168, **0.5 Leads**, SKU `05L-0001`, GYD 100.00, 184 units in the
queried inventory row, not featured, with an image reference. The public
description is `2B` followed by `St-LD05-2B` on the next line. The product
category appears publicly as `Stationary`; staff should confirm or correct
that spelling along with the other product copy. The image
registry marks it as an active PRODUCT JPEG with local and cloud copies; the
read-only audit verified the local file's SHA-256 against the registry.

The 22:52 UTC read-only repeat confirmed the same live product ID, SKU,
category, size, public price, description, stock row, and image checksum as
the saved catalog candidate. It also confirmed schema version 2 and Skeldon's
ordering setting still enabled. The backup-derived candidate has not drifted
in those observed fields, but its snapshot is not a live public response.
Staff must approve that wording, price, publication, and the actual image before
launch. The reviewed photo shows three 0.5 mm 2B lead packages small within a
large, uneven paper backdrop. A tighter, cleaner product image is recommended
for the premium presentation before publication; the current review copy is
`target/storefront-content-review/store-1-product-168.jpg` (ignored local
output). The public image route and calculated availability remain unverified.
After origin recovery, test `/shop/image?storeId=1&id=168` from an outside
network and compare its bytes with the reviewed image before approving it.
No project or service availability table exists at schema version 2, so the absence of
rows cannot be interpreted as an editorial decision. The local setting
`storefront.settings.enabled` is **true** for Skeldon; it must be set to false
and verified before public exposure, with the Worker and origin browse-only
guards retained. The old installed origin should not be made reachable first.

The proposed public identity is company name **DECKERS**, store **Skeldon**,
address **Lot 1 & 2 #81**, with the longer address **Skeldon, Corriverton,
Berbice, Guyana**. The two public email fields are `deckershcn@gmail.com` and
`deckershcn@yahoo.com`; both phone fields are empty. The motto is split as
`"Sales goes up and down` / `Service is forever"`, so staff should approve or
correct the quotation marks and confirm the intended public contact method.
The active COMPANY_LOGO PNG and its local SHA-256 were verified. Its review copy
is `target/storefront-content-review/company-logo.png`; the current multicolor
legacy mark should be reviewed against the premium site design before launch.

A private loopback browser preview used the rebuilt site, the backup-derived
catalog JSON above, and the reviewed product/logo image bytes. Its home page
showed the one **0.5 Leads** card at GYD 100 with no add-to-bag or favorite
control. The direct product page showed the photo, `Stationary` category,
`2B` / `St-LD05-2B` description, `In Stock`, and store-contact purchase
guidance. Direct `#login` and `#checkout` URLs resolved to the browse home
without showing those flows. This is a local content preview, not evidence of
live origin or gateway behavior.

That preview also exposed 14 service links: 3D Printing, Custom Printing,
Custom Apparel, Embroidery, Signs & Banners, Business Printing, Personalized
Gifts, Business Cards, Banner Printing, Custom T-Shirts, Laser Engraving,
Stationery, Large Format Printing, and Signs. The backup catalog has an empty
`unavailableServices` list because production schema version 2 has no service
availability table. The empty list is **not** evidence that every service is
offered at Skeldon. Staff must approve the capability list and store-specific
availability before any public launch; the current preview cannot pass that
content gate.

The public site cannot be called ready while its page and catalog fail. These
local rows describe a candidate inventory, not a verified public response.

The production company-backup scheduler is enabled at a 1,440-minute interval.
Ten `.ssbackup` files are retained. The newest, created 2026-09-28 16:10 UTC,
is 358,992,618 bytes with SHA-256
`73dbde657e6c9073b50aa4fc7814e87e8c23eb8e017e0e7729ef37339aee822d`.
Its ZIP structure contains one `data.sql`, one `assets.tsv`, and 1,466 asset
entries; reading all 1,468 entries passed CRC validation (501,978,508
uncompressed bytes). This proves archive integrity, **not** that a restore
works. A SQL-only restore rehearsal has now passed on the isolated cluster:
the backup `data.sql` applied to a newly created disposable database, and
product 168, Skeldon's enabled setting, and storefront schema version 2 were
read back. The disposable database was removed afterward. This verifies the
database portion of this backup. A later, faithful upgrade rehearsal also
passed: a read-only schema-only copy of the live PostgreSQL 17 database was
installed into a disposable database, the backup's `data.sql` restored its
rows, and `StorefrontSchema.ensure` advanced storefront schema version 2 to
15 with catalog validation. Product 168 and Skeldon's original enabled setting
were checked before migration. The test database was removed afterward.
The same rehearsal then generated the public catalog through
`StorefrontService.snapshot` and `catalog` on that disposable database. It
contained exactly one published product: ID 168, **0.5 Leads**, price GYD
100.00. It contained zero published projects. Customer and receipt records
were absent from the public projection. Applying the origin browse-only
projection set `browseOnly=true`, `settings.enabled=false`, and
`product.canOrder=false`. The test passed with no failures, and a direct check
confirmed no disposable restore database remained. This proves the reviewed
code's catalog projection on backed-up rows; the public HTTP route still
returns 503 and cannot yet be accepted as live behavior.
An ignored JSON export of that projection is available at
`target/storefront-content-review/browse-only-catalog-candidate.json` for
exact field-by-field review. It has one product, no projects, and no customer,
receipt, session, or secret fields. Both the Java origin and Worker now replace
the 3D campaign's old upload claim with store-contact guidance in browse-only
catalog responses. The restored-data export confirms that corrected wording.
The schema-only copy is ignored local build output, not a release artifact.
No production schema or data was changed. Package asset restoration remains
unverified before the live upgrade.
The backup asset verifier also read all 1,466 packaged assets and confirmed
each SHA-256 against `assets.tsv`. The two current public image references
(product 168 and the company logo) match packaged asset targets and their
live image-registry SHA-256 values. This proves their bytes are recoverable
from this archive; it does not test a cloud upload or image serving after an
installed upgrade.
The isolated PostgreSQL test cluster remains available on loopback port 55439,
with a dedicated `storefront_test` role. The application's normal package
restore routine also uploads packaged assets to configured cloud storage, so it
was not used for the database-only rehearsal. The opt-in
`StorefrontBackupRestoreRehearsalTest` extracted only `data.sql`, restored it
into a newly created disposable database, and checked the relevant rows
without transmitting assets.

Run `SmartStock/tools/check-storefront-public-content.ps1` from the repository root for a
read-only JSON inventory of every public store's published products, projects,
campaign, and service availability. It exits unsuccessfully when the home or a
catalog is unavailable, the browse-only flag is absent, or any product remains
orderable. Its 2026-09-28 15:29 UTC run found Skeldon in the store directory but
returned HTTP 503 for both the home page and Skeldon catalog; the empty product
and project arrays in that report mean **unknown**, not zero published content.
The 16:06 UTC, 18:43 UTC, 19:08 UTC, 22:49 UTC, and 23:02 UTC repeats produced the same 503 results; the latter
also confirmed no local 8449 listener despite two running tunnel connectors.
The public checker now sends harmless GET probes to eight representative
customer routes and requires the exact browse-only gateway rejection, not
merely an HTTP 503. On the still-deployed Worker, all eight returned 503 for
another reason and failed that gate. This is direct evidence that the current
gateway cannot be accepted as browse-only, even while the origin is down.
The 23:02 UTC repeat again found zero of eight routes blocked by the exact
browse-only gateway response. Its saved JSON report is
`target/storefront-content-review/latest-public-check.json`.
Run this route check immediately after deploying the reviewed Worker and
before making the old origin reachable.
The read-only local database inventory was produced with
`SmartStock/tools/PublicStorefrontContentAudit.java`
using an explicit production profile and a read-only transaction; it did not
invoke the mutating storefront snapshot routine.
That audit now exits with a failing status when the local schema is below 15,
any store has ordering enabled, or required public-content tables are absent.
Its current exit status is 2 for those three launch gates.

## Checks required before the browse-only switch

1. Prepare the reviewed installed SmartStock package and Worker bundle, retaining
   the existing production origin mapping and secret outside the repository.
   Deploy the Worker with `BROWSE_ONLY=true` **before** restoring a reachable
   origin. Confirm customer API routes return 503 at the public gateway.
   Use the guarded `pnpm run deploy:production <review-config>` command from the Worker folder;
   its preflight rejects the current empty origin mapping, missing browse-only
   flag, unexpected domains, and origin keys placed in public variables.
2. Migrate the local production storefront schema through the reviewed version,
   verify the migration and backup, disable Skeldon online ordering in the live
   database, and have staff approve the one published product and its image.
   Install the separately versioned reviewed server package and verify that it
   reads the protected settings under the correct Windows user. Check the
   existing tunnel connectors and loopback website listener, then verify the
   public home page, store list, selected-store catalog, images, clean product
   and project links, service pages, sitemap, and apex redirect from an outside
   network. Recheck unattended startup after reboot or service update.
3. Review the live catalog record by record: publication, product names and
   descriptions, public prices and sale windows, photos, variant names,
   availability, selected service locations, campaign actions, project consent,
   captions, contact details, and claims made by concept images. Record the
   exact approved inventory and publication timestamps.
4. Verify the installed build and gateway version on the public domain. Confirm
   all customer API routes reject requests and that the public pages have no
   working account, order, or request flow.
5. Check desktop/mobile, keyboard, image fallback, search, direct links, and
   basic accessibility using the real catalog over cellular data. Watch the
   origin and Worker for errors after launch.

## Separate gate before transactions

Keep `BROWSE_ONLY=true` and SmartStock ordering disabled until the acceptance
list in `storefront-deployment.md` is complete: restorable backups and upgrade,
two installed servers with stable tunnels and failover, Auth/email enrollment
and recovery, permission and identity boundaries, accurate stock/prices/tax,
one-time checkout and collection, receipts, print/drawer workflows, expiry and
interruption recovery, and real desktop/mobile and installed-app checks.
Only then schedule a separately reviewed customer-flow rollout and remove the
browse-only gate deliberately. Live printer, cash drawer, NFC, service, database,
and installed-application behavior remain unverified by source tests.

## Local verification for this preparation

- Website TypeScript production build and 38 tests passed.
- The browse-only 3D hero now avoids promising online model upload or current
  service availability; it directs visitors to ask their store. The website
  build and all 38 tests passed again after this copy change.
- Synthetic browse-only preview checked in a browser: home and 3D printing pages
  showed store-contact guidance, with no account, bag, favorite, or upload form.
- The current browse-only preview was rechecked after the UI changes: the header
  had no account or bag control, product cards had no favorite or add-to-bag
  control, and a quick view showed store-contact guidance. The 3D printing hero
  image rendered after the preview server's static image routes were corrected.
- A later local browser check confirmed the revised 3D hero copy, no account
  or bag controls, and no model-upload form on the 3D page. It also exposed a
  duplicate top-level heading there; the generic follow-on section is now a
  second-level heading. The browser accessibility tree confirmed one H1 after
  rebuilding the site. This remains a synthetic preview, not a public test.
- Gateway 33 tests passed, including fail-closed rejection when the browse-only
  setting is absent, removal of ordering eligibility from a public catalog,
  rejection of customer-specific background sync data, and paused checkout
  reconciliation.
- The production deployment preflight was run and correctly blocked the current
  `ORIGINS_JSON=[]` placeholder. No public Worker deployment was attempted.
- The production Worker configuration compiled in a local Wrangler dry run.
  The resulting `index.js` is 26,645 bytes, SHA-256
  `b320689428cc1d94243d87518f0b2d5f0b826e1da9d9fe72538bb940f057338a`.
  The dry run reported `BROWSE_ONLY=true`; it did not deploy anything. Its
  `ORIGINS_JSON=[]` remains a placeholder and must be replaced with the verified
  registered origin mapping, with secrets supplied outside the repository.
- Maven test suite passed. `git diff --check` passed (line-ending warnings only).
- The repository security check passed under Git Bash after the revised Windows
  candidate was built. The 23:02 UTC public content check still failed with
  HTTP 503 for home and catalog and no local 8449 listener.
- The opt-in two-store PostgreSQL integration test passed on the disposable
  loopback cluster (1 test, 0 skipped, 0 failures). It exercises migration from
  older storefront versions, including version 2, to version 15 and catalog
  validation. This is a rehearsal; the live database has not been migrated.
- The opt-in restore and upgrade rehearsal of the latest company backup passed
  on the isolated cluster (1 test, 0 skipped, 0 failures). A schema-only,
  read-only production copy supplied the version 2 starting structure; backup
  SQL restored product 168 and Skeldon's setting; the storefront migration
  reached version 15. No cloud asset upload or production write was made; the
  disposable database was removed.
- The archive asset verifier passed for all 1,466 entries and matched both
  current public image references to backup bytes and live registry checksums.
  It made no cloud upload.
- No installed-package or public browser acceptance test passed; the origin is down.

## Isolated Windows packaging rehearsal

The current local candidate is in `target/browse-only-candidate-order-guard-1.0.222`.
Version 1.0.221 is already a published catalogue studio updater, so this
candidate uses 1.0.222. It was **not installed or published**. It includes the
other working-tree SmartStock changes present during packaging, including the
catalogue studio work; review that scope before any production update.
The two installed 1.0.221 JAR copies (the running task's app directory and
`C:\Program Files\SmartStock\app`) are byte-identical, SHA-256
`429c06aabc2859bdd9f77f811fdbed34d50976af40aeac463c8d47a073ccf067`.
The 1.0.222 candidate JAR is SHA-256
`69437ab55d9d729cc4c3db06f13e75b6cd376e424817ce3e578dd96e16db67d0`.
Compared with the installed JAR's file inventory, the candidate adds 54
entries, removes 23 (mostly obsolete hashed website assets), and changes the
bytes of 288 shared entries. The additions include 10 application migrations,
13 storefront schema scripts, catalogue studio, and storefront services. The
changed entries include 219 service files and 61 UI files. These are archive
entry comparisons, not a count of distinct source changes; they establish that
installing this candidate updates more than the public website. Its full
application scope still needs review before production installation.

| Artifact | Bytes | Independently checked SHA-256 |
| --- | ---: | --- |
| `smartstock-windows-1.0.222.zip` | 296,442,033 | `d7ef5f7dba41f2ca13f103e0af4f226136082b0fa71e0bb650b0c71ac52f82f8` |
| `smartstock-windows-setup-1.0.222.exe` | 307,732,430 | `5c83d17a3f4e20e1a7e68a6d8081681591911f010137b704ca1f43d1c37173b1` |

The first packaging attempt retained 35 obsolete hashed website assets in
Maven's output directory. The build now removes only that generated website
copy before resource packaging, and the Maven bundle test checks the packaged
copy against the current source bundle. The 1.0.222 ZIP's embedded JAR hash
matches the local build JAR, its manifest reports 1.0.222, and all eight
packaged website files match the current source files by SHA-256 with no extra
website files. The installer was created successfully but has not been run.
The earlier `target/browse-only-candidate-1.0.222` was superseded by the 3D
hero copy correction and marked `SUPERSEDED-DO-NOT-USE.txt`. The new ZIP's
embedded JAR hash independently matches the current local JAR, and all eight
website files in that JAR independently match the rebuilt source bundle.
The intervening `target/browse-only-candidate-final-1.0.222` was superseded by
the 3D page heading correction and is also marked `SUPERSEDED-DO-NOT-USE.txt`.
The later `target/browse-only-candidate-reviewed-1.0.222` was superseded by
the browse-only campaign catalog correction and is marked the same way.
The later `target/browse-only-candidate-catalog-1.0.222` was superseded by
two content corrections: the browse-only 3D hero now says "Explore at Deckers"
instead of implying confirmed availability, and the contact section no longer
suggests a phone call when the published store record has no phone number.
The revised website build and all 38 website tests passed. The new ZIP's
embedded JAR SHA-256 matches the local 1.0.222 JAR, and all eight packaged
website files match the rebuilt source by SHA-256. The package script's Maven
build and tests passed. Neither candidate has been installed or published.
The subsequent `target/browse-only-candidate-copy-1.0.222` was superseded by
an immediate-render guard for direct account, bag, checkout, confirmation, and
help routes and for a stale quote confirmation. The redirect remains as a
second layer. The revised website build and 38 tests passed, and direct login
and checkout URLs in the private preview landed on the browse home. The new
ZIP's embedded JAR hash matches the local JAR, and all eight website files
inside it match the source bundle by SHA-256. The package script's Maven build
and tests passed. The installer has not been run.
The subsequent `target/browse-only-candidate-route-guard-1.0.222` was
superseded when background synchronization was narrowed to public catalog
fields. The current package's embedded JAR matches the local JAR by SHA-256;
all eight packaged website files again match the source bundle. Its Maven
build and tests passed. The revised gateway passed 32 tests, including private
sync rejection and public-only handoff. The isolated backup restore and
upgrade rehearsal passed with an assertion that the outbound browse-only
snapshot omits customer, account-link, order, and receipt fields (1 test,
0 skipped, 0 failures). No production migration, package installation, or
gateway deployment was made.
The subsequent `target/browse-only-candidate-public-sync-1.0.222` was
superseded by the internal delayed-order guard. The current package's embedded
JAR matches the local JAR by SHA-256, and its eight website files match the
source bundle. The packaging script's Maven build and tests passed. The
gateway's 33 tests passed, and the guarded Wrangler production dry run passed
with `BROWSE_ONLY=true` and the verified origin secret binding. No installed
application or public domain was changed.
The older rehearsal artifact in
`target/browse-only-candidate-20260928` is invalid and must not be used.
It is marked `INVALID-DO-NOT-USE.txt` in that local directory.
The subsequent `target/browse-only-candidate-clean-20260928` was superseded
after adding origin-side browse-only enforcement and protected-settings startup
support. It is marked `SUPERSEDED-DO-NOT-USE.txt`.
The later `target/browse-only-candidate-protected-20260928` was superseded by
the final tested origin route and catalog guard and is also marked
`SUPERSEDED-DO-NOT-USE.txt`.
The later `target/browse-only-candidate-origin-20260928` was superseded after
the browse-only website controls were tightened; it is marked
`SUPERSEDED-DO-NOT-USE.txt`.
