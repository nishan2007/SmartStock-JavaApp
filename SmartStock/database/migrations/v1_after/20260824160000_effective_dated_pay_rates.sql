ALTER TABLE public.employee_payroll_settings
    ADD COLUMN IF NOT EXISTS compensation_type public.compensation_type_enum,
    ADD COLUMN IF NOT EXISTS pay_rate numeric(12,2);

UPDATE public.employee_payroll_settings eps
SET compensation_type = u.compensation_type,
    pay_rate = u.salary
FROM public.users u
WHERE u.user_id = eps.user_id
  AND (eps.compensation_type IS NULL OR eps.pay_rate IS NULL);

ALTER TABLE public.employee_payroll_settings
    ALTER COLUMN compensation_type SET NOT NULL,
    ALTER COLUMN pay_rate SET NOT NULL;

ALTER TABLE public.employee_payroll_settings
    DROP CONSTRAINT IF EXISTS employee_payroll_settings_pay_rate_chk;

ALTER TABLE public.employee_payroll_settings
    ADD CONSTRAINT employee_payroll_settings_pay_rate_chk CHECK (pay_rate >= 0);
