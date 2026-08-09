-- Distinguish immutable snapshot completion time from the latest successful
-- end-to-end comparison by the store server. A quiet store can prove its
-- existing generation is current without creating duplicate snapshots.

ALTER TABLE public.smartstock_store_mirror_status
    ADD COLUMN IF NOT EXISTS verified_at timestamptz;

UPDATE public.smartstock_store_mirror_status
SET verified_at = COALESCE(verified_at, completed_at);

CREATE OR REPLACE FUNCTION public.smartstock_verify_store_mirror(
    p_location_id integer,
    p_generation_id uuid,
    p_table_counts jsonb,
    p_active_row_count bigint
)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO ''
AS $$
DECLARE
    v_verified_at timestamptz := pg_catalog.clock_timestamp();
BEGIN
    IF p_location_id IS NULL OR p_location_id <= 0 OR p_generation_id IS NULL
       OR p_table_counts IS NULL
       OR pg_catalog.jsonb_typeof(p_table_counts) <> 'object'
       OR p_active_row_count IS NULL OR p_active_row_count < 0 THEN
        RAISE EXCEPTION 'Valid mirror verification values are required.';
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM public.smartstock_store_mirror_status status
        JOIN public.smartstock_store_snapshot_generations generation
          ON generation.generation_id = status.current_generation_id
         AND generation.status = 'COMPLETE'
        WHERE status.location_id = p_location_id
          AND status.current_generation_id = p_generation_id
          AND status.table_counts = p_table_counts
          AND status.active_row_count = p_active_row_count
          AND generation.table_counts = p_table_counts
          AND generation.active_row_count = p_active_row_count
          AND (SELECT pg_catalog.count(*)
               FROM public.smartstock_store_snapshot_rows rows
               WHERE rows.generation_id = p_generation_id) = p_active_row_count
    ) THEN
        RAISE EXCEPTION 'The current recovery generation does not match the local snapshot.';
    END IF;

    UPDATE public.smartstock_store_mirror_status
    SET verified_at = v_verified_at
    WHERE location_id = p_location_id;

    RETURN pg_catalog.jsonb_build_object(
        'verified', true,
        'generation_id', p_generation_id,
        'verified_at', v_verified_at
    );
END
$$;

REVOKE ALL ON FUNCTION public.smartstock_verify_store_mirror(integer, uuid, jsonb, bigint)
    FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.smartstock_verify_store_mirror(integer, uuid, jsonb, bigint)
    TO service_role;

CREATE OR REPLACE FUNCTION smartstock_private.assert_legacy_cleanup_ready(
    p_max_snapshot_age interval DEFAULT interval '15 minutes'
)
RETURNS void
LANGUAGE plpgsql
SECURITY INVOKER
SET search_path TO ''
AS $$
BEGIN
    IF p_max_snapshot_age IS NULL OR p_max_snapshot_age <= interval '0 seconds' THEN
        RAISE EXCEPTION 'A positive maximum snapshot age is required.';
    END IF;
    IF EXISTS (
        SELECT 1 FROM public.smartstock_store_snapshot_generations
        WHERE status = 'BUILDING'
    ) THEN
        RAISE EXCEPTION 'A store recovery generation is still being built.';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM public.locations) THEN
        RAISE EXCEPTION 'No store locations are registered.';
    END IF;
    IF EXISTS (
        SELECT 1
        FROM public.locations location
        LEFT JOIN public.smartstock_store_mirror_status status
          ON status.location_id = location.location_id
        LEFT JOIN public.smartstock_store_snapshot_generations generation
          ON generation.generation_id = status.current_generation_id
         AND generation.status = 'COMPLETE'
        WHERE generation.generation_id IS NULL
           OR status.verified_at IS NULL
           OR status.verified_at < pg_catalog.now() - p_max_snapshot_age
           OR generation.active_row_count <> status.active_row_count
           OR generation.table_counts <> status.table_counts
    ) THEN
        RAISE EXCEPTION 'Every store requires a recently verified, completed, count-matched recovery generation.';
    END IF;
END
$$;

REVOKE ALL ON FUNCTION smartstock_private.assert_legacy_cleanup_ready(interval)
    FROM PUBLIC, anon, authenticated;
