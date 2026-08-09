-- Wi-Fi sessions are no longer used by SmartStock. Remove the obsolete local
-- table without CASCADE so an unexpected dependency fails closed instead of
-- deleting another object. The bounded lock prevents provisioning from hanging.

DO $$
BEGIN
    PERFORM pg_catalog.set_config('lock_timeout', '5s', true);
    IF pg_catalog.to_regclass('public.wifi_sessions') IS NOT NULL THEN
        EXECUTE 'DROP TABLE public.wifi_sessions';
    END IF;
END
$$;
