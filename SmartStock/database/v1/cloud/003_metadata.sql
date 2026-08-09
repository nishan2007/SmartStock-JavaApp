CREATE TABLE smartstock_private.schema_migrations (
    migration_name text PRIMARY KEY,
    checksum_sha256 text NOT NULL CHECK (checksum_sha256 ~ '^[0-9a-f]{64}$'),
    app_version text,
    applied_at timestamptz NOT NULL DEFAULT current_timestamp
);

ALTER TABLE smartstock_private.schema_migrations ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON TABLE smartstock_private.schema_migrations
    FROM PUBLIC, anon, authenticated;
GRANT ALL ON TABLE smartstock_private.schema_migrations TO service_role;

INSERT INTO smartstock_private.smartstock_schema_metadata(
    schema_scope, baseline_version, resource_fingerprint_sha256,
    catalog_fingerprint_sha256
) VALUES (
    'CLOUD', 1, '__SMARTSTOCK_RESOURCE_FINGERPRINT__',
    '0000000000000000000000000000000000000000000000000000000000000000'
);

INSERT INTO smartstock_private.schema_migrations(
    migration_name, checksum_sha256, app_version
) VALUES (
    'v1_baseline', '__SMARTSTOCK_RESOURCE_FINGERPRINT__', 'v1'
);
