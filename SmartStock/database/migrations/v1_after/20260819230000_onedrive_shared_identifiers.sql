CREATE TABLE IF NOT EXISTS public.image_cloud_configuration (
    provider text PRIMARY KEY,
    tenant_id text NOT NULL,
    client_id text NOT NULL,
    drive_id text NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_by_user_id integer,
    updated_by_name text,
    CONSTRAINT image_cloud_configuration_provider_check CHECK (provider IN ('ONEDRIVE'))
);

ALTER TABLE public.image_cloud_configuration ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON TABLE public.image_cloud_configuration FROM PUBLIC;
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'anon') THEN
        REVOKE ALL ON TABLE public.image_cloud_configuration FROM anon;
    END IF;
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'authenticated') THEN
        REVOKE ALL ON TABLE public.image_cloud_configuration FROM authenticated;
    END IF;
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'service_role') THEN
        GRANT ALL ON TABLE public.image_cloud_configuration TO service_role;
    END IF;
END
$$;
