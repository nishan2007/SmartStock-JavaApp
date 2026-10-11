-- Archive preserves active state, stock, variants and historical order references.
ALTER TABLE public.custom_order_items ADD COLUMN IF NOT EXISTS is_archived boolean NOT NULL DEFAULT false;
CREATE INDEX IF NOT EXISTS custom_order_items_visible_idx
    ON public.custom_order_items(is_active, item_name) WHERE is_archived=false;
