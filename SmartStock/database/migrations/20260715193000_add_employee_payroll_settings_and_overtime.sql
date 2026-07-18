-- Effective-dated hourly payroll periods, hour limits, and paid-payroll overtime snapshots.
CREATE TABLE IF NOT EXISTS public.employee_payroll_settings (
    setting_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id INTEGER NOT NULL REFERENCES public.users(user_id) ON DELETE CASCADE,
    period_type TEXT NOT NULL DEFAULT 'SEMI_MONTHLY',
    work_hour_limit NUMERIC(8,2) NOT NULL DEFAULT 80.00,
    effective_from DATE NOT NULL,
    created_by_user_id INTEGER REFERENCES public.users(user_id),
    created_by_name TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT employee_payroll_settings_period_type_chk
        CHECK (period_type IN ('SEMI_MONTHLY', 'WEEKLY', 'FOUR_MONTH_BLOCKS')),
    CONSTRAINT employee_payroll_settings_hour_limit_chk CHECK (work_hour_limit > 0),
    CONSTRAINT employee_payroll_settings_user_effective_key UNIQUE (user_id, effective_from)
);

CREATE INDEX IF NOT EXISTS employee_payroll_settings_user_effective_idx
ON public.employee_payroll_settings(user_id, effective_from DESC);

CREATE OR REPLACE FUNCTION public.set_employee_payroll_settings_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.updated_at IS NOT DISTINCT FROM OLD.updated_at THEN
        NEW.updated_at = CURRENT_TIMESTAMP;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS employee_payroll_settings_set_updated_at ON public.employee_payroll_settings;
CREATE TRIGGER employee_payroll_settings_set_updated_at
BEFORE UPDATE ON public.employee_payroll_settings
FOR EACH ROW EXECUTE FUNCTION public.set_employee_payroll_settings_updated_at();

INSERT INTO public.employee_payroll_settings (
    setting_id, user_id, period_type, work_hour_limit, effective_from, created_by_name
)
SELECT (
    SUBSTR(md5('smartstock-employee-payroll-default:' || u.user_id), 1, 8) || '-' ||
    SUBSTR(md5('smartstock-employee-payroll-default:' || u.user_id), 9, 4) || '-' ||
    SUBSTR(md5('smartstock-employee-payroll-default:' || u.user_id), 13, 4) || '-' ||
    SUBSTR(md5('smartstock-employee-payroll-default:' || u.user_id), 17, 4) || '-' ||
    SUBSTR(md5('smartstock-employee-payroll-default:' || u.user_id), 21, 12)
)::uuid,
u.user_id, 'SEMI_MONTHLY', 80.00, DATE '1900-01-01', 'System default'
FROM public.users u
WHERE NOT EXISTS (
    SELECT 1 FROM public.employee_payroll_settings existing WHERE existing.user_id = u.user_id
);

ALTER TABLE public.payroll_payments ADD COLUMN IF NOT EXISTS pay_period_type TEXT NOT NULL DEFAULT 'SEMI_MONTHLY';
ALTER TABLE public.payroll_payments ADD COLUMN IF NOT EXISTS work_hour_limit NUMERIC(8,2) NOT NULL DEFAULT 80.00;
ALTER TABLE public.payroll_payments ADD COLUMN IF NOT EXISTS regular_hours NUMERIC(10,2) NOT NULL DEFAULT 0;
ALTER TABLE public.payroll_payments ADD COLUMN IF NOT EXISTS overtime_hours NUMERIC(10,2) NOT NULL DEFAULT 0;
ALTER TABLE public.payroll_payments ADD COLUMN IF NOT EXISTS regular_pay NUMERIC(12,2) NOT NULL DEFAULT 0;
ALTER TABLE public.payroll_payments ADD COLUMN IF NOT EXISTS overtime_pay NUMERIC(12,2) NOT NULL DEFAULT 0;

ALTER TABLE public.employee_payroll_settings ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON TABLE public.employee_payroll_settings FROM PUBLIC;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'anon') THEN
        REVOKE ALL ON TABLE public.employee_payroll_settings FROM anon;
    END IF;
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'authenticated') THEN
        REVOKE ALL ON TABLE public.employee_payroll_settings FROM authenticated;
    END IF;
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'service_role') THEN
        GRANT ALL ON TABLE public.employee_payroll_settings TO service_role;
        DROP POLICY IF EXISTS employee_payroll_settings_service_role_all ON public.employee_payroll_settings;
        CREATE POLICY employee_payroll_settings_service_role_all
            ON public.employee_payroll_settings FOR ALL TO service_role
            USING (true) WITH CHECK (true);
    END IF;
END $$;
