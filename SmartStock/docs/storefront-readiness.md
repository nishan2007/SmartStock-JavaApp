# Storefront readiness evidence

Status: **in development; not approved for production enablement**.
The running SmartStock installation, live database, services, and production
cloud configuration are outside this development verification boundary.
The homepage's New at Deckers cards now use original, compressed illustrative
capability photos for 3D printing and apparel. They are editorial artwork, not
photos of completed customer jobs. The existing flagship hero remains a 3D
printing concept image; live Deckers equipment and project photography still
need to replace concept imagery where real production evidence is intended.

Published Made at Deckers projects now expose a store-specific share link and
downloadable SVG QR code. The public QR endpoint checks the selected store's
published project snapshot before returning artwork. Shared links select the
project's store on first load. Project links now use clean paths; the server returns
escaped project-specific metadata, a no-script page, and a sitemap of published
projects. Product details now offer same-category products and matching published
projects as inspiration; the matching does not claim that a product was used in a
project. Storefront schema v9 adds staff-curated product references for projects.
Project pages also offer native device sharing, WhatsApp, and Facebook links
that lead back to the published project. SmartStock staff with company preference
access can prepare editable social post text for a published project and copy it
to the clipboard. The server constructs the canonical project link from its
configured public origin and rejects unpublished or other-store projects.
Posting to social platforms and live clipboard/browser checks remain outstanding.
The public "Made Using" section contains only products currently published at
that store; the isolated database rehearsal checks save authorization boundaries
and unpublished-product removal. Staff selection and installed browser behavior
still require interactive verification.
Storefront schema v10 adds up to six staff-managed gallery photos per project,
with a public role and caption. Draft and archived project media stay outside
the public snapshot; gallery images use the approved project image path and are
prefetched by backup stores. Video and interactive staff/browser verification
remain outstanding.
Fourteen service pages now have clean URLs, distinct descriptions, server-rendered
canonical and sharing metadata, no-script summaries, and sitemap entries. They
include the brief's named pages for 3D printing, business cards, banner printing,
custom T-shirts, embroidery, laser engraving, signs, stationery, and large format
printing. Browser checks, service availability confirmation at each location, and
live crawler inspection remain outstanding.
Storefront schema v11 adds per-store service availability controlled by staff with
company preference permission. The selected store's catalog now excludes paused
services from discovery, search, and direct quote entry, while the store server
rejects new direct-service requests for paused services. Old request retries remain
idempotent. The isolated two-database rehearsal covers fresh and upgraded schema,
store separation, publication, and request rejection. Public service URLs remain
indexable because another Deckers location may offer the service; live service
availability and browser behavior still need confirmation.
Storefront schema v12 lets staff link each Made at Deckers project to a canonical
service. New project-inspired quotes and repeat requests carrying that approved
project context are rejected when the linked service is paused at the selected
store. Project pages remain available as portfolio content. Older projects have
no service link until staff curate one; their production-method text is not used
as an authority for availability. The website blocks the quote form for a
linked project when its service is paused. Live staff and browser checks remain.
Storefront schema v13 adds private, per-customer product favorites at the selected
store. The server accepts a save only for a currently published, active product;
unpublished products disappear from the returned list. Catalog browsing uses a
small favorites-only read instead of loading order and receipt history. The account screen and
catalog cards expose saving and removal. Favorites require the selected store's
primary server for writes. Live login, multi-store, and browser checks remain.
Published products now have store-specific clean URLs and full product pages.
The server checks the selected store's public catalog before returning product
metadata, price and availability structured data, and a no-script summary.
The sitemap includes products currently published in store snapshots. Quick view
remains available from catalog cards. Direct-link browser and live crawler checks
remain outstanding.
The "Make something like this" quote form now asks short project-specific
questions for embroidery, apparel, 3D printing, business cards, and signage.
Answers accompany the customer's description. The server resolves the published
project at the selected store and stores its production context separately.
Customers can start a fresh quote from a previous custom request in their account.
The selected store verifies ownership before returning the old details. The form
restores quantity, choices, and guided answers for review, while clearing the due
date and requiring artwork to be attached again. A saved project context can be
reused after that public project is archived. The isolated database test covers
cross-customer rejection, archived-project reuse, and idempotent retry; it does
not place or repeat a production order.
Services, Business, and About now have dedicated discovery pages with clean
URLs, direct links to relevant services and published projects, server-rendered
metadata, no-script summaries, and sitemap entries. The desktop and mobile
navigation reach these pages. Visual browser checks remain outstanding.
The flagship homepage and Services page now use original, optimized concept
images. They illustrate capabilities and are not presented as photographs of
Deckers equipment or completed customer orders. The local browser preview was
checked at desktop and 390 px mobile widths; live catalog and service data were
unavailable in that preview, so data-driven states still need visual checks.

