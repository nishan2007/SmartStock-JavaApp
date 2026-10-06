# SmartStock 1.0.245 — Rosehall catalog enrollment repair

The production Rosehall server was enrolled with only its system miscellaneous item and employee image records. Normal sync exchanged operational events and cross-store display caches but did not populate its local product catalog.

This update performs one additive bootstrap from Skeldon's completed cloud snapshot before reference announcements, image work, and mirror publication. It downloads only catalog reference tables, validates complete table counts, preserves the source product IDs and OneDrive manifests, and marks newly imported image files missing locally. Normal image synchronization then downloads and verifies their bytes.

The system miscellaneous item is identified by the reserved SMARTSTOCK-MISC SKU. An unused Rosehall placeholder can be aligned with Skeldon's reserved miscellaneous ID. Existing stock, cart, or transaction references block this change. All other identifier and uniqueness collisions abort the transaction. Sales, inventory quantities, employee records, credentials, and store settings are excluded from the import.

The completion marker commits with the catalog. Failed downloads leave local data unchanged; failed imports roll back; successful reruns skip the repair. This is an enrollment repair, not ongoing catalog-edit replication.

Validation: full Maven suite, repository security checks, whitespace checks, and an explicitly enabled PostgreSQL test using session-local temporary tables covering the Rosehall miscellaneous collision, complete import, image-local-state reset, idempotency, and collision rollback. Temporary-table tests do not reproduce Rosehall's complete foreign-key environment. Live installed-app verification and OneDrive download verification on Rosehall remain required after installation.
