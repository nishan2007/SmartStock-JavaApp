-- Company-wide schedule holidays. Manual scheduling remains allowed.

CREATE TABLE IF NOT EXISTS public.employee_schedule_holidays (
    holiday_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    holiday_date DATE NOT NULL UNIQUE,
    holiday_name TEXT NOT NULL DEFAULT 'Holiday',
    created_by_user_id INTEGER REFERENCES public.users(user_id),
    created_by_name TEXT,
    updated_by_user_id INTEGER REFERENCES public.users(user_id),
    updated_by_name TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT employee_schedule_holidays_name_chk CHECK (LENGTH(TRIM(holiday_name)) > 0)
);

CREATE INDEX IF NOT EXISTS employee_schedule_holidays_date_idx
ON public.employee_schedule_holidays(holiday_date);

CREATE OR REPLACE FUNCTION public.set_employee_schedule_holidays_updated_at()
RETURNS TRIGGER
LANGUAGE plpgsql
SET search_path = ''
AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        NEW.updated_at = COALESCE(NEW.updated_at, CURRENT_TIMESTAMP);
    ELSIF NEW.updated_at IS NOT DISTINCT FROM OLD.updated_at THEN
        NEW.updated_at = CURRENT_TIMESTAMP;
    END IF;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS employee_schedule_holidays_set_updated_at
ON public.employee_schedule_holidays;
CREATE TRIGGER employee_schedule_holidays_set_updated_at
BEFORE INSERT OR UPDATE ON public.employee_schedule_holidays
FOR EACH ROW EXECUTE FUNCTION public.set_employee_schedule_holidays_updated_at();

ALTER TABLE public.employee_schedule_holidays ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON TABLE public.employee_schedule_holidays FROM PUBLIC;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'anon') THEN
        REVOKE ALL ON TABLE public.employee_schedule_holidays FROM anon;
    END IF;
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'authenticated') THEN
        REVOKE ALL ON TABLE public.employee_schedule_holidays FROM authenticated;
    END IF;
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'service_role') THEN
        GRANT ALL ON TABLE public.employee_schedule_holidays TO service_role;
        DROP POLICY IF EXISTS employee_schedule_holidays_service_role_all
            ON public.employee_schedule_holidays;
        CREATE POLICY employee_schedule_holidays_service_role_all
            ON public.employee_schedule_holidays
            FOR ALL TO service_role USING (true) WITH CHECK (true);
    END IF;
END $$;