## Verified locally

- React/TypeScript production build, with compiled assets packaged as Java resources.
- Catalog responses identify backup serving, and the frontend displays the sync
  timestamp plus an outage notice. Manual refresh invalidates the reviewed quote.
  Build and bundle checks pass; visual verification of this newest notice is
  outstanding because the preview tab's navigation policy blocked recovery
  from an earlier local connection failure.
- Build-generated source/asset hashes are verified during Maven tests, preventing
  stale checked-in website bundles from silently reaching packaged applications.
- Synthetic browser catalog, cart, order review and confirmation; desktop/mobile
  presentation; catalog deep-link reload, browser Back, and independent store bags.
- Pure tests for cart validation, route restoration, tax/discount policy,
  pickup deadlines, and encrypted-session tamper/expiry handling.
- Worker tests for origin preference, pending commitments, session revocation,
  large Unicode snapshot chunking, stale snapshot rejection, lost-response
  recovery, lifecycle authority, and immutable customer/quote handoff.
- Worker alarm tests recover a saved order without the shopper returning, and
  retain an unresolved command until every attempted server certifies absence.
- Verified-email enrollment creates a customer/link before checkout, or queues
  a durable backup enrollment for the fulfillment store. Isolated database tests
  cover duplicate retries, handoff, ambiguous contacts, and staff resolution.
- Staff resolution now searches a verified email and presents customer records
  by account number, name, phone, and balance rather than asking for UUIDs.
  Tests reject unverified, banned, deleted, ambiguous, or metadata-only identities.
- Account responses include store-qualified balances and itemized receipts with
  freshness labels; tests deny another identity access to an existing link and
  exclude other customers' receipts. Synthetic receipt expansion was checked
  in the browser at a narrow viewport.
- The full Maven suite passed after the enrollment/account changes. Its report
  marked the opt-in database test skipped; database evidence must come from
  the separate explicit integration invocation, not the aggregate suite result.
  The separate invocation passed with one test, zero failures/errors, and zero
  skipped tests, including schema upgrade, enrollment, and account-history checks.
- Two disposable PostgreSQL databases verify backup acceptance, repeat replay,
  locked prices, configurable READY deadlines, expiry, shortage handling,
  collection retry with one sale/stock deduction, first-import READY/COLLECTED
  metadata, immutable handoff, and durable rejection of delayed checkout.
- Worker dry-run builds successfully; this is not a deployment.
- Gateway submission tests exercise session verification after body parsing,
  lost-response protection across coordinator restarts, and alarm recovery.
  Streaming request limits count UTF-8 bytes and stop oversized chunked uploads.
  Backup eligibility requires a snapshot for the selected fulfillment store.
- Concurrent coordinator tests with an offline-primary fixture confirm that
  only one of two shoppers receives the last item, the pickup store stays fixed,
  and changed payloads or another customer cannot reuse the accepted identifier.
  These are coordinator tests with simulated HTTP origins, not installed-server
  concurrency evidence.
- Completed or definitively rejected checkout commands release their pinned
  private snapshots. Unresolved commands retain them for exact replay; accepted
  results and identity hashes remain available for idempotent retries.
- Pickup deadline regression coverage checks READY → NEEDS_ATTENTION →
  PREPARING → READY after a store expiry-setting change. The original saved
  deadline survives instead of restarting the timer. Cart parsing rejects
  fractional and overflowing integer inputs instead of silently truncating them.
- Synchronization rechecks the server role after network waits and before commit.
  An isolated database test revokes authorization during handoff and verifies
  that snapshot writes and delivered-event acknowledgements both roll back.
  Checkout, enrollment, command resolution, and staff mutations also recheck
  role authority around their write work. This does not replace the outstanding
  generation/lease and installed server-replacement acceptance tests.
- The Windows packaging script produced isolated artifacts earlier in development.
  Those artifacts predate subsequent source changes and are not final candidates.

## Source work still requiring completion or stronger evidence

