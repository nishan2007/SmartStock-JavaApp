ALTER TABLE public.users
    ADD COLUMN IF NOT EXISTS hire_date DATE;

UPDATE public.users
SET hire_date = COALESCE(created_at::date, CURRENT_DATE)
WHERE hire_date IS NULL;

ALTER TABLE public.users
    ALTER COLUMN hire_date SET DEFAULT CURRENT_DATE;

ALTER TABLE public.users
    ALTER COLUMN hire_date SET NOT NULL;
