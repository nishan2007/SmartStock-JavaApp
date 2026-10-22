-- Preserve the first existing category in display order for legacy overlaps.
UPDATE public.custom_order_items SET
 is_sublimation=is_sublimation AND NOT is_shirt,
 is_business_cards=is_business_cards AND NOT (is_shirt OR is_sublimation),
 is_large_format_printing=is_large_format_printing AND NOT (is_shirt OR is_sublimation OR is_business_cards),
 is_laser=is_laser AND NOT (is_shirt OR is_sublimation OR is_business_cards OR is_large_format_printing)
WHERE is_shirt::integer + is_sublimation::integer + is_business_cards::integer + is_large_format_printing::integer + is_laser::integer > 1;
DO $$ BEGIN
 IF NOT EXISTS(SELECT 1 FROM pg_constraint WHERE conrelid='public.custom_order_items'::regclass AND conname='custom_item_single_category') THEN
 ALTER TABLE public.custom_order_items ADD CONSTRAINT custom_item_single_category CHECK (is_shirt::integer + is_sublimation::integer + is_business_cards::integer + is_large_format_printing::integer + is_laser::integer <= 1);
 END IF;
END $$;