The 3D Printing navigation now opens a dedicated service introduction with the
request process and recent published 3D projects when available. Its private
request form inspects ASCII and binary STL and mesh OBJ files locally,
shows triangle count and bounding dimensions in model units, and offers a rotatable
canvas preview sampled from the model. For closed, consistently wound meshes of
at most 25,000 triangles it also estimates enclosed geometric volume in cubic
model units. Open and complex meshes do not receive a volume value. This is not
a material-usage, print-time, printability, or price estimate. It rejects unreadable STL/OBJ mesh data before
upload. The 3D request captures a standard or fine-detail preference for staff
review; it does not promise a print setting or price. Customers can explicitly
select millimeters, centimeters, or inches for STL/OBJ model coordinates. The
preview then shows physical dimensions in millimeters and, when available,
geometric volume in cubic centimeters. The chosen units persist in the staff
request and restore for repeat requests. Unknown units remain an option. The
isolated database test covers persistence and rejection of unsupported units.
Customers must confirm
physical scale with the store. Direct-mesh and same-model component 3MF packages
now have a browser preview that respects declared units and nested transforms.
Assembly volume remains unavailable because overlapping components cannot be
summed as a print-material estimate. Packages requiring external model parts or
extensions remain uploadable for staff review without a misleading preview. The browser
limits extracted model XML, and the store server rejects oversized or unsafe 3MF
archives. 3MF extension geometry, manufacturability checks, estimated material/time,
configurable pricing, and visual verification of the preview remain outstanding.

Private design proof revision and decision history now use storefront schema v7.
Staff with custom-order permission can upload JPEG, PNG, or PDF proofs for a request;
only the request's authenticated customer can retrieve and decide on each proof.
Pending proofs block advancement to production. Isolated PostgreSQL tests cover
fresh install, v1–v6 upgrades, ownership, revision, replay rejection, and the
approval history. Storefront schema v8 allows staff to link a request to an existing
native SmartStock custom order after verifying the same store and linked customer.
The customer account then reads production, ready, completed, and cancelled status
from the native order. Staff still create and price the native order through the
normal SmartStock workflow; the website request does not automatically create it.
Live notification delivery, browser interaction, and installed server behavior
still need verification.

Gateway origins now require registered instance UUIDs and distinct per-origin
secrets. Health checks match the instance, sync rejects cross-origin credentials,
and persisted checkout attempts include the accepting instance identity. A
replacement cannot certify nonacceptance for the old instance. Unresolved legacy
attempts without instance identities also remain pending for reconciliation.
Automated registry generation/lease validation and replacement recovery are still
outstanding; this identity check does not establish those stronger guarantees.

Storefront schema startup now validates the PostgreSQL catalog rather than trusting
only the version row. The expected catalog covers columns, defaults, constraints,
indexes, numeric precision, identities and index/constraint validity; PUBLIC schema
access is rejected separately. This contract is anchored to PostgreSQL 17 and must
be verified before supporting another PostgreSQL major version.

Company backup enumeration now includes both public and storefront schemas, and
exports database rows in a repeatable-read transaction. The isolated company SQL
export/restore rehearsal covers storefront tables, reservations, stable sale
links, and rejected-checkout fences. Always-generated identity sequences are reset
after restore so subsequent audit inserts remain valid. This does not prove the
separate cloud-replacement recovery path or packaged image recovery.

Missing store email sender configuration now leaves a durable failed notification
in the existing Email Outbox instead of dropping the message. Senderless messages
do not consume delivery attempts, and the queue resumes the same row after sender
configuration becomes available. The online-store staff screen flags failed order
emails and directs staff to Email Outbox for delivery/configuration diagnostics.
Real sender authorization, delivery, and operator retry flows still need staging.

The main menu now provides a dedicated online pickup fulfillment view for staff
with MAKE_SALE permission. Its server response contains store-qualified orders
and notification counts, with no customer-link or account-enrollment records.
Company settings and publication/customer-link controls are not shown in this
view. Isolated Swing interaction and installed cashier verification remain open.

Public catalog responses now replace internal image URLs/references with a presence
indicator without modifying the trusted synchronization snapshot. The public image
loader permits only active PRODUCT assets with PNG/JPEG/WebP/GIF content types;
employee photos, custom-order images, deleted assets, and active web content are
rejected before bytes are loaded. Storefront policy and bundle tests and the Bash
security check passed after this change. This does not establish legacy image
compatibility or image availability on backup servers by itself.

Trusted catalog snapshots now include product-only image manifests. Legacy storage
URLs resolve through the existing image registry without fetching arbitrary URLs.
Backups use these manifests to download checksum-verified image bytes into a
separate content-addressed cache, without inserting another store's assets into
their authoritative registry. Isolated tests cover cache reuse, corrupt-cache
repair, wrong cloud checksums, unsafe manifests, and legacy-reference resolution
in the two-database rehearsal. Storefront image reads/downloads now enforce a
12 MiB limit, including chunked HTTP bodies and OneDrive redirects. Local HTTP
tests verify exact-limit success and oversized-response cancellation. Live
cloud/provider access and deployed outage coverage remain unverified. A separate
background worker now prefetches four replica images per batch, rotates through
the remote catalogs, and rechecks the server-role fence before each download.
The replica cache has a 512 MiB least-recently-used budget, verifies bytes before
serving, and only removes checksum-named files in its dedicated directory.

