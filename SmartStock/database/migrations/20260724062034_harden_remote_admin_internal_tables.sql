-- Remote Admin state is internal to the gateway and sync workers. Keep it
-- unavailable to public Data API roles while allowing the service role.

ALTER TABLE IF EXISTS public.store_sync_status ENABLE ROW LEVEL SECURITY;
ALTER TABLE IF EXISTS public.remote_admin_commands ENABLE ROW LEVEL SECURITY;

REVOKE ALL ON TABLE public.store_sync_status FROM PUBLIC, anon, authenticated;
REVOKE ALL ON TABLE public.remote_admin_commands FROM PUBLIC, anon, authenticated;

DROP POLICY IF EXISTS store_sync_status_authenticated_all
    ON public.store_sync_status;
DROP POLICY IF EXISTS store_sync_status_anon_all
    ON public.store_sync_status;
DROP POLICY IF EXISTS remote_admin_commands_authenticated_all
    ON public.remote_admin_commands;
DROP POLICY IF EXISTS remote_admin_commands_anon_all
    ON public.remote_admin_commands;

DROP POLICY IF EXISTS store_sync_status_service_role_all
    ON public.store_sync_status;
CREATE POLICY store_sync_status_service_role_all
    ON public.store_sync_status
    FOR ALL
    TO service_role
    USING (true)
    WITH CHECK (true);

DROP POLICY IF EXISTS remote_admin_commands_service_role_all
    ON public.remote_admin_commands;
CREATE POLICY remote_admin_commands_service_role_all
    ON public.remote_admin_commands
    FOR ALL
    TO service_role
    USING (true)
    WITH CHECK (true);

GRANT ALL ON TABLE public.store_sync_status TO service_role;
GRANT ALL ON TABLE public.remote_admin_commands TO service_role;
