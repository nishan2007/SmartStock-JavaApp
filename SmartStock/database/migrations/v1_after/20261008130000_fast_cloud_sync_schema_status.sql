-- The frequent sync preflight needs only the schema contract. The recovery
-- manifest retains exact row counts and must not run on every sync cycle.
CREATE FUNCTION public.smartstock_sync_schema_status() RETURNS jsonb
LANGUAGE sql SECURITY DEFINER
SET search_path TO ''
AS $$
    SELECT pg_catalog.jsonb_build_object(
        'schema_version', baseline_version,
        'schema_ready', baseline_version = 1
            AND resource_fingerprint_sha256 ~ '^[0-9a-f]{64}$'
            AND catalog_fingerprint_sha256 ~ '^[0-9a-f]{64}$'
            AND resource_fingerprint_sha256 <> pg_catalog.repeat('0', 64)
            AND catalog_fingerprint_sha256 <> pg_catalog.repeat('0', 64))
    FROM smartstock_private.smartstock_schema_metadata
    WHERE schema_scope = 'CLOUD'
$$;

REVOKE ALL ON FUNCTION public.smartstock_sync_schema_status() FROM PUBLIC;
REVOKE ALL ON FUNCTION public.smartstock_sync_schema_status() FROM anon, authenticated;
GRANT EXECUTE ON FUNCTION public.smartstock_sync_schema_status() TO service_role;
