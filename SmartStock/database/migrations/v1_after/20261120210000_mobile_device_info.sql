ALTER TABLE public.mobile_managed_devices ADD COLUMN IF NOT EXISTS device_info jsonb NOT NULL DEFAULT '{}'::jsonb;
ALTER TABLE public.mobile_managed_devices ADD COLUMN IF NOT EXISTS device_info_updated_at timestamptz;
