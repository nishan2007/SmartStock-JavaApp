ALTER TABLE public.customer_accounts
    ADD COLUMN IF NOT EXISTS internal_expense_location_id integer REFERENCES public.locations(location_id);
CREATE UNIQUE INDEX IF NOT EXISTS customer_accounts_expense_location_idx
    ON public.customer_accounts(internal_expense_location_id) WHERE internal_expense_location_id IS NOT NULL;
ALTER TABLE public.stock_ledger_entries
    ADD COLUMN IF NOT EXISTS expense_checkout_id uuid,
    ADD COLUMN IF NOT EXISTS representative_name text;
