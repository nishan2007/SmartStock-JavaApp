ALTER TABLE public.custom_order_items ADD COLUMN IF NOT EXISTS is_business_cards boolean NOT NULL DEFAULT false;
CREATE INDEX IF NOT EXISTS idx_custom_items_business_cards_catalog ON public.custom_order_items(custom_item_id) WHERE is_business_cards AND is_active AND NOT is_archived;
ALTER TABLE public.custom_order_items ADD COLUMN IF NOT EXISTS is_large_format_printing boolean NOT NULL DEFAULT false;
CREATE INDEX IF NOT EXISTS idx_custom_items_large_format_printing_catalog ON public.custom_order_items(custom_item_id) WHERE is_large_format_printing AND is_active AND NOT is_archived;
ALTER TABLE public.custom_order_items ADD COLUMN IF NOT EXISTS is_laser boolean NOT NULL DEFAULT false;
CREATE INDEX IF NOT EXISTS idx_custom_items_laser_catalog ON public.custom_order_items(custom_item_id) WHERE is_laser AND is_active AND NOT is_archived;
