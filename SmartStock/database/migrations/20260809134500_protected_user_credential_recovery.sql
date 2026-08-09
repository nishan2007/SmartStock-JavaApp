-- Employee credential hashes are intentionally excluded from the general row
-- mirror. Recovery may read the already-retained cloud user directory through
-- this service-role-only RPC and merge those hashes into a replacement server.

ALTER TABLE public.smartstock_store_mirror_status
    ADD COLUMN IF NOT EXISTS credentials_verified_at timestamptz;

CREATE OR REPLACE FUNCTION public.smartstock_upsert_store_user_credentials(
    p_location_id integer,
    p_generation_id uuid,
    p_rows jsonb DEFAULT '[]'::jsonb
)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY INVOKER
SET search_path TO ''
AS $$
DECLARE
    v_row jsonb;
    v_user_id integer;
    v_expected integer;
    v_verified_at timestamptz := pg_catalog.clock_timestamp();
BEGIN
    IF p_location_id IS NULL OR p_location_id <= 0 OR p_generation_id IS NULL
       OR pg_catalog.jsonb_typeof(COALESCE(p_rows, '[]'::jsonb)) <> 'array'
       OR pg_catalog.jsonb_array_length(COALESCE(p_rows, '[]'::jsonb)) > 1000 THEN
        RAISE EXCEPTION 'Valid protected credential synchronization values are required.';
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM public.smartstock_store_mirror_status status
        JOIN public.smartstock_store_snapshot_generations generation
          ON generation.generation_id = status.current_generation_id
         AND generation.status = 'COMPLETE'
        WHERE status.location_id = p_location_id
          AND status.current_generation_id = p_generation_id
    ) THEN
        RAISE EXCEPTION 'Credentials may update only the current completed generation.';
    END IF;

    SELECT pg_catalog.count(*)::integer INTO v_expected
    FROM public.smartstock_store_snapshot_rows row
    WHERE row.generation_id = p_generation_id AND row.table_name = 'users';

    IF pg_catalog.jsonb_array_length(COALESCE(p_rows, '[]'::jsonb)) <> v_expected
       OR (SELECT pg_catalog.count(DISTINCT (entry->>'user_id')::integer)
           FROM pg_catalog.jsonb_array_elements(COALESCE(p_rows, '[]'::jsonb)) entry)
          <> v_expected THEN
        RAISE EXCEPTION 'Protected credential rows must exactly match the generation users.';
    END IF;

    FOR v_row IN
        SELECT value FROM pg_catalog.jsonb_array_elements(COALESCE(p_rows, '[]'::jsonb))
    LOOP
        IF pg_catalog.jsonb_typeof(v_row) <> 'object'
           OR COALESCE(v_row->>'user_id', '') !~ '^[1-9][0-9]*$'
           OR pg_catalog.length(COALESCE(v_row->>'password_hash', '')) > 1024
           OR pg_catalog.length(COALESCE(v_row->>'employee_pin_salt', '')) > 1024
           OR pg_catalog.length(COALESCE(v_row->>'employee_pin_hash', '')) > 1024 THEN
            RAISE EXCEPTION 'Invalid protected credential row.';
        END IF;
        v_user_id := (v_row->>'user_id')::integer;
        IF NOT EXISTS (
            SELECT 1 FROM public.smartstock_store_snapshot_rows row
            WHERE row.generation_id = p_generation_id
              AND row.table_name = 'users'
              AND (row.row_data->>'user_id')::integer = v_user_id
        ) THEN
            RAISE EXCEPTION 'Credential user is not present in the recovery generation.';
        END IF;

        UPDATE public.users
        SET password_hash = v_row->>'password_hash',
            password_cache_invalidated_at = NULLIF(
                v_row->>'password_cache_invalidated_at', '')::timestamptz,
            employee_pin_salt = v_row->>'employee_pin_salt',
            employee_pin_hash = v_row->>'employee_pin_hash',
            employee_pin_updated_at = NULLIF(
                v_row->>'employee_pin_updated_at', '')::timestamptz
        WHERE user_id = v_user_id;
        IF NOT FOUND THEN
            RAISE EXCEPTION 'Credential user is missing from the retained user directory.';
        END IF;
    END LOOP;

    UPDATE public.smartstock_store_mirror_status
    SET credentials_verified_at = v_verified_at
    WHERE location_id = p_location_id AND current_generation_id = p_generation_id;

    RETURN pg_catalog.jsonb_build_object(
        'generation_id', p_generation_id,
        'credential_rows', v_expected,
        'verified_at', v_verified_at
    );
END
$$;

CREATE OR REPLACE FUNCTION public.smartstock_store_user_credentials(
    p_location_id integer,
    p_generation_id uuid
)
RETURNS jsonb
LANGUAGE sql
STABLE
SECURITY INVOKER
SET search_path TO ''
AS $$
    WITH authorized_users AS (
        SELECT DISTINCT (row.row_data->>'user_id')::integer AS user_id
        FROM public.smartstock_store_snapshot_generations generation
        JOIN public.smartstock_store_snapshot_rows row
          ON row.generation_id = generation.generation_id
         AND row.table_name = 'users'
        WHERE generation.generation_id = p_generation_id
          AND generation.location_id = p_location_id
          AND generation.status = 'COMPLETE'
    )
    SELECT pg_catalog.jsonb_build_object(
        'generation_id', p_generation_id,
        'rows', COALESCE(pg_catalog.jsonb_agg(
            pg_catalog.jsonb_build_object(
                'user_id', users.user_id,
                'password_hash', users.password_hash,
                'password_cache_invalidated_at', users.password_cache_invalidated_at,
                'employee_pin_salt', users.employee_pin_salt,
                'employee_pin_hash', users.employee_pin_hash,
                'employee_pin_updated_at', users.employee_pin_updated_at
            ) ORDER BY users.user_id
        ), '[]'::jsonb)
    )
    FROM authorized_users
    JOIN public.users ON users.user_id = authorized_users.user_id
$$;

REVOKE ALL ON FUNCTION public.smartstock_store_user_credentials(integer, uuid)
    FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION public.smartstock_upsert_store_user_credentials(integer, uuid, jsonb)
    FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.smartstock_store_user_credentials(integer, uuid)
    TO service_role;
GRANT EXECUTE ON FUNCTION public.smartstock_upsert_store_user_credentials(integer, uuid, jsonb)
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
           OR status.credentials_verified_at IS NULL
           OR status.credentials_verified_at < pg_catalog.now() - p_max_snapshot_age
           OR generation.active_row_count <> status.active_row_count
           OR generation.table_counts <> status.table_counts
    ) THEN
        RAISE EXCEPTION 'Every store requires a recently verified recovery generation and protected credential set.';
    END IF;
END
$$;

REVOKE ALL ON FUNCTION smartstock_private.assert_legacy_cleanup_ready(interval)
    FROM PUBLIC, anon, authenticated;

NOTIFY pgrst, 'reload schema';
