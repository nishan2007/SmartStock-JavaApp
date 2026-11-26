ALTER TABLE public.mobile_managed_devices ADD COLUMN IF NOT EXISTS remember_login boolean NOT NULL DEFAULT false;
