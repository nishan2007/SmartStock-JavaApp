-- The store service now resolves usernames and badges before authenticating with
-- Supabase. Direct anonymous identity enumeration is no longer required.

DO $$
BEGIN
    IF to_regprocedure('public.lookup_login_user(text)') IS NOT NULL THEN
        EXECUTE 'REVOKE EXECUTE ON FUNCTION public.lookup_login_user(text) FROM PUBLIC, anon, authenticated';
        EXECUTE 'GRANT EXECUTE ON FUNCTION public.lookup_login_user(text) TO service_role';
    END IF;
END
$$;

