ALTER TABLE IF EXISTS public.company_customization
    ADD COLUMN IF NOT EXISTS scheduler_link_email_enabled boolean NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS scheduler_link_notification_email text NOT NULL DEFAULT '';

ALTER TABLE IF EXISTS public.scheduler_web_runtime
    ADD COLUMN IF NOT EXISTS last_notified_origin text,
    ADD COLUMN IF NOT EXISTS last_notified_recipient text;
