ALTER TABLE public.custom_order_items
    ADD COLUMN IF NOT EXISTS size text,
    ADD COLUMN IF NOT EXISTS color text;

ALTER TABLE public.custom_order_item_variants
    ADD COLUMN IF NOT EXISTS size text,
    ADD COLUMN IF NOT EXISTS color text;

ALTER TABLE public.custom_order_lines
    ADD COLUMN IF NOT EXISTS item_size text,
    ADD COLUMN IF NOT EXISTS item_color text;
