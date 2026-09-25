-- Size, color, or brand may distinguish variants that intentionally share a display name.
ALTER TABLE public.custom_order_item_variants
    DROP CONSTRAINT IF EXISTS custom_order_item_variants_item_name_uidx;
