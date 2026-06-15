-- Supabase RPC hardening for SmartStock API clients.
--
-- This script is for the live Supabase/PostgREST project, not plain local
-- PostgreSQL. The Java desktop app uses direct JDBC for these same flows, but
-- mobile/API clients call these public RPCs.

CREATE OR REPLACE FUNCTION public.current_app_user_id()
RETURNS integer
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path TO ''
AS $$
  SELECT u.user_id
  FROM public.users u
  WHERE u.auth_user_id = auth.uid()
    AND COALESCE(u.is_active, true) = true
  LIMIT 1
$$;

CREATE OR REPLACE FUNCTION public.current_app_user_is_admin()
RETURNS boolean
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path TO ''
AS $$
  SELECT EXISTS (
    SELECT 1
    FROM public.users u
    WHERE u.auth_user_id = auth.uid()
      AND COALESCE(u.is_active, true) = true
      AND u.role_id = 1
  )
$$;

CREATE OR REPLACE FUNCTION public.current_app_user_has_location(target_location_id integer)
RETURNS boolean
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path TO ''
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

CREATE OR REPLACE FUNCTION public.lookup_login_user(identifier text)
RETURNS TABLE(email text, auth_user_id uuid, is_active boolean)
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path TO ''
AS $$
  WITH normalized AS (
    SELECT
      pg_catalog.lower(pg_catalog.btrim(identifier)) AS ident,
      pg_catalog.upper(pg_catalog.regexp_replace(pg_catalog.btrim(identifier), '[^a-zA-Z0-9]', '', 'g')) AS badge
    WHERE identifier IS NOT NULL
      AND pg_catalog.length(pg_catalog.btrim(identifier)) BETWEEN 1 AND 320
  )
  SELECT u.email, u.auth_user_id, u.is_active
  FROM normalized n
  JOIN public.users u ON u.is_active = true
  WHERE u.email IS NOT NULL
    AND u.auth_user_id IS NOT NULL
    AND (
      pg_catalog.lower(u.username) = n.ident
      OR pg_catalog.lower(u.email) = n.ident
      OR pg_catalog.upper(pg_catalog.regexp_replace(coalesce(u.badge_id, ''), '[^a-zA-Z0-9]', '', 'g')) = n.badge
    )
  ORDER BY u.user_id
  LIMIT 1
$$;

CREATE OR REPLACE FUNCTION public.next_store_receipt_counter(p_location_id integer)
RETURNS TABLE(sequence integer)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO ''
AS $$
DECLARE
  v_sequence integer;
BEGIN
  IF auth.uid() IS NULL THEN
    RAISE EXCEPTION 'Not authenticated';
  END IF;

  IF p_location_id IS NULL OR p_location_id <= 0 OR NOT public.current_app_user_has_location(p_location_id) THEN
    RAISE EXCEPTION 'You do not have access to this store.';
  END IF;

  UPDATE public.company_customization cc
  SET next_receipt_counter = GREATEST(COALESCE(cc.next_receipt_counter, 1), 1) + 1,
      updated_at = pg_catalog.now()
  WHERE cc.location_id = p_location_id
  RETURNING GREATEST(COALESCE(cc.next_receipt_counter, 1), 1) - 1 INTO v_sequence;

  IF NOT FOUND THEN
    RAISE EXCEPTION 'Missing company_customization row for location_id=%', p_location_id;
  END IF;

  RETURN QUERY SELECT v_sequence;
END;
$$;

CREATE OR REPLACE FUNCTION public.register_mobile_device(
  p_app_version text DEFAULT NULL::text,
  p_device_fingerprint text DEFAULT NULL::text,
  p_device_name text DEFAULT NULL::text,
  p_hostname text DEFAULT NULL::text,
  p_installation_id text DEFAULT NULL::text,
  p_java_version text DEFAULT NULL::text,
  p_local_username text DEFAULT NULL::text,
  p_mac_addresses text DEFAULT NULL::text,
  p_os_arch text DEFAULT NULL::text,
  p_os_name text DEFAULT NULL::text,
  p_os_version text DEFAULT NULL::text,
  p_store_id integer DEFAULT NULL::integer,
  p_user_id integer DEFAULT NULL::integer
)
RETURNS SETOF public.devices
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO ''
AS $$
DECLARE
  v_current_user_id integer;
  v_existing public.devices%ROWTYPE;
  v_receipt_device_code text;
  v_device_name text;
  v_installation_id text;
