-- wifi_sessions is no longer used by SmartStock. The local PostgreSQL table is
-- intentionally untouched; this migration removes only Supabase copies after
-- the local/cloud row checksum was verified out of band.

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM public.smartstock_store_snapshot_generations
        WHERE status = 'BUILDING'
    ) THEN
        RAISE EXCEPTION 'Cannot remove wifi_sessions while a recovery generation is being built.';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM public.smartstock_store_snapshot_generations generation
        WHERE (generation.table_counts ? 'wifi_sessions'
               OR EXISTS (
                   SELECT 1 FROM public.smartstock_store_snapshot_rows rows
                   WHERE rows.generation_id = generation.generation_id
                     AND rows.table_name = 'wifi_sessions'
               ))
          AND COALESCE((generation.table_counts->>'wifi_sessions')::bigint, 0) <>
              (SELECT pg_catalog.count(*)
               FROM public.smartstock_store_snapshot_rows rows
               WHERE rows.generation_id = generation.generation_id
                 AND rows.table_name = 'wifi_sessions')
    ) THEN
        RAISE EXCEPTION 'A recovery generation has inconsistent wifi_sessions accounting.';
    END IF;

    IF pg_catalog.to_regclass('public.smartstock_store_snapshot_rows') IS NOT NULL THEN
        UPDATE public.smartstock_store_snapshot_generations generation
        SET active_row_count = generation.active_row_count - (
                SELECT pg_catalog.count(*)
                FROM public.smartstock_store_snapshot_rows rows
                WHERE rows.generation_id = generation.generation_id
                  AND rows.table_name = 'wifi_sessions'
            ),
            table_counts = generation.table_counts - 'wifi_sessions'
        WHERE generation.table_counts ? 'wifi_sessions'
           OR EXISTS (
                SELECT 1
                FROM public.smartstock_store_snapshot_rows rows
                WHERE rows.generation_id = generation.generation_id
                  AND rows.table_name = 'wifi_sessions'
            );

        DELETE FROM public.smartstock_store_snapshot_rows
        WHERE table_name = 'wifi_sessions';
    END IF;

    IF pg_catalog.to_regclass('public.smartstock_store_rows') IS NOT NULL THEN
        UPDATE public.smartstock_store_mirror_status status
        SET active_row_count = status.active_row_count
                - COALESCE((status.table_counts->>'wifi_sessions')::bigint, 0),
            table_counts = status.table_counts - 'wifi_sessions'
        WHERE status.table_counts ? 'wifi_sessions'
           OR EXISTS (
                SELECT 1
                FROM public.smartstock_store_rows rows
                WHERE rows.location_id = status.location_id
                  AND rows.table_name = 'wifi_sessions'
            );

        DELETE FROM public.smartstock_store_rows
        WHERE table_name = 'wifi_sessions';
    END IF;

    IF pg_catalog.to_regclass('smartstock_private.cloud_object_manifest') IS NOT NULL THEN
        DELETE FROM smartstock_private.cloud_object_manifest
        WHERE object_type = 'TABLE' AND object_name = 'wifi_sessions';
    END IF;
END
$$;

-- Do not use CASCADE. An unexpected external dependency must stop the migration
-- instead of broadening the approved deletion scope.
DROP TABLE IF EXISTS public.wifi_sessions;
DROP TABLE IF EXISTS smartstock_legacy.wifi_sessions;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM public.smartstock_store_snapshot_generations generation
        WHERE generation.active_row_count <> (
            SELECT pg_catalog.count(*)
            FROM public.smartstock_store_snapshot_rows rows
            WHERE rows.generation_id = generation.generation_id
        ) OR generation.table_counts ? 'wifi_sessions'
    ) THEN
        RAISE EXCEPTION 'A recovery generation count is inconsistent after wifi_sessions removal.';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM public.smartstock_store_mirror_status status
        LEFT JOIN public.smartstock_store_snapshot_generations generation
          ON generation.generation_id = status.current_generation_id
        WHERE status.table_counts ? 'wifi_sessions'
           OR EXISTS (
                SELECT 1 FROM public.smartstock_store_rows rows
                WHERE rows.location_id = status.location_id
                  AND rows.table_name = 'wifi_sessions'
           )
           OR (status.current_generation_id IS NOT NULL AND (
                generation.generation_id IS NULL
                OR generation.status <> 'COMPLETE'
                OR generation.active_row_count <> status.active_row_count
                OR generation.table_counts <> status.table_counts
           ))
    ) THEN
        RAISE EXCEPTION 'A current mirror count is inconsistent after wifi_sessions removal.';
    END IF;
END
$$;
