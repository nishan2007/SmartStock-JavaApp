-- Group scans select a sellable variant; groups never own stock.
ALTER TABLE public.product_groups ADD COLUMN IF NOT EXISTS barcode text NOT NULL DEFAULT '';
ALTER TABLE public.product_groups ADD COLUMN IF NOT EXISTS additional_barcodes jsonb NOT NULL DEFAULT '[]'::jsonb CHECK (jsonb_typeof(additional_barcodes) = 'array');
CREATE INDEX IF NOT EXISTS product_groups_barcode_lookup_idx ON public.product_groups (barcode);
CREATE INDEX IF NOT EXISTS product_groups_additional_barcodes_idx ON public.product_groups USING gin (additional_barcodes);
