-- Studio permission belongs to the physical device. Companion keys stay LAN-local.
ALTER TABLE public.devices ADD COLUMN IF NOT EXISTS allow_studio boolean NOT NULL DEFAULT false;
CREATE INDEX IF NOT EXISTS devices_studio_identity_idx ON public.devices(device_fingerprint,hostname,last_store_id);
DO $$ BEGIN
    IF to_regclass('public.lan_api_sessions') IS NOT NULL THEN
        ALTER TABLE public.lan_api_sessions ADD COLUMN IF NOT EXISTS client_application text NOT NULL DEFAULT 'smartstock';
        CREATE INDEX IF NOT EXISTS lan_api_sessions_application_idx ON public.lan_api_sessions(device_id,user_id,client_application) WHERE revoked_at IS NULL;
        CREATE TABLE IF NOT EXISTS public.studio_device_clients (
            installation_id text PRIMARY KEY,
            device_id uuid NOT NULL REFERENCES public.devices(device_id),
            legacy_device_id uuid REFERENCES public.devices(device_id),
            location_id integer NOT NULL REFERENCES public.locations(location_id),
            public_key text NOT NULL,
            challenge_hash text,
            challenge_expires_at timestamptz,
            credential_hash text UNIQUE,
            credential_expires_at timestamptz,
            created_at timestamptz NOT NULL DEFAULT now()
        );
        CREATE INDEX IF NOT EXISTS studio_device_clients_device_idx ON public.studio_device_clients(device_id);
        CREATE INDEX IF NOT EXISTS studio_device_clients_legacy_idx ON public.studio_device_clients(legacy_device_id);
        ALTER TABLE public.studio_device_clients ENABLE ROW LEVEL SECURITY;
        IF EXISTS(SELECT 1 FROM pg_roles WHERE rolname='anon') THEN REVOKE ALL ON public.studio_device_clients FROM anon; END IF;
        IF EXISTS(SELECT 1 FROM pg_roles WHERE rolname='authenticated') THEN REVOKE ALL ON public.studio_device_clients FROM authenticated; END IF;
    END IF;
END $$;
