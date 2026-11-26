-- Local-only browser approvals. Never mirror credentials or approval codes to cloud/registers.
CREATE TABLE IF NOT EXISTS public.mobile_managed_devices (
 device_id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
 device_name text NOT NULL CHECK (length(device_name) BETWEEN 1 AND 120),
 created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
 last_seen_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
 revoked_at timestamptz,
 revision bigint NOT NULL DEFAULT 1
);
CREATE TABLE IF NOT EXISTS public.mobile_device_apps (
 device_id uuid NOT NULL REFERENCES public.mobile_managed_devices(device_id) ON DELETE CASCADE,
 app_key text NOT NULL,
 PRIMARY KEY(device_id,app_key)
);
CREATE TABLE IF NOT EXISTS public.mobile_device_credentials (
 credential_hash text PRIMARY KEY,
 device_id uuid NOT NULL REFERENCES public.mobile_managed_devices(device_id) ON DELETE CASCADE,
 created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS mobile_device_credentials_device_idx ON public.mobile_device_credentials(device_id);
CREATE TABLE IF NOT EXISTS public.mobile_device_pairings (
 code_hash text PRIMARY KEY,
 device_name text NOT NULL,
 app_keys text[] NOT NULL,
 issued_by integer NOT NULL REFERENCES public.users(user_id),
 expires_at timestamptz NOT NULL,
 consumed_at timestamptz
);
CREATE INDEX IF NOT EXISTS mobile_device_pairings_expiry_idx ON public.mobile_device_pairings(expires_at);
CREATE TABLE IF NOT EXISTS public.mobile_device_handoffs (
 token_hash text PRIMARY KEY,
 device_id uuid NOT NULL REFERENCES public.mobile_managed_devices(device_id) ON DELETE CASCADE,
 app_key text NOT NULL,
 destination text NOT NULL,
 expires_at timestamptz NOT NULL,
 consumed_at timestamptz
);
CREATE INDEX IF NOT EXISTS mobile_device_handoffs_expiry_idx ON public.mobile_device_handoffs(expires_at);
-- Old browser/session approvals are deliberately not promoted. They must pair once again.
-- Their records remain available for history; the shared request guard rejects them.

ALTER TABLE public.mobile_managed_devices ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.mobile_device_apps ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.mobile_device_credentials ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.mobile_device_pairings ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.mobile_device_handoffs ENABLE ROW LEVEL SECURITY;
