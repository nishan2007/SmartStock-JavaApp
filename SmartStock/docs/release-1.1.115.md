# SmartStock Windows 1.1.115 — 2026-11-14

Build **101115**. Administrator inventory consolidation with separate store
quantities, duplicate photos, description combination, retained barcode aliases,
per-store acknowledgements and stock-change requests from Skeldon.

The retained item keeps its complete original photo set, primary barcode, price
and cost. Duplicate barcodes and SKU become additional scan aliases. Descriptions
combine without repeated equivalent lines. Linked business records retain monetary
amounts, and source costs are snapshotted for historical profit reports.

Local migration `20261113150000_product_merges.sql` is included in the fresh
baseline, guarded upgrade path and recovery/shared-reference manifests. Each store
applies the shared UUID mapping to its own authoritative inventory exactly once.
Cloud storage is only the existing protected queue/recovery transport. Old and
unavailable servers remain awaiting acknowledgement; cached stock is never used
as an authoritative write or a claim of completion.

## Exact published artifact

- Update ZIP: `smartstock-windows-1.1.115.zip`
- Size: **169178814 bytes**
- SHA-256: `f129fe2214a44d6461ebf161261402b4ab376cc6597ea58083baf737034cecba`
- Immutable object: `updates/windows/1.1.115/f129fe2214a44d6461ebf161261402b4ab376cc6597ea58083baf737034cecba/smartstock-windows-1.1.115.zip`

The standard protected publisher uploaded and fully downloaded the stored artifact,
verified size/hash, and then published the release row. Independent metadata
read-back matched version, build, object path, size, hash and published state.
This is an update-only release; the existing first-install selection is unchanged.

## Validation and rollout

- Full Maven suite: 1486 test cases, zero failures/errors, 163 optional skips.
- Separately enabled product-merge tests passed against two independent disposable
  PostgreSQL databases with different numeric IDs for the same catalogue UUIDs.
  Covered replay, store ownership, expected-quantity conflicts, historical money/
  cost retention, photos, barcode aliases, retirement, and drift-guarded upgrade.
- Repository security and whitespace checks passed.
- Exact updater accepted over isolated 1.1.114; installed JAR identity, native
  startup, launcher configuration, runtime/profile preservation and model exclusion
  passed. Hosted immutable model catalogue and update layout were verified.
- Installed the verified artifact on Skeldon's server through its standard updater;
  service restart and certificate-pinned health verification passed. Private
  before-state evidence remains on the store server, outside Git.
- Applied the three administrator-approved pairs on Skeldon and independently
  verified active/archived identities, quantities, descriptions, barcode aliases
  and unchanged original photo references. The shared queue confirmed all six
  mapping/application announcements were uploaded successfully.
- Rosehall has not acknowledged the new mappings and still reports version
  1.1.113. Its recent heartbeat is not evidence of merge completion; those store
  applications remain pending. No direct cloud stock edit was performed.

Live Rosehall application/synchronization, physical stock counts, printers,
cash drawers, NFC and macOS checks remain outstanding. No model weights changed.
