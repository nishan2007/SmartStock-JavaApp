ALTER TABLE public.company_customization
    ADD COLUMN IF NOT EXISTS sale_quick_pick_size_item_type_ids jsonb NOT NULL DEFAULT '[]'::jsonb;

ALTER TABLE public.company_customization
    DROP CONSTRAINT IF EXISTS company_customization_sale_quick_pick_size_item_type_ids_chk;
ALTER TABLE public.company_customization
    ADD CONSTRAINT company_customization_sale_quick_pick_size_item_type_ids_chk
    CHECK (jsonb_typeof(sale_quick_pick_size_item_type_ids) = 'array');

ALTER TABLE public.company_customization
    ADD COLUMN IF NOT EXISTS sale_quick_pick_photo_item_type_ids jsonb NOT NULL DEFAULT '[]'::jsonb;

ALTER TABLE public.company_customization
    DROP CONSTRAINT IF EXISTS company_customization_sale_quick_pick_photo_item_type_ids_chk;
ALTER TABLE public.company_customization
    ADD CONSTRAINT company_customization_sale_quick_pick_photo_item_type_ids_chk
    CHECK (jsonb_typeof(sale_quick_pick_photo_item_type_ids) = 'array');
