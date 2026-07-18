-- Final hosted Supabase boundary for the LAN API cutover.
-- Registers authenticate with Supabase, but all SmartStock database mutations now
-- cross the store service. Anonymous access is limited to the login lookup RPC.

REVOKE ALL PRIVILEGES ON ALL TABLES IN SCHEMA public FROM anon;
REVOKE ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public FROM anon;
REVOKE TRUNCATE, REFERENCES, TRIGGER ON ALL TABLES IN SCHEMA public FROM authenticated;

ALTER DEFAULT PRIVILEGES FOR ROLE postgres IN SCHEMA public
    REVOKE ALL ON TABLES FROM anon;
ALTER DEFAULT PRIVILEGES FOR ROLE postgres IN SCHEMA public
    REVOKE ALL ON SEQUENCES FROM anon;
ALTER DEFAULT PRIVILEGES FOR ROLE postgres IN SCHEMA public
    REVOKE EXECUTE ON FUNCTIONS FROM PUBLIC, anon;

-- SECURITY DEFINER functions must never be anonymously callable merely because
-- PostgreSQL grants EXECUTE to PUBLIC by default. Preserve authenticated behavior
-- used by store-aware RLS helpers, then explicitly restore the sole pre-login RPC.
DO $$
DECLARE
    fn record;
BEGIN
    FOR fn IN
        SELECT p.oid::regprocedure AS signature
        FROM pg_proc p
        JOIN pg_namespace n ON n.oid = p.pronamespace
        WHERE n.nspname = 'public'
          AND p.prosecdef
    LOOP
        EXECUTE format('GRANT EXECUTE ON FUNCTION %s TO authenticated', fn.signature);
        EXECUTE format('REVOKE EXECUTE ON FUNCTION %s FROM PUBLIC, anon', fn.signature);
    END LOOP;
END
$$;

DO $$
BEGIN
    IF to_regprocedure('public.lookup_login_user(text)') IS NOT NULL THEN
        EXECUTE 'GRANT EXECUTE ON FUNCTION public.lookup_login_user(text) TO anon, authenticated, service_role';
    END IF;
END
$$;

-- A fixed, trusted search path prevents caller-controlled object shadowing while
-- retaining compatibility with legacy trigger functions that use public names.
DO $$
DECLARE
    fn record;
BEGIN
    FOR fn IN
        SELECT p.oid::regprocedure AS signature
        FROM pg_proc p
        JOIN pg_namespace n ON n.oid = p.pronamespace
        WHERE n.nspname = 'public'
          AND NOT EXISTS (
              SELECT 1
              FROM unnest(coalesce(p.proconfig, ARRAY[]::text[])) setting
              WHERE setting LIKE 'search_path=%'
          )
    LOOP
        BEGIN
            EXECUTE format('ALTER FUNCTION %s SET search_path TO pg_catalog, public', fn.signature);
        EXCEPTION
            WHEN insufficient_privilege THEN
                RAISE NOTICE 'Skipping function not owned by migration role: %', fn.signature;
        END;
    END LOOP;
END
$$;

-- Views inherit the caller's RLS context instead of running with their owner's
-- table privileges.
DO $$
DECLARE
    view_row record;
BEGIN
    FOR view_row IN
        SELECT format('%I.%I', n.nspname, c.relname) AS qualified_name
        FROM pg_class c
        JOIN pg_namespace n ON n.oid = c.relnamespace
        WHERE n.nspname = 'public'
          AND c.relkind = 'v'
    LOOP
        EXECUTE format('ALTER VIEW %s SET (security_invoker = true)', view_row.qualified_name);
    END LOOP;
END
$$;

-- These records are server-service or audit boundaries. Authenticated mobile or
-- desktop sessions cannot query or mutate them directly even if a policy drifts.
DO $$
DECLARE
    table_name text;
BEGIN
    FOREACH table_name IN ARRAY ARRAY[
        'devices', 'device_sessions', 'sync_locks', 'sync_service_status',
        'wifi_sessions', 'payroll_payments', 'employee_payroll_settings',
        'roles', 'permissions', 'role_permissions', 'mobile_permissions',
        'role_mobile_permissions', 'smartstock_email_outbox',
        'sale_audit_log', 'quotation_audit_log', 'invoice_audit_log',
        'custom_order_audit_log', 'security_audit_events',
        'lan_api_device_credentials', 'lan_api_sessions',
        'lan_api_idempotency', 'lan_api_manager_approvals',
        'lan_api_failed_logins', 'lan_api_service_health'
    ]
    LOOP
        IF to_regclass(format('public.%I', table_name)) IS NOT NULL THEN
            EXECUTE format('REVOKE ALL PRIVILEGES ON TABLE public.%I FROM anon, authenticated', table_name);
        END IF;
    END LOOP;
END
$$;
