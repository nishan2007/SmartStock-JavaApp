-- Failed or terminated uploads must never leave cleanup and recovery readiness
-- blocked indefinitely. Only incomplete generations are removable; completed
-- recovery generations remain immutable.

CREATE OR REPLACE FUNCTION public.smartstock_abandon_store_mirror(
    p_location_id integer,
    p_generation_id uuid
)
RETURNS boolean
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO ''
AS $$
DECLARE
    v_deleted integer;
BEGIN
    IF p_location_id IS NULL OR p_location_id <= 0 OR p_generation_id IS NULL THEN
        RAISE EXCEPTION 'A valid location and generation are required.';
    END IF;

    DELETE FROM public.smartstock_store_snapshot_generations
    WHERE generation_id = p_generation_id
      AND location_id = p_location_id
      AND status = 'BUILDING';
    GET DIAGNOSTICS v_deleted = ROW_COUNT;
    RETURN v_deleted = 1;
END
$$;

CREATE OR REPLACE FUNCTION public.smartstock_discard_abandoned_store_mirrors(
    p_location_id integer,
    p_older_than_seconds integer DEFAULT 900
)
RETURNS integer
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO ''
AS $$
DECLARE
    v_deleted integer;
BEGIN
    IF p_location_id IS NULL OR p_location_id <= 0 THEN
        RAISE EXCEPTION 'A valid location is required.';
    END IF;
    IF p_older_than_seconds IS NULL
       OR p_older_than_seconds < 60
       OR p_older_than_seconds > 86400 THEN
        RAISE EXCEPTION 'The stale-generation age must be between 60 and 86400 seconds.';
    END IF;

    DELETE FROM public.smartstock_store_snapshot_generations
    WHERE location_id = p_location_id
      AND status = 'BUILDING'
      AND started_at < pg_catalog.now()
          - pg_catalog.make_interval(secs => p_older_than_seconds);
    GET DIAGNOSTICS v_deleted = ROW_COUNT;
    RETURN v_deleted;
END
$$;

REVOKE ALL ON FUNCTION public.smartstock_abandon_store_mirror(integer, uuid)
    FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION public.smartstock_discard_abandoned_store_mirrors(integer, integer)
    FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.smartstock_abandon_store_mirror(integer, uuid)
    TO service_role;
GRANT EXECUTE ON FUNCTION public.smartstock_discard_abandoned_store_mirrors(integer, integer)
    TO service_role;
