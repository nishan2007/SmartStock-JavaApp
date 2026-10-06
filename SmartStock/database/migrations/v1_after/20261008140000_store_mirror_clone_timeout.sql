-- Snapshot cloning is a privileged recovery operation and can exceed the
-- API's eight-second default on a populated store. Keep the increase scoped
-- to this service-only function; ordinary queries retain their existing limit.
ALTER FUNCTION public.smartstock_begin_store_mirror(integer, uuid, boolean)
    SET statement_timeout = '30s';
NOTIFY pgrst, 'reload schema';