- Full staging Auth activation/recovery and HTTP authorization exercise. Local
  tests cover customer creation/linking and access rules, but do not establish
  real Auth email delivery or deployed HTTP integration.
- Paginated customer history beyond the current snapshot/history limits and
  isolated Swing verification of the staff selection workflow.
- Real product image/provider compatibility and realistic-volume cache behavior,
  and image availability on backup servers during cloud outages.
- Bounded/paginated synchronization and journal retention at realistic customer,
  catalog, and order volumes; payload-limit and storage-limit failure tests.
- Authoritative instance/generation validation through server role transitions,
  along with concurrent HTTP checkout/failure rehearsals against the actual Worker.
- End-to-end background resolution of interrupted commands in the local Worker
  runtime, beyond the isolated coordinator and PostgreSQL tests.
- Explicit schema/recovery coverage and a final package built from the exact
  verified source state. Bundle freshness now has a Maven verification gate.

## Separate staging and installation acceptance

These require isolated installed test servers and staging service configuration:
real verification/status emails, permanent tunnels, cellular access, service
restart, failover, recovery, cash-drawer constraints, and printed receipts.
Do not substitute source tests for these checks or run them against the existing
live SmartStock installation. See `storefront-deployment.md` for the full gate.

## Mobile storefront review

The 320 px browser preview exposed a header layout failure: the store name
wrapped into a vertical column beside the desktop quote action. The compact
header now keeps the name on one line, gives its icon controls usable tap
targets, and places Get a Quote first in the mobile menu. At 320 px, the
refreshed preview had no horizontal page overflow. Selecting Get a Quote
opened the custom project request screen, closed the menu, and focused the
page heading. This is a local preview check; an installed application and
physical phone browser still need acceptance testing.

The homepage Made at Deckers feature now selects a published project with a
cover image, preferring a featured project, and presents its real photo with
links to view the project or start a related custom request. The abstract
visual remains only when no published project has a cover. This selection
needs a live catalog and real approved project photography for visual
acceptance; the local preview has no store backend content.

The default 3D Printing campaign now presents Start a 3D Print and Explore
3D Printing as separate actions. The first opens the private request area and
focuses its first action; the second opens the service introduction at the top.
The browser preview verified both routes. Older untouched campaign defaults
are translated by the website until store settings are refreshed. Staff can
now choose explicit campaign button destinations in SmartStock, as described
below.

Service discovery cards now display a published project cover when the project
is explicitly linked to that service. A featured project takes precedence;
unrelated projects and projects without approved covers are ignored. The
existing illustrated card remains when there is no suitable published work.
Live visual acceptance still needs real approved portfolio content.

The homepage now has a For Your Business feature after Shop by Purpose. It
shows only services available at the selected store, links into the business
discovery page, and uses a published project cover when one is linked to a
business service. The local desktop preview verified the layout and route;
at 320 px the section has one column and no horizontal overflow. Approved
business photography and an installed-app review remain outstanding.

Product cards, Quick View, and full product pages now keep a consistent image
area when the published catalog has no product photo or an image request fails.
They show a clearly labeled placeholder in those cases. This needs a live
catalog review with real product photography and a deliberately broken image
request to verify both paths in the installed storefront.

Homepage campaign buttons now use explicit START, EXPLORE, SHOP, or MADE
destinations, independent of their editable labels. Staff can select these in
SmartStock's featured campaign editor. Storefront schema v14 persists the
choices per store, and the public catalog exposes only the chosen actions.
Existing 3D campaigns default to the custom request and service introduction;
other existing campaigns migrate to service exploration and shop. The isolated
database rehearsal covers fresh schema, upgrade paths, and repeat migration.
An installed SmartStock editor and browser check remains outstanding.

The homepage custom-project invitation now opens the quote request area
directly and focuses its first action. In the local browser preview, the
unauthenticated route opened the sign-in action inside the request area.
The sticky navigation becomes smaller after scrolling and respects reduced
motion. Desktop scroll behavior was checked in the local browser; mobile and
installed-app acceptance remain outstanding.

Published product groups now carry their option names and values through the
store catalog. Product pages and Quick View show selectable published variant
combinations, including each option's store-specific availability. Selecting a
variant opens that product's own price, image, and clean URL; unpublished or
inactive group members are not in the public catalog. A live catalog with
grouped products and an installed browser check are still needed.
