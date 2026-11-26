# SmartStock Windows 1.1.117 — 2026-11-14

Cross-store product sync previously inserted photo metadata by asset UUID alone.
When a store had independently discovered the same bucket/object under a different
UUID, the unique storage-object constraint rejected the insert. Product merges
then waited indefinitely for the original asset UUID, hiding those decisions from
the receiving store's merge progress.

Product sync now reuses the existing local asset for an exact storage object,
checks category, access level, provider and any known checksums, and remembers the
verified identity in local `image_sync_state`. Aliases are immutable and validated
against the local object on use. Local image files and cloud status are retained.
Product URLs and merge photo assignments resolve to that local identity; shared
merge approvals retain their original fields, so replay stays idempotent. No stock
is imported from another store. No schema, Supabase or model changes are required.

The PostgreSQL regression creates separate stores with different numeric product
IDs and different image UUIDs for the same storage object. It verifies a blocked
merge, metadata import despite newer local product details, successful replay with
stock moved only once, unchanged shared approval and rejection of conflicting
object/checksum identities. Existing photo-reference and merge tests also pass.

Live Rosehall installation and its two remaining merge receipts must be verified
after deployment. No printer, drawer, NFC or macOS behavior is changed or verified
by these checks.

Validation: full Maven suite with disposable PostgreSQL enabled: 1488 tests, zero failures/errors, 160 optional skips. Repository security and whitespace checks passed. Packaging reuses the completed test gate and skips a duplicate Maven test run.

Exact updater acceptance over isolated 1.1.116 passed: installed JAR identity, native welcome-screen startup, launcher configuration, runtime/model-profile preservation and model-free layout. ZIP: 169182778 bytes; SHA-256: fe4c38b0b281b8246f910ae3f57ea04198388d2160f3f6ec6f3c3ae357450b0f. Live store installation is separate and has not been performed by this acceptance check.

Published build 101117 after hosted download/size/SHA-256 verification. Independent release metadata read-back confirmed version, build, immutable object path, bytes, hash and published=true. This update-only publication leaves the first-install selection unchanged.
