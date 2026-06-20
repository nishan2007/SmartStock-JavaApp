ALTER TABLE company_customization
ADD COLUMN IF NOT EXISTS price_tag_show_company BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE company_customization
ADD COLUMN IF NOT EXISTS price_tag_show_sku BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE company_customization
ADD COLUMN IF NOT EXISTS price_tag_show_barcode BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE company_customization
ADD COLUMN IF NOT EXISTS price_tag_width_inches NUMERIC(5, 2) NOT NULL DEFAULT 2.25;

ALTER TABLE company_customization
ADD COLUMN IF NOT EXISTS price_tag_height_inches NUMERIC(5, 2) NOT NULL DEFAULT 1.25;
