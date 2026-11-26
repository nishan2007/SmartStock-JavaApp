-- Store-owned digital fulfilment. Cloud recovery preserves the row payload.
DO $smartstock_local$ BEGIN
IF to_regclass('public.overseas_order_lines') IS NOT NULL THEN
ALTER TABLE public.overseas_order_lines ADD COLUMN IF NOT EXISTS digital boolean NOT NULL DEFAULT false;
END IF;
END $smartstock_local$;