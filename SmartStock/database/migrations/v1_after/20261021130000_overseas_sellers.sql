-- POS tables remain local; cloud recovery uses smartstock_store_rows.
DO $smartstock_local$ BEGIN
IF to_regclass('public.overseas_order_lines') IS NOT NULL THEN
ALTER TABLE public.overseas_order_lines ADD COLUMN IF NOT EXISTS seller text NOT NULL DEFAULT '';
CREATE INDEX IF NOT EXISTS overseas_order_lines_seller_idx
ON public.overseas_order_lines (lower(seller)) WHERE seller<>'';

END IF;
END $smartstock_local$;
