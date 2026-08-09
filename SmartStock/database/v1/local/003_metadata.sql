CREATE TABLE public.smartstock_schema_metadata (
    schema_scope text PRIMARY KEY CHECK (schema_scope IN ('LOCAL')),
    baseline_version integer NOT NULL CHECK (baseline_version > 0),
    resource_fingerprint_sha256 text NOT NULL
        CHECK (resource_fingerprint_sha256 ~ '^[0-9a-f]{64}$'),
    catalog_fingerprint_sha256 text NOT NULL
        CHECK (catalog_fingerprint_sha256 ~ '^[0-9a-f]{64}$'),
    installed_at timestamptz NOT NULL DEFAULT current_timestamp
);

INSERT INTO public.smartstock_schema_metadata(
    schema_scope, baseline_version, resource_fingerprint_sha256,
    catalog_fingerprint_sha256
) VALUES (
    'LOCAL', 1, '__SMARTSTOCK_RESOURCE_FINGERPRINT__',
    '0000000000000000000000000000000000000000000000000000000000000000'
);

REVOKE ALL ON TABLE public.smartstock_schema_metadata FROM PUBLIC;

-- pg_dump intentionally clears search_path while defining objects. Restore the
-- normal local application path before handing this connection to SmartStock.
SELECT pg_catalog.set_config('search_path', 'public, pg_temp', false);
