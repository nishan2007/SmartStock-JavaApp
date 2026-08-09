-- Credential verifiers must never live in the API-readable employee directory.
-- Keep them bound to immutable recovery generations in an unexposed schema.

CREATE TABLE IF NOT EXISTS smartstock_private.store_user_credentials (
    location_id integer NOT NULL,
    generation_id uuid NOT NULL
        REFERENCES public.smartstock_store_snapshot_generations(generation_id)
        ON DELETE CASCADE,
    user_id integer NOT NULL,
    password_hash text,
    password_cache_invalidated_at timestamptz,
    employee_pin_salt text,
    employee_pin_hash text,
    employee_pin_updated_at timestamptz,
    badge_secret_salt text,
    badge_secret_hash text,
    verified_at timestamptz NOT NULL DEFAULT pg_catalog.now(),
    PRIMARY KEY (generation_id, user_id),
    CHECK (pg_catalog.length(COALESCE(password_hash, '')) <= 1024),
    CHECK (pg_catalog.length(COALESCE(employee_pin_salt, '')) <= 1024),
    CHECK (pg_catalog.length(COALESCE(employee_pin_hash, '')) <= 1024),
    CHECK (pg_catalog.length(COALESCE(badge_secret_salt, '')) <= 1024),
    CHECK (pg_catalog.length(COALESCE(badge_secret_hash, '')) <= 1024),
    CHECK ((employee_pin_salt IS NULL) = (employee_pin_hash IS NULL)),
    CHECK ((badge_secret_salt IS NULL) = (badge_secret_hash IS NULL))
);

ALTER TABLE smartstock_private.store_user_credentials ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON TABLE smartstock_private.store_user_credentials
    FROM PUBLIC, anon, authenticated;
GRANT USAGE ON SCHEMA smartstock_private TO service_role;
GRANT SELECT, INSERT, UPDATE, DELETE
    ON TABLE smartstock_private.store_user_credentials TO service_role;

-- Preserve every currently recoverable generation before clearing public.users.
INSERT INTO smartstock_private.store_user_credentials(
    location_id, generation_id, user_id, password_hash,
    password_cache_invalidated_at, employee_pin_salt, employee_pin_hash,
    employee_pin_updated_at, badge_secret_salt, badge_secret_hash
)
SELECT generation.location_id, generation.generation_id,
       (snapshot.row_data->>'user_id')::integer,
       users.password_hash, users.password_cache_invalidated_at,
       users.employee_pin_salt, users.employee_pin_hash,
       users.employee_pin_updated_at, users.badge_secret_salt,
       users.badge_secret_hash
FROM public.smartstock_store_snapshot_generations generation
JOIN public.smartstock_store_snapshot_rows snapshot
  ON snapshot.generation_id = generation.generation_id
 AND snapshot.table_name = 'users'
JOIN public.users
  ON users.user_id = (snapshot.row_data->>'user_id')::integer
WHERE generation.status = 'COMPLETE'
ON CONFLICT (generation_id, user_id) DO UPDATE
SET password_hash = EXCLUDED.password_hash,
    password_cache_invalidated_at = EXCLUDED.password_cache_invalidated_at,
    employee_pin_salt = EXCLUDED.employee_pin_salt,
    employee_pin_hash = EXCLUDED.employee_pin_hash,
    employee_pin_updated_at = EXCLUDED.employee_pin_updated_at,
    badge_secret_salt = EXCLUDED.badge_secret_salt,
    badge_secret_hash = EXCLUDED.badge_secret_hash,
    verified_at = pg_catalog.now();

CREATE OR REPLACE FUNCTION smartstock_private.clear_public_user_credentials()
RETURNS trigger
LANGUAGE plpgsql
SECURITY INVOKER
SET search_path TO ''
AS $$
BEGIN
    NEW.password_hash := NULL;
    NEW.password_cache_invalidated_at := NULL;
    NEW.employee_pin_salt := NULL;
    NEW.employee_pin_hash := NULL;
    NEW.employee_pin_updated_at := NULL;
    NEW.badge_secret_salt := NULL;
    NEW.badge_secret_hash := NULL;
    RETURN NEW;
END
$$;

REVOKE ALL ON FUNCTION smartstock_private.clear_public_user_credentials()
    FROM PUBLIC, anon, authenticated, service_role;

DROP TRIGGER IF EXISTS users_clear_public_credentials ON public.users;
CREATE TRIGGER users_clear_public_credentials
BEFORE INSERT OR UPDATE OF password_hash, password_cache_invalidated_at,
    employee_pin_salt, employee_pin_hash, employee_pin_updated_at,
    badge_secret_salt, badge_secret_hash
