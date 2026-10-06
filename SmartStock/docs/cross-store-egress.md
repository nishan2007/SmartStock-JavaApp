# Cross-store egress reduction

Cross-store inventory, sales/returns, and customer history share one background
refresh coordinator. It checks due stores independently of operational sync;
the persisted deadline is five minutes after each successful refresh (or five
minutes after a failed attempt). The scheduler checks deadlines every 15 seconds.
Initial caches are fetched immediately. Process restarts preserve the deadlines;
a database advisory lock prevents duplicate refreshes by separate processes.

Each remote store supplies one completed snapshot manifest. Its per-table SHA-256
fingerprints include row count and ordered row keys/hashes. An unchanged table
needs no hash listing or row bodies, including when another table changed the
snapshot generation. For a changed table, the client pages through keys/hashes,
downloads only new/changed bodies in batches of at most 1,000, and removes keys
absent from the complete listing. A pruned previous generation does not require
a new full download because the comparison baseline is stored locally.

The source cache is shared between all three consumers. Updates to source rows,
existing display caches, fingerprints, and successful refresh timestamps commit
together per store. A missing capability, failed page, invalid response, or
application failure retains prior rows and marks the cache stale. The displayed
cache timestamp is the source generation's completion time, not the time it was
checked. Search requests continue to use only the local database through the
authenticated LAN API. Transfers, refunds, payroll, and operational sync retain
their existing cadence. Full recovery snapshots and the original recovery RPC
remain available.

## Installation and compatibility

1. Apply `20261003173203_cross_store_egress_cloud.sql` through SmartStock's cloud
   migration runner, **before** starting upgraded store servers. The migration
   adds fingerprints, backfills retained completed generations, and adds two
   service-role-only RPCs. Existing clients and recovery callers remain compatible.
2. Upgrade store servers using the normal application deployment process.
   `20261003173205_cross_store_egress_local.sql` is registered in the canonical
   local contract and exact-contract upgrade path. Fresh installations run it as
   part of the baseline-plus-ordered-migrations installation. Windows/macOS
   provisioning use the same packaged contract resources. Immutable historical
   baseline SQL is deliberately preserved rather than changing its checksum.
3. If the cloud capability is absent, the client does not fall back to repeated
   full downloads; it retains stale cache data and records an actionable error.

The existing snapshot primary key `(generation_id,table_name,row_key)` covers
changed-body lookups; the existing `(generation_id,table_name,row_sequence)` index
covers hash pagination. Local cache primary keys cover store/table/key lookups,
and transfer metrics have an index on their timestamp. New local cache tables
enable RLS and do not grant public access. Registers receive no cloud credentials.

## Measurements and the 48-hour observation

The Sync Status screen reports measured download and upload payload bytes for
today and for an explicitly selected billing period. Use **Set Billing Period**
to enter the actual start date shown in Supabase; do not assume calendar months.
This setting requires the server-side synchronization management permission.
Update it when the billing cycle changes. Measurements are retained for 90 days.

These are application UTF-8 payload measurements, not exact Supabase billed bytes:
compression, CDN traffic, other applications, and uninstrumented requests can
differ. The screen identifies the earliest available measurement; historical
usage before instrumentation cannot be reconstructed. Upload totals are shown
separately because uploads themselves are not download egress.
The **Other Stores** tab shows source snapshot time, last successful check, next
scheduled check, and refresh errors, including missing cloud capabilities.

After **all** store servers are upgraded, record Supabase's per-service egress
breakdown and each server's measured downloads. Compare them again after 24 and
48 hours. Confirm unchanged tables generate no row-body requests, a single edit
downloads only that body's data plus metadata, and inventory changes appear at
the destination within the five-minute check interval plus transfer time. Check
sales, returns, customer history, payroll, transfers, and refunds on two running
servers. Investigate any remaining large recovery/image/API traffic separately;
this change does not claim to explain the entire previous billing-period overage.

## Validation

Run the normal Maven/security/diff gates. `CrossStoreDeltaTest` exercises unchanged
tables, unrelated generations, edits/inserts/deletes, empty tables, exact page
boundaries, failed/pruned snapshots, and invalid bodies. The database suite uses
only disposable databases on the explicit isolated test cluster:

```powershell
mvn -q -f SmartStock/pom.xml '-Dtest=CrossStoreEgressDatabaseTest' '-Degress.test.admin=jdbc:postgresql://127.0.0.1:55447/postgres' test
```

It verifies real PostgreSQL cloud/local migrations, backfill and finalization
fingerprints, grants, generation rejection, restart scheduling, atomic rollback,
source freshness, and payload measurements. The cluster must use the test-only
`egress_test` role. Automated checks do not replace installed-app, Windows service,
or two-live-server acceptance checks. No release is published by these tests.
