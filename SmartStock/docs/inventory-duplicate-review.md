# Inventory duplicate review

Administrators open **View Inventory → Review duplicates** while connected to the
store being reviewed. This feature runs against the authenticated local store
server. Opening the screen does not merge anything.

Find duplicates compares active inventory products using either a shared barcode
(including additional barcodes and the existing barcode normalization rules), or
the same normalized name, brand, size, color and flavor. Case, spacing and name
punctuation are ignored. Custom items, services, archived products and the system
miscellaneous product are excluded. Search by name or SKU to narrow large
catalogues; the screen explicitly reports its 5,000-product / 500-pair limit.
Pairs are suggestions, not proof that two products are the same.

Select a pair, choose which product to keep, and inspect the live comparison.
It shows both photos, live local stock, separately labelled other-store synced
stock, price, cost, descriptions, department, barcodes, variants, creator,
creation date, and linked-history counts. Missing stock rows are not shown as
verified zeroes. A blocked merge explains a genuine variant or stock conflict.

After confirmation, each store independently adds its duplicate stock to its
kept product, zeroes source stock, and archives the source. Stock is never moved
between stores or copied out of a cache. Confirm that the
quantities represent separate physical stock before combining them. If the same
physical stock was counted twice, reconcile it first. The survivor's other
settings, including price, cost and the complete Skeldon photo set, are retained.
Descriptions are combined without repeated equivalent lines. Source primary and
additional barcodes, plus the old SKU, become additional scan aliases; the kept
primary barcode remains primary. Local shelf settings prefer existing target
assignments. Per-store audit receipts retain before-state quantities and source
catalogue data. Source photo-review evidence remains attached to its archived ID.

Sales, returns, orders, transfers, receiving and stock-ledger references are
consolidated without changing quantities, prices, discounts, payments or refund
amounts. Legacy product costs are snapshotted before relinking sale lines so
profit reports retain their original costs. Checkout and website-order JSON
changes only product IDs. Website reservations combine on their compound keys;
favorites follow the retained item and duplicate website listings are unpublished.
Grouped variants, differing size/color/flavor/brand, negative stock and overflow
remain blocked. Unknown uniqueness conflicts roll back the complete local merge.

Every merge requires an active administrator plus Edit Item and Product Archive
permissions on the server, uses a transaction and bounded lock waits, rechecks
the review fingerprint under locks, and retains its request key on retry.
The ordered local migration `20261113150000_product_merges.sql` is also present
in the local baseline. Server provisioning validates and upgrades an undrifted
previous contract. Recovery manifests and shared-reference sync carry immutable
catalogue-UUID mappings and per-store acknowledgements. Raw remote cache rows
remain source-scoped; cross-store restocking resolves aliases through verified
catalogue identity mappings. Database guards reject stale writes to retired stock,
redirect new line/barcode references, and prevent reactivation or identity deletion.

**Merge progress** shows Applied versus Awaiting store separately. An offline or
older server cannot acknowledge a merge until it can execute compatible code.
Skeldon is the control point; no operator needs to visit another store's desktop.

## Stock edits from Skeldon

Select an item in Inventory and open **Stock by store**. An active administrator
with Edit Item permission can request a quantity at either store. Requests are
authenticated, idempotent and scoped by catalogue UUID and owning store. The
owner rechecks administrator status and expected stock under locks before
applying an adjustment and audit movement. A stale request records Conflict;
an unavailable store leaves the request Queued. Check requests shows confirmed
results. The UI never updates cached stock to pretend a remote write succeeded.

These tables live only in store PostgreSQL. Supabase continues to carry the
existing protected queue/recovery JSON; no register-facing cloud stock table or
new public Data API permissions are introduced.

## Validation

Run `SmartStock/tools/test-product-merges-windows.ps1 -FullSuite` on Windows
with PostgreSQL 17 installed. It starts its own loopback-only disposable cluster.
`ProductMergeServiceTest` covers candidate and description rules.
`ProductMergeDatabaseTest` is opt-in, using only an explicitly named disposable
database: set `smartstock.test.productMergeDatabaseUrl` to
`jdbc:postgresql://127.0.0.1:<port>/product_merge_test`. It installs the local
baseline in two independent databases, with different numeric IDs for the same
catalogue UUIDs, and checks permissions, stale reviews, replay, stock conservation,
financial/cost snapshots, photo preservation, barcode aliases, retirement guards,
and remote-stock owner/expected-quantity checks.
Do not point this test at a live store database.

Installed-server/register review, live cross-store synchronization and concurrent
register workflows require separate deployment verification. Building this feature
does not install it or inspect a live store's inventory.
