-- Make the quarantine state independently and exactly verifiable. This does
-- not move or delete any object.

CREATE OR REPLACE VIEW smartstock_private.cloud_object_inventory
WITH (security_invoker = true)
AS
SELECT manifest.object_type,
       manifest.object_name,
       manifest.disposition,
       manifest.rationale,
       manifest.last_verified_at,
       manifest.quarantine_started_at,
       public_object.oid IS NOT NULL OR legacy_object.oid IS NOT NULL AS object_exists,
       COALESCE(public_stats.n_live_tup, legacy_stats.n_live_tup, 0)::bigint AS estimated_rows,
       CASE
           WHEN public_object.oid IS NOT NULL
               THEN pg_catalog.pg_total_relation_size(public_object.oid)
           WHEN legacy_object.oid IS NOT NULL
               THEN pg_catalog.pg_total_relation_size(legacy_object.oid)
           ELSE 0
       END AS total_bytes,
       public_object.oid IS NOT NULL AS public_exists,
       legacy_object.oid IS NOT NULL AS legacy_exists,
       CASE
           WHEN public_object.oid IS NOT NULL THEN 'public'
           WHEN legacy_object.oid IS NOT NULL THEN 'smartstock_legacy'
           ELSE NULL
       END AS current_schema
FROM smartstock_private.cloud_object_manifest manifest
LEFT JOIN pg_catalog.pg_namespace public_namespace
  ON public_namespace.nspname = 'public'
LEFT JOIN pg_catalog.pg_class public_object
  ON public_object.relnamespace = public_namespace.oid
 AND public_object.relname = manifest.object_name
 AND public_object.relkind IN ('r', 'p')
LEFT JOIN pg_catalog.pg_stat_user_tables public_stats
  ON public_stats.relid = public_object.oid
LEFT JOIN pg_catalog.pg_namespace legacy_namespace
  ON legacy_namespace.nspname = 'smartstock_legacy'
LEFT JOIN pg_catalog.pg_class legacy_object
  ON legacy_object.relnamespace = legacy_namespace.oid
 AND legacy_object.relname = manifest.object_name
 AND legacy_object.relkind IN ('r', 'p')
LEFT JOIN pg_catalog.pg_stat_user_tables legacy_stats
  ON legacy_stats.relid = legacy_object.oid;

REVOKE ALL ON smartstock_private.cloud_object_inventory
    FROM PUBLIC, anon, authenticated;

CREATE OR REPLACE FUNCTION smartstock_private.assert_legacy_quarantine_intact(
    p_expected_candidates integer DEFAULT 85,
    p_minimum_age interval DEFAULT interval '0 seconds'
)
RETURNS void
LANGUAGE plpgsql
SECURITY INVOKER
SET search_path TO ''
AS $$
DECLARE
    v_candidate_count integer;
BEGIN
    IF p_expected_candidates IS NULL OR p_expected_candidates <= 0 THEN
        RAISE EXCEPTION 'A positive expected candidate count is required.';
    END IF;
    IF p_minimum_age IS NULL OR p_minimum_age < interval '0 seconds' THEN
        RAISE EXCEPTION 'A non-negative minimum quarantine age is required.';
    END IF;

    SELECT pg_catalog.count(*)::integer
      INTO v_candidate_count
      FROM smartstock_private.cloud_object_manifest
     WHERE disposition = 'LEGACY_CANDIDATE';
    IF v_candidate_count <> p_expected_candidates THEN
        RAISE EXCEPTION 'Expected % legacy candidates, but manifest contains %.',
            p_expected_candidates, v_candidate_count;
    END IF;

    IF EXISTS (
        SELECT 1
          FROM smartstock_private.cloud_object_inventory
         WHERE disposition = 'LEGACY_CANDIDATE'
           AND (public_exists OR NOT legacy_exists OR current_schema <> 'smartstock_legacy')
    ) THEN
        RAISE EXCEPTION 'Every legacy candidate must exist only in smartstock_legacy.';
    END IF;

    IF EXISTS (
        SELECT 1
          FROM smartstock_private.cloud_object_inventory
         WHERE disposition = 'RETAIN'
           AND (NOT public_exists OR legacy_exists OR current_schema <> 'public')
    ) THEN
        RAISE EXCEPTION 'Every retained object must exist only in public.';
    END IF;

    IF EXISTS (
        SELECT 1
          FROM smartstock_private.cloud_object_manifest
         WHERE disposition = 'LEGACY_CANDIDATE'
           AND (quarantine_started_at IS NULL
                OR quarantine_started_at > pg_catalog.now() - p_minimum_age)
    ) THEN
        RAISE EXCEPTION 'The complete legacy quarantine has not reached the required age of %.',
            p_minimum_age;
    END IF;

    IF (SELECT pg_catalog.count(DISTINCT quarantine_started_at)
          FROM smartstock_private.cloud_object_manifest
         WHERE disposition = 'LEGACY_CANDIDATE') <> 1 THEN
        RAISE EXCEPTION 'Legacy candidates do not share one quarantine start time.';
    END IF;

    IF pg_catalog.has_schema_privilege('anon', 'smartstock_legacy', 'USAGE')
       OR pg_catalog.has_schema_privilege('authenticated', 'smartstock_legacy', 'USAGE')
       OR pg_catalog.has_schema_privilege('service_role', 'smartstock_legacy', 'USAGE') THEN
        RAISE EXCEPTION 'A Supabase API role can still use the quarantine schema.';
    END IF;
END
$$;

REVOKE ALL ON FUNCTION smartstock_private.assert_legacy_quarantine_intact(integer, interval)
    FROM PUBLIC, anon, authenticated;

NOTIFY pgrst, 'reload schema';
