ALTER TABLE public.employee_payroll_settings
    DROP CONSTRAINT IF EXISTS employee_payroll_settings_period_type_chk;

ALTER TABLE public.employee_payroll_settings
    ADD CONSTRAINT employee_payroll_settings_period_type_chk
    CHECK (period_type IN ('SEMI_MONTHLY', 'WEEKLY', 'FOUR_MONTH_BLOCKS'));