BEGIN
  v_current_user_id := public.current_app_user_id();
  v_installation_id := NULLIF(pg_catalog.btrim(p_installation_id), '');
  v_device_name := COALESCE(
      NULLIF(pg_catalog.btrim(p_device_name), ''),
      NULLIF(pg_catalog.btrim(p_local_username), ''),
      NULLIF(pg_catalog.btrim(p_hostname), ''),
      'Unknown Device'
  );

  IF auth.uid() IS NULL THEN
    RAISE EXCEPTION 'Not authenticated';
  END IF;

  IF v_current_user_id IS NULL OR p_user_id IS DISTINCT FROM v_current_user_id THEN
    RAISE EXCEPTION 'Invalid device registration user';
  END IF;

  IF p_store_id IS NOT NULL AND NOT public.current_app_user_has_location(p_store_id) THEN
    RAISE EXCEPTION 'You do not have access to this store.';
  END IF;

  IF v_installation_id IS NULL THEN
    RAISE EXCEPTION 'Missing installation id';
  END IF;

  IF pg_catalog.length(v_installation_id) > 200 THEN
    RAISE EXCEPTION 'Installation id is too long';
  END IF;

  SELECT *
  INTO v_existing
  FROM public.devices d
  WHERE d.installation_id = v_installation_id
  LIMIT 1;

  IF FOUND THEN
    IF COALESCE(v_existing.is_blocked, false) THEN
      RAISE EXCEPTION 'This device has been blocked.';
    END IF;

    RETURN QUERY
    UPDATE public.devices d
    SET
      device_fingerprint = p_device_fingerprint,
      device_name = COALESCE(NULLIF(pg_catalog.btrim(d.device_name), ''), v_device_name),
      hostname = p_hostname,
      os_name = p_os_name,
      os_version = p_os_version,
      os_arch = p_os_arch,
      java_version = p_java_version,
      app_version = p_app_version,
      local_username = p_local_username,
      mac_addresses = p_mac_addresses,
      last_login_user_id = p_user_id,
      last_store_id = p_store_id,
      last_seen = pg_catalog.now()
    WHERE d.installation_id = v_installation_id
    RETURNING d.*;
  ELSE
    LOOP
      SELECT pg_catalog.lpad(candidate::text, 4, '0')
      INTO v_receipt_device_code
      FROM pg_catalog.generate_series(1, 9999) AS candidate
      WHERE NOT EXISTS (
        SELECT 1
        FROM public.devices d
        WHERE d.receipt_device_code = pg_catalog.lpad(candidate::text, 4, '0')
      )
      ORDER BY candidate
      LIMIT 1;

      IF v_receipt_device_code IS NULL THEN
        RAISE EXCEPTION 'No available receipt device codes';
      END IF;

      BEGIN
        RETURN QUERY
        INSERT INTO public.devices (
          installation_id, device_fingerprint, device_name, hostname,
          os_name, os_version, os_arch, java_version, app_version,
          local_username, mac_addresses, last_login_user_id, last_store_id,
          first_seen, last_seen, is_approved, is_blocked, receipt_device_code
        )
        VALUES (
          v_installation_id, p_device_fingerprint, v_device_name, p_hostname,
          p_os_name, p_os_version, p_os_arch, p_java_version, p_app_version,
          p_local_username, p_mac_addresses, p_user_id, p_store_id,
          pg_catalog.now(), pg_catalog.now(), false, false, v_receipt_device_code
        )
        RETURNING *;
        RETURN;
      EXCEPTION
        WHEN unique_violation THEN
          SELECT *
          INTO v_existing
          FROM public.devices d
          WHERE d.installation_id = v_installation_id
          LIMIT 1;

          IF FOUND THEN
            IF COALESCE(v_existing.is_blocked, false) THEN
              RAISE EXCEPTION 'This device has been blocked.';
            END IF;

            RETURN QUERY
            UPDATE public.devices d
            SET
              device_fingerprint = p_device_fingerprint,
              device_name = COALESCE(NULLIF(pg_catalog.btrim(d.device_name), ''), v_device_name),
              hostname = p_hostname,
              os_name = p_os_name,
              os_version = p_os_version,
              os_arch = p_os_arch,
              java_version = p_java_version,
              app_version = p_app_version,
              local_username = p_local_username,
              mac_addresses = p_mac_addresses,
              last_login_user_id = p_user_id,
              last_store_id = p_store_id,
              last_seen = pg_catalog.now()
            WHERE d.installation_id = v_installation_id
            RETURNING d.*;
            RETURN;
          END IF;
      END;
    END LOOP;
  END IF;
END;
$$;

REVOKE ALL ON FUNCTION public.lookup_login_user(text) FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.lookup_login_user(text) TO anon, service_role;

REVOKE ALL ON FUNCTION public.next_store_receipt_counter(integer) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.next_store_receipt_counter(integer) TO authenticated, service_role;

REVOKE ALL ON FUNCTION public.register_mobile_device(text, text, text, text, text, text, text, text, text, text, text, integer, integer) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.register_mobile_device(text, text, text, text, text, text, text, text, text, text, text, integer, integer) TO authenticated, service_role;

REVOKE ALL ON FUNCTION public.current_app_user_id() FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.current_app_user_id() TO authenticated, service_role;

REVOKE ALL ON FUNCTION public.current_app_user_is_admin() FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.current_app_user_is_admin() TO authenticated, service_role;

REVOKE ALL ON FUNCTION public.current_app_user_has_location(integer) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.current_app_user_has_location(integer) TO authenticated, service_role;
