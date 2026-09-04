ALTER TABLE public.lan_api_sessions
    DROP CONSTRAINT IF EXISTS lan_api_sessions_auth_source_check;

ALTER TABLE public.lan_api_sessions
    ADD CONSTRAINT lan_api_sessions_auth_source_check CHECK (auth_source = ANY (ARRAY[
        'SUPABASE'::text,
        'SUPABASE_PASSWORD'::text,
        'LOCAL_CACHE'::text,
        'LOCAL_PASSWORD_CACHE'::text,
        'BADGE_PIN'::text,
        'EMPLOYEE_PIN'::text,
        'BADGE_ONLY'::text,
        'BADGE_PIN_SETUP'::text,
        'WALLET_BARCODE'::text,
        'WALLET_BARCODE_PIN'::text,
        'WALLET_NFC'::text,
        'WALLET_NFC_PIN'::text
    ]));
