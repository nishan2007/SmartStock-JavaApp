# Cross-store catalog identity

Products, categories, item types, brands and vendors retain their existing local
integer primary keys. A separate immutable `catalog_uid` travels in catalog JSON
snapshots. A receiving server resolves that identity before choosing a local ID,
then rewrites the imported product's foreign keys. Existing inventory, sales,
history and references are never renumbered by this importer.

The local migration backfills legacy products from their original numeric ID and
UTC creation timestamp. Legacy reference identities use their natural keys; item
types include their source category identity. Newly created local rows use random
UUIDs. Old snapshots without UUID fields remain readable. Reference names can
match an existing equivalent local reference, with the source alias retained in
`catalog_source_ids`. Products are never merged merely because names match.

Repeated imports and an imported product subsequently exported back to its origin
resolve to the same identity. Numeric ID collisions allocate an unused local ID;
actual SKU/barcode uniqueness conflicts still roll back and require review.
Stock quantities remain local. Cross-store return restocking resolves the source
product through a verified alias and waits when the mapping is missing or
ambiguous, rather than assuming numeric IDs mean the same product.

The per-store JSON mirror includes identities and aliases for recovery. There is
no new cloud catalog authority or register database credential. No cloud catalog
DDL is required: the existing mirror transports JSON. Fresh local provisioning
and existing local schema upgrade both install the migration.

## Rollout and limits

Upgrade both store servers before relying on bidirectional exports of remapped
products. An old server drops the additional identity on imports/exports and
cannot guarantee round-trip matching. Take normal backups before rollout and
verify each server's first completed snapshot and incoming event results.

This handles standard product notifications and identities preserved in full
new-store recovery snapshots. It does not turn the separate one-time Rosehall
additive bootstrap into an arbitrary catalog merge: that path continues to stop
on incompatible existing records. Custom-order item identity, network failures,
expired credentials, simultaneous business edits and duplicate real barcodes
remain separate concerns. New-store recovery must use a verified complete
snapshot; product notifications alone are not a complete historical catalog.

## Validation

`CrossStoreProductSyncDatabaseTest` uses session temporary tables to cover numeric
collisions, reference mapping, repeat imports, return to origin, explicit UUIDs
from different stores, new-store import and stock/barcode preservation.
`CatalogIdentityMigrationDatabaseTest` requires the empty disposable database
`smartstock_catalog_identity_test`, runs transactional public-schema DDL and rolls
it back. It checks SQL/Java legacy identities, migration retries, old snapshot
null identities and immutable UUIDs. Enable these with
`-Dsmartstock.test.productSync=true`; never point migration tests at a live store.

Installed server rollout, live failed-event replay and physical return/restock
verification are separate deployment checks.
