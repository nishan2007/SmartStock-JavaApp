ALTER TABLE public.custom_order_items ADD COLUMN IF NOT EXISTS is_sublimation boolean NOT NULL DEFAULT false;
CREATE INDEX IF NOT EXISTS idx_custom_items_sublimation_catalog ON public.custom_order_items(custom_item_id) WHERE is_sublimation AND is_active AND NOT is_archived;
