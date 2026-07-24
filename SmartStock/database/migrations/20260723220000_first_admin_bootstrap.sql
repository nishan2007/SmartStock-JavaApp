-- Server-only, one-time bootstrap used after SmartStock initializes a new project.
-- The function is intentionally idempotent so a local failure can be retried.

CREATE SCHEMA IF NOT EXISTS smartstock_private;

CREATE TABLE IF NOT EXISTS smartstock_private.first_admin_bootstrap (
    bootstrap_key TEXT PRIMARY KEY,
    auth_user_id UUID NOT NULL,
    user_id INTEGER NOT NULL,
    location_id INTEGER NOT NULL,
    normalized_email TEXT NOT NULL,
    completed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

REVOKE ALL ON SCHEMA smartstock_private FROM PUBLIC, anon, authenticated;
REVOKE ALL ON smartstock_private.first_admin_bootstrap
FROM PUBLIC, anon, authenticated;

CREATE OR REPLACE FUNCTION public.smartstock_bootstrap_first_admin(payload JSONB)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    v_auth_user_id UUID;
    v_username TEXT;
    v_email TEXT;
    v_full_name TEXT;
    v_nickname TEXT;
    v_badge_id TEXT;
    v_badge_secret_salt TEXT;
    v_badge_secret_hash TEXT;
    v_badge_generated_at TIMESTAMPTZ;
    v_date_of_birth DATE;
    v_location_id INTEGER;
    v_store_name TEXT;
    v_store_code TEXT;
    v_timezone TEXT;
    v_address TEXT;
    v_role_id INTEGER;
    v_user_id INTEGER;
    v_existing smartstock_private.first_admin_bootstrap%ROWTYPE;
BEGIN
    v_auth_user_id := NULLIF(BTRIM(payload->>'auth_user_id'), '')::UUID;
    v_username := NULLIF(BTRIM(payload->>'username'), '');
    v_email := LOWER(NULLIF(BTRIM(payload->>'email'), ''));
    v_full_name := NULLIF(BTRIM(payload->>'full_name'), '');
    v_nickname := NULLIF(BTRIM(payload->>'nickname'), '');
    v_badge_id := NULLIF(BTRIM(payload->>'badge_id'), '');
    v_badge_secret_salt := NULLIF(payload->>'badge_secret_salt', '');
    v_badge_secret_hash := NULLIF(payload->>'badge_secret_hash', '');
    v_badge_generated_at := NULLIF(payload->>'badge_generated_at', '')::TIMESTAMPTZ;
    v_date_of_birth := NULLIF(payload->>'date_of_birth', '')::DATE;
    v_location_id := NULLIF(payload->>'location_id', '')::INTEGER;
    v_store_name := NULLIF(BTRIM(payload->>'store_name'), '');
    v_store_code := NULLIF(BTRIM(payload->>'store_code'), '');
    v_timezone := COALESCE(NULLIF(BTRIM(payload->>'timezone'), ''), 'America/New_York');
    v_address := NULLIF(BTRIM(payload->>'address'), '');

    IF v_auth_user_id IS NULL OR v_username IS NULL OR v_email IS NULL
       OR v_full_name IS NULL OR v_location_id IS NULL
       OR v_store_name IS NULL OR v_store_code IS NULL THEN
        RAISE EXCEPTION 'First administrator identity and store fields are required.';
    END IF;
    IF v_store_code !~ '^[0-9]{4}$' OR v_store_code = '0000' THEN
        RAISE EXCEPTION 'Store code must contain four digits from 0001 to 9999.';
    END IF;
    IF (v_badge_secret_salt IS NULL) <> (v_badge_secret_hash IS NULL) THEN
        RAISE EXCEPTION 'Badge verifier metadata is incomplete.';
    END IF;

    SELECT * INTO v_existing
    FROM smartstock_private.first_admin_bootstrap
    WHERE bootstrap_key = 'primary';

    IF FOUND THEN
        IF v_existing.auth_user_id <> v_auth_user_id
           OR v_existing.normalized_email <> v_email
           OR v_existing.location_id <> v_location_id THEN
            RAISE EXCEPTION 'This Supabase project already has a different first administrator.';
        END IF;
        RETURN jsonb_build_object(
            'user_id', v_existing.user_id,
            'location_id', v_existing.location_id,
            'auth_user_id', v_existing.auth_user_id,
            'reconciled', TRUE
        );
    END IF;

    IF EXISTS (SELECT 1 FROM public.users) THEN
        RAISE EXCEPTION 'The hosted SmartStock user table is not empty; first-admin bootstrap is blocked.';
    END IF;

    SELECT role_id INTO v_role_id
    FROM public.roles
    WHERE UPPER(role_name) = 'ADMIN';
    IF v_role_id IS NULL THEN
        RAISE EXCEPTION 'The ADMIN role is missing.';
    END IF;

    INSERT INTO public.locations (
        location_id, name, receipt_store_code, timezone, address
    )
    VALUES (
        v_location_id, v_store_name, v_store_code, v_timezone, v_address
    )
    ON CONFLICT (location_id) DO UPDATE SET
        name = CASE
            WHEN UPPER(BTRIM(public.locations.receipt_store_code)) = UPPER(v_store_code)
            THEN EXCLUDED.name
            ELSE public.locations.name
        END,
        timezone = CASE
            WHEN UPPER(BTRIM(public.locations.receipt_store_code)) = UPPER(v_store_code)
            THEN EXCLUDED.timezone
            ELSE public.locations.timezone
        END,
        address = CASE
            WHEN UPPER(BTRIM(public.locations.receipt_store_code)) = UPPER(v_store_code)
            THEN EXCLUDED.address
            ELSE public.locations.address
        END;

    IF NOT EXISTS (
        SELECT 1 FROM public.locations
        WHERE location_id = v_location_id
          AND UPPER(BTRIM(receipt_store_code)) = UPPER(v_store_code)
    ) THEN
        RAISE EXCEPTION 'The hosted location ID belongs to a different store.';
    END IF;

    INSERT INTO public.users (
        username, password_hash, full_name, nickname, email, date_of_birth,
        hire_date, badge_id, badge_secret_salt, badge_secret_hash,
        badge_generated_at, badge_print_count, compensation_type, salary,
        role_id, auth_user_id, is_active, password_cache_invalidated_at,
        employee_pin_salt, employee_pin_hash, employee_pin_updated_at
    )
    VALUES (
        v_username, NULL, v_full_name, v_nickname, v_email, v_date_of_birth,
        CURRENT_DATE, v_badge_id, v_badge_secret_salt, v_badge_secret_hash,
        v_badge_generated_at, 0, 'HOURLY', 0,
        v_role_id, v_auth_user_id, TRUE, CURRENT_TIMESTAMP,
        NULL, NULL, NULL
    )
    RETURNING user_id INTO v_user_id;

    INSERT INTO public.user_locations (user_id, location_id)
    VALUES (v_user_id, v_location_id)
    ON CONFLICT (user_id, location_id) DO NOTHING;

    INSERT INTO smartstock_private.first_admin_bootstrap (
        bootstrap_key, auth_user_id, user_id, location_id, normalized_email
    )
    VALUES ('primary', v_auth_user_id, v_user_id, v_location_id, v_email);

    RETURN jsonb_build_object(
        'user_id', v_user_id,
        'location_id', v_location_id,
        'auth_user_id', v_auth_user_id,
        'reconciled', FALSE
    );
END;
$$;

REVOKE ALL ON FUNCTION public.smartstock_bootstrap_first_admin(JSONB)
FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.smartstock_bootstrap_first_admin(JSONB)
TO service_role;

COMMENT ON FUNCTION public.smartstock_bootstrap_first_admin(JSONB) IS
'One-time idempotent first-administrator bootstrap. Server secret only.';
