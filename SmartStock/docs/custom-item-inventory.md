# Custom-item inventory by store

Custom-item names, prices, variants, barcodes, and other catalog details remain
shared. Live quantity, reorder level, and sold quantity belong to a store:

- `custom_item_inventory` uses `(custom_item_id, location_id)`.
- `custom_variant_inventory` uses `(custom_variant_id, location_id)`.
- A variant parent's displayed stock is the sum of its active variants at the
  selected store. Missing store balances read as zero.

The LAN and mobile catalog endpoints take the store from the authenticated
session. Receiving and counted-stock corrections, POS sales/refunds, custom
orders and pending-order additions, returns, exchanges, and spoil replacements
use that store's balance. Inventory searches, price tags, low-stock notifications,
and the orders dashboard read the same balances. Existing overselling and spoil
shortage policies are preserved. The existing store-transfer workflow supports
regular products only; this change does not add custom items to transfers.

The canonical local schema and ordered migration
`20261116120000_custom_inventory_per_store.sql` cover fresh provisioning and
existing server upgrades. Composite primary keys support the stock lookups and
updates; location indexes support store inventory queries. Recovery manifests
include both stock tables and the one-time migration marker. Mirror exports
scope those records to their owning store; catalog updates do not write stock.

## Existing quantities

At the first schema-readiness check with a configured server location, the
upgrade copies legacy catalog balances to that location once, inside a
transaction. It does not copy the same balance into every location and does not
overwrite an existing store balance. `custom_inventory_legacy_migration` records
completion, including for stores whose catalog is initially empty. Legacy catalog
quantity columns remain as upgrade evidence and are no longer live stock.

The old shared balance cannot establish how many physical units each store owns.
Before deploying to a live store, review the configured location and reconcile
the actual quantities at each store using the normal stock-count workflow. A
prepared code change does not install the upgrade or change a live database.

## Authorized Rosehall correction (November 25, 2026)

The next server update applies the owner's requested zero opening quantity to
the existing Rosehall production installation only. The guard requires server
mode, production profile, location 2 named Rosehall, and its exact legacy stock
migration timestamp (`2026-11-17 10:04:46.069939-04`). Skeldon is unchanged:
the audited Rosehall speaker-flask order consumed Rosehall stock, while Skeldon's
balance remained unchanged since migration; no add-back was justified.

Schema readiness installs the correction evidence columns through
`20261125120000_rosehall_custom_stock_correction.sql`. The data correction runs
inside the startup transaction with stock tables locked. It saves all Rosehall
item/variant balances in `custom_inventory_legacy_migration.rosehall_stock_correction_before`,
records signed stock-count movements, zeros on-hand quantities (including
negative quantities and inactive variants), and sets `rosehall_stock_corrected_at`.
Sold quantities, reorder settings, regular inventory, and other locations remain
unchanged. Any error rolls back the correction. A completed manual reset with
the same operation note also prevents another reset.

The existing store-scoped mirror/recovery table includes these evidence columns;
restoring a corrected snapshot retains the completion marker. Sync never applies
Rosehall's zero balance to Skeldon. New installations and other migration records
are ineligible. Both store servers should run the update; publication and live
installation remain separate release steps. The correction executes when the
updated Rosehall server first passes schema readiness, not when registers update.

## Verification details

`CustomItemStockDatabaseTest` installs the full schema in a disposable database,
then checks two stores, item and variant balances, receiving, catalog edits,
order consumption, guarded shortages, rollback, and read paths. It also checks
one-time legacy migration and the existing-server schema upgrade.

The opt-in JDBC URL is restricted to
`jdbc:postgresql://127.0.0.1:55439/postgres`, using disposable role
`custom_stock_test`. Run it with
`mvn -q -f SmartStock/pom.xml -Dtest=CustomItemStockDatabaseTest -Dcustom.stock.test.admin=jdbc:postgresql://127.0.0.1:55439/postgres test`.
The test creates and drops only its own randomly named database.
