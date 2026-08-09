-- Supabase's security advisor requires an explicit anon revocation for every
-- SECURITY DEFINER function, even when EXECUTE was already revoked from PUBLIC.
-- Authenticated helper grants and service-role RPC grants remain unchanged.
DO $migration$
DECLARE
    secured_function record;
BEGIN
    IF pg_catalog.to_regrole('anon') IS NULL THEN
        RETURN;
    END IF;

    FOR secured_function IN
        SELECT n.nspname AS schema_name,
               p.proname AS function_name,
               pg_catalog.pg_get_function_identity_arguments(p.oid) AS arguments
        FROM pg_catalog.pg_proc p
        JOIN pg_catalog.pg_namespace n ON n.oid = p.pronamespace
        WHERE n.nspname IN ('public', 'smartstock_private')
          AND p.prosecdef
    LOOP
        EXECUTE pg_catalog.format(
            'REVOKE EXECUTE ON FUNCTION %I.%I(%s) FROM PUBLIC, anon',
            secured_function.schema_name,
            secured_function.function_name,
            secured_function.arguments
        );
    END LOOP;
END
$migration$;
