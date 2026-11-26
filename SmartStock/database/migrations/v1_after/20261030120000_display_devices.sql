ALTER TABLE public.devices DROP CONSTRAINT IF EXISTS devices_access_mode_check;
ALTER TABLE public.devices ADD CONSTRAINT devices_access_mode_check CHECK (access_mode IN ('CLIENT','SERVER','REMOTE_ADMIN','DISPLAY'));
