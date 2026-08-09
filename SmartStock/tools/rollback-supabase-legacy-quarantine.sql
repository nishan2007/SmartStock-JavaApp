-- Recoverable rollback during the 14-day quarantine window.
-- Required session confirmation:
--   SET smartstock.legacy_cleanup_confirmation =
--       'ROLL BACK SMARTSTOCK LEGACY QUARANTINE';

BEGIN;
SET LOCAL lock_timeout = '5s';

SELECT smartstock_private.assert_legacy_quarantine_intact(NULL, interval '0 seconds');

DO $$
DECLARE
    candidate record;
BEGIN
    IF current_setting('smartstock.legacy_cleanup_confirmation', true)
           IS DISTINCT FROM 'ROLL BACK SMARTSTOCK LEGACY QUARANTINE' THEN
        RAISE EXCEPTION 'Explicit legacy rollback confirmation is required.';
    END IF;

    FOR candidate IN
        SELECT manifest.object_name
        FROM smartstock_private.cloud_object_manifest manifest
        WHERE manifest.disposition = 'LEGACY_CANDIDATE'
          AND pg_catalog.to_regclass('smartstock_legacy.' || manifest.object_name) IS NOT NULL
        ORDER BY manifest.object_name
    LOOP
        IF pg_catalog.to_regclass('public.' || candidate.object_name) IS NOT NULL THEN
            RAISE EXCEPTION 'Cannot restore %, because public.% already exists.',
                candidate.object_name, candidate.object_name;
        END IF;
        EXECUTE pg_catalog.format(
            'ALTER TABLE smartstock_legacy.%I SET SCHEMA public',
            candidate.object_name
        );
    END LOOP;

    UPDATE smartstock_private.cloud_object_manifest
    SET quarantine_started_at = NULL,
        last_verified_at = pg_catalog.now()
    WHERE disposition = 'LEGACY_CANDIDATE';
END
$$;

DO $$
DECLARE
    v_public_candidates integer;
    v_expected_candidates integer;
BEGIN
    SELECT pg_catalog.count(*) FILTER (WHERE quarantine_expected_present)::integer
      INTO v_expected_candidates
      FROM smartstock_private.cloud_object_manifest
     WHERE disposition = 'LEGACY_CANDIDATE';

    SELECT pg_catalog.count(*)::integer
      INTO v_public_candidates
      FROM smartstock_private.cloud_object_inventory
     WHERE disposition = 'LEGACY_CANDIDATE'
       AND public_exists
       AND NOT legacy_exists;
    IF v_public_candidates <> v_expected_candidates THEN
        RAISE EXCEPTION 'Rollback restored % of % baseline-present legacy candidates.',
            v_public_candidates, v_expected_candidates;
    END IF;
END
$$;

UPDATE smartstock_private.cloud_object_manifest
SET quarantine_expected_present = NULL
WHERE disposition = 'LEGACY_CANDIDATE';

COMMIT;
