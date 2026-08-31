INSERT INTO public.employee_payroll_settings (
    setting_id, user_id, period_type, work_hour_limit, effective_from,
    compensation_type, pay_rate, created_by_name
)
SELECT gen_random_uuid(), u.user_id, 'SEMI_MONTHLY', 80.00, DATE '1900-01-01',
       u.compensation_type, u.salary, 'Effective pay-rate baseline backfill'
FROM public.users u
WHERE NOT EXISTS (
    SELECT 1
    FROM public.employee_payroll_settings eps
    WHERE eps.user_id = u.user_id
      AND eps.effective_from = DATE '1900-01-01'
)
ON CONFLICT (user_id, effective_from) DO NOTHING;
