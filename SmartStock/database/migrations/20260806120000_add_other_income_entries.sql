CREATE TABLE IF NOT EXISTS public.other_income_entries (
    other_income_id BIGSERIAL PRIMARY KEY,
    location_id INTEGER REFERENCES public.locations(location_id),
    income_date DATE NOT NULL DEFAULT CURRENT_DATE,
    source_name TEXT NOT NULL,
    description TEXT,
    amount NUMERIC(12, 2) NOT NULL,
    payment_method TEXT NOT NULL DEFAULT 'CASH',
    payment_reference TEXT,
    created_by_user_id INTEGER REFERENCES public.users(user_id),
    created_by_name TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT other_income_amount_chk CHECK (amount > 0),
    CONSTRAINT other_income_whole_gyd_chk CHECK (amount = TRUNC(amount)),
    CONSTRAINT other_income_payment_method_chk CHECK (payment_method = 'CASH')
);

CREATE INDEX IF NOT EXISTS other_income_location_date_idx
ON public.other_income_entries(location_id, income_date DESC);

CREATE INDEX IF NOT EXISTS other_income_created_by_user_idx
ON public.other_income_entries(created_by_user_id);

ALTER TABLE public.other_income_entries ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON TABLE public.other_income_entries FROM PUBLIC;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'anon') THEN
        REVOKE ALL ON TABLE public.other_income_entries FROM anon;
    END IF;
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'authenticated') THEN
        REVOKE ALL ON TABLE public.other_income_entries FROM authenticated;
    END IF;
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'service_role') THEN
        DROP POLICY IF EXISTS other_income_entries_service_role_all ON public.other_income_entries;
        CREATE POLICY other_income_entries_service_role_all
            ON public.other_income_entries
            FOR ALL TO service_role
            USING (true)
            WITH CHECK (true);
        GRANT ALL ON TABLE public.other_income_entries TO service_role;
        GRANT ALL ON SEQUENCE public.other_income_entries_other_income_id_seq TO service_role;
    END IF;
END
$$;
