CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE IF NOT EXISTS public.image_assets (
    asset_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    category TEXT NOT NULL,
    bucket_name TEXT NOT NULL,
    object_path TEXT NOT NULL,
    access_level TEXT NOT NULL DEFAULT 'PUBLIC' CHECK (access_level IN ('PUBLIC','AUTHENTICATED')),
    original_filename TEXT NOT NULL DEFAULT '',
    content_type TEXT NOT NULL DEFAULT 'application/octet-stream',
    byte_size BIGINT NOT NULL DEFAULT 0 CHECK (byte_size >= 0),
    sha256 TEXT NOT NULL DEFAULT '',
    lifecycle_status TEXT NOT NULL DEFAULT 'ACTIVE' CHECK (lifecycle_status IN ('ACTIVE','UNUSED','DELETE_PENDING','DELETED')),
    local_status TEXT NOT NULL DEFAULT 'MISSING' CHECK (local_status IN ('PRESENT','MISSING','CORRUPT')),
    cloud_status TEXT NOT NULL DEFAULT 'PENDING' CHECK (cloud_status IN ('PENDING','PRESENT','MISSING','FAILED','DELETED')),
    retained BOOLEAN NOT NULL DEFAULT FALSE,
    unused_since TIMESTAMPTZ,
    last_verified_at TIMESTAMPTZ,
    last_error TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMPTZ,
    deleted_by_user_id INTEGER,
    deleted_by_name TEXT,
    UNIQUE (bucket_name, object_path)
);

CREATE TABLE IF NOT EXISTS public.image_asset_references (
    reference_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    asset_id UUID NOT NULL REFERENCES public.image_assets(asset_id),
    source_table TEXT NOT NULL,
    source_key TEXT NOT NULL,
    source_column TEXT NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (source_table, source_key, source_column)
);

CREATE INDEX IF NOT EXISTS image_assets_status_idx ON public.image_assets(lifecycle_status, updated_at DESC);
CREATE INDEX IF NOT EXISTS image_assets_cloud_idx ON public.image_assets(cloud_status, updated_at);
CREATE INDEX IF NOT EXISTS image_asset_refs_asset_idx ON public.image_asset_references(asset_id, active);

CREATE OR REPLACE FUNCTION public.set_image_assets_updated_at()
RETURNS TRIGGER LANGUAGE plpgsql SET search_path = pg_catalog AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        NEW.updated_at = COALESCE(NEW.updated_at, CURRENT_TIMESTAMP);
    ELSIF NEW.updated_at IS NOT DISTINCT FROM OLD.updated_at THEN
        NEW.updated_at = CURRENT_TIMESTAMP;
    END IF;
    RETURN NEW;
END $$;

CREATE OR REPLACE FUNCTION public.set_image_asset_references_updated_at()
RETURNS TRIGGER LANGUAGE plpgsql SET search_path = pg_catalog AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        NEW.updated_at = COALESCE(NEW.updated_at, CURRENT_TIMESTAMP);
    ELSIF NEW.updated_at IS NOT DISTINCT FROM OLD.updated_at THEN
        NEW.updated_at = CURRENT_TIMESTAMP;
    END IF;
    RETURN NEW;
END $$;

DROP TRIGGER IF EXISTS image_assets_set_updated_at ON public.image_assets;
CREATE TRIGGER image_assets_set_updated_at
BEFORE INSERT OR UPDATE ON public.image_assets
FOR EACH ROW EXECUTE FUNCTION public.set_image_assets_updated_at();

DROP TRIGGER IF EXISTS image_asset_references_set_updated_at ON public.image_asset_references;
CREATE TRIGGER image_asset_references_set_updated_at
BEFORE INSERT OR UPDATE ON public.image_asset_references
FOR EACH ROW EXECUTE FUNCTION public.set_image_asset_references_updated_at();

ALTER TABLE public.image_assets ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.image_asset_references ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON public.image_assets, public.image_asset_references FROM anon, authenticated;
GRANT SELECT, INSERT, UPDATE, DELETE ON public.image_assets, public.image_asset_references TO service_role;
