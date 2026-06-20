ALTER TABLE company_customization
ADD COLUMN IF NOT EXISTS change_basket_target_amount NUMERIC(12, 2) NOT NULL DEFAULT 60000;
