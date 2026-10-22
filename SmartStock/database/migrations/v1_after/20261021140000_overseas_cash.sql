-- POS tables remain local; cloud recovery uses smartstock_store_rows.
DO $smartstock_local$ BEGIN
IF to_regclass('public.cash_drawers') IS NOT NULL THEN
-- Separate cash has no opening float. Existing payments retain their original classification.
ALTER TABLE public.cash_drawers ADD COLUMN IF NOT EXISTS handles_overseas_cash boolean NOT NULL DEFAULT false;
ALTER TABLE public.cash_drawer_sessions ADD COLUMN IF NOT EXISTS handles_overseas_cash boolean NOT NULL DEFAULT false;
ALTER TABLE public.cash_drawer_sessions ADD COLUMN IF NOT EXISTS overseas_expected numeric(14,2);
ALTER TABLE public.cash_drawer_sessions ADD COLUMN IF NOT EXISTS overseas_counted numeric(14,2) CHECK (overseas_counted>=0);
ALTER TABLE public.cash_drawer_sessions ADD COLUMN IF NOT EXISTS overseas_variance_reason text;
ALTER TABLE public.custom_order_payments ADD COLUMN IF NOT EXISTS overseas_cash_separate boolean NOT NULL DEFAULT false;
ALTER TABLE public.balance_sheet_submissions ADD COLUMN IF NOT EXISTS overseas_cash_snapshot jsonb;
CREATE TABLE IF NOT EXISTS public.overseas_cash_fund (
  entry_id uuid PRIMARY KEY,
  location_id integer NOT NULL REFERENCES public.locations(location_id),
  cash_drawer_session_id bigint UNIQUE REFERENCES public.cash_drawer_sessions(cash_drawer_session_id),
  entry_type text NOT NULL CHECK (entry_type IN ('HANDOVER','WITHDRAWAL')),
  amount numeric(14,2) NOT NULL CHECK (amount>=0),
  reason text NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  user_id integer REFERENCES public.users(user_id),
  user_name text NOT NULL,
  device_id text,
  CHECK ((entry_type='HANDOVER')=(cash_drawer_session_id IS NOT NULL))
);
CREATE INDEX IF NOT EXISTS overseas_cash_fund_location_date_idx ON public.overseas_cash_fund(location_id,created_at,entry_id);
CREATE INDEX IF NOT EXISTS overseas_cash_payments_session_idx ON public.custom_order_payments(cash_drawer_session_id) WHERE overseas_cash_separate;
ALTER TABLE public.overseas_cash_fund ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON public.overseas_cash_fund FROM PUBLIC;
DO $$ BEGIN
 IF EXISTS(SELECT 1 FROM pg_roles WHERE rolname='service_role') THEN GRANT ALL ON public.overseas_cash_fund TO service_role; END IF;
 IF EXISTS(SELECT 1 FROM pg_roles WHERE rolname='anon') THEN REVOKE ALL ON public.overseas_cash_fund FROM anon; END IF;
 IF EXISTS(SELECT 1 FROM pg_roles WHERE rolname='authenticated') THEN REVOKE ALL ON public.overseas_cash_fund FROM authenticated; END IF;
END $$;

END IF;
END $smartstock_local$;
