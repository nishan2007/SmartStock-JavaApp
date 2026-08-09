-- Run only through the TLS Supabase administrator connection after deploying
-- the versioned-mirror compatibility release and completing a recovery drill.
--
-- Required session confirmation:
--   SET smartstock.legacy_cleanup_confirmation =
--       'QUARANTINE SMARTSTOCK LEGACY TABLES';

BEGIN;
SET LOCAL lock_timeout = '5s';

SELECT public.smartstock_discard_abandoned_store_mirrors(location_id, 900)
FROM public.locations;
SELECT smartstock_private.assert_legacy_cleanup_ready(interval '15 minutes');

DO $$
DECLARE
    candidate record;
BEGIN
    IF current_setting('smartstock.legacy_cleanup_confirmation', true)
           IS DISTINCT FROM 'QUARANTINE SMARTSTOCK LEGACY TABLES' THEN
        RAISE EXCEPTION 'Explicit legacy quarantine confirmation is required.';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM smartstock_private.cloud_object_manifest manifest
        JOIN pg_catalog.pg_class object ON object.relname = manifest.object_name
        JOIN pg_catalog.pg_namespace namespace ON namespace.oid = object.relnamespace
        WHERE manifest.disposition = 'LEGACY_CANDIDATE'
          AND namespace.nspname = 'smartstock_legacy'
    ) THEN
        RAISE EXCEPTION 'A legacy quarantine is already active.';
    END IF;

    CREATE SCHEMA IF NOT EXISTS smartstock_legacy;
    REVOKE ALL ON SCHEMA smartstock_legacy
        FROM PUBLIC, anon, authenticated, service_role;

    UPDATE smartstock_private.cloud_object_manifest manifest
    SET quarantine_expected_present =
            pg_catalog.to_regclass('public.' || manifest.object_name) IS NOT NULL
    WHERE manifest.disposition = 'LEGACY_CANDIDATE';

    IF (SELECT pg_catalog.count(*)
        FROM smartstock_private.cloud_object_manifest
        WHERE disposition = 'LEGACY_CANDIDATE'
          AND quarantine_expected_present) <> 85 THEN
        RAISE EXCEPTION 'This approved development quarantine requires exactly 85 physical tables.';
    END IF;

    FOR candidate IN
        SELECT manifest.object_name
        FROM smartstock_private.cloud_object_manifest manifest
        WHERE manifest.disposition = 'LEGACY_CANDIDATE'
          AND pg_catalog.to_regclass('public.' || manifest.object_name) IS NOT NULL
        ORDER BY manifest.object_name
    LOOP
        EXECUTE pg_catalog.format(
            'ALTER TABLE public.%I SET SCHEMA smartstock_legacy',
            candidate.object_name
        );
    END LOOP;

    UPDATE smartstock_private.cloud_object_manifest
    SET quarantine_started_at = pg_catalog.now(),
        last_verified_at = pg_catalog.now()
    WHERE disposition = 'LEGACY_CANDIDATE';
END
$$;

COMMIT;
