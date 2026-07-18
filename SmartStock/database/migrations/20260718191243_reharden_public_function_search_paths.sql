-- Re-apply the trusted search path after later setup migrations recreate
-- trigger functions. Extension-owned routines are deliberately excluded.
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
              FROM pg_depend d
              WHERE d.classid = 'pg_proc'::regclass
                AND d.objid = p.oid
                AND d.deptype = 'e'
          )
    LOOP
        BEGIN
            EXECUTE format(
                'ALTER FUNCTION %s SET search_path TO pg_catalog, public',
                fn.signature
            );
        EXCEPTION
            WHEN insufficient_privilege THEN
                RAISE NOTICE 'Skipping function not owned by migration role: %',
                    fn.signature;
        END;
    END LOOP;
END
$$;
