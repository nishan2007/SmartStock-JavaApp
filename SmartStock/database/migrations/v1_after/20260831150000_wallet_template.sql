-- Uses the existing company customization row scope, permissions and sync pipeline.
ALTER TABLE public.company_customization
    ADD COLUMN IF NOT EXISTS wallet_template_json text NOT NULL DEFAULT '';
ALTER TABLE public.employee_wallet_enrollments
    ADD COLUMN IF NOT EXISTS location_id integer REFERENCES public.locations(location_id);
CREATE INDEX IF NOT EXISTS employee_wallet_enrollment_location_idx
    ON public.employee_wallet_enrollments(location_id);
