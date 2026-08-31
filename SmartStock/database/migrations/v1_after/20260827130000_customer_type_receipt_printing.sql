ALTER TABLE public.customer_types
    ADD COLUMN IF NOT EXISTS auto_print_sale_receipt boolean DEFAULT true NOT NULL;
