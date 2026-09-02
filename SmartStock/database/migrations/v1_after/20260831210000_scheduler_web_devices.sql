CREATE TABLE IF NOT EXISTS public.scheduler_web_devices (
 device_id uuid DEFAULT gen_random_uuid() PRIMARY KEY,
 browser_hash text NOT NULL UNIQUE,
 user_id integer NOT NULL REFERENCES public.users(user_id) ON DELETE CASCADE,
 auth_user_id uuid NOT NULL,
 device_name text NOT NULL,
 user_agent text,
 stay_signed_in boolean NOT NULL DEFAULT false,
 stay_signed_in_expires_at timestamp with time zone,
 created_at timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
 last_seen_at timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
 revoked_at timestamp with time zone
);

ALTER TABLE public.scheduler_web_sessions
 ADD COLUMN IF NOT EXISTS browser_device_id uuid REFERENCES public.scheduler_web_devices(device_id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS scheduler_web_devices_user_idx
 ON public.scheduler_web_devices(user_id,last_seen_at DESC) WHERE revoked_at IS NULL;
CREATE INDEX IF NOT EXISTS scheduler_web_sessions_browser_idx
 ON public.scheduler_web_sessions(browser_device_id,expires_at) WHERE revoked_at IS NULL;

ALTER TABLE public.scheduler_web_devices ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON TABLE public.scheduler_web_devices FROM PUBLIC;
