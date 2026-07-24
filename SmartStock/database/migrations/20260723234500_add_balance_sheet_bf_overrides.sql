CREATE TABLE IF NOT EXISTS public.balance_sheet_bf_overrides (
    balance_sheet_bf_override_id BIGSERIAL PRIMARY KEY,
    location_id INTEGER NOT NULL REFERENCES public.locations(location_id),
    period_start DATE NOT NULL,
    amount NUMERIC(12, 2) NOT NULL,
    updated_by_user_id INTEGER REFERENCES public.users(user_id),
    updated_by_name TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT balance_sheet_bf_overrides_location_period_unique
        UNIQUE (location_id, period_start)
);

CREATE INDEX IF NOT EXISTS balance_sheet_bf_overrides_location_period_idx
ON public.balance_sheet_bf_overrides(location_id, period_start DESC);

ALTER TABLE public.balance_sheet_bf_overrides ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON TABLE public.balance_sheet_bf_overrides FROM PUBLIC;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'anon') THEN
        REVOKE ALL ON TABLE public.balance_sheet_bf_overrides FROM anon;
    END IF;
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'authenticated') THEN
        REVOKE ALL ON TABLE public.balance_sheet_bf_overrides FROM authenticated;
    END IF;
END
$$;
