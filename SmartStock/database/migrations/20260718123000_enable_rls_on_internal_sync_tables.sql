-- Internal sync coordination is service-role only. RLS provides a second denial
-- boundary even though anon/authenticated table grants are already revoked.

ALTER TABLE IF EXISTS public.sync_locks ENABLE ROW LEVEL SECURITY;
ALTER TABLE IF EXISTS public.sync_service_status ENABLE ROW LEVEL SECURITY;
ALTER TABLE IF EXISTS public.wifi_sessions ENABLE ROW LEVEL SECURITY;

