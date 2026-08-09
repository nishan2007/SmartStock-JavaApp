-- The base schema seeds one placeholder location, so location existence alone
-- cannot distinguish a fresh project. Quarantine empty local-POS compatibility
-- tables only when no operational cloud state or candidate data exists.

DO $$
DECLARE
    candidate record;
    v_has_rows boolean;
    v_quarantine_started_at timestamptz := pg_catalog.now();
BEGIN
    IF EXISTS (
        SELECT 1 FROM smartstock_private.cloud_object_manifest
        WHERE disposition = 'LEGACY_CANDIDATE'
          AND quarantine_started_at IS NOT NULL
    ) OR EXISTS (SELECT 1 FROM public.users)
       OR EXISTS (SELECT 1 FROM public.devices)
       OR EXISTS (SELECT 1 FROM public.smartstock_store_rows)
       OR EXISTS (SELECT 1 FROM public.smartstock_store_mirror_status)
       OR EXISTS (SELECT 1 FROM public.smartstock_store_snapshot_generations)
       OR EXISTS (SELECT 1 FROM public.sync_outbox)
       OR EXISTS (SELECT 1 FROM public.sync_applied_events)
       OR EXISTS (SELECT 1 FROM public.store_server_instances)
       OR EXISTS (SELECT 1 FROM public.remote_admin_commands) THEN
        RETURN;
    END IF;

    FOR candidate IN
        SELECT object_name
        FROM smartstock_private.cloud_object_manifest
        WHERE disposition = 'LEGACY_CANDIDATE'
          AND pg_catalog.to_regclass('public.' || object_name) IS NOT NULL
          AND object_name NOT IN (
              'categories', 'company_info', 'custom_order_design_placements',
              'customer_types', 'employee_schedule_shifts',
              'time_clock_auto_close_settings'
          )
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

    UPDATE smartstock_private.cloud_object_manifest manifest
    SET quarantine_expected_present =
            pg_catalog.to_regclass('public.' || manifest.object_name) IS NOT NULL,
        quarantine_started_at = v_quarantine_started_at,
        last_verified_at = v_quarantine_started_at
    WHERE manifest.disposition = 'LEGACY_CANDIDATE';

    FOR candidate IN
        SELECT object_name
        FROM smartstock_private.cloud_object_manifest
        WHERE disposition = 'LEGACY_CANDIDATE'
          AND quarantine_expected_present
        ORDER BY object_name
    LOOP
        EXECUTE pg_catalog.format(
            'ALTER TABLE public.%I SET SCHEMA smartstock_legacy',
            candidate.object_name
        );
    END LOOP;

    PERFORM smartstock_private.assert_legacy_quarantine_intact(
        NULL, interval '0 seconds');
END
$$;

NOTIFY pgrst, 'reload schema';
