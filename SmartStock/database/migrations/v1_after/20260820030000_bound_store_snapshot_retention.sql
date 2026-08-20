ALTER TABLE public.smartstock_store_snapshot_generations
    DROP CONSTRAINT IF EXISTS smartstock_store_snapshot_generatio_based_on_generation_id_fkey;

ALTER TABLE public.smartstock_store_snapshot_generations
    DROP CONSTRAINT IF EXISTS smartstock_store_snapshot_generations_based_on_fkey;

ALTER TABLE public.smartstock_store_snapshot_generations
    ADD CONSTRAINT smartstock_store_snapshot_generations_based_on_fkey
    FOREIGN KEY (based_on_generation_id)
    REFERENCES public.smartstock_store_snapshot_generations(generation_id)
    ON DELETE SET NULL;

CREATE OR REPLACE FUNCTION public.smartstock_prune_store_mirror_generations(
    p_location_id integer,
    p_keep_complete integer DEFAULT 2,
    p_max_delete integer DEFAULT 25
) RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO ''
AS $$
DECLARE
    v_deleted integer;
    v_remaining_generations bigint;
    v_remaining_rows bigint;
BEGIN
    IF p_location_id IS NULL OR p_location_id <= 0 THEN
        RAISE EXCEPTION 'A valid location is required.';
    END IF;
    IF p_keep_complete IS NULL OR p_keep_complete < 1 OR p_keep_complete > 10 THEN
        RAISE EXCEPTION 'Completed generation retention must be between 1 and 10.';
    END IF;
    IF p_max_delete IS NULL OR p_max_delete < 1 OR p_max_delete > 100 THEN
        RAISE EXCEPTION 'The generation deletion batch must be between 1 and 100.';
    END IF;

    WITH ranked AS (
        SELECT generation_id,
               pg_catalog.row_number() OVER (
                   ORDER BY completed_at DESC, generation_id DESC
               ) AS completed_rank
        FROM public.smartstock_store_snapshot_generations
        WHERE location_id = p_location_id AND status = 'COMPLETE'
    ), candidates AS (
        SELECT ranked.generation_id
        FROM ranked
        LEFT JOIN public.smartstock_store_mirror_status status
          ON status.location_id = p_location_id
         AND status.current_generation_id = ranked.generation_id
        WHERE ranked.completed_rank > p_keep_complete
          AND status.current_generation_id IS NULL
        ORDER BY ranked.completed_rank DESC
        LIMIT p_max_delete
    )
    DELETE FROM public.smartstock_store_snapshot_generations generation
    USING candidates
    WHERE generation.generation_id = candidates.generation_id;
    GET DIAGNOSTICS v_deleted = ROW_COUNT;

    SELECT pg_catalog.count(*) INTO v_remaining_generations
    FROM public.smartstock_store_snapshot_generations
    WHERE location_id = p_location_id;

    SELECT pg_catalog.count(*) INTO v_remaining_rows
    FROM public.smartstock_store_snapshot_rows
    WHERE location_id = p_location_id;

    RETURN pg_catalog.jsonb_build_object(
        'location_id', p_location_id,
        'deleted_generations', v_deleted,
        'remaining_generations', v_remaining_generations,
        'remaining_rows', v_remaining_rows
    );
END
$$;

REVOKE ALL ON FUNCTION public.smartstock_prune_store_mirror_generations(integer, integer, integer)
    FROM PUBLIC;
REVOKE ALL ON FUNCTION public.smartstock_prune_store_mirror_generations(integer, integer, integer)
    FROM anon;
REVOKE ALL ON FUNCTION public.smartstock_prune_store_mirror_generations(integer, integer, integer)
    FROM authenticated;
GRANT EXECUTE ON FUNCTION public.smartstock_prune_store_mirror_generations(integer, integer, integer)
    TO service_role;
