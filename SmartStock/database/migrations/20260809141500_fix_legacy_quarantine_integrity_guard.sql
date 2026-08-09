-- Correct the first integrity guard's use of the SQL current_schema keyword
-- and distinguish classified candidates from physical project tables.

ALTER VIEW smartstock_private.cloud_object_inventory
    RENAME COLUMN "current_schema" TO object_schema;

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
    v_manifest_candidates integer;
    v_quarantined_tables integer;
BEGIN
    -- The existing parameter name is retained for CREATE OR REPLACE
    -- compatibility; it now means expected physical quarantined tables.
    IF p_expected_candidates IS NULL OR p_expected_candidates <= 0 THEN
        RAISE EXCEPTION 'A positive expected quarantined-table count is required.';
    END IF;
    IF p_minimum_age IS NULL OR p_minimum_age < interval '0 seconds' THEN
        RAISE EXCEPTION 'A non-negative minimum quarantine age is required.';
    END IF;

    SELECT pg_catalog.count(*)::integer
      INTO v_manifest_candidates
      FROM smartstock_private.cloud_object_manifest
     WHERE disposition = 'LEGACY_CANDIDATE';
    IF v_manifest_candidates <> 86 THEN
        RAISE EXCEPTION 'Expected 86 classified legacy names, but manifest contains %.',
            v_manifest_candidates;
    END IF;

    SELECT pg_catalog.count(*)::integer
      INTO v_quarantined_tables
      FROM smartstock_private.cloud_object_inventory inventory
     WHERE inventory.disposition = 'LEGACY_CANDIDATE'
       AND inventory.legacy_exists
       AND NOT inventory.public_exists
       AND inventory.object_schema = 'smartstock_legacy';
    IF v_quarantined_tables <> p_expected_candidates THEN
        RAISE EXCEPTION 'Expected % quarantined physical tables, but found %.',
            p_expected_candidates, v_quarantined_tables;
    END IF;

    IF EXISTS (
        SELECT 1
          FROM smartstock_private.cloud_object_inventory inventory
         WHERE inventory.disposition = 'LEGACY_CANDIDATE'
           AND inventory.public_exists
    ) THEN
        RAISE EXCEPTION 'A legacy candidate is still present in public.';
    END IF;

    IF EXISTS (
        SELECT 1
          FROM smartstock_private.cloud_object_inventory inventory
         WHERE inventory.disposition = 'LEGACY_CANDIDATE'
           AND NOT inventory.object_exists
           AND inventory.object_name <> 'balance_sheet_bf_overrides'
    ) OR NOT EXISTS (
        SELECT 1
          FROM smartstock_private.cloud_object_inventory inventory
         WHERE inventory.disposition = 'LEGACY_CANDIDATE'
           AND inventory.object_name = 'balance_sheet_bf_overrides'
           AND NOT inventory.object_exists
    ) THEN
        RAISE EXCEPTION 'Only balance_sheet_bf_overrides may be absent from this development project.';
    END IF;

    IF EXISTS (
        SELECT 1
          FROM smartstock_private.cloud_object_inventory inventory
         WHERE inventory.disposition = 'RETAIN'
           AND (NOT inventory.public_exists
                OR inventory.legacy_exists
                OR inventory.object_schema <> 'public')
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
