ALTER TABLE storefront.products
 ADD COLUMN IF NOT EXISTS website_price numeric(12,2) CHECK (website_price > 0),
 ADD COLUMN IF NOT EXISTS promotional_price numeric(12,2)
   CHECK (promotional_price IS NULL OR (website_price IS NOT NULL AND promotional_price > 0 AND promotional_price < website_price));
INSERT INTO storefront.schema_version(version) VALUES(15) ON CONFLICT DO NOTHING;
