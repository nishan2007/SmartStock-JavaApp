-- Every normal sale item is tied to a catalog product. Custom-order lines use
-- their own tables and are not represented by public.sale_items.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM public.sale_items WHERE product_id IS NULL) THEN
        RAISE EXCEPTION 'Cannot require sale_items.product_id while null product rows exist';
    END IF;
END $$;

ALTER TABLE public.sale_items ALTER COLUMN product_id SET NOT NULL;
