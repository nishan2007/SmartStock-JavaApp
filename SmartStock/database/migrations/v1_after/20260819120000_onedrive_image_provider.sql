ALTER TABLE public.image_assets
    ADD COLUMN IF NOT EXISTS cloud_provider text NOT NULL DEFAULT 'SUPABASE',
    ADD COLUMN IF NOT EXISTS remote_drive_id text,
    ADD COLUMN IF NOT EXISTS remote_item_id text,
    ADD COLUMN IF NOT EXISTS remote_path text,
    ADD COLUMN IF NOT EXISTS cloud_etag text,
    ADD COLUMN IF NOT EXISTS migration_status text NOT NULL DEFAULT 'NOT_REQUIRED',
    ADD COLUMN IF NOT EXISTS cloud_verified_at timestamp with time zone;

DO $$ BEGIN
    ALTER TABLE public.image_assets ADD CONSTRAINT image_assets_cloud_provider_check
        CHECK (cloud_provider IN ('SUPABASE','ONEDRIVE'));
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

DO $$ BEGIN
    ALTER TABLE public.image_assets ADD CONSTRAINT image_assets_migration_status_check
        CHECK (migration_status IN ('NOT_REQUIRED','PENDING','VERIFIED','FAILED','RESOLVED'));
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

UPDATE public.image_assets
SET migration_status='PENDING'
WHERE category IN ('PRODUCT','CUSTOM_ITEM','CUSTOM_VARIANT')
  AND lifecycle_status<>'DELETED'
  AND migration_status='NOT_REQUIRED';

CREATE INDEX IF NOT EXISTS image_assets_provider_migration_idx
    ON public.image_assets(cloud_provider,migration_status,updated_at);
