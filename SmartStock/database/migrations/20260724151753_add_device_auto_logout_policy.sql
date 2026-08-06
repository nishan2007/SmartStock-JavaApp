ALTER TABLE public.devices
    ADD COLUMN IF NOT EXISTS auto_logout_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS auto_logout_minutes INTEGER NOT NULL DEFAULT 15;

UPDATE public.devices
SET auto_logout_enabled = COALESCE(auto_logout_enabled, FALSE),
    auto_logout_minutes = COALESCE(auto_logout_minutes, 15);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'devices_auto_logout_minutes_check'
          AND conrelid = 'public.devices'::regclass
    ) THEN
        ALTER TABLE public.devices
            ADD CONSTRAINT devices_auto_logout_minutes_check
            CHECK (auto_logout_minutes BETWEEN 1 AND 480);
    END IF;
END $$;
