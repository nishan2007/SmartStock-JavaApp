ALTER TABLE public.products
    ADD COLUMN IF NOT EXISTS additional_image_urls jsonb NOT NULL DEFAULT '[]'::jsonb;
ALTER TABLE public.custom_order_items
    ADD COLUMN IF NOT EXISTS additional_image_urls jsonb NOT NULL DEFAULT '[]'::jsonb;
ALTER TABLE public.custom_order_item_variants
    ADD COLUMN IF NOT EXISTS additional_image_urls jsonb NOT NULL DEFAULT '[]'::jsonb;

ALTER TABLE public.products
    ADD CONSTRAINT products_additional_image_urls_array
    CHECK (jsonb_typeof(additional_image_urls) = 'array');
ALTER TABLE public.custom_order_items
    ADD CONSTRAINT custom_order_items_additional_image_urls_array
    CHECK (jsonb_typeof(additional_image_urls) = 'array');
ALTER TABLE public.custom_order_item_variants
    ADD CONSTRAINT custom_order_item_variants_additional_image_urls_array
    CHECK (jsonb_typeof(additional_image_urls) = 'array');
