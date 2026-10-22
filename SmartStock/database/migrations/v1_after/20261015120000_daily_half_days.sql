-- One explicit daily-pay choice per employee and calendar work date.
CREATE TABLE IF NOT EXISTS public.employee_daily_pay_days (
    user_id integer NOT NULL REFERENCES public.users(user_id),
    work_date date NOT NULL,
    half_day boolean NOT NULL DEFAULT false,
    location_id integer NOT NULL REFERENCES public.locations(location_id),
    updated_by integer NOT NULL REFERENCES public.users(user_id),
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, work_date)
);
CREATE INDEX IF NOT EXISTS employee_daily_pay_days_location_updated_idx
    ON public.employee_daily_pay_days(location_id, updated_at);
