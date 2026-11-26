ALTER TABLE public.employee_web_sessions ADD COLUMN IF NOT EXISTS remember_device boolean NOT NULL DEFAULT false;
