-- Record which classified candidates physically existed when quarantine began.
-- This keeps integrity checks exact without assuming every project started with
-- the same optional legacy tables.

ALTER TABLE smartstock_private.cloud_object_manifest
    ADD COLUMN IF NOT EXISTS quarantine_expected_present boolean;

UPDATE smartstock_private.cloud_object_manifest manifest
SET quarantine_expected_present = CASE
        WHEN manifest.quarantine_started_at IS NULL THEN NULL
        ELSE pg_catalog.to_regclass('public.' || manifest.object_name) IS NOT NULL
          OR pg_catalog.to_regclass('smartstock_legacy.' || manifest.object_name) IS NOT NULL
    END
WHERE manifest.disposition = 'LEGACY_CANDIDATE';

CREATE OR REPLACE FUNCTION smartstock_private.assert_legacy_quarantine_intact(
    p_expected_candidates integer DEFAULT NULL,
    p_minimum_age interval DEFAULT interval '0 seconds'
)
RETURNS void
LANGUAGE plpgsql
SECURITY INVOKER
SET search_path TO ''
AS $$
DECLARE
    v_expected_physical_tables integer;
BEGIN
    IF p_expected_candidates IS NOT NULL AND p_expected_candidates <= 0 THEN
        RAISE EXCEPTION 'Expected quarantined-table count must be positive when supplied.';
    END IF;
    IF p_minimum_age IS NULL OR p_minimum_age < interval '0 seconds' THEN
        RAISE EXCEPTION 'A non-negative minimum quarantine age is required.';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM smartstock_private.cloud_object_manifest
        WHERE disposition = 'LEGACY_CANDIDATE'
    ) OR EXISTS (
        SELECT 1
        FROM smartstock_private.cloud_object_manifest
        WHERE disposition = 'LEGACY_CANDIDATE'
          AND (quarantine_started_at IS NULL
               OR quarantine_expected_present IS NULL)
    ) THEN
        RAISE EXCEPTION 'Every classified legacy name requires a recorded quarantine baseline.';
    END IF;

    SELECT pg_catalog.count(*) FILTER (WHERE quarantine_expected_present)::integer
      INTO v_expected_physical_tables
      FROM smartstock_private.cloud_object_manifest
     WHERE disposition = 'LEGACY_CANDIDATE';
    IF p_expected_candidates IS NOT NULL
       AND v_expected_physical_tables <> p_expected_candidates THEN
        RAISE EXCEPTION 'Expected % quarantined physical tables, but baseline records %.',
            p_expected_candidates, v_expected_physical_tables;
    END IF;

    IF EXISTS (
        SELECT 1
        FROM smartstock_private.cloud_object_inventory inventory
        JOIN smartstock_private.cloud_object_manifest manifest
          ON manifest.object_type = inventory.object_type
         AND manifest.object_name = inventory.object_name
        WHERE manifest.disposition = 'LEGACY_CANDIDATE'
          AND manifest.quarantine_expected_present
          AND (NOT inventory.legacy_exists
               OR inventory.public_exists
               OR inventory.object_schema <> 'smartstock_legacy')
    ) THEN
        RAISE EXCEPTION 'A baseline-present legacy table is not isolated in smartstock_legacy.';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM smartstock_private.cloud_object_inventory inventory
        JOIN smartstock_private.cloud_object_manifest manifest
          ON manifest.object_type = inventory.object_type
         AND manifest.object_name = inventory.object_name
        WHERE manifest.disposition = 'LEGACY_CANDIDATE'
          AND NOT manifest.quarantine_expected_present
          AND inventory.object_exists
    ) THEN
        RAISE EXCEPTION 'A baseline-absent legacy table appeared during quarantine.';
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
          AND quarantine_started_at > pg_catalog.now() - p_minimum_age
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
