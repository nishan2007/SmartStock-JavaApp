--
-- PostgreSQL database dump
--

-- SmartStock Supabase v1 canonical baseline. This script is fresh-project only.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM pg_catalog.pg_class c
        JOIN pg_catalog.pg_namespace n ON n.oid = c.relnamespace
        WHERE n.nspname = 'public' AND c.relkind IN ('r', 'p')
    ) OR EXISTS (
        SELECT 1 FROM pg_catalog.pg_namespace
        WHERE nspname = 'smartstock_private'
    ) THEN
        RAISE EXCEPTION 'SmartStock cloud v1 baseline requires an empty Supabase project';
    END IF;
END
$$;


-- Dumped from database version 18.3 (Homebrew)
-- Dumped by pg_dump version 18.3 (Homebrew)

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

--
-- Name: public; Type: SCHEMA; Schema: -; Owner: -
--



--
-- Name: SCHEMA public; Type: COMMENT; Schema: -; Owner: -
--

COMMENT ON SCHEMA public IS 'standard public schema';


--
-- Name: smartstock_private; Type: SCHEMA; Schema: -; Owner: -
--

CREATE SCHEMA smartstock_private;


--
-- Name: compensation_type_enum; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public.compensation_type_enum AS ENUM (
    'HOURLY',
    'SALARY',
    'DAILY'
);


--
-- Name: pay_period_type_enum; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public.pay_period_type_enum AS ENUM (
    'SEMI_MONTHLY',
    'WEEKLY'
);


