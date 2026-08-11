ALTER TABLE public.employee_time_clock
    ADD COLUMN IF NOT EXISTS clock_uuid uuid;

UPDATE public.employee_time_clock
SET clock_uuid = gen_random_uuid()
WHERE clock_uuid IS NULL;

ALTER TABLE public.employee_time_clock
    ALTER COLUMN clock_uuid SET DEFAULT gen_random_uuid(),
    ALTER COLUMN clock_uuid SET NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS employee_time_clock_clock_uuid_uidx
    ON public.employee_time_clock(clock_uuid);

ALTER TABLE public.payroll_payments
    ADD COLUMN IF NOT EXISTS sync_uuid uuid;

UPDATE public.payroll_payments
SET sync_uuid = gen_random_uuid()
WHERE sync_uuid IS NULL;

ALTER TABLE public.payroll_payments
    ALTER COLUMN sync_uuid SET DEFAULT gen_random_uuid(),
    ALTER COLUMN sync_uuid SET NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS payroll_payments_sync_uuid_uidx
    ON public.payroll_payments(sync_uuid);