ON public.users
FOR EACH ROW EXECUTE FUNCTION smartstock_private.clear_public_user_credentials();

UPDATE public.users
SET password_hash = NULL,
    password_cache_invalidated_at = NULL,
    employee_pin_salt = NULL,
    employee_pin_hash = NULL,
    employee_pin_updated_at = NULL,
    badge_secret_salt = NULL,
    badge_secret_hash = NULL
WHERE password_hash IS NOT NULL
   OR password_cache_invalidated_at IS NOT NULL
   OR employee_pin_salt IS NOT NULL
   OR employee_pin_hash IS NOT NULL
   OR employee_pin_updated_at IS NOT NULL
   OR badge_secret_salt IS NOT NULL
   OR badge_secret_hash IS NOT NULL;

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

    DELETE FROM smartstock_private.store_user_credentials
    WHERE generation_id = p_generation_id;

    FOR v_row IN
        SELECT value FROM pg_catalog.jsonb_array_elements(COALESCE(p_rows, '[]'::jsonb))
    LOOP
        IF pg_catalog.jsonb_typeof(v_row) <> 'object'
           OR COALESCE(v_row->>'user_id', '') !~ '^[1-9][0-9]*$'
           OR pg_catalog.length(COALESCE(v_row->>'password_hash', '')) > 1024
           OR pg_catalog.length(COALESCE(v_row->>'employee_pin_salt', '')) > 1024
           OR pg_catalog.length(COALESCE(v_row->>'employee_pin_hash', '')) > 1024
           OR pg_catalog.length(COALESCE(v_row->>'badge_secret_salt', '')) > 1024
           OR pg_catalog.length(COALESCE(v_row->>'badge_secret_hash', '')) > 1024
           OR ((v_row->>'employee_pin_salt' IS NULL) <>
               (v_row->>'employee_pin_hash' IS NULL))
           OR ((v_row->>'badge_secret_salt' IS NULL) <>
               (v_row->>'badge_secret_hash' IS NULL)) THEN
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

        INSERT INTO smartstock_private.store_user_credentials(
            location_id, generation_id, user_id, password_hash,
            password_cache_invalidated_at, employee_pin_salt,
            employee_pin_hash, employee_pin_updated_at,
            badge_secret_salt, badge_secret_hash, verified_at
        ) VALUES (
            p_location_id, p_generation_id, v_user_id,
            v_row->>'password_hash',
            NULLIF(v_row->>'password_cache_invalidated_at', '')::timestamptz,
            v_row->>'employee_pin_salt', v_row->>'employee_pin_hash',
            NULLIF(v_row->>'employee_pin_updated_at', '')::timestamptz,
            v_row->>'badge_secret_salt', v_row->>'badge_secret_hash',
            v_verified_at
        );
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
    SELECT pg_catalog.jsonb_build_object(
        'generation_id', p_generation_id,
        'rows', COALESCE(pg_catalog.jsonb_agg(
            pg_catalog.jsonb_build_object(
                'user_id', credential.user_id,
                'password_hash', credential.password_hash,
                'password_cache_invalidated_at', credential.password_cache_invalidated_at,
                'employee_pin_salt', credential.employee_pin_salt,
                'employee_pin_hash', credential.employee_pin_hash,
                'employee_pin_updated_at', credential.employee_pin_updated_at,
                'badge_secret_salt', credential.badge_secret_salt,
                'badge_secret_hash', credential.badge_secret_hash
            ) ORDER BY credential.user_id
        ), '[]'::jsonb)
    )
    FROM smartstock_private.store_user_credentials credential
    JOIN public.smartstock_store_snapshot_generations generation
      ON generation.generation_id = credential.generation_id
     AND generation.location_id = p_location_id
     AND generation.status = 'COMPLETE'
    WHERE credential.location_id = p_location_id
      AND credential.generation_id = p_generation_id
$$;

REVOKE ALL ON FUNCTION public.smartstock_store_user_credentials(integer, uuid)
    FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION public.smartstock_upsert_store_user_credentials(integer, uuid, jsonb)
    FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.smartstock_store_user_credentials(integer, uuid)
    TO service_role;
GRANT EXECUTE ON FUNCTION public.smartstock_upsert_store_user_credentials(integer, uuid, jsonb)
    TO service_role;

NOTIFY pgrst, 'reload schema';

