-- SmartStock cloud synchronization, recovery, bootstrap, registry, and refund
-- queue RPCs are server-only. Supabase grants EXECUTE on new public functions
-- to authenticated by default, so make this boundary explicit.
DO $migration$
DECLARE
    service_function record;
BEGIN
    FOR service_function IN
        SELECT p.proname AS function_name,
               pg_catalog.pg_get_function_identity_arguments(p.oid) AS arguments
        FROM pg_catalog.pg_proc p
        JOIN pg_catalog.pg_namespace n ON n.oid = p.pronamespace
        WHERE n.nspname = 'public'
          AND p.prokind = 'f'
          AND p.proname LIKE 'smartstock\_%' ESCAPE '\'
    LOOP
        EXECUTE pg_catalog.format(
            'REVOKE EXECUTE ON FUNCTION public.%I(%s) FROM PUBLIC, anon, authenticated',
            service_function.function_name,
            service_function.arguments
        );
        EXECUTE pg_catalog.format(
            'GRANT EXECUTE ON FUNCTION public.%I(%s) TO service_role',
            service_function.function_name,
            service_function.arguments
        );
    END LOOP;
END
$migration$;
