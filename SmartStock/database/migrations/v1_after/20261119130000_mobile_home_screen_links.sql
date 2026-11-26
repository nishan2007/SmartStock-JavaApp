-- Connect isolated iPhone home-screen storage to an already approved device.
-- The request token is shown to the paired browser; the independent claim secret
-- remains in an HttpOnly cookie in the requesting app. Neither is stored plaintext.
CREATE TABLE IF NOT EXISTS public.mobile_device_links (
 request_hash text PRIMARY KEY,
 claim_hash text NOT NULL,
 app_key text NOT NULL,
 destination text NOT NULL,
 device_id uuid REFERENCES public.mobile_managed_devices(device_id) ON DELETE CASCADE,
 expires_at timestamptz NOT NULL,
 approved_at timestamptz,
 consumed_at timestamptz
);
CREATE INDEX IF NOT EXISTS mobile_device_links_expiry_idx ON public.mobile_device_links(expires_at);
ALTER TABLE public.mobile_device_links ENABLE ROW LEVEL SECURITY;