--
-- Name: current_app_user_can_manage_employee_files(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.current_app_user_can_manage_employee_files() RETURNS boolean
    LANGUAGE sql STABLE SECURITY DEFINER
    SET search_path TO 'pg_catalog', 'public'
    AS $$ SELECT EXISTS (SELECT 1 FROM public.users u LEFT JOIN public.role_permissions rp ON rp.role_id=u.role_id LEFT JOIN public.permissions p ON p.permission_id=rp.permission_id WHERE u.auth_user_id=auth.uid() AND COALESCE(u.is_active,true)=true AND (u.role_id=1 OR UPPER(COALESCE(p.permission_key,'')) IN ('EMPLOYEE_MANAGEMENT','COMPANY_PREFERENCES'))) $$;


--
-- Name: current_app_user_has_location(integer); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.current_app_user_has_location(target_location_id integer) RETURNS boolean
    LANGUAGE sql STABLE SECURITY DEFINER
    SET search_path TO 'pg_catalog', 'public'
    AS $$
  SELECT target_location_id IS NOT NULL
     AND auth.uid() IS NOT NULL
     AND (
       COALESCE(public.current_app_user_is_admin(), false)
       OR EXISTS (
         SELECT 1
         FROM public.user_locations ul
         WHERE ul.user_id = public.current_app_user_id()
           AND ul.location_id = target_location_id
       )
     )
$$;


--
-- Name: current_app_user_id(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.current_app_user_id() RETURNS integer
    LANGUAGE sql STABLE SECURITY DEFINER
    SET search_path TO 'pg_catalog', 'public'
    AS $$
  SELECT u.user_id
  FROM public.users u
  WHERE u.auth_user_id = auth.uid()
    AND COALESCE(u.is_active, true) = true
  LIMIT 1
$$;


--
-- Name: current_app_user_is_admin(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.current_app_user_is_admin() RETURNS boolean
    LANGUAGE sql STABLE SECURITY DEFINER
    SET search_path TO 'pg_catalog', 'public'
    AS $$
  SELECT EXISTS (
    SELECT 1
    FROM public.users u
    WHERE u.auth_user_id = auth.uid()
      AND COALESCE(u.is_active, true) = true
      AND u.role_id = 1
  )
$$;


--
-- Name: refresh_device_session_count(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.refresh_device_session_count() RETURNS trigger
    LANGUAGE plpgsql
    SET search_path TO 'pg_catalog', 'public'
    AS $$
            BEGIN
                IF TG_OP IN ('INSERT', 'UPDATE') THEN
                    UPDATE public.devices
                    SET session_count = (
                        SELECT COUNT(*)::BIGINT
                        FROM public.device_sessions
                        WHERE device_id = NEW.device_id
                    )
                    WHERE device_id = NEW.device_id;
                END IF;

                IF TG_OP IN ('DELETE', 'UPDATE')
                   AND (TG_OP = 'DELETE' OR OLD.device_id IS DISTINCT FROM NEW.device_id) THEN
                    UPDATE public.devices
                    SET session_count = (
                        SELECT COUNT(*)::BIGINT
                        FROM public.device_sessions
                        WHERE device_id = OLD.device_id
                    )
                    WHERE device_id = OLD.device_id;
                END IF;

                RETURN COALESCE(NEW, OLD);
            END;
            $$;


--
-- Name: set_devices_updated_at(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.set_devices_updated_at() RETURNS trigger
    LANGUAGE plpgsql
    SET search_path TO 'pg_catalog', 'public'
    AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        NEW.updated_at = COALESCE(NEW.updated_at, CURRENT_TIMESTAMP);
    ELSIF NEW.updated_at IS NOT DISTINCT FROM OLD.updated_at THEN
        NEW.updated_at = CURRENT_TIMESTAMP;
    END IF;
    RETURN NEW;
END;
$$;


--
-- Name: set_email_outbox_updated_at(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.set_email_outbox_updated_at() RETURNS trigger
    LANGUAGE plpgsql
    SET search_path TO 'pg_catalog', 'public'
    AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        NEW.updated_at = COALESCE(NEW.updated_at, CURRENT_TIMESTAMP);
    ELSIF NEW.updated_at IS NOT DISTINCT FROM OLD.updated_at THEN
        NEW.updated_at = CURRENT_TIMESTAMP;
    END IF;
    RETURN NEW;
END;
$$;


--
-- Name: set_image_asset_references_updated_at(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.set_image_asset_references_updated_at() RETURNS trigger
    LANGUAGE plpgsql
    SET search_path TO 'pg_catalog'
    AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        NEW.updated_at = COALESCE(NEW.updated_at, CURRENT_TIMESTAMP);
    ELSIF NEW.updated_at IS NOT DISTINCT FROM OLD.updated_at THEN
        NEW.updated_at = CURRENT_TIMESTAMP;
    END IF;
    RETURN NEW;
END $$;


--
-- Name: set_image_assets_updated_at(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.set_image_assets_updated_at() RETURNS trigger
    LANGUAGE plpgsql
    SET search_path TO 'pg_catalog'
    AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        NEW.updated_at = COALESCE(NEW.updated_at, CURRENT_TIMESTAMP);
    ELSIF NEW.updated_at IS NOT DISTINCT FROM OLD.updated_at THEN
        NEW.updated_at = CURRENT_TIMESTAMP;
    END IF;
    RETURN NEW;
END $$;


--
-- Name: set_locations_updated_at(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.set_locations_updated_at() RETURNS trigger
    LANGUAGE plpgsql
    SET search_path TO 'pg_catalog', 'public'
    AS $$
            BEGIN
                IF TG_OP = 'INSERT' THEN
                    NEW.updated_at = COALESCE(NEW.updated_at, CURRENT_TIMESTAMP);
                ELSIF NEW.updated_at IS NOT DISTINCT FROM OLD.updated_at THEN
                    NEW.updated_at = CURRENT_TIMESTAMP;
                END IF;
                RETURN NEW;
            END;
            $$;


--
-- Name: set_role_mobile_permissions_updated_at(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.set_role_mobile_permissions_updated_at() RETURNS trigger
    LANGUAGE plpgsql
    SET search_path TO 'pg_catalog', 'public'
    AS $$
            BEGIN
                IF TG_OP = 'INSERT' THEN
                    NEW.updated_at = COALESCE(NEW.updated_at, CURRENT_TIMESTAMP);
                ELSIF NEW.updated_at IS NOT DISTINCT FROM OLD.updated_at THEN
                    NEW.updated_at = CURRENT_TIMESTAMP;
                END IF;
                RETURN NEW;
            END;
            $$;


--
-- Name: set_role_permissions_updated_at(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.set_role_permissions_updated_at() RETURNS trigger
    LANGUAGE plpgsql
    SET search_path TO 'pg_catalog', 'public'
    AS $$
            BEGIN
                IF TG_OP = 'INSERT' THEN
                    NEW.updated_at = COALESCE(NEW.updated_at, CURRENT_TIMESTAMP);
                ELSIF NEW.updated_at IS NOT DISTINCT FROM OLD.updated_at THEN
                    NEW.updated_at = CURRENT_TIMESTAMP;
                END IF;
                RETURN NEW;
            END;
            $$;


--
-- Name: set_roles_updated_at(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.set_roles_updated_at() RETURNS trigger
    LANGUAGE plpgsql
    SET search_path TO 'pg_catalog', 'public'
    AS $$
            BEGIN
                IF TG_OP = 'INSERT' THEN
                    NEW.updated_at = COALESCE(NEW.updated_at, CURRENT_TIMESTAMP);
                ELSIF NEW.updated_at IS NOT DISTINCT FROM OLD.updated_at THEN
                    NEW.updated_at = CURRENT_TIMESTAMP;
                END IF;
                RETURN NEW;
            END;
            $$;


--
-- Name: set_user_locations_updated_at(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.set_user_locations_updated_at() RETURNS trigger
    LANGUAGE plpgsql
    SET search_path TO 'pg_catalog', 'public'
    AS $$
            BEGIN
                IF TG_OP = 'INSERT' THEN
                    NEW.updated_at = COALESCE(NEW.updated_at, CURRENT_TIMESTAMP);
                ELSIF NEW.updated_at IS NOT DISTINCT FROM OLD.updated_at THEN
                    NEW.updated_at = CURRENT_TIMESTAMP;
                END IF;
                RETURN NEW;
            END;
            $$;


--
-- Name: set_users_updated_at(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.set_users_updated_at() RETURNS trigger
    LANGUAGE plpgsql
    SET search_path TO 'pg_catalog', 'public'
    AS $$
            BEGIN
                IF TG_OP = 'INSERT' THEN
                    NEW.updated_at = COALESCE(NEW.updated_at, CURRENT_TIMESTAMP);
                ELSIF NEW.updated_at IS NOT DISTINCT FROM OLD.updated_at THEN
                    NEW.updated_at = CURRENT_TIMESTAMP;
                END IF;
                RETURN NEW;
            END;
            $$;


--
-- Name: smartstock_abandon_store_mirror(integer, uuid); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.smartstock_abandon_store_mirror(p_location_id integer, p_generation_id uuid) RETURNS boolean
    LANGUAGE plpgsql SECURITY DEFINER
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


--
-- Name: smartstock_begin_store_mirror(integer, uuid, boolean); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.smartstock_begin_store_mirror(p_location_id integer, p_generation_id uuid, p_clone_current boolean DEFAULT true) RETURNS jsonb
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO ''
    AS $$
DECLARE
    v_base uuid;
    v_existing record;
BEGIN
    IF p_location_id IS NULL OR p_location_id <= 0 OR p_generation_id IS NULL THEN
        RAISE EXCEPTION 'A valid location and generation are required.';
    END IF;

    SELECT generation_id, location_id, status INTO v_existing
    FROM public.smartstock_store_snapshot_generations
    WHERE generation_id = p_generation_id;
    IF FOUND THEN
        IF v_existing.location_id <> p_location_id OR v_existing.status <> 'BUILDING' THEN
            RAISE EXCEPTION 'The mirror generation is not writable for this location.';
        END IF;
        RETURN pg_catalog.jsonb_build_object(
            'generation_id', p_generation_id, 'started', false);
    END IF;

    -- Store sync is serialized locally. Any different BUILDING generation is an
    -- abandoned attempt whose local hashes were never committed.
    DELETE FROM public.smartstock_store_snapshot_generations
    WHERE location_id = p_location_id AND status = 'BUILDING'
      AND generation_id <> p_generation_id;

    IF COALESCE(p_clone_current, true) THEN
        SELECT current_generation_id INTO v_base
        FROM public.smartstock_store_mirror_status
        WHERE location_id = p_location_id;
    ELSE
        v_base := NULL;
    END IF;

    INSERT INTO public.smartstock_store_snapshot_generations(
        generation_id, location_id, based_on_generation_id
    ) VALUES (p_generation_id, p_location_id, v_base);

    IF v_base IS NOT NULL THEN
        INSERT INTO public.smartstock_store_snapshot_rows(
            generation_id, location_id, table_name, row_key, row_data, row_hash,
            source_updated_at
        )
        SELECT p_generation_id, location_id, table_name, row_key, row_data, row_hash,
               source_updated_at
        FROM public.smartstock_store_snapshot_rows
        WHERE generation_id = v_base;
    END IF;

    RETURN pg_catalog.jsonb_build_object(
        'generation_id', p_generation_id,
        'based_on_generation_id', v_base,
        'started', true
    );
END
$$;


--
-- Name: smartstock_bootstrap_first_admin(jsonb); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.smartstock_bootstrap_first_admin(payload jsonb) RETURNS jsonb
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO ''
    AS $_$
DECLARE
    v_auth_user_id UUID;
    v_username TEXT;
    v_email TEXT;
    v_full_name TEXT;
    v_nickname TEXT;
    v_badge_id TEXT;
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
        username, full_name, nickname, email, date_of_birth,
        hire_date, badge_id,
        badge_generated_at, badge_print_count, compensation_type, salary,
        role_id, auth_user_id, is_active, password_cache_invalidated_at,
        employee_pin_updated_at
    )
    VALUES (
        v_username, v_full_name, v_nickname, v_email, v_date_of_birth,
        CURRENT_DATE, v_badge_id,
        v_badge_generated_at, 0, 'HOURLY', 0,
        v_role_id, v_auth_user_id, TRUE, CURRENT_TIMESTAMP,
        NULL
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
$_$;


--
-- Name: FUNCTION smartstock_bootstrap_first_admin(payload jsonb); Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON FUNCTION public.smartstock_bootstrap_first_admin(payload jsonb) IS 'One-time idempotent first-administrator bootstrap. Server secret only.';


--
-- Name: smartstock_cross_store_refund_queue(integer); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.smartstock_cross_store_refund_queue(p_location_id integer) RETURNS jsonb
    LANGUAGE sql STABLE SECURITY DEFINER
    SET search_path TO ''
    AS $$
 SELECT pg_catalog.jsonb_build_object('requests',COALESCE(pg_catalog.jsonb_agg(row_data ORDER BY request_sequence),'[]'::jsonb))
 FROM (SELECT q.request_sequence,pg_catalog.jsonb_build_object('request',pg_catalog.to_jsonb(q),
   'lines',COALESCE((SELECT pg_catalog.jsonb_agg(pg_catalog.to_jsonb(l) ORDER BY l.source_sale_item_id)
     FROM public.smartstock_cross_store_refund_lines l WHERE l.request_id=q.request_id),'[]'::jsonb)) row_data
   FROM public.smartstock_cross_store_refund_requests q
   WHERE q.status NOT IN ('COMPLETED','REJECTED','CANCELLED') AND
    (q.source_location_id=p_location_id OR q.receiving_location_id=p_location_id
      OR EXISTS(SELECT 1 FROM public.smartstock_cross_store_refund_lines l WHERE l.request_id=q.request_id AND l.destination_location_id=p_location_id))
 ) queued $$;


--
-- Name: smartstock_discard_abandoned_store_mirrors(integer, integer); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.smartstock_discard_abandoned_store_mirrors(p_location_id integer, p_older_than_seconds integer DEFAULT 900) RETURNS integer
    LANGUAGE plpgsql SECURITY DEFINER
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


--
-- Name: smartstock_finalize_store_mirror(integer, uuid, jsonb); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.smartstock_finalize_store_mirror(p_location_id integer, p_generation_id uuid, p_table_counts jsonb) RETURNS jsonb
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO ''
    AS $_$
DECLARE
    v_entry record;
    v_total bigint := 0;
    v_actual_counts jsonb;
    v_completed_at timestamptz := pg_catalog.now();
BEGIN
    IF p_location_id IS NULL OR p_location_id <= 0 OR p_generation_id IS NULL
       OR pg_catalog.jsonb_typeof(p_table_counts) <> 'object' THEN
        RAISE EXCEPTION 'A valid location, generation, and table-count object are required.';
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM public.smartstock_store_snapshot_generations
        WHERE generation_id = p_generation_id
          AND location_id = p_location_id AND status = 'BUILDING'
    ) THEN
        RAISE EXCEPTION 'The mirror generation is not writable for this location.';
    END IF;

    FOR v_entry IN SELECT key, value FROM pg_catalog.jsonb_each_text(p_table_counts)
    LOOP
        IF v_entry.key !~ '^[a-z][a-z0-9_]{0,100}$'
           OR v_entry.value !~ '^[0-9]+$' THEN
            RAISE EXCEPTION 'Invalid store mirror table count.';
        END IF;
        v_total := v_total + v_entry.value::bigint;
    END LOOP;

    DELETE FROM public.smartstock_store_snapshot_rows rows
    WHERE rows.generation_id = p_generation_id
      AND NOT (p_table_counts ? rows.table_name);

    SELECT COALESCE(pg_catalog.jsonb_object_agg(
               expected.key,
               (SELECT pg_catalog.count(*)
                FROM public.smartstock_store_snapshot_rows rows
                WHERE rows.generation_id = p_generation_id
                  AND rows.table_name = expected.key)
           ), '{}'::jsonb)
    INTO v_actual_counts
    FROM pg_catalog.jsonb_each_text(p_table_counts) expected;

    IF v_actual_counts <> p_table_counts
       OR (SELECT pg_catalog.count(*)
           FROM public.smartstock_store_snapshot_rows
           WHERE generation_id = p_generation_id) <> v_total THEN
        RAISE EXCEPTION 'Mirror generation row counts do not match the completed local snapshot.';
    END IF;

    UPDATE public.smartstock_store_snapshot_generations
    SET status = 'COMPLETE', table_counts = p_table_counts,
        active_row_count = v_total, completed_at = v_completed_at
    WHERE generation_id = p_generation_id;

    INSERT INTO public.smartstock_store_mirror_status(
        location_id, table_counts, active_row_count, completed_at,
        current_generation_id
    ) VALUES (
        p_location_id, p_table_counts, v_total, v_completed_at, p_generation_id
    )
    ON CONFLICT(location_id) DO UPDATE
    SET table_counts = EXCLUDED.table_counts,
        active_row_count = EXCLUDED.active_row_count,
        completed_at = EXCLUDED.completed_at,
        current_generation_id = EXCLUDED.current_generation_id;

    RETURN pg_catalog.jsonb_build_object(
        'completed', true,
        'generation_id', p_generation_id,
        'active_row_count', v_total,
        'completed_at', v_completed_at
    );
END
$_$;


--
-- Name: smartstock_materialize_store_rows(integer, uuid, jsonb); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.smartstock_materialize_store_rows(p_location_id integer, p_generation_id uuid, p_rows jsonb DEFAULT '[]'::jsonb) RETURNS jsonb
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO ''
    AS $_$
DECLARE
    v_row jsonb;
    v_count integer := 0;
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM public.smartstock_store_snapshot_generations
        WHERE generation_id = p_generation_id
          AND location_id = p_location_id AND status = 'BUILDING'
    ) THEN
        RAISE EXCEPTION 'The mirror generation is not writable for this location.';
    END IF;

    IF pg_catalog.jsonb_typeof(COALESCE(p_rows, '[]'::jsonb)) <> 'array' THEN
        RAISE EXCEPTION 'p_rows must be a JSON array.';
    END IF;
    IF pg_catalog.jsonb_array_length(COALESCE(p_rows, '[]'::jsonb)) > 250 THEN
        RAISE EXCEPTION 'A materialization request cannot contain more than 250 rows.';
    END IF;

    FOR v_row IN
        SELECT value
        FROM pg_catalog.jsonb_array_elements(COALESCE(p_rows, '[]'::jsonb))
    LOOP
        IF COALESCE(v_row->>'table_name', '') !~ '^[a-z][a-z0-9_]{0,100}$'
           OR pg_catalog.jsonb_typeof(v_row->'row_key') <> 'object'
           OR pg_catalog.jsonb_typeof(v_row->'row_data') <> 'object'
           OR pg_catalog.length(COALESCE(v_row->>'row_hash', '')) NOT BETWEEN 16 AND 128 THEN
            RAISE EXCEPTION 'Invalid materialized store row.';
        END IF;
        IF v_row->>'table_name' LIKE 'sync\_%'
           OR v_row->>'table_name' LIKE 'lan\_%'
           OR v_row->>'table_name' IN (
                'local_auth_cache', 'device_credentials'
           ) THEN
            RAISE EXCEPTION 'Table is not allowed in the cloud store mirror.';
        END IF;

        INSERT INTO public.smartstock_store_rows(
            location_id, table_name, row_key, row_data, row_hash, is_deleted,
            source_updated_at
        ) VALUES (
            p_location_id, v_row->>'table_name', v_row->'row_key',
            v_row->'row_data', v_row->>'row_hash',
            COALESCE((v_row->>'is_deleted')::boolean, false),
            CASE WHEN COALESCE(v_row->>'source_updated_at', '') = ''
                THEN NULL ELSE (v_row->>'source_updated_at')::timestamptz END
        )
        ON CONFLICT (location_id, table_name, row_key) DO UPDATE
        SET row_data = EXCLUDED.row_data,
            row_hash = EXCLUDED.row_hash,
            is_deleted = EXCLUDED.is_deleted,
            source_updated_at = EXCLUDED.source_updated_at,
            version_sequence = nextval('public.smartstock_store_row_version_seq'::regclass),
            materialized_at = pg_catalog.now()
        WHERE public.smartstock_store_rows.row_hash IS DISTINCT FROM EXCLUDED.row_hash
           OR public.smartstock_store_rows.is_deleted IS DISTINCT FROM EXCLUDED.is_deleted;

        IF COALESCE((v_row->>'is_deleted')::boolean, false) THEN
            DELETE FROM public.smartstock_store_snapshot_rows
            WHERE generation_id = p_generation_id
              AND table_name = v_row->>'table_name'
              AND row_key = v_row->'row_key';
        ELSE
            INSERT INTO public.smartstock_store_snapshot_rows(
                generation_id, location_id, table_name, row_key, row_data,
                row_hash, source_updated_at
            ) VALUES (
                p_generation_id, p_location_id, v_row->>'table_name',
                v_row->'row_key', v_row->'row_data', v_row->>'row_hash',
                CASE WHEN COALESCE(v_row->>'source_updated_at', '') = ''
                    THEN NULL ELSE (v_row->>'source_updated_at')::timestamptz END
            )
            ON CONFLICT (generation_id, table_name, row_key) DO UPDATE
            SET row_data = EXCLUDED.row_data,
                row_hash = EXCLUDED.row_hash,
                source_updated_at = EXCLUDED.source_updated_at;
        END IF;
        v_count := v_count + 1;
    END LOOP;

    RETURN pg_catalog.jsonb_build_object(
        'acknowledged', v_count, 'generation_id', p_generation_id);
END
$_$;


--
-- Name: smartstock_reserve_cross_store_refund(jsonb); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.smartstock_reserve_cross_store_refund(p_request jsonb) RETURNS jsonb
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO ''
    AS $$
DECLARE v_id uuid; v_source integer; v_receiving integer; v_sale integer; v_line jsonb;
        v_item integer; v_qty integer; v_sold integer; v_returned integer; v_reserved integer;
        v_sequence bigint;
BEGIN
  v_id=(p_request->>'request_id')::uuid; v_source=(p_request->>'source_location_id')::integer;
  v_receiving=(p_request->>'receiving_location_id')::integer; v_sale=(p_request->>'source_sale_id')::integer;
  IF v_source<=0 OR v_receiving<=0 OR v_source=v_receiving OR v_sale<=0
     OR COALESCE(p_request->>'return_receipt_number','')='' OR COALESCE(p_request->>'receipt_device_id','')=''
     OR (p_request->>'receipt_sequence')::integer<=0
     OR pg_catalog.jsonb_typeof(p_request->'lines')<>'array'
     OR pg_catalog.jsonb_array_length(p_request->'lines') NOT BETWEEN 1 AND 200 THEN
    RAISE EXCEPTION 'Invalid cross-store refund request.' USING ERRCODE='22023';
  END IF;
  SELECT request_sequence INTO v_sequence FROM public.smartstock_cross_store_refund_requests WHERE request_id=v_id;
  IF FOUND THEN RETURN pg_catalog.jsonb_build_object('requestId',v_id,'requestSequence',v_sequence,'duplicate',true); END IF;
  FOR v_line IN SELECT value FROM pg_catalog.jsonb_array_elements(p_request->'lines') LOOP
    v_item=(v_line->>'source_sale_item_id')::integer; v_qty=(v_line->>'quantity')::integer;
    SELECT COALESCE((r.row_data->>'quantity')::integer,0) INTO v_sold
      FROM public.smartstock_store_rows r WHERE r.location_id=v_source AND r.table_name='sale_items'
       AND NOT r.is_deleted AND (r.row_data->>'sale_id')::integer=v_sale
       AND (r.row_data->>'sale_item_id')::integer=v_item;
    IF NOT FOUND THEN RAISE EXCEPTION 'Sale item is not available in the latest source mirror.' USING ERRCODE='P0002'; END IF;
    SELECT COALESCE(SUM((r.row_data->>'quantity')::integer),0) INTO v_returned
      FROM public.smartstock_store_rows r WHERE r.location_id=v_source AND r.table_name='sale_return_items'
       AND NOT r.is_deleted AND (r.row_data->>'sale_item_id')::integer=v_item;
    SELECT COALESCE(SUM(l.quantity),0) INTO v_reserved
      FROM public.smartstock_cross_store_refund_lines l JOIN public.smartstock_cross_store_refund_requests q USING(request_id)
      WHERE q.source_location_id=v_source AND q.status NOT IN ('REJECTED','CANCELLED') AND l.source_sale_item_id=v_item;
    IF v_qty<=0 OR v_qty>v_sold-v_returned-v_reserved THEN
      RAISE EXCEPTION 'Requested return quantity is no longer available.' USING ERRCODE='23514';
    END IF;
  END LOOP;
  INSERT INTO public.smartstock_cross_store_refund_requests(request_id,source_location_id,receiving_location_id,
    source_sale_id,refund_method,refund_amount,reason,actor,return_receipt_number,receipt_device_id,receipt_sequence)
  VALUES(v_id,v_source,v_receiving,v_sale,p_request->>'refund_method',(p_request->>'refund_amount')::numeric,
    p_request->>'reason',COALESCE(p_request->'actor','{}'::jsonb),p_request->>'return_receipt_number',
    p_request->>'receipt_device_id',(p_request->>'receipt_sequence')::integer) RETURNING request_sequence INTO v_sequence;
  FOR v_line IN SELECT value FROM pg_catalog.jsonb_array_elements(p_request->'lines') LOOP
    INSERT INTO public.smartstock_cross_store_refund_lines(request_id,source_sale_item_id,product_id,quantity,unit_price,
      disposition,destination_location_id,disposition_reason)
    VALUES(v_id,(v_line->>'source_sale_item_id')::integer,(v_line->>'product_id')::integer,
      (v_line->>'quantity')::integer,(v_line->>'unit_price')::numeric,v_line->>'disposition',
      NULLIF(v_line->>'destination_location_id','')::integer,v_line->>'disposition_reason');
  END LOOP;
  RETURN pg_catalog.jsonb_build_object('requestId',v_id,'requestSequence',v_sequence,'duplicate',false);
END $$;


--
-- Name: smartstock_server_registry(text, jsonb); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.smartstock_server_registry(p_action text, p_payload jsonb DEFAULT '{}'::jsonb) RETURNS jsonb
    LANGUAGE sql
    SET search_path TO ''
    AS $$
    SELECT smartstock_private.smartstock_server_registry(p_action,p_payload)
$$;


--
-- Name: smartstock_store_snapshot_manifest(integer); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.smartstock_store_snapshot_manifest(p_location_id integer) RETURNS jsonb
    LANGUAGE sql STABLE SECURITY DEFINER
    SET search_path TO ''
    AS $_$
    SELECT pg_catalog.jsonb_build_object(
        'generation_id', s.current_generation_id,
        'completed_at', g.completed_at,
        'schema_version', (
            SELECT baseline_version
            FROM smartstock_private.smartstock_schema_metadata
            WHERE schema_scope = 'CLOUD'
        ),
        'schema_ready', COALESCE((
            SELECT baseline_version = 1
              AND resource_fingerprint_sha256 ~ '^[0-9a-f]{64}$'
              AND catalog_fingerprint_sha256 ~ '^[0-9a-f]{64}$'
              AND resource_fingerprint_sha256 <> repeat('0', 64)
              AND catalog_fingerprint_sha256 <> repeat('0', 64)
            FROM smartstock_private.smartstock_schema_metadata
            WHERE schema_scope = 'CLOUD'
        ), false),
        'active_row_count', COALESCE(g.active_row_count, 0),
        'tables', COALESCE((
            SELECT pg_catalog.jsonb_agg(
                pg_catalog.jsonb_build_object(
                    'name', entry.key,
                    'row_count', entry.value::bigint
                ) ORDER BY entry.key
            )
            FROM pg_catalog.jsonb_each_text(g.table_counts) entry
        ), '[]'::jsonb)
    )
    FROM public.smartstock_store_mirror_status s
    JOIN public.smartstock_store_snapshot_generations g
      ON g.generation_id = s.current_generation_id
     AND g.status = 'COMPLETE'
    WHERE s.location_id = p_location_id
$_$;


--
-- Name: smartstock_store_table_snapshot(integer, text, uuid, bigint, integer); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.smartstock_store_table_snapshot(p_location_id integer, p_table_name text, p_generation_id uuid, p_after_sequence bigint DEFAULT 0, p_limit integer DEFAULT 500) RETURNS jsonb
    LANGUAGE sql STABLE SECURITY DEFINER
    SET search_path TO ''
    AS $$
    WITH authorized_generation AS (
        SELECT generation_id
        FROM public.smartstock_store_snapshot_generations
        WHERE generation_id = p_generation_id
          AND location_id = p_location_id AND status = 'COMPLETE'
    ), page AS (
        SELECT r.row_key, r.row_data, r.row_hash, r.source_updated_at,
               r.row_sequence
        FROM public.smartstock_store_snapshot_rows r
        JOIN authorized_generation g USING (generation_id)
        WHERE r.table_name = p_table_name
          AND r.row_sequence > GREATEST(COALESCE(p_after_sequence, 0::bigint), 0::bigint)
        ORDER BY r.row_sequence
        LIMIT LEAST(GREATEST(COALESCE(p_limit, 500), 1), 1000)
    )
    SELECT pg_catalog.jsonb_build_object(
        'generation_id', p_generation_id,
        'rows', COALESCE(pg_catalog.jsonb_agg(
            pg_catalog.jsonb_build_object(
                'row_key', row_key,
                'row_data', row_data,
                'row_hash', row_hash,
                'is_deleted', false,
                'sequence', row_sequence,
                'source_updated_at', source_updated_at
            ) ORDER BY row_sequence
        ), '[]'::jsonb),
        'next_cursor', COALESCE(pg_catalog.max(row_sequence), p_after_sequence, 0)
    )
    FROM page
$$;


--
-- Name: smartstock_store_user_credentials(integer, uuid); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.smartstock_store_user_credentials(p_location_id integer, p_generation_id uuid) RETURNS jsonb
    LANGUAGE sql STABLE
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


--
-- Name: smartstock_sync_exchange(integer, bigint, jsonb, integer); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.smartstock_sync_exchange(p_location_id integer, p_cursor bigint DEFAULT 0, p_events jsonb DEFAULT '[]'::jsonb, p_limit integer DEFAULT 100) RETURNS jsonb
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO ''
    AS $_$
DECLARE
    v_limit integer := LEAST(GREATEST(COALESCE(p_limit, 100), 1), 500);
    v_cursor bigint := GREATEST(COALESCE(p_cursor, 0::bigint), 0::bigint);
    v_event jsonb;
    v_event_id uuid;
    v_acknowledged jsonb := '[]'::jsonb;
    v_changes jsonb := '[]'::jsonb;
    v_next_cursor bigint := v_cursor;
BEGIN
    IF p_location_id IS NULL OR p_location_id <= 0 THEN
        RAISE EXCEPTION 'A valid store location is required.';
    END IF;

    IF pg_catalog.jsonb_typeof(COALESCE(p_events, '[]'::jsonb)) <> 'array' THEN
        RAISE EXCEPTION 'p_events must be a JSON array.';
    END IF;

    IF pg_catalog.jsonb_array_length(COALESCE(p_events, '[]'::jsonb)) > 100 THEN
        RAISE EXCEPTION 'A sync request cannot contain more than 100 events.';
    END IF;

    FOR v_event IN
        SELECT value
        FROM pg_catalog.jsonb_array_elements(COALESCE(p_events, '[]'::jsonb))
    LOOP
        IF pg_catalog.jsonb_typeof(v_event) <> 'object'
           OR COALESCE(v_event->>'event_id', '') = ''
           OR COALESCE(v_event->>'event_type', '') = '' THEN
            RAISE EXCEPTION 'Each sync event requires event_id and event_type.';
        END IF;

        v_event_id := (v_event->>'event_id')::uuid;

        INSERT INTO public.sync_applied_events (
            origin_event_id, event_type, origin_location_id,
            origin_device_id, cloud_reference
        )
        VALUES (
            v_event_id,
            pg_catalog.left(v_event->>'event_type', 200),
            p_location_id,
            NULLIF(v_event->>'device_id', ''),
            'smartstock_sync_exchange'
        )
        ON CONFLICT (origin_event_id) DO NOTHING;

        INSERT INTO public.sync_outbox (
            event_id, event_type, location_id, device_id, user_id, payload,
            status, attempts, created_at, synced_at,
            origin_event_id, origin_location_id, origin_device_id, origin_created_at
        )
        VALUES (
            v_event_id,
            pg_catalog.left(v_event->>'event_type', 200),
            p_location_id,
            NULLIF(v_event->>'device_id', ''),
            CASE WHEN COALESCE(v_event->>'user_id', '') ~ '^[0-9]+$'
                THEN (v_event->>'user_id')::integer ELSE NULL END,
            COALESCE(v_event->'payload', '{}'::jsonb),
            'RECEIVED_FROM_STORE',
            0,
            pg_catalog.now(),
            pg_catalog.now(),
            v_event_id,
            p_location_id,
            NULLIF(v_event->>'device_id', ''),
            CASE WHEN COALESCE(v_event->>'created_at', '') = ''
                THEN pg_catalog.now() ELSE (v_event->>'created_at')::timestamptz END
        )
        ON CONFLICT (event_id) DO NOTHING;

        v_acknowledged := v_acknowledged || pg_catalog.jsonb_build_array(v_event_id::text);
    END LOOP;

    WITH delta AS (
        SELECT o.cloud_sequence, o.event_id, o.event_type, o.location_id,
               o.device_id, o.user_id, o.payload, o.origin_location_id,
               o.origin_device_id, o.origin_created_at
        FROM public.sync_outbox o
        WHERE o.cloud_sequence > v_cursor
          AND o.origin_location_id IS DISTINCT FROM p_location_id
          AND (
              o.location_id IS NULL
              OR o.event_type IN (
                  'PRODUCT_CREATED', 'PRODUCT_UPDATED',
                  'ROLE_CREATED', 'ROLE_PERMISSIONS_UPDATED',
                  'TIME_CLOCK_AUTO_CLOSE_SETTINGS_UPDATED',
                  'DEVICE_ACCESS_UPDATED'
              )
              OR (
                  o.event_type = 'STORE_TRANSFER_CREATED'
                  AND COALESCE(o.payload->>'destination_location_id', '') ~ '^[0-9]+$'
                  AND (o.payload->>'destination_location_id')::integer = p_location_id
              )
          )
        ORDER BY o.cloud_sequence
        LIMIT v_limit
    )
    SELECT
        COALESCE(
            pg_catalog.jsonb_agg(
                pg_catalog.jsonb_build_object(
                    'sequence', cloud_sequence,
                    'event_id', event_id,
                    'event_type', event_type,
                    'location_id', location_id,
                    'device_id', device_id,
                    'user_id', user_id,
                    'payload', payload,
                    'origin_location_id', origin_location_id,
                    'origin_device_id', origin_device_id,
                    'created_at', origin_created_at
                )
                ORDER BY cloud_sequence
            ),
            '[]'::jsonb
        ),
        COALESCE(pg_catalog.max(cloud_sequence), v_cursor)
    INTO v_changes, v_next_cursor
    FROM delta;

    INSERT INTO public.store_sync_status (
        location_id, status, message, last_success_at, last_seen_at, updated_at
    )
    VALUES (
        p_location_id, 'Online', 'Store synchronized through the HTTPS API',
        pg_catalog.now(), pg_catalog.now(), pg_catalog.now()
    )
    ON CONFLICT (location_id) DO UPDATE
    SET status = EXCLUDED.status,
        message = EXCLUDED.message,
        last_success_at = EXCLUDED.last_success_at,
        last_seen_at = EXCLUDED.last_seen_at,
        updated_at = EXCLUDED.updated_at;

    RETURN pg_catalog.jsonb_build_object(
        'acknowledged_event_ids', v_acknowledged,
        'changes', v_changes,
        'next_cursor', v_next_cursor,
        'has_more', pg_catalog.jsonb_array_length(v_changes) = v_limit
    );
END
$_$;


--
-- Name: FUNCTION smartstock_sync_exchange(p_location_id integer, p_cursor bigint, p_events jsonb, p_limit integer); Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON FUNCTION public.smartstock_sync_exchange(p_location_id integer, p_cursor bigint, p_events jsonb, p_limit integer) IS 'Server-only batched SmartStock event upload and cursor-based delta download.';


--
-- Name: smartstock_sync_manifest(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.smartstock_sync_manifest() RETURNS jsonb
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO ''
    AS $_$
DECLARE
    v_table record;
    v_count bigint;
    v_tables jsonb := '[]'::jsonb;
    v_schema_version integer;
    v_schema_ready boolean := false;
BEGIN
    SELECT baseline_version,
           baseline_version = 1
             AND resource_fingerprint_sha256 ~ '^[0-9a-f]{64}$'
             AND catalog_fingerprint_sha256 ~ '^[0-9a-f]{64}$'
             AND resource_fingerprint_sha256 <> repeat('0', 64)
             AND catalog_fingerprint_sha256 <> repeat('0', 64)
    INTO v_schema_version, v_schema_ready
    FROM smartstock_private.smartstock_schema_metadata
    WHERE schema_scope = 'CLOUD';

    FOR v_table IN
        SELECT c.relname AS table_name
        FROM pg_catalog.pg_class c
        JOIN pg_catalog.pg_namespace n ON n.oid = c.relnamespace
        WHERE n.nspname = 'public'
          AND c.relkind IN ('r', 'p')
        ORDER BY c.relname
    LOOP
        EXECUTE pg_catalog.format('SELECT count(*) FROM public.%I', v_table.table_name)
            INTO v_count;
        v_tables := v_tables || pg_catalog.jsonb_build_array(
            pg_catalog.jsonb_build_object(
                'name', v_table.table_name,
                'row_count', v_count,
                'columns', (
                    SELECT COALESCE(
                        pg_catalog.jsonb_agg(a.attname ORDER BY a.attnum),
                        '[]'::jsonb
                    )
                    FROM pg_catalog.pg_attribute a
                    JOIN pg_catalog.pg_class tc ON tc.oid = a.attrelid
                    JOIN pg_catalog.pg_namespace tn ON tn.oid = tc.relnamespace
                    WHERE tn.nspname = 'public'
                      AND tc.relname = v_table.table_name
                      AND a.attnum > 0
                      AND NOT a.attisdropped
                )
            )
        );
    END LOOP;

    RETURN pg_catalog.jsonb_build_object(
        'tables', v_tables,
        'schema_version', v_schema_version,
        'schema_ready', COALESCE(v_schema_ready, false),
        'generated_at', pg_catalog.now()
    );
END
$_$;


--
-- Name: FUNCTION smartstock_sync_manifest(); Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON FUNCTION public.smartstock_sync_manifest() IS 'Server-only schema and recovery row-count manifest for SmartStock.';


--
-- Name: smartstock_update_cross_store_refund(uuid, text, jsonb, text); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.smartstock_update_cross_store_refund(p_request_id uuid, p_status text, p_lines jsonb DEFAULT '[]'::jsonb, p_error text DEFAULT NULL::text) RETURNS jsonb
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO ''
    AS $$
DECLARE v_line jsonb;
BEGIN
 UPDATE public.smartstock_cross_store_refund_requests SET status=p_status,source_error=p_error,updated_at=pg_catalog.now()
 WHERE request_id=p_request_id;
 IF NOT FOUND THEN RAISE EXCEPTION 'Cross-store refund request was not found.' USING ERRCODE='P0002'; END IF;
 FOR v_line IN SELECT value FROM pg_catalog.jsonb_array_elements(COALESCE(p_lines,'[]'::jsonb)) LOOP
   UPDATE public.smartstock_cross_store_refund_lines SET
    source_status=COALESCE(v_line->>'source_status',source_status),
    destination_status=COALESCE(v_line->>'destination_status',destination_status),
    confirmed_quantity=COALESCE((v_line->>'confirmed_quantity')::integer,confirmed_quantity),
    conflict_quantity=COALESCE((v_line->>'conflict_quantity')::integer,conflict_quantity)
   WHERE request_id=p_request_id AND source_sale_item_id=(v_line->>'source_sale_item_id')::integer;
 END LOOP;
 IF NOT EXISTS(SELECT 1 FROM public.smartstock_cross_store_refund_lines
   WHERE request_id=p_request_id AND (source_status='PENDING' OR (disposition='RESTOCK' AND confirmed_quantity>0 AND destination_status<>'APPLIED'))) THEN
   UPDATE public.smartstock_cross_store_refund_requests
   SET status=CASE WHEN EXISTS(SELECT 1 FROM public.smartstock_cross_store_refund_lines WHERE request_id=p_request_id AND conflict_quantity>0)
     THEN 'CONFLICT_REVIEW' ELSE 'COMPLETED' END,updated_at=pg_catalog.now()
   WHERE request_id=p_request_id;
 END IF;
 RETURN pg_catalog.jsonb_build_object('updated',true);
END $$;


--
-- Name: smartstock_upsert_store_user_credentials(integer, uuid, jsonb); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.smartstock_upsert_store_user_credentials(p_location_id integer, p_generation_id uuid, p_rows jsonb DEFAULT '[]'::jsonb) RETURNS jsonb
    LANGUAGE plpgsql
    SET search_path TO ''
    AS $_$
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
$_$;


--
-- Name: smartstock_verify_store_mirror(integer, uuid, jsonb, bigint); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.smartstock_verify_store_mirror(p_location_id integer, p_generation_id uuid, p_table_counts jsonb, p_active_row_count bigint) RETURNS jsonb
    LANGUAGE plpgsql SECURITY DEFINER
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


--
-- Name: smartstock_server_registry(text, jsonb); Type: FUNCTION; Schema: smartstock_private; Owner: -
--

CREATE FUNCTION smartstock_private.smartstock_server_registry(p_action text, p_payload jsonb DEFAULT '{}'::jsonb) RETURNS jsonb
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO ''
    AS $$
DECLARE
    v_action text := upper(coalesce(p_action, ''));
    v_location_id integer := nullif(p_payload->>'location_id','')::integer;
    v_instance_id uuid := nullif(p_payload->>'server_instance_id','')::uuid;
    v_target_id uuid := nullif(p_payload->>'target_server_instance_id','')::uuid;
    v_source public.store_server_instances%ROWTYPE;
    v_target public.store_server_instances%ROWTYPE;
    v_handoff public.store_server_handoffs%ROWTYPE;
    v_generation bigint;
    v_result jsonb;
BEGIN
    IF v_location_id IS NULL OR v_location_id <= 0 THEN
        RAISE EXCEPTION 'A valid store location is required.' USING ERRCODE = '22023';
    END IF;

    IF v_action = 'ENSURE_LOCATION' THEN
        IF coalesce(p_payload->>'store_name','')='' OR coalesce(p_payload->>'store_code','')='' THEN
            RAISE EXCEPTION 'Store name and store code are required.' USING ERRCODE='22023';
        END IF;
        IF EXISTS (SELECT 1 FROM public.locations WHERE location_id=v_location_id
          AND upper(coalesce(receipt_store_code,''))<>upper(p_payload->>'store_code'))
          OR EXISTS (SELECT 1 FROM public.locations WHERE location_id<>v_location_id
          AND upper(coalesce(receipt_store_code,''))=upper(p_payload->>'store_code')) THEN
            RAISE EXCEPTION 'Store identity conflicts with an existing cloud location.' USING ERRCODE='40001';
        END IF;
        INSERT INTO public.locations(location_id,name,receipt_store_code,timezone,address)
        VALUES(v_location_id,p_payload->>'store_name',p_payload->>'store_code',
          coalesce(nullif(p_payload->>'timezone',''),'America/New_York'),nullif(p_payload->>'address',''))
        ON CONFLICT(location_id) DO UPDATE SET name=EXCLUDED.name,
          receipt_store_code=EXCLUDED.receipt_store_code,timezone=EXCLUDED.timezone,
          address=coalesce(EXCLUDED.address,public.locations.address);
        SELECT coalesce(max(location_id),1) INTO v_generation FROM public.locations;
        IF pg_get_serial_sequence('public.locations','location_id') IS NOT NULL THEN
            PERFORM setval(pg_get_serial_sequence('public.locations','location_id'),v_generation,true);
        END IF;
        RETURN jsonb_build_object('locationId',v_location_id,'ensured',true);
    END IF;

    IF v_action = 'LIST' THEN
        SELECT coalesce(jsonb_agg(jsonb_build_object(
            'serverInstanceId', s.server_instance_id,
            'locationId', s.location_id,
            'installationId', s.installation_id,
            'displayName', coalesce(s.display_name, s.hostname),
            'hostname', s.hostname,
            'appVersion', coalesce(s.app_version,''),
            'certificateFingerprint', s.certificate_fingerprint,
            'endpointHost', s.endpoint_host,
            'endpointPort', s.endpoint_port,
            'role', s.role,
            'generation', s.generation,
            'health', CASE
                WHEN s.role = 'FENCED' THEN 'FENCED'
                WHEN s.role = 'RETIRED' THEN 'RETIRED'
                WHEN coalesce(s.status_message,'') <> '' THEN 'DEGRADED'
                WHEN s.last_heartbeat_at >= now() - interval '2 minutes' THEN 'ONLINE'
                WHEN s.last_heartbeat_at >= now() - interval '10 minutes' THEN 'STALE'
                ELSE 'OFFLINE' END,
            'lastHeartbeatAt', s.last_heartbeat_at,
            'lastSyncAt', s.last_sync_at,
            'lastMaterializationAt', s.last_materialization_at,
            'materializedRowCount', s.materialized_row_count,
            'recoveryValidatedAt', s.recovery_validated_at,
            'recoveryMaterializationAt', s.recovery_materialization_at,
            'recoveryNetworkCheckedAt', s.recovery_network_checked_at,
            'statusMessage', coalesce(s.status_message,''),
            'replacedByServerInstanceId', s.replaced_by_server_instance_id,
            'createdAt', s.created_at,
            'retiredAt', s.retired_at
        ) ORDER BY CASE s.role WHEN 'PRIMARY' THEN 0 WHEN 'DRAINING' THEN 1 WHEN 'STANDBY' THEN 2 ELSE 3 END,
          s.last_heartbeat_at DESC NULLS LAST), '[]'::jsonb)
        INTO v_result FROM public.store_server_instances s WHERE s.location_id = v_location_id;
        RETURN jsonb_build_object('servers', v_result);
    END IF;

    IF v_action = 'HANDOFF_STATUS' THEN
        SELECT * INTO v_handoff
        FROM public.store_server_handoffs h
        WHERE h.location_id=v_location_id
          AND (nullif(p_payload->>'handoff_id','') IS NULL
               OR h.handoff_id=nullif(p_payload->>'handoff_id','')::uuid)
          AND (coalesce(p_payload->>'idempotency_key','')=''
               OR h.idempotency_key=p_payload->>'idempotency_key')
          AND (v_instance_id IS NULL OR h.source_server_instance_id=v_instance_id
               OR h.target_server_instance_id=v_instance_id)
        ORDER BY h.created_at DESC LIMIT 1;
        IF NOT FOUND THEN RETURN jsonb_build_object('status','NONE'); END IF;
        RETURN jsonb_build_object('handoffId',v_handoff.handoff_id,'status',v_handoff.status,
          'sourceServerInstanceId',v_handoff.source_server_instance_id,
          'targetServerInstanceId',v_handoff.target_server_instance_id,
          'emergency',v_handoff.emergency,
          'recoveryMaterializedAt',v_handoff.recovery_materialized_at,
          'recoveryRowCount',v_handoff.recovery_row_count,
          'failureMessage',coalesce(v_handoff.failure_message,''));
    END IF;

    IF v_action = 'LIST_EVENTS' THEN
        SELECT coalesce(jsonb_agg(jsonb_build_object(
          'eventType',e.event_type,'serverInstanceId',e.server_instance_id,
          'handoffId',e.handoff_id,'actorName',coalesce(e.actor_name,''),
          'details',coalesce(e.details,''),'createdAt',e.created_at)
          ORDER BY e.created_at DESC),'[]'::jsonb)
        INTO v_result FROM (SELECT * FROM public.store_server_events
          WHERE location_id=v_location_id ORDER BY created_at DESC LIMIT 100) e;
        RETURN jsonb_build_object('events',v_result);
    END IF;

    PERFORM pg_advisory_xact_lock(1398035026, v_location_id);

    IF v_action IN ('BEGIN_HANDOFF','EMERGENCY_TAKEOVER')
       AND coalesce(p_payload->>'idempotency_key','') <> '' THEN
        SELECT * INTO v_handoff FROM public.store_server_handoffs
        WHERE location_id=v_location_id AND idempotency_key=p_payload->>'idempotency_key';
        IF FOUND THEN
            RETURN jsonb_build_object('handoffId',v_handoff.handoff_id,'status',v_handoff.status,
              'recoveryMaterializedAt',v_handoff.recovery_materialized_at,
              'recoveryRowCount',v_handoff.recovery_row_count);
        END IF;
    END IF;

    IF v_action IN ('REGISTER_PRIMARY','REGISTER_STANDBY') THEN
        IF coalesce(p_payload->>'installation_id','') = ''
           OR coalesce(p_payload->>'hostname','') = ''
           OR coalesce(p_payload->>'certificate_fingerprint','') = ''
           OR coalesce(p_payload->>'endpoint_host','') = '' THEN
            RAISE EXCEPTION 'Server identity and endpoint details are required.' USING ERRCODE = '22023';
        END IF;
        IF v_action = 'REGISTER_PRIMARY' AND EXISTS (
            SELECT 1 FROM public.store_server_instances
            WHERE location_id=v_location_id AND role IN ('PRIMARY','DRAINING')
              AND installation_id<>p_payload->>'installation_id') THEN
            RAISE EXCEPTION 'Another primary server is already registered for this store.' USING ERRCODE = '23505';
        END IF;
        SELECT coalesce(max(generation),0) INTO v_generation
          FROM public.store_server_instances WHERE location_id=v_location_id;
        INSERT INTO public.store_server_instances(
            location_id,installation_id,display_name,hostname,app_version,
            certificate_fingerprint,endpoint_host,endpoint_port,role,generation,last_heartbeat_at
        ) VALUES (
            v_location_id,p_payload->>'installation_id',nullif(p_payload->>'display_name',''),
            p_payload->>'hostname',nullif(p_payload->>'app_version',''),
            p_payload->>'certificate_fingerprint',p_payload->>'endpoint_host',
            coalesce(nullif(p_payload->>'endpoint_port','')::integer,8443),
            CASE WHEN v_action='REGISTER_PRIMARY' THEN 'PRIMARY' ELSE 'STANDBY' END,
            CASE WHEN v_action='REGISTER_PRIMARY' THEN v_generation+1 ELSE 0 END,now()
        ) ON CONFLICT(location_id,installation_id) DO UPDATE SET
            display_name=coalesce(EXCLUDED.display_name,public.store_server_instances.display_name),
            hostname=EXCLUDED.hostname,app_version=EXCLUDED.app_version,
            certificate_fingerprint=EXCLUDED.certificate_fingerprint,
            endpoint_host=EXCLUDED.endpoint_host,endpoint_port=EXCLUDED.endpoint_port,
            role=CASE WHEN v_action='REGISTER_PRIMARY' THEN 'PRIMARY' ELSE public.store_server_instances.role END,
            generation=CASE WHEN v_action='REGISTER_PRIMARY' THEN v_generation+1 ELSE public.store_server_instances.generation END,
            last_heartbeat_at=now(),updated_at=now()
        RETURNING server_instance_id INTO v_instance_id;
        INSERT INTO public.store_server_events(location_id,server_instance_id,event_type,details)
        VALUES(v_location_id,v_instance_id,v_action,'Server registered through secured control plane.');
        RETURN jsonb_build_object('serverInstanceId',v_instance_id,'registered',true);
    END IF;

    SELECT * INTO v_source FROM public.store_server_instances
      WHERE server_instance_id=v_instance_id AND location_id=v_location_id FOR UPDATE;
    IF NOT FOUND THEN RAISE EXCEPTION 'Server instance was not found.' USING ERRCODE='P0002'; END IF;

    IF v_action = 'PREPARE_STANDBY' THEN
        IF v_source.role<>'STANDBY' THEN RAISE EXCEPTION 'Server is not a standby.' USING ERRCODE='55000'; END IF;
        INSERT INTO public.store_server_events(location_id,server_instance_id,event_type,actor_user_id,actor_name,details)
        VALUES(v_location_id,v_instance_id,'STANDBY_PREPARED',nullif(p_payload->>'actor_user_id','')::integer,
          nullif(p_payload->>'actor_name',''),'Administrator verified standby readiness.');
        RETURN jsonb_build_object('prepared',true);
    END IF;

    IF v_action = 'HEARTBEAT' THEN
        UPDATE public.store_server_instances SET
            hostname=coalesce(nullif(p_payload->>'hostname',''),hostname),
            app_version=coalesce(nullif(p_payload->>'app_version',''),app_version),
            endpoint_host=coalesce(nullif(p_payload->>'endpoint_host',''),endpoint_host),
            endpoint_port=coalesce(nullif(p_payload->>'endpoint_port','')::integer,endpoint_port),
            last_heartbeat_at=now(),
            last_sync_at=coalesce(nullif(p_payload->>'last_sync_at','')::timestamptz,last_sync_at),
            last_materialization_at=coalesce(nullif(p_payload->>'last_materialization_at','')::timestamptz,last_materialization_at),
            materialized_row_count=coalesce(nullif(p_payload->>'materialized_row_count','')::bigint,materialized_row_count),
            status_message=nullif(p_payload->>'status_message',''),updated_at=now()
        WHERE server_instance_id=v_instance_id;
        RETURN jsonb_build_object('accepted',true,'role',v_source.role,'generation',v_source.generation,
            'fenced',v_source.role='FENCED');
    END IF;

    IF v_action = 'MARK_RECOVERY_READY' THEN
        IF v_source.role <> 'STANDBY' THEN
            RAISE EXCEPTION 'Only a standby can be marked recovery-ready.' USING ERRCODE='55000';
        END IF;
        SELECT * INTO v_target FROM public.store_server_instances
          WHERE location_id=v_location_id AND role='PRIMARY' FOR UPDATE;
        IF NOT FOUND OR v_target.last_materialization_at IS NULL THEN
            RAISE EXCEPTION 'No verified primary recovery point is available.' USING ERRCODE='55000';
        END IF;
        UPDATE public.store_server_instances SET recovery_validated_at=now(),
          recovery_materialization_at=v_target.last_materialization_at,
          recovery_network_checked_at=now(),updated_at=now()
          WHERE server_instance_id=v_instance_id;
        INSERT INTO public.store_server_events(location_id,server_instance_id,event_type,details)
        VALUES(v_location_id,v_instance_id,'STANDBY_RECOVERY_VALIDATED','Standby restored the latest verified cloud materialization.');
        RETURN jsonb_build_object('ready',true,'recoveryMaterializedAt',v_target.last_materialization_at);
    END IF;

    IF v_action = 'RENAME' THEN
        UPDATE public.store_server_instances SET display_name=nullif(p_payload->>'display_name',''),updated_at=now()
          WHERE server_instance_id=v_instance_id;
        INSERT INTO public.store_server_events(location_id,server_instance_id,event_type,actor_user_id,actor_name,details)
        VALUES(v_location_id,v_instance_id,'SERVER_RENAMED',nullif(p_payload->>'actor_user_id','')::integer,
          nullif(p_payload->>'actor_name',''),'Server display name changed.');
        RETURN jsonb_build_object('updated',true);
    END IF;

    IF v_action = 'RETIRE' THEN
        IF v_source.role IN ('PRIMARY','DRAINING') THEN
            RAISE EXCEPTION 'The active primary must be replaced before it can be retired.' USING ERRCODE='55000';
        END IF;
        UPDATE public.store_server_instances SET role='RETIRED',retired_at=now(),updated_at=now()
          WHERE server_instance_id=v_instance_id;
        INSERT INTO public.store_server_events(location_id,server_instance_id,event_type,actor_user_id,actor_name,details)
        VALUES(v_location_id,v_instance_id,'SERVER_RETIRED',nullif(p_payload->>'actor_user_id','')::integer,
          nullif(p_payload->>'actor_name',''),'Server retired by administrator.');
        RETURN jsonb_build_object('retired',true);
    END IF;

    IF v_action = 'BEGIN_HANDOFF' THEN
        IF v_source.role <> 'PRIMARY' THEN RAISE EXCEPTION 'Source server is not the active primary.' USING ERRCODE='55000'; END IF;
        SELECT * INTO v_target FROM public.store_server_instances
          WHERE server_instance_id=v_target_id AND location_id=v_location_id FOR UPDATE;
        IF NOT FOUND OR v_target.role <> 'STANDBY' THEN RAISE EXCEPTION 'Target server is not an available standby.' USING ERRCODE='55000'; END IF;
        INSERT INTO public.store_server_handoffs(location_id,source_server_instance_id,target_server_instance_id,
          status,requested_by_user_id,requested_by_name,idempotency_key)
        VALUES(v_location_id,v_instance_id,v_target_id,'PREPARING',nullif(p_payload->>'actor_user_id','')::integer,
          nullif(p_payload->>'actor_name',''),p_payload->>'idempotency_key')
        ON CONFLICT(location_id,idempotency_key) DO UPDATE SET idempotency_key=EXCLUDED.idempotency_key
        RETURNING * INTO v_handoff;
        UPDATE public.store_server_instances SET role='DRAINING',updated_at=now() WHERE server_instance_id=v_instance_id;
        INSERT INTO public.store_server_events(location_id,server_instance_id,handoff_id,event_type,actor_user_id,actor_name,details)
        VALUES(v_location_id,v_instance_id,v_handoff.handoff_id,'HANDOFF_STARTED',v_handoff.requested_by_user_id,
          v_handoff.requested_by_name,'Primary entered draining state.');
        RETURN jsonb_build_object('handoffId',v_handoff.handoff_id,'status',v_handoff.status);
    END IF;

    IF v_action = 'MARK_HANDOFF_READY' THEN
        SELECT * INTO v_handoff FROM public.store_server_handoffs
          WHERE handoff_id=nullif(p_payload->>'handoff_id','')::uuid AND location_id=v_location_id FOR UPDATE;
        IF FOUND AND v_handoff.source_server_instance_id=v_instance_id
           AND v_handoff.status IN ('READY','COMPLETED') THEN
            RETURN jsonb_build_object('handoffId',v_handoff.handoff_id,'status',v_handoff.status,
              'recoveryMaterializedAt',v_handoff.recovery_materialized_at,
              'recoveryRowCount',v_handoff.recovery_row_count);
        END IF;
        IF NOT FOUND OR v_handoff.status<>'PREPARING' OR v_handoff.source_server_instance_id<>v_instance_id THEN
            RAISE EXCEPTION 'Handoff is not awaiting source readiness.' USING ERRCODE='55000';
        END IF;
        IF v_source.last_materialization_at IS NULL THEN
            RAISE EXCEPTION 'A verified cloud materialization is required.' USING ERRCODE='55000';
        END IF;
        UPDATE public.store_server_handoffs SET status='READY',ready_at=now(),
          recovery_materialized_at=v_source.last_materialization_at,recovery_row_count=v_source.materialized_row_count
          WHERE handoff_id=v_handoff.handoff_id;
        INSERT INTO public.store_server_events(location_id,server_instance_id,handoff_id,event_type,actor_user_id,actor_name,details)
        VALUES(v_location_id,v_instance_id,v_handoff.handoff_id,'HANDOFF_READY',v_handoff.requested_by_user_id,
          v_handoff.requested_by_name,'Final sync and cloud materialization verified.');
        RETURN jsonb_build_object('handoffId',v_handoff.handoff_id,'status','READY',
          'recoveryMaterializedAt',v_source.last_materialization_at,'recoveryRowCount',v_source.materialized_row_count);
    END IF;

    IF v_action = 'COMPLETE_HANDOFF' THEN
        SELECT * INTO v_handoff FROM public.store_server_handoffs
          WHERE handoff_id=nullif(p_payload->>'handoff_id','')::uuid AND location_id=v_location_id FOR UPDATE;
        IF FOUND AND v_handoff.status='COMPLETED' AND v_handoff.target_server_instance_id=v_instance_id THEN
            SELECT generation INTO v_generation FROM public.store_server_instances
              WHERE server_instance_id=v_instance_id;
            RETURN jsonb_build_object('handoffId',v_handoff.handoff_id,'status','COMPLETED','generation',v_generation);
        END IF;
        IF NOT FOUND OR v_handoff.status<>'READY' OR v_handoff.target_server_instance_id<>v_instance_id THEN
            RAISE EXCEPTION 'Handoff is not ready for this replacement server.' USING ERRCODE='55000';
        END IF;
        SELECT coalesce(max(generation),0)+1 INTO v_generation FROM public.store_server_instances WHERE location_id=v_location_id;
        UPDATE public.store_server_instances SET role='RETIRED',retired_at=now(),
          replaced_by_server_instance_id=v_instance_id,updated_at=now()
          WHERE server_instance_id=v_handoff.source_server_instance_id;
        UPDATE public.store_server_instances SET role='PRIMARY',generation=v_generation,last_heartbeat_at=now(),updated_at=now()
          WHERE server_instance_id=v_instance_id;
        UPDATE public.store_server_handoffs SET status='COMPLETED',completed_at=now() WHERE handoff_id=v_handoff.handoff_id;
        INSERT INTO public.store_server_events(location_id,server_instance_id,handoff_id,event_type,actor_user_id,actor_name,details)
        VALUES(v_location_id,v_instance_id,v_handoff.handoff_id,'HANDOFF_COMPLETED',v_handoff.requested_by_user_id,
          v_handoff.requested_by_name,'Replacement server activated.');
        RETURN jsonb_build_object('handoffId',v_handoff.handoff_id,'status','COMPLETED','generation',v_generation);
    END IF;

    IF v_action = 'FAIL_HANDOFF' THEN
        SELECT * INTO v_handoff FROM public.store_server_handoffs
          WHERE handoff_id=nullif(p_payload->>'handoff_id','')::uuid AND location_id=v_location_id FOR UPDATE;
        IF FOUND AND v_handoff.status='FAILED' THEN
            RETURN jsonb_build_object('handoffId',v_handoff.handoff_id,'status','FAILED');
        END IF;
        IF NOT FOUND OR v_handoff.status NOT IN ('PREPARING','READY') THEN
            RAISE EXCEPTION 'Open handoff was not found.' USING ERRCODE='P0002';
        END IF;
        UPDATE public.store_server_handoffs SET status='FAILED',failure_message=left(coalesce(p_payload->>'failure_message','Handoff cancelled.'),2000)
          WHERE handoff_id=v_handoff.handoff_id;
        UPDATE public.store_server_instances SET role='PRIMARY',updated_at=now()
          WHERE server_instance_id=v_handoff.source_server_instance_id AND role='DRAINING';
        INSERT INTO public.store_server_events(location_id,server_instance_id,handoff_id,event_type,actor_user_id,actor_name,details)
        VALUES(v_location_id,v_handoff.source_server_instance_id,v_handoff.handoff_id,'HANDOFF_FAILED',
          v_handoff.requested_by_user_id,v_handoff.requested_by_name,
          left(coalesce(p_payload->>'failure_message','Handoff cancelled.'),2000));
        RETURN jsonb_build_object('handoffId',v_handoff.handoff_id,'status','FAILED');
    END IF;

    IF v_action = 'EMERGENCY_TAKEOVER' THEN
        IF coalesce((p_payload->>'warning_acknowledged')::boolean,false) IS NOT TRUE THEN
            RAISE EXCEPTION 'Emergency recovery warning must be acknowledged.' USING ERRCODE='22023';
        END IF;
        IF v_source.role <> 'STANDBY' THEN RAISE EXCEPTION 'Replacement server is not a standby.' USING ERRCODE='55000'; END IF;
        SELECT * INTO v_target FROM public.store_server_instances
          WHERE location_id=v_location_id AND role='PRIMARY' FOR UPDATE;
        IF FOUND AND v_target.last_heartbeat_at >= now()-interval '2 minutes' THEN
            RAISE EXCEPTION 'The current primary is still online; use verified handoff.' USING ERRCODE='55000';
        END IF;
        IF NOT FOUND OR v_target.last_materialization_at IS NULL THEN
            RAISE EXCEPTION 'No verified recovery materialization is available.' USING ERRCODE='55000';
        END IF;
        IF v_source.recovery_validated_at IS NULL
           OR v_source.recovery_materialization_at IS DISTINCT FROM v_target.last_materialization_at
           OR v_source.recovery_network_checked_at < now()-interval '15 minutes' THEN
            RAISE EXCEPTION 'The standby has not restored and validated the latest recovery point.' USING ERRCODE='55000';
        END IF;
        SELECT coalesce(max(generation),0)+1 INTO v_generation FROM public.store_server_instances WHERE location_id=v_location_id;
        INSERT INTO public.store_server_handoffs(location_id,source_server_instance_id,target_server_instance_id,status,
          emergency,requested_by_user_id,requested_by_name,idempotency_key,recovery_materialized_at,recovery_row_count,
          warning_acknowledged,completed_at)
        VALUES(v_location_id,v_target.server_instance_id,v_instance_id,'COMPLETED',true,
          nullif(p_payload->>'actor_user_id','')::integer,nullif(p_payload->>'actor_name',''),p_payload->>'idempotency_key',
          v_target.last_materialization_at,v_target.materialized_row_count,true,now()) RETURNING * INTO v_handoff;
        UPDATE public.store_server_instances SET role='FENCED',replaced_by_server_instance_id=v_instance_id,updated_at=now()
          WHERE server_instance_id=v_target.server_instance_id;
        UPDATE public.store_server_instances SET role='PRIMARY',generation=v_generation,last_heartbeat_at=now(),updated_at=now()
          WHERE server_instance_id=v_instance_id;
        INSERT INTO public.store_server_events(location_id,server_instance_id,handoff_id,event_type,actor_user_id,actor_name,details)
        VALUES(v_location_id,v_instance_id,v_handoff.handoff_id,'EMERGENCY_TAKEOVER',v_handoff.requested_by_user_id,
          v_handoff.requested_by_name,'Offline primary fenced; administrator acknowledged possible unsynced data.');
        RETURN jsonb_build_object('handoffId',v_handoff.handoff_id,'status','COMPLETED','generation',v_generation,
          'recoveryMaterializedAt',v_target.last_materialization_at,'recoveryRowCount',v_target.materialized_row_count);
    END IF;

    RAISE EXCEPTION 'Unsupported server registry action.' USING ERRCODE='22023';
END
$$;


SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: app_releases; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.app_releases (
    release_id bigint NOT NULL,
    version text NOT NULL,
    build_number integer NOT NULL,
    platform text DEFAULT 'windows'::text NOT NULL,
    artifact_bucket text DEFAULT 'smartstock-releases'::text NOT NULL,
    artifact_path text NOT NULL,
    sha256 text NOT NULL,
    file_size_bytes bigint,
    release_notes text,
    required boolean DEFAULT false NOT NULL,
    minimum_supported_version text,
    published boolean DEFAULT false NOT NULL,
    published_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    created_by_user_id integer,
    CONSTRAINT app_releases_platform_check CHECK ((platform = ANY (ARRAY['windows'::text, 'mac'::text, 'linux'::text, 'all'::text]))),
    CONSTRAINT app_releases_published_at_check CHECK (((published = false) OR (published_at IS NOT NULL))),
    CONSTRAINT app_releases_sha256_check CHECK ((sha256 ~* '^[a-f0-9]{64}$'::text))
);


--
-- Name: app_releases_release_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.app_releases_release_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: app_releases_release_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.app_releases_release_id_seq OWNED BY public.app_releases.release_id;


--
-- Name: customer_account_ba_number_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.customer_account_ba_number_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: customer_account_ca_number_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.customer_account_ca_number_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: device_sessions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.device_sessions (
    session_id bigint NOT NULL,
    device_id uuid NOT NULL,
    user_id integer,
    store_id integer,
    login_time timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    logout_time timestamp with time zone,
    session_status text DEFAULT 'ACTIVE'::text NOT NULL
);


--
-- Name: device_sessions_session_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.device_sessions_session_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: device_sessions_session_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.device_sessions_session_id_seq OWNED BY public.device_sessions.session_id;


--
-- Name: devices; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.devices (
    device_id uuid DEFAULT gen_random_uuid() NOT NULL,
    installation_id text NOT NULL,
    device_fingerprint text,
    device_name text,
    hostname text,
    os_name text,
    os_version text,
    os_arch text,
    java_version text,
    app_version text,
    local_username text,
    mac_addresses text,
    first_seen timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    last_seen timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    last_login_user_id integer,
    last_store_id integer,
    is_approved boolean DEFAULT false NOT NULL,
    allow_persistent_login boolean DEFAULT false NOT NULL,
    auto_logout_enabled boolean DEFAULT false NOT NULL,
    auto_logout_minutes integer DEFAULT 15 NOT NULL,
    is_blocked boolean DEFAULT false NOT NULL,
    approved_at timestamp with time zone,
    approved_by_user_id integer,
    blocked_at timestamp with time zone,
    blocked_by_user_id integer,
    status_notes text,
    receipt_device_code text DEFAULT '0001'::text NOT NULL,
    allow_sales boolean DEFAULT true NOT NULL,
    allow_orders boolean DEFAULT true NOT NULL,
    access_mode text DEFAULT 'CLIENT'::text NOT NULL,
    session_count bigint DEFAULT 0 NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    pairing_public_key text,
    credential_status text DEFAULT 'PENDING'::text NOT NULL,
    credential_issued_at timestamp with time zone,
    credential_claimed_at timestamp with time zone,
    CONSTRAINT devices_access_mode_check CHECK ((access_mode = ANY (ARRAY['CLIENT'::text, 'SERVER'::text, 'REMOTE_ADMIN'::text]))),
    CONSTRAINT devices_auto_logout_minutes_check CHECK (((auto_logout_minutes >= 1) AND (auto_logout_minutes <= 480))),
    CONSTRAINT devices_credential_status_check CHECK ((credential_status = ANY (ARRAY['PENDING'::text, 'ISSUED'::text, 'CLAIMED'::text, 'ROTATION_PENDING'::text, 'REVOKED'::text])))
);


--
-- Name: email_outbox; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.email_outbox (
    email_outbox_id bigint NOT NULL,
    location_id integer,
    sender_email text NOT NULL,
    sender_name text DEFAULT ''::text NOT NULL,
    recipient_email text NOT NULL,
    bcc_email text,
    subject text NOT NULL,
    body_text text DEFAULT ''::text NOT NULL,
    body_html text DEFAULT ''::text NOT NULL,
    attachment_name text,
    attachment_content_type text,
    attachment_body text,
    document_type text NOT NULL,
    document_id text NOT NULL,
    status text DEFAULT 'QUEUED'::text NOT NULL,
    attempts integer DEFAULT 0 NOT NULL,
    max_attempts integer DEFAULT 3 NOT NULL,
    last_error text,
    sent_at timestamp with time zone,
    queued_by_user_id integer,
    queued_by_name text,
    device_id text,
    device_name text,
    sync_uuid uuid DEFAULT gen_random_uuid() NOT NULL,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT email_outbox_attempts_chk CHECK (((attempts >= 0) AND (max_attempts > 0))),
    CONSTRAINT email_outbox_status_chk CHECK ((status = ANY (ARRAY['QUEUED'::text, 'SENDING'::text, 'SENT'::text, 'FAILED'::text, 'CANCELLED'::text])))
);


--
-- Name: email_outbox_email_outbox_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.email_outbox_email_outbox_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: email_outbox_email_outbox_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.email_outbox_email_outbox_id_seq OWNED BY public.email_outbox.email_outbox_id;


--
-- Name: email_outbox_events; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.email_outbox_events (
    email_outbox_event_id bigint NOT NULL,
    email_outbox_id bigint NOT NULL,
    event_type text NOT NULL,
    message text,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    user_id integer,
    user_name text,
    device_id text,
    device_name text,
    sync_uuid uuid DEFAULT gen_random_uuid() NOT NULL
);


--
-- Name: email_outbox_events_email_outbox_event_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.email_outbox_events_email_outbox_event_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: email_outbox_events_email_outbox_event_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.email_outbox_events_email_outbox_event_id_seq OWNED BY public.email_outbox_events.email_outbox_event_id;


--
-- Name: image_asset_references; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.image_asset_references (
    reference_id uuid DEFAULT gen_random_uuid() NOT NULL,
    asset_id uuid NOT NULL,
    source_table text NOT NULL,
    source_key text NOT NULL,
    source_column text NOT NULL,
    active boolean DEFAULT true NOT NULL,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: image_assets; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.image_assets (
    asset_id uuid DEFAULT gen_random_uuid() NOT NULL,
    category text NOT NULL,
    bucket_name text NOT NULL,
    object_path text NOT NULL,
    access_level text DEFAULT 'PUBLIC'::text NOT NULL,
    original_filename text DEFAULT ''::text NOT NULL,
    content_type text DEFAULT 'application/octet-stream'::text NOT NULL,
    byte_size bigint DEFAULT 0 NOT NULL,
    sha256 text DEFAULT ''::text NOT NULL,
    lifecycle_status text DEFAULT 'ACTIVE'::text NOT NULL,
    local_status text DEFAULT 'MISSING'::text NOT NULL,
    cloud_status text DEFAULT 'PENDING'::text NOT NULL,
    retained boolean DEFAULT false NOT NULL,
    unused_since timestamp with time zone,
    last_verified_at timestamp with time zone,
    last_error text,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    deleted_at timestamp with time zone,
    deleted_by_user_id integer,
    deleted_by_name text,
    CONSTRAINT image_assets_access_level_check CHECK ((access_level = ANY (ARRAY['PUBLIC'::text, 'AUTHENTICATED'::text]))),
    CONSTRAINT image_assets_byte_size_check CHECK ((byte_size >= 0)),
    CONSTRAINT image_assets_cloud_status_check CHECK ((cloud_status = ANY (ARRAY['PENDING'::text, 'PRESENT'::text, 'MISSING'::text, 'FAILED'::text, 'DELETED'::text]))),
    CONSTRAINT image_assets_lifecycle_status_check CHECK ((lifecycle_status = ANY (ARRAY['ACTIVE'::text, 'UNUSED'::text, 'DELETE_PENDING'::text, 'DELETED'::text]))),
    CONSTRAINT image_assets_local_status_check CHECK ((local_status = ANY (ARRAY['PRESENT'::text, 'MISSING'::text, 'CORRUPT'::text])))
);


--
-- Name: locations; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.locations (
    location_id integer NOT NULL,
    name text NOT NULL,
    address text,
    company_address_line1 text DEFAULT ''::text NOT NULL,
    company_address_line2 text DEFAULT ''::text NOT NULL,
    company_address_line3 text DEFAULT ''::text NOT NULL,
    company_phone_line1 text DEFAULT ''::text NOT NULL,
    company_phone_line2 text DEFAULT ''::text NOT NULL,
    company_email_line1 text DEFAULT ''::text NOT NULL,
    company_email_line2 text DEFAULT ''::text NOT NULL,
    email_sender_address text DEFAULT ''::text NOT NULL,
    email_sender_name text DEFAULT ''::text NOT NULL,
    email_bcc_address text DEFAULT ''::text NOT NULL,
    balance_sheet_recipient_email text DEFAULT ''::text NOT NULL,
    email_receipts_enabled boolean DEFAULT false NOT NULL,
    email_order_confirmations_enabled boolean DEFAULT false NOT NULL,
    email_quotes_enabled boolean DEFAULT false NOT NULL,
    email_invoices_enabled boolean DEFAULT false NOT NULL,
    email_delivery_bills_enabled boolean DEFAULT false NOT NULL,
    email_connected_at timestamp with time zone,
    email_last_tested_at timestamp with time zone,
    receipt_store_code text DEFAULT '0001'::text NOT NULL,
    timezone text DEFAULT 'America/New_York'::text NOT NULL,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: locations_location_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.locations_location_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: locations_location_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.locations_location_id_seq OWNED BY public.locations.location_id;


--
-- Name: mobile_permissions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.mobile_permissions (
    permission_key text NOT NULL,
    permission_name text,
    description text,
    permission_group text NOT NULL,
    permission_subgroup text,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: permissions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.permissions (
    permission_id integer NOT NULL,
    permission_key text NOT NULL,
    permission_name text NOT NULL,
    description text,
    permission_group text,
    permission_subgroup text,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: permissions_permission_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.permissions_permission_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: permissions_permission_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.permissions_permission_id_seq OWNED BY public.permissions.permission_id;


--
-- Name: remote_admin_commands; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.remote_admin_commands (
    command_id uuid NOT NULL,
    location_id integer NOT NULL,
    device_id uuid,
    user_id integer,
    operation text NOT NULL,
    status text NOT NULL,
    details text,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    applied_at timestamp with time zone,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT remote_admin_commands_status_check CHECK ((status = ANY (ARRAY['APPLIED_CLOUD'::text, 'PENDING_STORE'::text, 'APPLIED_STORE'::text, 'REJECTED'::text, 'CONFLICT'::text])))
);


--
-- Name: role_mobile_permissions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.role_mobile_permissions (
    role_id integer NOT NULL,
    permission_key text NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: role_permissions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.role_permissions (
    role_id integer NOT NULL,
    permission_id integer NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: roles; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.roles (
    role_id integer NOT NULL,
    role_name text NOT NULL,
    description text,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: roles_role_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.roles_role_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: roles_role_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.roles_role_id_seq OWNED BY public.roles.role_id;


--
-- Name: smartstock_cross_store_refund_lines; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.smartstock_cross_store_refund_lines (
    request_id uuid NOT NULL,
    source_sale_item_id integer CONSTRAINT smartstock_cross_store_refund_line_source_sale_item_id_not_null NOT NULL,
    product_id integer NOT NULL,
    quantity integer NOT NULL,
    unit_price numeric(14,2) NOT NULL,
    disposition text NOT NULL,
    destination_location_id integer,
    disposition_reason text,
    source_status text DEFAULT 'PENDING'::text NOT NULL,
    destination_status text DEFAULT 'PENDING'::text NOT NULL,
    confirmed_quantity integer DEFAULT 0 NOT NULL,
    conflict_quantity integer DEFAULT 0 NOT NULL,
    CONSTRAINT smartstock_cross_store_refund_lines_disposition_check CHECK ((disposition = ANY (ARRAY['RESTOCK'::text, 'DISCARD'::text]))),
    CONSTRAINT smartstock_cross_store_refund_lines_quantity_check CHECK ((quantity > 0)),
    CONSTRAINT smartstock_cross_store_refund_lines_unit_price_check CHECK ((unit_price >= (0)::numeric))
);


--
-- Name: smartstock_cross_store_refund_sequence; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.smartstock_cross_store_refund_sequence
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: smartstock_cross_store_refund_requests; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.smartstock_cross_store_refund_requests (
    request_id uuid NOT NULL,
    request_sequence bigint DEFAULT nextval('public.smartstock_cross_store_refund_sequence'::regclass) CONSTRAINT smartstock_cross_store_refund_request_request_sequence_not_null NOT NULL,
    source_location_id integer CONSTRAINT smartstock_cross_store_refund_reque_source_location_id_not_null NOT NULL,
    receiving_location_id integer CONSTRAINT smartstock_cross_store_refund_re_receiving_location_id_not_null NOT NULL,
    source_sale_id integer NOT NULL,
    refund_method text NOT NULL,
    refund_amount numeric(14,2) NOT NULL,
    reason text NOT NULL,
    actor jsonb DEFAULT '{}'::jsonb NOT NULL,
    return_receipt_number text,
    receipt_device_id text,
    receipt_sequence integer,
    status text DEFAULT 'PAID_PENDING_SOURCE'::text NOT NULL,
    source_error text,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT smartstock_cross_store_refund_requests_refund_amount_check CHECK ((refund_amount > (0)::numeric))
);


--
-- Name: smartstock_store_mirror_status; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.smartstock_store_mirror_status (
    location_id integer NOT NULL,
    table_counts jsonb DEFAULT '{}'::jsonb NOT NULL,
    active_row_count bigint DEFAULT 0 NOT NULL,
    completed_at timestamp with time zone DEFAULT now() NOT NULL,
    current_generation_id uuid,
    verified_at timestamp with time zone,
    credentials_verified_at timestamp with time zone,
    CONSTRAINT smartstock_store_mirror_status_active_row_count_check CHECK ((active_row_count >= 0)),
    CONSTRAINT smartstock_store_mirror_status_table_counts_check CHECK ((jsonb_typeof(table_counts) = 'object'::text))
);


--
-- Name: smartstock_store_row_version_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.smartstock_store_row_version_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: smartstock_store_rows; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.smartstock_store_rows (
    location_id integer NOT NULL,
    table_name text NOT NULL,
    row_key jsonb NOT NULL,
    row_data jsonb NOT NULL,
    row_hash text NOT NULL,
    is_deleted boolean DEFAULT false NOT NULL,
    version_sequence bigint DEFAULT nextval('public.smartstock_store_row_version_seq'::regclass) NOT NULL,
    source_updated_at timestamp with time zone,
    materialized_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT smartstock_store_rows_row_hash_check CHECK (((length(row_hash) >= 16) AND (length(row_hash) <= 128))),
    CONSTRAINT smartstock_store_rows_table_name_check CHECK ((table_name ~ '^[a-z][a-z0-9_]{0,100}$'::text))
);


--
-- Name: smartstock_store_snapshot_generations; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.smartstock_store_snapshot_generations (
    generation_id uuid NOT NULL,
    location_id integer NOT NULL,
    status text DEFAULT 'BUILDING'::text NOT NULL,
    table_counts jsonb DEFAULT '{}'::jsonb NOT NULL,
    active_row_count bigint DEFAULT 0 NOT NULL,
    based_on_generation_id uuid,
    started_at timestamp with time zone DEFAULT now() NOT NULL,
    completed_at timestamp with time zone,
    CONSTRAINT smartstock_store_snapshot_generations_active_row_count_check CHECK ((active_row_count >= 0)),
    CONSTRAINT smartstock_store_snapshot_generations_check CHECK ((((status = 'BUILDING'::text) AND (completed_at IS NULL)) OR ((status = 'COMPLETE'::text) AND (completed_at IS NOT NULL)))),
    CONSTRAINT smartstock_store_snapshot_generations_status_check CHECK ((status = ANY (ARRAY['BUILDING'::text, 'COMPLETE'::text]))),
    CONSTRAINT smartstock_store_snapshot_generations_table_counts_check CHECK ((jsonb_typeof(table_counts) = 'object'::text))
);


--
-- Name: smartstock_store_snapshot_rows; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.smartstock_store_snapshot_rows (
    generation_id uuid NOT NULL,
    location_id integer NOT NULL,
    table_name text NOT NULL,
    row_key jsonb NOT NULL,
    row_data jsonb NOT NULL,
    row_hash text NOT NULL,
    source_updated_at timestamp with time zone,
    row_sequence bigint NOT NULL,
    CONSTRAINT smartstock_store_snapshot_rows_row_hash_check CHECK (((length(row_hash) >= 16) AND (length(row_hash) <= 128))),
    CONSTRAINT smartstock_store_snapshot_rows_table_name_check CHECK ((table_name ~ '^[a-z][a-z0-9_]{0,100}$'::text))
);


--
-- Name: smartstock_store_snapshot_rows_row_sequence_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.smartstock_store_snapshot_rows ALTER COLUMN row_sequence ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME public.smartstock_store_snapshot_rows_row_sequence_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: store_server_events; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.store_server_events (
    server_event_id bigint NOT NULL,
    location_id integer NOT NULL,
    server_instance_id uuid,
    handoff_id uuid,
    event_type text NOT NULL,
    actor_user_id integer,
    actor_name text,
    details text,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: store_server_events_server_event_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.store_server_events ALTER COLUMN server_event_id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.store_server_events_server_event_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: store_server_handoffs; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.store_server_handoffs (
    handoff_id uuid DEFAULT gen_random_uuid() NOT NULL,
    location_id integer NOT NULL,
    source_server_instance_id uuid,
    target_server_instance_id uuid NOT NULL,
    status text NOT NULL,
    emergency boolean DEFAULT false NOT NULL,
    requested_by_user_id integer,
    requested_by_name text,
    idempotency_key text NOT NULL,
    recovery_materialized_at timestamp with time zone,
    recovery_row_count bigint,
    warning_acknowledged boolean DEFAULT false NOT NULL,
    failure_message text,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    ready_at timestamp with time zone,
    completed_at timestamp with time zone,
    CONSTRAINT store_server_handoffs_status_check CHECK ((status = ANY (ARRAY['PREPARING'::text, 'READY'::text, 'COMPLETED'::text, 'FAILED'::text])))
);


--
-- Name: store_server_instances; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.store_server_instances (
    server_instance_id uuid DEFAULT gen_random_uuid() NOT NULL,
    location_id integer NOT NULL,
    installation_id text NOT NULL,
    display_name text,
    hostname text NOT NULL,
    app_version text,
    certificate_fingerprint text NOT NULL,
    endpoint_host text NOT NULL,
    endpoint_port integer DEFAULT 8443 NOT NULL,
    role text NOT NULL,
    generation bigint DEFAULT 0 NOT NULL,
    last_heartbeat_at timestamp with time zone,
    last_sync_at timestamp with time zone,
    last_materialization_at timestamp with time zone,
    materialized_row_count bigint,
    recovery_validated_at timestamp with time zone,
    recovery_materialization_at timestamp with time zone,
    recovery_network_checked_at timestamp with time zone,
    status_message text,
    replaced_by_server_instance_id uuid,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    retired_at timestamp with time zone,
    CONSTRAINT store_server_instances_endpoint_port_check CHECK (((endpoint_port >= 1) AND (endpoint_port <= 65535))),
    CONSTRAINT store_server_instances_generation_check CHECK ((generation >= 0)),
    CONSTRAINT store_server_instances_materialized_row_count_check CHECK (((materialized_row_count IS NULL) OR (materialized_row_count >= 0))),
    CONSTRAINT store_server_instances_role_check CHECK ((role = ANY (ARRAY['PRIMARY'::text, 'STANDBY'::text, 'DRAINING'::text, 'RETIRED'::text, 'FENCED'::text])))
);


--
-- Name: store_sync_status; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.store_sync_status (
    location_id integer NOT NULL,
    status text NOT NULL,
    message text,
    last_success_at timestamp with time zone,
    last_seen_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: sync_applied_events; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.sync_applied_events (
    origin_event_id uuid NOT NULL,
    event_type text NOT NULL,
    origin_location_id integer,
    origin_device_id text,
    applied_at timestamp with time zone DEFAULT now() NOT NULL,
    cloud_reference text
);


--
-- Name: sync_outbox; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.sync_outbox (
    event_id uuid DEFAULT gen_random_uuid() NOT NULL,
    event_type text NOT NULL,
    location_id integer,
    device_id text,
    user_id integer,
    payload jsonb DEFAULT '{}'::jsonb NOT NULL,
    status text DEFAULT 'PENDING'::text NOT NULL,
    attempts integer DEFAULT 0 NOT NULL,
    last_error text,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    synced_at timestamp with time zone,
    origin_event_id uuid,
    origin_location_id integer,
    origin_device_id text,
    origin_created_at timestamp with time zone,
    cloud_sequence bigint NOT NULL
);


--
-- Name: sync_outbox_cloud_sequence_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.sync_outbox ALTER COLUMN cloud_sequence ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME public.sync_outbox_cloud_sequence_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: user_locations; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.user_locations (
    user_id integer NOT NULL,
    location_id integer NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: users; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.users (
    user_id integer NOT NULL,
    username text NOT NULL,
    first_name text,
    middle_name text,
    last_name text,
    full_name text NOT NULL,
    nickname text,
    email text,
    phone text,
    employee_photo_url text,
    employee_id_card_document_url text,
    date_of_birth date,
    hire_date date DEFAULT CURRENT_DATE NOT NULL,
    badge_id text,
    badge_generated_at timestamp with time zone,
    badge_print_count integer DEFAULT 0 NOT NULL,
    badge_rotated_at timestamp with time zone,
    badge_rotated_by_user_id integer,
    badge_rotated_by_name text,
    compensation_type public.compensation_type_enum DEFAULT 'HOURLY'::public.compensation_type_enum NOT NULL,
    salary numeric(12,2) DEFAULT 0 NOT NULL,
    role_id integer,
    auth_user_id uuid,
    is_active boolean DEFAULT true NOT NULL,
    deactivated_at timestamp with time zone,
    deactivated_by_user_id integer,
    deactivated_by_name text,
    password_cache_invalidated_at timestamp with time zone,
    employee_pin_updated_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: users_user_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.users_user_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: users_user_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.users_user_id_seq OWNED BY public.users.user_id;


--
-- Name: first_admin_bootstrap; Type: TABLE; Schema: smartstock_private; Owner: -
--

CREATE TABLE smartstock_private.first_admin_bootstrap (
    bootstrap_key text NOT NULL,
    auth_user_id uuid NOT NULL,
    user_id integer NOT NULL,
    location_id integer NOT NULL,
    normalized_email text NOT NULL,
    completed_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: smartstock_schema_metadata; Type: TABLE; Schema: smartstock_private; Owner: -
--

CREATE TABLE smartstock_private.smartstock_schema_metadata (
    schema_scope text NOT NULL,
    baseline_version integer NOT NULL,
    resource_fingerprint_sha256 text NOT NULL,
    catalog_fingerprint_sha256 text NOT NULL,
    installed_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT smartstock_schema_metadata_baseline_version_check CHECK ((baseline_version > 0)),
    CONSTRAINT smartstock_schema_metadata_catalog_fingerprint_sha256_check CHECK ((catalog_fingerprint_sha256 ~ '^[0-9a-f]{64}$'::text)),
    CONSTRAINT smartstock_schema_metadata_resource_fingerprint_sha256_check CHECK ((resource_fingerprint_sha256 ~ '^[0-9a-f]{64}$'::text)),
    CONSTRAINT smartstock_schema_metadata_schema_scope_check CHECK ((schema_scope = 'CLOUD'::text))
);


--
-- Name: store_user_credentials; Type: TABLE; Schema: smartstock_private; Owner: -
--

CREATE TABLE smartstock_private.store_user_credentials (
    location_id integer NOT NULL,
    generation_id uuid NOT NULL,
    user_id integer NOT NULL,
    password_hash text,
    password_cache_invalidated_at timestamp with time zone,
    employee_pin_salt text,
    employee_pin_hash text,
    employee_pin_updated_at timestamp with time zone,
    badge_secret_salt text,
    badge_secret_hash text,
    verified_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT store_user_credentials_badge_secret_hash_check CHECK ((length(COALESCE(badge_secret_hash, ''::text)) <= 1024)),
    CONSTRAINT store_user_credentials_badge_secret_salt_check CHECK ((length(COALESCE(badge_secret_salt, ''::text)) <= 1024)),
    CONSTRAINT store_user_credentials_check CHECK (((employee_pin_salt IS NULL) = (employee_pin_hash IS NULL))),
    CONSTRAINT store_user_credentials_check1 CHECK (((badge_secret_salt IS NULL) = (badge_secret_hash IS NULL))),
    CONSTRAINT store_user_credentials_employee_pin_hash_check CHECK ((length(COALESCE(employee_pin_hash, ''::text)) <= 1024)),
    CONSTRAINT store_user_credentials_employee_pin_salt_check CHECK ((length(COALESCE(employee_pin_salt, ''::text)) <= 1024)),
    CONSTRAINT store_user_credentials_password_hash_check CHECK ((length(COALESCE(password_hash, ''::text)) <= 1024))
);


--
-- Name: app_releases release_id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.app_releases ALTER COLUMN release_id SET DEFAULT nextval('public.app_releases_release_id_seq'::regclass);


--
-- Name: device_sessions session_id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.device_sessions ALTER COLUMN session_id SET DEFAULT nextval('public.device_sessions_session_id_seq'::regclass);


--
-- Name: email_outbox email_outbox_id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.email_outbox ALTER COLUMN email_outbox_id SET DEFAULT nextval('public.email_outbox_email_outbox_id_seq'::regclass);


--
-- Name: email_outbox_events email_outbox_event_id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.email_outbox_events ALTER COLUMN email_outbox_event_id SET DEFAULT nextval('public.email_outbox_events_email_outbox_event_id_seq'::regclass);


--
-- Name: locations location_id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.locations ALTER COLUMN location_id SET DEFAULT nextval('public.locations_location_id_seq'::regclass);


--
-- Name: permissions permission_id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.permissions ALTER COLUMN permission_id SET DEFAULT nextval('public.permissions_permission_id_seq'::regclass);


--
-- Name: roles role_id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.roles ALTER COLUMN role_id SET DEFAULT nextval('public.roles_role_id_seq'::regclass);


--
-- Name: users user_id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.users ALTER COLUMN user_id SET DEFAULT nextval('public.users_user_id_seq'::regclass);


--
-- Name: app_releases app_releases_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.app_releases
    ADD CONSTRAINT app_releases_pkey PRIMARY KEY (release_id);


--
-- Name: device_sessions device_sessions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.device_sessions
    ADD CONSTRAINT device_sessions_pkey PRIMARY KEY (session_id);


--
-- Name: devices devices_installation_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.devices
    ADD CONSTRAINT devices_installation_id_key UNIQUE (installation_id);


--
-- Name: devices devices_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.devices
    ADD CONSTRAINT devices_pkey PRIMARY KEY (device_id);


--
-- Name: email_outbox_events email_outbox_events_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.email_outbox_events
    ADD CONSTRAINT email_outbox_events_pkey PRIMARY KEY (email_outbox_event_id);


--
-- Name: email_outbox email_outbox_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.email_outbox
    ADD CONSTRAINT email_outbox_pkey PRIMARY KEY (email_outbox_id);


--
-- Name: image_asset_references image_asset_references_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.image_asset_references
    ADD CONSTRAINT image_asset_references_pkey PRIMARY KEY (reference_id);


--
-- Name: image_asset_references image_asset_references_source_table_source_key_source_colum_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.image_asset_references
    ADD CONSTRAINT image_asset_references_source_table_source_key_source_colum_key UNIQUE (source_table, source_key, source_column);


--
-- Name: image_assets image_assets_bucket_name_object_path_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.image_assets
    ADD CONSTRAINT image_assets_bucket_name_object_path_key UNIQUE (bucket_name, object_path);


--
-- Name: image_assets image_assets_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.image_assets
    ADD CONSTRAINT image_assets_pkey PRIMARY KEY (asset_id);


--
-- Name: locations locations_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.locations
    ADD CONSTRAINT locations_pkey PRIMARY KEY (location_id);


--
-- Name: mobile_permissions mobile_permissions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.mobile_permissions
    ADD CONSTRAINT mobile_permissions_pkey PRIMARY KEY (permission_key);


--
-- Name: permissions permissions_permission_key_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.permissions
    ADD CONSTRAINT permissions_permission_key_key UNIQUE (permission_key);


--
-- Name: permissions permissions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.permissions
    ADD CONSTRAINT permissions_pkey PRIMARY KEY (permission_id);


--
-- Name: remote_admin_commands remote_admin_commands_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.remote_admin_commands
    ADD CONSTRAINT remote_admin_commands_pkey PRIMARY KEY (command_id);


--
-- Name: role_mobile_permissions role_mobile_permissions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.role_mobile_permissions
    ADD CONSTRAINT role_mobile_permissions_pkey PRIMARY KEY (role_id, permission_key);


--
-- Name: role_permissions role_permissions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.role_permissions
    ADD CONSTRAINT role_permissions_pkey PRIMARY KEY (role_id, permission_id);


--
-- Name: roles roles_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.roles
    ADD CONSTRAINT roles_pkey PRIMARY KEY (role_id);


--
-- Name: roles roles_role_name_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.roles
    ADD CONSTRAINT roles_role_name_key UNIQUE (role_name);


--
-- Name: smartstock_cross_store_refund_lines smartstock_cross_store_refund_lines_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.smartstock_cross_store_refund_lines
    ADD CONSTRAINT smartstock_cross_store_refund_lines_pkey PRIMARY KEY (request_id, source_sale_item_id);


--
-- Name: smartstock_cross_store_refund_requests smartstock_cross_store_refund_requests_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.smartstock_cross_store_refund_requests
    ADD CONSTRAINT smartstock_cross_store_refund_requests_pkey PRIMARY KEY (request_id);


--
-- Name: smartstock_cross_store_refund_requests smartstock_cross_store_refund_requests_request_sequence_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.smartstock_cross_store_refund_requests
    ADD CONSTRAINT smartstock_cross_store_refund_requests_request_sequence_key UNIQUE (request_sequence);


--
-- Name: smartstock_store_mirror_status smartstock_store_mirror_status_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.smartstock_store_mirror_status
    ADD CONSTRAINT smartstock_store_mirror_status_pkey PRIMARY KEY (location_id);


--
-- Name: smartstock_store_rows smartstock_store_rows_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.smartstock_store_rows
    ADD CONSTRAINT smartstock_store_rows_pkey PRIMARY KEY (location_id, table_name, row_key);


--
-- Name: smartstock_store_snapshot_generations smartstock_store_snapshot_generations_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.smartstock_store_snapshot_generations
    ADD CONSTRAINT smartstock_store_snapshot_generations_pkey PRIMARY KEY (generation_id);


--
-- Name: smartstock_store_snapshot_rows smartstock_store_snapshot_rows_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.smartstock_store_snapshot_rows
    ADD CONSTRAINT smartstock_store_snapshot_rows_pkey PRIMARY KEY (generation_id, table_name, row_key);


--
-- Name: store_server_events store_server_events_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.store_server_events
    ADD CONSTRAINT store_server_events_pkey PRIMARY KEY (server_event_id);


--
-- Name: store_server_handoffs store_server_handoffs_location_id_idempotency_key_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.store_server_handoffs
    ADD CONSTRAINT store_server_handoffs_location_id_idempotency_key_key UNIQUE (location_id, idempotency_key);


--
-- Name: store_server_handoffs store_server_handoffs_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.store_server_handoffs
    ADD CONSTRAINT store_server_handoffs_pkey PRIMARY KEY (handoff_id);


--
-- Name: store_server_instances store_server_instances_location_id_installation_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.store_server_instances
    ADD CONSTRAINT store_server_instances_location_id_installation_id_key UNIQUE (location_id, installation_id);


--
-- Name: store_server_instances store_server_instances_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.store_server_instances
    ADD CONSTRAINT store_server_instances_pkey PRIMARY KEY (server_instance_id);


--
-- Name: store_sync_status store_sync_status_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.store_sync_status
    ADD CONSTRAINT store_sync_status_pkey PRIMARY KEY (location_id);


--
-- Name: sync_applied_events sync_applied_events_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sync_applied_events
    ADD CONSTRAINT sync_applied_events_pkey PRIMARY KEY (origin_event_id);


--
-- Name: sync_outbox sync_outbox_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sync_outbox
    ADD CONSTRAINT sync_outbox_pkey PRIMARY KEY (event_id);


--
-- Name: user_locations user_locations_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_locations
    ADD CONSTRAINT user_locations_pkey PRIMARY KEY (user_id, location_id);


--
-- Name: users users_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_pkey PRIMARY KEY (user_id);


--
-- Name: users users_username_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_username_key UNIQUE (username);


--
-- Name: first_admin_bootstrap first_admin_bootstrap_pkey; Type: CONSTRAINT; Schema: smartstock_private; Owner: -
--

ALTER TABLE ONLY smartstock_private.first_admin_bootstrap
    ADD CONSTRAINT first_admin_bootstrap_pkey PRIMARY KEY (bootstrap_key);


--
-- Name: smartstock_schema_metadata smartstock_schema_metadata_pkey; Type: CONSTRAINT; Schema: smartstock_private; Owner: -
--

ALTER TABLE ONLY smartstock_private.smartstock_schema_metadata
    ADD CONSTRAINT smartstock_schema_metadata_pkey PRIMARY KEY (schema_scope);


--
-- Name: store_user_credentials store_user_credentials_pkey; Type: CONSTRAINT; Schema: smartstock_private; Owner: -
--

ALTER TABLE ONLY smartstock_private.store_user_credentials
    ADD CONSTRAINT store_user_credentials_pkey PRIMARY KEY (generation_id, user_id);


--
-- Name: app_releases_latest_published_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX app_releases_latest_published_idx ON public.app_releases USING btree (platform, build_number DESC) WHERE (published = true);


--
-- Name: app_releases_platform_build_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX app_releases_platform_build_idx ON public.app_releases USING btree (platform, build_number);


--
-- Name: device_sessions_active_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX device_sessions_active_idx ON public.device_sessions USING btree (device_id, session_status, logout_time);


--
-- Name: device_sessions_device_login_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX device_sessions_device_login_idx ON public.device_sessions USING btree (device_id, login_time DESC);


--
-- Name: device_sessions_login_time_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX device_sessions_login_time_idx ON public.device_sessions USING btree (login_time DESC);


--
-- Name: devices_installation_id_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX devices_installation_id_idx ON public.devices USING btree (installation_id);


--
-- Name: devices_last_seen_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX devices_last_seen_idx ON public.devices USING btree (last_seen DESC);


--
-- Name: devices_updated_at_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX devices_updated_at_idx ON public.devices USING btree (updated_at DESC);


--
-- Name: email_outbox_document_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX email_outbox_document_idx ON public.email_outbox USING btree (document_type, document_id, created_at DESC);


--
-- Name: email_outbox_events_outbox_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX email_outbox_events_outbox_idx ON public.email_outbox_events USING btree (email_outbox_id, created_at DESC);


--
-- Name: email_outbox_events_sync_uuid_key; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX email_outbox_events_sync_uuid_key ON public.email_outbox_events USING btree (sync_uuid);


--
-- Name: email_outbox_location_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX email_outbox_location_idx ON public.email_outbox USING btree (location_id, created_at DESC);


--
-- Name: email_outbox_status_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX email_outbox_status_idx ON public.email_outbox USING btree (status, created_at);


--
-- Name: email_outbox_sync_uuid_key; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX email_outbox_sync_uuid_key ON public.email_outbox USING btree (sync_uuid);


--
-- Name: image_asset_refs_asset_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX image_asset_refs_asset_idx ON public.image_asset_references USING btree (asset_id, active);


--
-- Name: image_assets_cloud_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX image_assets_cloud_idx ON public.image_assets USING btree (cloud_status, updated_at);


--
-- Name: image_assets_status_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX image_assets_status_idx ON public.image_assets USING btree (lifecycle_status, updated_at DESC);


--
-- Name: locations_updated_at_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX locations_updated_at_idx ON public.locations USING btree (updated_at DESC);


--
-- Name: role_mobile_permissions_updated_at_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX role_mobile_permissions_updated_at_idx ON public.role_mobile_permissions USING btree (updated_at DESC);


--
-- Name: role_permissions_updated_at_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX role_permissions_updated_at_idx ON public.role_permissions USING btree (updated_at DESC);


--
-- Name: roles_updated_at_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX roles_updated_at_idx ON public.roles USING btree (updated_at DESC);


--
-- Name: smartstock_cross_store_refund_destination_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX smartstock_cross_store_refund_destination_idx ON public.smartstock_cross_store_refund_lines USING btree (destination_location_id, destination_status);


--
-- Name: smartstock_cross_store_refund_source_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX smartstock_cross_store_refund_source_idx ON public.smartstock_cross_store_refund_requests USING btree (source_location_id, status, request_sequence);


--
-- Name: smartstock_cross_store_refund_receipt_number_uidx; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX smartstock_cross_store_refund_receipt_number_uidx ON public.smartstock_cross_store_refund_requests USING btree (return_receipt_number) WHERE (COALESCE(return_receipt_number, ''::text) <> ''::text);


--
-- Name: smartstock_store_rows_location_version_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX smartstock_store_rows_location_version_idx ON public.smartstock_store_rows USING btree (location_id, version_sequence);


--
-- Name: smartstock_store_rows_version_key; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX smartstock_store_rows_version_key ON public.smartstock_store_rows USING btree (version_sequence);


--
-- Name: smartstock_store_snapshot_completed_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX smartstock_store_snapshot_completed_idx ON public.smartstock_store_snapshot_generations USING btree (location_id, completed_at DESC) WHERE (status = 'COMPLETE'::text);


--
-- Name: smartstock_store_snapshot_one_building_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX smartstock_store_snapshot_one_building_idx ON public.smartstock_store_snapshot_generations USING btree (location_id) WHERE (status = 'BUILDING'::text);


--
-- Name: smartstock_store_snapshot_rows_page_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX smartstock_store_snapshot_rows_page_idx ON public.smartstock_store_snapshot_rows USING btree (generation_id, table_name, row_sequence);


--
-- Name: store_server_events_handoff_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX store_server_events_handoff_idx ON public.store_server_events USING btree (handoff_id);


--
-- Name: store_server_events_instance_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX store_server_events_instance_idx ON public.store_server_events USING btree (server_instance_id);


--
-- Name: store_server_events_location_created_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX store_server_events_location_created_idx ON public.store_server_events USING btree (location_id, created_at DESC);


--
-- Name: store_server_handoffs_one_open_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX store_server_handoffs_one_open_idx ON public.store_server_handoffs USING btree (location_id) WHERE (status = ANY (ARRAY['PREPARING'::text, 'READY'::text]));


--
-- Name: store_server_handoffs_source_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX store_server_handoffs_source_idx ON public.store_server_handoffs USING btree (source_server_instance_id);


--
-- Name: store_server_handoffs_target_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX store_server_handoffs_target_idx ON public.store_server_handoffs USING btree (target_server_instance_id);


--
-- Name: store_server_instances_generation_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX store_server_instances_generation_idx ON public.store_server_instances USING btree (location_id, generation) WHERE (generation > 0);


--
-- Name: store_server_instances_health_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX store_server_instances_health_idx ON public.store_server_instances USING btree (location_id, last_heartbeat_at DESC);


--
-- Name: store_server_instances_one_primary_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX store_server_instances_one_primary_idx ON public.store_server_instances USING btree (location_id) WHERE (role = 'PRIMARY'::text);


--
-- Name: store_server_instances_replaced_by_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX store_server_instances_replaced_by_idx ON public.store_server_instances USING btree (replaced_by_server_instance_id);


--
-- Name: sync_outbox_cloud_delta_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX sync_outbox_cloud_delta_idx ON public.sync_outbox USING btree (cloud_sequence, location_id);


--
-- Name: sync_outbox_cloud_sequence_key; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX sync_outbox_cloud_sequence_key ON public.sync_outbox USING btree (cloud_sequence);


--
-- Name: sync_outbox_status_created_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX sync_outbox_status_created_idx ON public.sync_outbox USING btree (status, created_at);


--
-- Name: user_locations_updated_at_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX user_locations_updated_at_idx ON public.user_locations USING btree (updated_at DESC);


--
-- Name: users_badge_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX users_badge_idx ON public.users USING btree (lower(badge_id));


--
-- Name: users_badge_normalized_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX users_badge_normalized_idx ON public.users USING btree (upper(regexp_replace(COALESCE(badge_id, ''::text), '[^a-zA-Z0-9]'::text, ''::text, 'g'::text)));


--
-- Name: users_email_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX users_email_idx ON public.users USING btree (lower(email));


--
-- Name: users_updated_at_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX users_updated_at_idx ON public.users USING btree (updated_at DESC);


--
-- Name: users_username_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX users_username_idx ON public.users USING btree (lower(username));


--
-- Name: device_sessions device_sessions_refresh_count; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER device_sessions_refresh_count AFTER INSERT OR DELETE OR UPDATE ON public.device_sessions FOR EACH ROW EXECUTE FUNCTION public.refresh_device_session_count();


--
-- Name: devices devices_set_updated_at; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER devices_set_updated_at BEFORE INSERT OR UPDATE ON public.devices FOR EACH ROW EXECUTE FUNCTION public.set_devices_updated_at();


--
-- Name: email_outbox email_outbox_set_updated_at; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER email_outbox_set_updated_at BEFORE INSERT OR UPDATE ON public.email_outbox FOR EACH ROW EXECUTE FUNCTION public.set_email_outbox_updated_at();


--
-- Name: image_asset_references image_asset_references_set_updated_at; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER image_asset_references_set_updated_at BEFORE INSERT OR UPDATE ON public.image_asset_references FOR EACH ROW EXECUTE FUNCTION public.set_image_asset_references_updated_at();


--
-- Name: image_assets image_assets_set_updated_at; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER image_assets_set_updated_at BEFORE INSERT OR UPDATE ON public.image_assets FOR EACH ROW EXECUTE FUNCTION public.set_image_assets_updated_at();


--
-- Name: locations locations_set_updated_at; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER locations_set_updated_at BEFORE INSERT OR UPDATE ON public.locations FOR EACH ROW EXECUTE FUNCTION public.set_locations_updated_at();


--
-- Name: role_mobile_permissions role_mobile_permissions_set_updated_at; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER role_mobile_permissions_set_updated_at BEFORE INSERT OR UPDATE ON public.role_mobile_permissions FOR EACH ROW EXECUTE FUNCTION public.set_role_mobile_permissions_updated_at();


--
-- Name: role_permissions role_permissions_set_updated_at; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER role_permissions_set_updated_at BEFORE INSERT OR UPDATE ON public.role_permissions FOR EACH ROW EXECUTE FUNCTION public.set_role_permissions_updated_at();


--
-- Name: roles roles_set_updated_at; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER roles_set_updated_at BEFORE INSERT OR UPDATE ON public.roles FOR EACH ROW EXECUTE FUNCTION public.set_roles_updated_at();


--
-- Name: user_locations user_locations_set_updated_at; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER user_locations_set_updated_at BEFORE INSERT OR UPDATE ON public.user_locations FOR EACH ROW EXECUTE FUNCTION public.set_user_locations_updated_at();


--
-- Name: users users_set_updated_at; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER users_set_updated_at BEFORE INSERT OR UPDATE ON public.users FOR EACH ROW EXECUTE FUNCTION public.set_users_updated_at();


--
-- Name: app_releases app_releases_created_by_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.app_releases
    ADD CONSTRAINT app_releases_created_by_user_id_fkey FOREIGN KEY (created_by_user_id) REFERENCES public.users(user_id);


--
-- Name: device_sessions device_sessions_device_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.device_sessions
    ADD CONSTRAINT device_sessions_device_id_fkey FOREIGN KEY (device_id) REFERENCES public.devices(device_id) ON DELETE CASCADE;


--
-- Name: device_sessions device_sessions_store_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.device_sessions
    ADD CONSTRAINT device_sessions_store_id_fkey FOREIGN KEY (store_id) REFERENCES public.locations(location_id);


--
-- Name: device_sessions device_sessions_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.device_sessions
    ADD CONSTRAINT device_sessions_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(user_id);


--
-- Name: devices devices_approved_by_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.devices
    ADD CONSTRAINT devices_approved_by_user_id_fkey FOREIGN KEY (approved_by_user_id) REFERENCES public.users(user_id);


--
-- Name: devices devices_blocked_by_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.devices
    ADD CONSTRAINT devices_blocked_by_user_id_fkey FOREIGN KEY (blocked_by_user_id) REFERENCES public.users(user_id);


--
-- Name: devices devices_last_login_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.devices
    ADD CONSTRAINT devices_last_login_user_id_fkey FOREIGN KEY (last_login_user_id) REFERENCES public.users(user_id);


--
-- Name: devices devices_last_store_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.devices
    ADD CONSTRAINT devices_last_store_id_fkey FOREIGN KEY (last_store_id) REFERENCES public.locations(location_id);


--
-- Name: email_outbox_events email_outbox_events_email_outbox_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.email_outbox_events
    ADD CONSTRAINT email_outbox_events_email_outbox_id_fkey FOREIGN KEY (email_outbox_id) REFERENCES public.email_outbox(email_outbox_id) ON DELETE CASCADE;


--
-- Name: email_outbox_events email_outbox_events_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.email_outbox_events
    ADD CONSTRAINT email_outbox_events_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(user_id);


--
-- Name: email_outbox email_outbox_location_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.email_outbox
    ADD CONSTRAINT email_outbox_location_id_fkey FOREIGN KEY (location_id) REFERENCES public.locations(location_id);


--
-- Name: email_outbox email_outbox_queued_by_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.email_outbox
    ADD CONSTRAINT email_outbox_queued_by_user_id_fkey FOREIGN KEY (queued_by_user_id) REFERENCES public.users(user_id);


--
-- Name: image_asset_references image_asset_references_asset_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.image_asset_references
    ADD CONSTRAINT image_asset_references_asset_id_fkey FOREIGN KEY (asset_id) REFERENCES public.image_assets(asset_id);


--
-- Name: remote_admin_commands remote_admin_commands_device_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.remote_admin_commands
    ADD CONSTRAINT remote_admin_commands_device_id_fkey FOREIGN KEY (device_id) REFERENCES public.devices(device_id) ON DELETE SET NULL;


--
-- Name: remote_admin_commands remote_admin_commands_location_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.remote_admin_commands
    ADD CONSTRAINT remote_admin_commands_location_id_fkey FOREIGN KEY (location_id) REFERENCES public.locations(location_id) ON DELETE CASCADE;


--
-- Name: remote_admin_commands remote_admin_commands_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.remote_admin_commands
    ADD CONSTRAINT remote_admin_commands_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(user_id) ON DELETE SET NULL;


--
-- Name: role_mobile_permissions role_mobile_permissions_permission_key_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.role_mobile_permissions
    ADD CONSTRAINT role_mobile_permissions_permission_key_fkey FOREIGN KEY (permission_key) REFERENCES public.mobile_permissions(permission_key) ON DELETE CASCADE;


--
-- Name: role_mobile_permissions role_mobile_permissions_role_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.role_mobile_permissions
    ADD CONSTRAINT role_mobile_permissions_role_id_fkey FOREIGN KEY (role_id) REFERENCES public.roles(role_id) ON DELETE CASCADE;


--
-- Name: role_permissions role_permissions_permission_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.role_permissions
    ADD CONSTRAINT role_permissions_permission_id_fkey FOREIGN KEY (permission_id) REFERENCES public.permissions(permission_id) ON DELETE CASCADE;


--
-- Name: role_permissions role_permissions_role_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.role_permissions
    ADD CONSTRAINT role_permissions_role_id_fkey FOREIGN KEY (role_id) REFERENCES public.roles(role_id) ON DELETE CASCADE;


--
-- Name: smartstock_cross_store_refund_lines smartstock_cross_store_refund_lines_request_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.smartstock_cross_store_refund_lines
    ADD CONSTRAINT smartstock_cross_store_refund_lines_request_id_fkey FOREIGN KEY (request_id) REFERENCES public.smartstock_cross_store_refund_requests(request_id) ON DELETE CASCADE;


--
-- Name: smartstock_store_mirror_status smartstock_store_mirror_status_current_generation_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.smartstock_store_mirror_status
    ADD CONSTRAINT smartstock_store_mirror_status_current_generation_id_fkey FOREIGN KEY (current_generation_id) REFERENCES public.smartstock_store_snapshot_generations(generation_id);


--
-- Name: smartstock_store_snapshot_generations smartstock_store_snapshot_generatio_based_on_generation_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.smartstock_store_snapshot_generations
    ADD CONSTRAINT smartstock_store_snapshot_generatio_based_on_generation_id_fkey FOREIGN KEY (based_on_generation_id) REFERENCES public.smartstock_store_snapshot_generations(generation_id);


--
-- Name: smartstock_store_snapshot_rows smartstock_store_snapshot_rows_generation_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.smartstock_store_snapshot_rows
    ADD CONSTRAINT smartstock_store_snapshot_rows_generation_id_fkey FOREIGN KEY (generation_id) REFERENCES public.smartstock_store_snapshot_generations(generation_id) ON DELETE CASCADE;


--
-- Name: store_server_events store_server_events_handoff_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.store_server_events
    ADD CONSTRAINT store_server_events_handoff_id_fkey FOREIGN KEY (handoff_id) REFERENCES public.store_server_handoffs(handoff_id);


--
-- Name: store_server_events store_server_events_location_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.store_server_events
    ADD CONSTRAINT store_server_events_location_id_fkey FOREIGN KEY (location_id) REFERENCES public.locations(location_id);


--
-- Name: store_server_events store_server_events_server_instance_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.store_server_events
    ADD CONSTRAINT store_server_events_server_instance_id_fkey FOREIGN KEY (server_instance_id) REFERENCES public.store_server_instances(server_instance_id);


--
-- Name: store_server_handoffs store_server_handoffs_location_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.store_server_handoffs
    ADD CONSTRAINT store_server_handoffs_location_id_fkey FOREIGN KEY (location_id) REFERENCES public.locations(location_id);


--
-- Name: store_server_handoffs store_server_handoffs_source_server_instance_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.store_server_handoffs
    ADD CONSTRAINT store_server_handoffs_source_server_instance_id_fkey FOREIGN KEY (source_server_instance_id) REFERENCES public.store_server_instances(server_instance_id);


--
-- Name: store_server_handoffs store_server_handoffs_target_server_instance_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.store_server_handoffs
    ADD CONSTRAINT store_server_handoffs_target_server_instance_id_fkey FOREIGN KEY (target_server_instance_id) REFERENCES public.store_server_instances(server_instance_id);


--
-- Name: store_server_instances store_server_instances_location_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.store_server_instances
    ADD CONSTRAINT store_server_instances_location_id_fkey FOREIGN KEY (location_id) REFERENCES public.locations(location_id);


--
-- Name: store_server_instances store_server_instances_replaced_by_server_instance_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.store_server_instances
    ADD CONSTRAINT store_server_instances_replaced_by_server_instance_id_fkey FOREIGN KEY (replaced_by_server_instance_id) REFERENCES public.store_server_instances(server_instance_id);


--
-- Name: store_sync_status store_sync_status_location_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.store_sync_status
    ADD CONSTRAINT store_sync_status_location_id_fkey FOREIGN KEY (location_id) REFERENCES public.locations(location_id) ON DELETE CASCADE;


--
-- Name: user_locations user_locations_location_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_locations
    ADD CONSTRAINT user_locations_location_id_fkey FOREIGN KEY (location_id) REFERENCES public.locations(location_id) ON DELETE CASCADE;


--
-- Name: user_locations user_locations_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_locations
    ADD CONSTRAINT user_locations_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(user_id) ON DELETE CASCADE;


--
-- Name: users users_role_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_role_id_fkey FOREIGN KEY (role_id) REFERENCES public.roles(role_id);


--
-- Name: store_user_credentials store_user_credentials_generation_id_fkey; Type: FK CONSTRAINT; Schema: smartstock_private; Owner: -
--

ALTER TABLE ONLY smartstock_private.store_user_credentials
    ADD CONSTRAINT store_user_credentials_generation_id_fkey FOREIGN KEY (generation_id) REFERENCES public.smartstock_store_snapshot_generations(generation_id) ON DELETE CASCADE;


--
-- Name: app_releases; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.app_releases ENABLE ROW LEVEL SECURITY;

--
-- Name: app_releases app_releases_authenticated_published_read; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY app_releases_authenticated_published_read ON public.app_releases FOR SELECT TO authenticated USING ((published = true));


--
-- Name: app_releases app_releases_service_role_all; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY app_releases_service_role_all ON public.app_releases TO service_role USING (true) WITH CHECK (true);


--
-- Name: device_sessions; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.device_sessions ENABLE ROW LEVEL SECURITY;

--
-- Name: devices; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.devices ENABLE ROW LEVEL SECURITY;

--
-- Name: email_outbox; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.email_outbox ENABLE ROW LEVEL SECURITY;

--
-- Name: email_outbox_events; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.email_outbox_events ENABLE ROW LEVEL SECURITY;

--
-- Name: email_outbox_events email_outbox_events_service_role_all; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY email_outbox_events_service_role_all ON public.email_outbox_events TO service_role USING (true) WITH CHECK (true);


--
-- Name: email_outbox email_outbox_service_role_all; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY email_outbox_service_role_all ON public.email_outbox TO service_role USING (true) WITH CHECK (true);


--
-- Name: image_asset_references; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.image_asset_references ENABLE ROW LEVEL SECURITY;

--
-- Name: image_assets; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.image_assets ENABLE ROW LEVEL SECURITY;

--
-- Name: locations; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.locations ENABLE ROW LEVEL SECURITY;

--
-- Name: mobile_permissions; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.mobile_permissions ENABLE ROW LEVEL SECURITY;

--
-- Name: permissions; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.permissions ENABLE ROW LEVEL SECURITY;

--
-- Name: remote_admin_commands; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.remote_admin_commands ENABLE ROW LEVEL SECURITY;

--
-- Name: remote_admin_commands remote_admin_commands_service_role_all; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY remote_admin_commands_service_role_all ON public.remote_admin_commands TO service_role USING (true) WITH CHECK (true);


--
-- Name: role_mobile_permissions; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.role_mobile_permissions ENABLE ROW LEVEL SECURITY;

--
-- Name: role_permissions; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.role_permissions ENABLE ROW LEVEL SECURITY;

--
-- Name: roles; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.roles ENABLE ROW LEVEL SECURITY;

--
-- Name: smartstock_cross_store_refund_lines; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.smartstock_cross_store_refund_lines ENABLE ROW LEVEL SECURITY;

--
-- Name: smartstock_cross_store_refund_requests; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.smartstock_cross_store_refund_requests ENABLE ROW LEVEL SECURITY;

--
-- Name: smartstock_store_mirror_status; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.smartstock_store_mirror_status ENABLE ROW LEVEL SECURITY;

--
-- Name: smartstock_store_rows; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.smartstock_store_rows ENABLE ROW LEVEL SECURITY;

--
-- Name: smartstock_store_snapshot_generations; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.smartstock_store_snapshot_generations ENABLE ROW LEVEL SECURITY;

--
-- Name: smartstock_store_snapshot_rows; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.smartstock_store_snapshot_rows ENABLE ROW LEVEL SECURITY;

--
-- Name: store_server_events; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.store_server_events ENABLE ROW LEVEL SECURITY;

--
-- Name: store_server_handoffs; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.store_server_handoffs ENABLE ROW LEVEL SECURITY;

--
-- Name: store_server_instances; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.store_server_instances ENABLE ROW LEVEL SECURITY;

--
-- Name: store_sync_status; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.store_sync_status ENABLE ROW LEVEL SECURITY;

--
-- Name: store_sync_status store_sync_status_service_role_all; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY store_sync_status_service_role_all ON public.store_sync_status TO service_role USING (true) WITH CHECK (true);


--
-- Name: sync_applied_events; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.sync_applied_events ENABLE ROW LEVEL SECURITY;

--
-- Name: sync_outbox; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.sync_outbox ENABLE ROW LEVEL SECURITY;

--
-- Name: user_locations; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.user_locations ENABLE ROW LEVEL SECURITY;

--
-- Name: users; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.users ENABLE ROW LEVEL SECURITY;

--
-- Name: first_admin_bootstrap; Type: ROW SECURITY; Schema: smartstock_private; Owner: -
--

ALTER TABLE smartstock_private.first_admin_bootstrap ENABLE ROW LEVEL SECURITY;

--
-- Name: smartstock_schema_metadata; Type: ROW SECURITY; Schema: smartstock_private; Owner: -
--

ALTER TABLE smartstock_private.smartstock_schema_metadata ENABLE ROW LEVEL SECURITY;

--
-- Name: store_user_credentials; Type: ROW SECURITY; Schema: smartstock_private; Owner: -
--

ALTER TABLE smartstock_private.store_user_credentials ENABLE ROW LEVEL SECURITY;

--
-- Name: SCHEMA smartstock_private; Type: ACL; Schema: -; Owner: -
--

GRANT USAGE ON SCHEMA smartstock_private TO service_role;


--
-- Name: FUNCTION current_app_user_can_manage_employee_files(); Type: ACL; Schema: public; Owner: -
--

REVOKE ALL ON FUNCTION public.current_app_user_can_manage_employee_files() FROM PUBLIC;
GRANT ALL ON FUNCTION public.current_app_user_can_manage_employee_files() TO service_role;
GRANT ALL ON FUNCTION public.current_app_user_can_manage_employee_files() TO authenticated;


--
-- Name: FUNCTION current_app_user_has_location(target_location_id integer); Type: ACL; Schema: public; Owner: -
--

REVOKE ALL ON FUNCTION public.current_app_user_has_location(target_location_id integer) FROM PUBLIC;
GRANT ALL ON FUNCTION public.current_app_user_has_location(target_location_id integer) TO service_role;
GRANT ALL ON FUNCTION public.current_app_user_has_location(target_location_id integer) TO authenticated;


--
-- Name: FUNCTION current_app_user_id(); Type: ACL; Schema: public; Owner: -
--

REVOKE ALL ON FUNCTION public.current_app_user_id() FROM PUBLIC;
GRANT ALL ON FUNCTION public.current_app_user_id() TO service_role;
GRANT ALL ON FUNCTION public.current_app_user_id() TO authenticated;


--
-- Name: FUNCTION current_app_user_is_admin(); Type: ACL; Schema: public; Owner: -
--

REVOKE ALL ON FUNCTION public.current_app_user_is_admin() FROM PUBLIC;
GRANT ALL ON FUNCTION public.current_app_user_is_admin() TO service_role;
GRANT ALL ON FUNCTION public.current_app_user_is_admin() TO authenticated;


--
-- Name: FUNCTION refresh_device_session_count(); Type: ACL; Schema: public; Owner: -
--

REVOKE ALL ON FUNCTION public.refresh_device_session_count() FROM PUBLIC;
GRANT ALL ON FUNCTION public.refresh_device_session_count() TO service_role;


--
-- Name: FUNCTION set_devices_updated_at(); Type: ACL; Schema: public; Owner: -
--

REVOKE ALL ON FUNCTION public.set_devices_updated_at() FROM PUBLIC;
GRANT ALL ON FUNCTION public.set_devices_updated_at() TO service_role;


--
-- Name: FUNCTION set_email_outbox_updated_at(); Type: ACL; Schema: public; Owner: -
--

REVOKE ALL ON FUNCTION public.set_email_outbox_updated_at() FROM PUBLIC;
GRANT ALL ON FUNCTION public.set_email_outbox_updated_at() TO service_role;


--
-- Name: FUNCTION set_image_asset_references_updated_at(); Type: ACL; Schema: public; Owner: -
--

REVOKE ALL ON FUNCTION public.set_image_asset_references_updated_at() FROM PUBLIC;
GRANT ALL ON FUNCTION public.set_image_asset_references_updated_at() TO service_role;


--
-- Name: FUNCTION set_image_assets_updated_at(); Type: ACL; Schema: public; Owner: -
--

REVOKE ALL ON FUNCTION public.set_image_assets_updated_at() FROM PUBLIC;
GRANT ALL ON FUNCTION public.set_image_assets_updated_at() TO service_role;


--
-- Name: FUNCTION set_locations_updated_at(); Type: ACL; Schema: public; Owner: -
--

REVOKE ALL ON FUNCTION public.set_locations_updated_at() FROM PUBLIC;
GRANT ALL ON FUNCTION public.set_locations_updated_at() TO service_role;


--
-- Name: FUNCTION set_role_mobile_permissions_updated_at(); Type: ACL; Schema: public; Owner: -
--

REVOKE ALL ON FUNCTION public.set_role_mobile_permissions_updated_at() FROM PUBLIC;
GRANT ALL ON FUNCTION public.set_role_mobile_permissions_updated_at() TO service_role;


--
-- Name: FUNCTION set_role_permissions_updated_at(); Type: ACL; Schema: public; Owner: -
--

REVOKE ALL ON FUNCTION public.set_role_permissions_updated_at() FROM PUBLIC;
GRANT ALL ON FUNCTION public.set_role_permissions_updated_at() TO service_role;


--
-- Name: FUNCTION set_roles_updated_at(); Type: ACL; Schema: public; Owner: -
--

REVOKE ALL ON FUNCTION public.set_roles_updated_at() FROM PUBLIC;
GRANT ALL ON FUNCTION public.set_roles_updated_at() TO service_role;


--
-- Name: FUNCTION set_user_locations_updated_at(); Type: ACL; Schema: public; Owner: -
--

REVOKE ALL ON FUNCTION public.set_user_locations_updated_at() FROM PUBLIC;
GRANT ALL ON FUNCTION public.set_user_locations_updated_at() TO service_role;


--
-- Name: FUNCTION set_users_updated_at(); Type: ACL; Schema: public; Owner: -
--

REVOKE ALL ON FUNCTION public.set_users_updated_at() FROM PUBLIC;
GRANT ALL ON FUNCTION public.set_users_updated_at() TO service_role;


--
-- Name: FUNCTION smartstock_abandon_store_mirror(p_location_id integer, p_generation_id uuid); Type: ACL; Schema: public; Owner: -
--

REVOKE ALL ON FUNCTION public.smartstock_abandon_store_mirror(p_location_id integer, p_generation_id uuid) FROM PUBLIC;
GRANT ALL ON FUNCTION public.smartstock_abandon_store_mirror(p_location_id integer, p_generation_id uuid) TO service_role;


--
-- Name: FUNCTION smartstock_begin_store_mirror(p_location_id integer, p_generation_id uuid, p_clone_current boolean); Type: ACL; Schema: public; Owner: -
--

REVOKE ALL ON FUNCTION public.smartstock_begin_store_mirror(p_location_id integer, p_generation_id uuid, p_clone_current boolean) FROM PUBLIC;
GRANT ALL ON FUNCTION public.smartstock_begin_store_mirror(p_location_id integer, p_generation_id uuid, p_clone_current boolean) TO service_role;


--
-- Name: FUNCTION smartstock_bootstrap_first_admin(payload jsonb); Type: ACL; Schema: public; Owner: -
--

REVOKE ALL ON FUNCTION public.smartstock_bootstrap_first_admin(payload jsonb) FROM PUBLIC;
GRANT ALL ON FUNCTION public.smartstock_bootstrap_first_admin(payload jsonb) TO service_role;


--
-- Name: FUNCTION smartstock_cross_store_refund_queue(p_location_id integer); Type: ACL; Schema: public; Owner: -
--

REVOKE ALL ON FUNCTION public.smartstock_cross_store_refund_queue(p_location_id integer) FROM PUBLIC;
GRANT ALL ON FUNCTION public.smartstock_cross_store_refund_queue(p_location_id integer) TO service_role;


--
-- Name: FUNCTION smartstock_discard_abandoned_store_mirrors(p_location_id integer, p_older_than_seconds integer); Type: ACL; Schema: public; Owner: -
--

REVOKE ALL ON FUNCTION public.smartstock_discard_abandoned_store_mirrors(p_location_id integer, p_older_than_seconds integer) FROM PUBLIC;
GRANT ALL ON FUNCTION public.smartstock_discard_abandoned_store_mirrors(p_location_id integer, p_older_than_seconds integer) TO service_role;


--
-- Name: FUNCTION smartstock_finalize_store_mirror(p_location_id integer, p_generation_id uuid, p_table_counts jsonb); Type: ACL; Schema: public; Owner: -
--

REVOKE ALL ON FUNCTION public.smartstock_finalize_store_mirror(p_location_id integer, p_generation_id uuid, p_table_counts jsonb) FROM PUBLIC;
GRANT ALL ON FUNCTION public.smartstock_finalize_store_mirror(p_location_id integer, p_generation_id uuid, p_table_counts jsonb) TO service_role;


--
-- Name: FUNCTION smartstock_materialize_store_rows(p_location_id integer, p_generation_id uuid, p_rows jsonb); Type: ACL; Schema: public; Owner: -
--

REVOKE ALL ON FUNCTION public.smartstock_materialize_store_rows(p_location_id integer, p_generation_id uuid, p_rows jsonb) FROM PUBLIC;
GRANT ALL ON FUNCTION public.smartstock_materialize_store_rows(p_location_id integer, p_generation_id uuid, p_rows jsonb) TO service_role;


--
-- Name: FUNCTION smartstock_reserve_cross_store_refund(p_request jsonb); Type: ACL; Schema: public; Owner: -
--

REVOKE ALL ON FUNCTION public.smartstock_reserve_cross_store_refund(p_request jsonb) FROM PUBLIC;
GRANT ALL ON FUNCTION public.smartstock_reserve_cross_store_refund(p_request jsonb) TO service_role;


--
-- Name: FUNCTION smartstock_server_registry(p_action text, p_payload jsonb); Type: ACL; Schema: public; Owner: -
--

REVOKE ALL ON FUNCTION public.smartstock_server_registry(p_action text, p_payload jsonb) FROM PUBLIC;
GRANT ALL ON FUNCTION public.smartstock_server_registry(p_action text, p_payload jsonb) TO service_role;


--
-- Name: FUNCTION smartstock_store_snapshot_manifest(p_location_id integer); Type: ACL; Schema: public; Owner: -
--

REVOKE ALL ON FUNCTION public.smartstock_store_snapshot_manifest(p_location_id integer) FROM PUBLIC;
GRANT ALL ON FUNCTION public.smartstock_store_snapshot_manifest(p_location_id integer) TO service_role;


--
-- Name: FUNCTION smartstock_store_table_snapshot(p_location_id integer, p_table_name text, p_generation_id uuid, p_after_sequence bigint, p_limit integer); Type: ACL; Schema: public; Owner: -
--

REVOKE ALL ON FUNCTION public.smartstock_store_table_snapshot(p_location_id integer, p_table_name text, p_generation_id uuid, p_after_sequence bigint, p_limit integer) FROM PUBLIC;
GRANT ALL ON FUNCTION public.smartstock_store_table_snapshot(p_location_id integer, p_table_name text, p_generation_id uuid, p_after_sequence bigint, p_limit integer) TO service_role;


--
-- Name: FUNCTION smartstock_store_user_credentials(p_location_id integer, p_generation_id uuid); Type: ACL; Schema: public; Owner: -
--

REVOKE ALL ON FUNCTION public.smartstock_store_user_credentials(p_location_id integer, p_generation_id uuid) FROM PUBLIC;
GRANT ALL ON FUNCTION public.smartstock_store_user_credentials(p_location_id integer, p_generation_id uuid) TO service_role;


--
-- Name: FUNCTION smartstock_sync_exchange(p_location_id integer, p_cursor bigint, p_events jsonb, p_limit integer); Type: ACL; Schema: public; Owner: -
--

REVOKE ALL ON FUNCTION public.smartstock_sync_exchange(p_location_id integer, p_cursor bigint, p_events jsonb, p_limit integer) FROM PUBLIC;
GRANT ALL ON FUNCTION public.smartstock_sync_exchange(p_location_id integer, p_cursor bigint, p_events jsonb, p_limit integer) TO service_role;


--
-- Name: FUNCTION smartstock_sync_manifest(); Type: ACL; Schema: public; Owner: -
--

REVOKE ALL ON FUNCTION public.smartstock_sync_manifest() FROM PUBLIC;
GRANT ALL ON FUNCTION public.smartstock_sync_manifest() TO service_role;


--
-- Name: FUNCTION smartstock_update_cross_store_refund(p_request_id uuid, p_status text, p_lines jsonb, p_error text); Type: ACL; Schema: public; Owner: -
--

REVOKE ALL ON FUNCTION public.smartstock_update_cross_store_refund(p_request_id uuid, p_status text, p_lines jsonb, p_error text) FROM PUBLIC;
GRANT ALL ON FUNCTION public.smartstock_update_cross_store_refund(p_request_id uuid, p_status text, p_lines jsonb, p_error text) TO service_role;


--
-- Name: FUNCTION smartstock_upsert_store_user_credentials(p_location_id integer, p_generation_id uuid, p_rows jsonb); Type: ACL; Schema: public; Owner: -
--

REVOKE ALL ON FUNCTION public.smartstock_upsert_store_user_credentials(p_location_id integer, p_generation_id uuid, p_rows jsonb) FROM PUBLIC;
GRANT ALL ON FUNCTION public.smartstock_upsert_store_user_credentials(p_location_id integer, p_generation_id uuid, p_rows jsonb) TO service_role;


--
-- Name: FUNCTION smartstock_verify_store_mirror(p_location_id integer, p_generation_id uuid, p_table_counts jsonb, p_active_row_count bigint); Type: ACL; Schema: public; Owner: -
--

REVOKE ALL ON FUNCTION public.smartstock_verify_store_mirror(p_location_id integer, p_generation_id uuid, p_table_counts jsonb, p_active_row_count bigint) FROM PUBLIC;
GRANT ALL ON FUNCTION public.smartstock_verify_store_mirror(p_location_id integer, p_generation_id uuid, p_table_counts jsonb, p_active_row_count bigint) TO service_role;


--
-- Name: FUNCTION smartstock_server_registry(p_action text, p_payload jsonb); Type: ACL; Schema: smartstock_private; Owner: -
--

REVOKE ALL ON FUNCTION smartstock_private.smartstock_server_registry(p_action text, p_payload jsonb) FROM PUBLIC;
GRANT ALL ON FUNCTION smartstock_private.smartstock_server_registry(p_action text, p_payload jsonb) TO service_role;


--
-- Name: TABLE app_releases; Type: ACL; Schema: public; Owner: -
--

GRANT ALL ON TABLE public.app_releases TO service_role;
GRANT SELECT ON TABLE public.app_releases TO authenticated;


--
-- Name: SEQUENCE app_releases_release_id_seq; Type: ACL; Schema: public; Owner: -
--

GRANT ALL ON SEQUENCE public.app_releases_release_id_seq TO service_role;


--
-- Name: SEQUENCE customer_account_ba_number_seq; Type: ACL; Schema: public; Owner: -
--

GRANT ALL ON SEQUENCE public.customer_account_ba_number_seq TO service_role;


--
-- Name: SEQUENCE customer_account_ca_number_seq; Type: ACL; Schema: public; Owner: -
--

GRANT ALL ON SEQUENCE public.customer_account_ca_number_seq TO service_role;


--
-- Name: TABLE device_sessions; Type: ACL; Schema: public; Owner: -
--

GRANT ALL ON TABLE public.device_sessions TO service_role;


--
-- Name: SEQUENCE device_sessions_session_id_seq; Type: ACL; Schema: public; Owner: -
--

GRANT ALL ON SEQUENCE public.device_sessions_session_id_seq TO service_role;


--
-- Name: TABLE devices; Type: ACL; Schema: public; Owner: -
--

GRANT ALL ON TABLE public.devices TO service_role;


--
-- Name: TABLE email_outbox; Type: ACL; Schema: public; Owner: -
--

GRANT ALL ON TABLE public.email_outbox TO service_role;


--
-- Name: SEQUENCE email_outbox_email_outbox_id_seq; Type: ACL; Schema: public; Owner: -
--

GRANT ALL ON SEQUENCE public.email_outbox_email_outbox_id_seq TO service_role;


--
-- Name: TABLE email_outbox_events; Type: ACL; Schema: public; Owner: -
--

GRANT ALL ON TABLE public.email_outbox_events TO service_role;


--
-- Name: SEQUENCE email_outbox_events_email_outbox_event_id_seq; Type: ACL; Schema: public; Owner: -
--

GRANT ALL ON SEQUENCE public.email_outbox_events_email_outbox_event_id_seq TO service_role;


--
-- Name: TABLE image_asset_references; Type: ACL; Schema: public; Owner: -
--

GRANT ALL ON TABLE public.image_asset_references TO service_role;


--
-- Name: TABLE image_assets; Type: ACL; Schema: public; Owner: -
--

GRANT ALL ON TABLE public.image_assets TO service_role;


--
-- Name: TABLE locations; Type: ACL; Schema: public; Owner: -
--

GRANT ALL ON TABLE public.locations TO service_role;


--
-- Name: SEQUENCE locations_location_id_seq; Type: ACL; Schema: public; Owner: -
--

GRANT ALL ON SEQUENCE public.locations_location_id_seq TO service_role;


--
-- Name: TABLE mobile_permissions; Type: ACL; Schema: public; Owner: -
--

GRANT ALL ON TABLE public.mobile_permissions TO service_role;


--
-- Name: TABLE permissions; Type: ACL; Schema: public; Owner: -
--

GRANT ALL ON TABLE public.permissions TO service_role;


--
-- Name: SEQUENCE permissions_permission_id_seq; Type: ACL; Schema: public; Owner: -
--

GRANT ALL ON SEQUENCE public.permissions_permission_id_seq TO service_role;


--
-- Name: TABLE remote_admin_commands; Type: ACL; Schema: public; Owner: -
--

GRANT ALL ON TABLE public.remote_admin_commands TO service_role;


--
-- Name: TABLE role_mobile_permissions; Type: ACL; Schema: public; Owner: -
--

GRANT ALL ON TABLE public.role_mobile_permissions TO service_role;


--
-- Name: TABLE role_permissions; Type: ACL; Schema: public; Owner: -
--

GRANT ALL ON TABLE public.role_permissions TO service_role;


--
-- Name: TABLE roles; Type: ACL; Schema: public; Owner: -
--

GRANT ALL ON TABLE public.roles TO service_role;


--
-- Name: SEQUENCE roles_role_id_seq; Type: ACL; Schema: public; Owner: -
--

GRANT ALL ON SEQUENCE public.roles_role_id_seq TO service_role;


--
-- Name: TABLE smartstock_cross_store_refund_lines; Type: ACL; Schema: public; Owner: -
--

GRANT ALL ON TABLE public.smartstock_cross_store_refund_lines TO service_role;


--
-- Name: SEQUENCE smartstock_cross_store_refund_sequence; Type: ACL; Schema: public; Owner: -
--

GRANT ALL ON SEQUENCE public.smartstock_cross_store_refund_sequence TO service_role;


--
-- Name: TABLE smartstock_cross_store_refund_requests; Type: ACL; Schema: public; Owner: -
--

GRANT ALL ON TABLE public.smartstock_cross_store_refund_requests TO service_role;


--
-- Name: TABLE smartstock_store_mirror_status; Type: ACL; Schema: public; Owner: -
--

GRANT ALL ON TABLE public.smartstock_store_mirror_status TO service_role;


--
-- Name: SEQUENCE smartstock_store_row_version_seq; Type: ACL; Schema: public; Owner: -
--

GRANT ALL ON SEQUENCE public.smartstock_store_row_version_seq TO service_role;


--
-- Name: TABLE smartstock_store_rows; Type: ACL; Schema: public; Owner: -
--

GRANT ALL ON TABLE public.smartstock_store_rows TO service_role;


--
-- Name: TABLE smartstock_store_snapshot_generations; Type: ACL; Schema: public; Owner: -
--

GRANT ALL ON TABLE public.smartstock_store_snapshot_generations TO service_role;


--
-- Name: TABLE smartstock_store_snapshot_rows; Type: ACL; Schema: public; Owner: -
--

GRANT ALL ON TABLE public.smartstock_store_snapshot_rows TO service_role;


--
-- Name: SEQUENCE smartstock_store_snapshot_rows_row_sequence_seq; Type: ACL; Schema: public; Owner: -
--

GRANT ALL ON SEQUENCE public.smartstock_store_snapshot_rows_row_sequence_seq TO service_role;


--
-- Name: TABLE store_server_events; Type: ACL; Schema: public; Owner: -
--

GRANT ALL ON TABLE public.store_server_events TO service_role;


--
-- Name: SEQUENCE store_server_events_server_event_id_seq; Type: ACL; Schema: public; Owner: -
--

GRANT ALL ON SEQUENCE public.store_server_events_server_event_id_seq TO service_role;


--
-- Name: TABLE store_server_handoffs; Type: ACL; Schema: public; Owner: -
--

GRANT ALL ON TABLE public.store_server_handoffs TO service_role;


--
-- Name: TABLE store_server_instances; Type: ACL; Schema: public; Owner: -
--

GRANT ALL ON TABLE public.store_server_instances TO service_role;


--
-- Name: TABLE store_sync_status; Type: ACL; Schema: public; Owner: -
--

GRANT ALL ON TABLE public.store_sync_status TO service_role;


--
-- Name: TABLE sync_applied_events; Type: ACL; Schema: public; Owner: -
--

GRANT ALL ON TABLE public.sync_applied_events TO service_role;


--
-- Name: TABLE sync_outbox; Type: ACL; Schema: public; Owner: -
--

GRANT ALL ON TABLE public.sync_outbox TO service_role;


--
-- Name: SEQUENCE sync_outbox_cloud_sequence_seq; Type: ACL; Schema: public; Owner: -
--

GRANT ALL ON SEQUENCE public.sync_outbox_cloud_sequence_seq TO service_role;


--
-- Name: TABLE user_locations; Type: ACL; Schema: public; Owner: -
--

GRANT ALL ON TABLE public.user_locations TO service_role;


--
-- Name: TABLE users; Type: ACL; Schema: public; Owner: -
--

GRANT ALL ON TABLE public.users TO service_role;


--
-- Name: SEQUENCE users_user_id_seq; Type: ACL; Schema: public; Owner: -
--

GRANT ALL ON SEQUENCE public.users_user_id_seq TO service_role;


--
-- Name: TABLE first_admin_bootstrap; Type: ACL; Schema: smartstock_private; Owner: -
--

GRANT ALL ON TABLE smartstock_private.first_admin_bootstrap TO service_role;


--
-- Name: TABLE smartstock_schema_metadata; Type: ACL; Schema: smartstock_private; Owner: -
--

GRANT ALL ON TABLE smartstock_private.smartstock_schema_metadata TO service_role;


--
-- Name: TABLE store_user_credentials; Type: ACL; Schema: smartstock_private; Owner: -
--

GRANT ALL ON TABLE smartstock_private.store_user_credentials TO service_role;


--
-- PostgreSQL database dump complete
--
CREATE TABLE public.register_transfers (
    transfer_id uuid DEFAULT gen_random_uuid() PRIMARY KEY,
    device_id uuid NOT NULL REFERENCES public.devices(device_id), installation_id text NOT NULL,
    source_location_id integer REFERENCES public.locations(location_id), destination_location_id integer NOT NULL REFERENCES public.locations(location_id),
    status text DEFAULT 'PREPARED' NOT NULL CHECK (status IN ('PREPARED','COMPLETED','CANCELLED','EXPIRED')),
    emergency boolean DEFAULT false NOT NULL, reason text,
    initiated_by_user_id integer REFERENCES public.users(user_id), completed_by_user_id integer REFERENCES public.users(user_id),
    prepared_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL, expires_at timestamp with time zone NOT NULL,
    completed_at timestamp with time zone, cancelled_at timestamp with time zone,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);
CREATE UNIQUE INDEX register_transfers_one_prepared_device_idx ON public.register_transfers(device_id) WHERE status='PREPARED';
CREATE INDEX register_transfers_destination_idx ON public.register_transfers(destination_location_id,status,expires_at);
CREATE INDEX register_transfers_installation_idx ON public.register_transfers(installation_id,status,expires_at);
ALTER TABLE public.register_transfers ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON TABLE public.register_transfers FROM anon;
REVOKE ALL ON TABLE public.register_transfers FROM authenticated;
