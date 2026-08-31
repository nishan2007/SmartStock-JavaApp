ALTER TABLE public.company_customization
    ADD COLUMN IF NOT EXISTS round_sales_to_nearest_twenty boolean DEFAULT true NOT NULL,
    ADD COLUMN IF NOT EXISTS round_custom_orders_to_nearest_twenty boolean DEFAULT true NOT NULL;
