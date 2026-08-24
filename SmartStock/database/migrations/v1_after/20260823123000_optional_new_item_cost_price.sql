ALTER TABLE public.company_customization
    ADD COLUMN IF NOT EXISTS require_cost_price_on_new_item boolean DEFAULT true NOT NULL;
