ALTER TABLE public.custom_order_item_variants
    ADD COLUMN IF NOT EXISTS brand_id integer;

CREATE INDEX IF NOT EXISTS custom_order_item_variants_brand_idx
    ON public.custom_order_item_variants (brand_id);

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname='custom_order_item_variants_brand_id_fkey') THEN
        ALTER TABLE public.custom_order_item_variants
            ADD CONSTRAINT custom_order_item_variants_brand_id_fkey
            FOREIGN KEY (brand_id) REFERENCES public.item_brands(brand_id);
    END IF;
END $$;
