ALTER TABLE public.company_customization
    ADD COLUMN IF NOT EXISTS require_badge_pin_login BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE public.lan_api_sessions
    DROP CONSTRAINT IF EXISTS lan_api_sessions_auth_source_check;

ALTER TABLE public.lan_api_sessions
    ADD CONSTRAINT lan_api_sessions_auth_source_check
    CHECK (auth_source IN ('SUPABASE', 'SUPABASE_PASSWORD', 'LOCAL_CACHE',
                           'LOCAL_PASSWORD_CACHE', 'BADGE_PIN', 'EMPLOYEE_PIN',
                           'BADGE_ONLY', 'BADGE_PIN_SETUP'));
