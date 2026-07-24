ALTER TABLE company_customization
ADD COLUMN IF NOT EXISTS always_print_sale_receipt BOOLEAN NOT NULL DEFAULT FALSE;
