-- POS tables remain local; cloud recovery uses smartstock_store_rows.
DO $smartstock_local$ BEGIN
IF to_regclass('public.custom_order_lines') IS NOT NULL THEN
-- Regular inventory products used as custom-order blanks. One saved line is one physical item.
ALTER TABLE public.custom_order_lines ADD COLUMN IF NOT EXISTS inventory_product_id integer REFERENCES public.products(product_id);
ALTER TABLE public.custom_order_lines ADD COLUMN IF NOT EXISTS inventory_restocked boolean NOT NULL DEFAULT false;
CREATE INDEX IF NOT EXISTS custom_order_lines_inventory_product_idx ON public.custom_order_lines(inventory_product_id) WHERE inventory_product_id IS NOT NULL;

END IF;
END $smartstock_local$;
