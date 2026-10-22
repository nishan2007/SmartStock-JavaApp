ALTER TABLE public.custom_order_items ADD COLUMN IF NOT EXISTS is_shirt boolean NOT NULL DEFAULT false;
ALTER TABLE public.custom_order_items ADD COLUMN IF NOT EXISTS shirt_designer_config jsonb NOT NULL DEFAULT '{}'::jsonb;
CREATE INDEX IF NOT EXISTS idx_custom_items_shirt_catalog ON public.custom_order_items(custom_item_id) WHERE is_shirt AND is_active;
