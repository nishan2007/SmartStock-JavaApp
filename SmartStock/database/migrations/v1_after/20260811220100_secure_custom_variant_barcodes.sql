ALTER TABLE public.custom_order_item_variant_barcodes ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON TABLE public.custom_order_item_variant_barcodes FROM anon;
REVOKE ALL ON TABLE public.custom_order_item_variant_barcodes FROM authenticated;
GRANT ALL ON TABLE public.custom_order_item_variant_barcodes TO service_role;
GRANT USAGE, SELECT ON SEQUENCE public.custom_order_item_variant_barcodes_custom_variant_barcode_id_seq TO service_role;
