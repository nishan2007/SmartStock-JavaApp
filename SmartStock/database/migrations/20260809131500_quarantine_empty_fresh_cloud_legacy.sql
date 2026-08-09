-- A newly provisioned cloud database must not expose the local-POS relational
-- schema after the compatibility migration chain has run. Existing projects
-- with any store identity or legacy data are deliberately left untouched for
-- the separately gated backup/quarantine workflow.

DO $$
DECLARE
    candidate record;
    v_has_rows boolean;
BEGIN
    IF EXISTS (SELECT 1 FROM public.locations)
       OR EXISTS (SELECT 1 FROM public.smartstock_store_mirror_status) THEN
        RETURN;
    END IF;

    FOR candidate IN
        SELECT object_name
        FROM smartstock_private.cloud_object_manifest
        WHERE disposition = 'LEGACY_CANDIDATE'
          AND pg_catalog.to_regclass('public.' || object_name) IS NOT NULL
        ORDER BY object_name
    LOOP
        EXECUTE pg_catalog.format(
            'SELECT EXISTS (SELECT 1 FROM public.%I LIMIT 1)',
            candidate.object_name
        ) INTO v_has_rows;
        IF v_has_rows THEN
            RAISE NOTICE 'Fresh-cloud legacy quarantine skipped because public.% contains data.',
                candidate.object_name;
            RETURN;
        END IF;
    END LOOP;

    CREATE SCHEMA IF NOT EXISTS smartstock_legacy;
    REVOKE ALL ON SCHEMA smartstock_legacy
        FROM PUBLIC, anon, authenticated, service_role;

    FOR candidate IN
        SELECT object_name
        FROM smartstock_private.cloud_object_manifest
        WHERE disposition = 'LEGACY_CANDIDATE'
          AND pg_catalog.to_regclass('public.' || object_name) IS NOT NULL
        ORDER BY object_name
    LOOP
        EXECUTE pg_catalog.format(
            'ALTER TABLE public.%I SET SCHEMA smartstock_legacy',
            candidate.object_name
        );
    END LOOP;
END
$$;
