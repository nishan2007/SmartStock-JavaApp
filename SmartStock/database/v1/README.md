# SmartStock v1 database baselines

These manifests are the only schema-authoring sources for a clean SmartStock v1
installation. They are applied in the exact order shown and only to empty,
side-by-side candidate databases.

## Local PostgreSQL manifest

1. `local/001_schema.sql`
2. `local/002_seed.sql`
3. `local/003_metadata.sql`

The local baseline contains the complete store-authority schema. It does not
contain Supabase Auth or Storage objects. Local startup validates version 1 and
the deterministic packaged-resource fingerprint; it never repairs schema in
place.

## Supabase manifest

1. `cloud/001_schema.sql`
2. `cloud/002_storage.sql`
3. `cloud/003_metadata.sql`

The cloud baseline contains only SmartStock control-plane, sync, recovery,
release, Auth-linkage, and private credential-recovery objects. Auth and Storage
platform-owned tables are not recreated by these scripts. Every application
object has explicit RLS and grants.

## Future changes

Post-v1 changes belong in `database/migrations/v1_after` as new, ordered,
immutable migrations. Applied migration files are never edited. A schema change
must update validation tests and produce a new expected fingerprint; v1 baseline
files remain immutable after the first accepted deployment.
