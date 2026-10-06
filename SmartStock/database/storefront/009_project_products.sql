ALTER TABLE storefront.projects ADD COLUMN product_ids jsonb NOT NULL DEFAULT '[]'::jsonb
 CHECK (jsonb_typeof(product_ids)='array');
INSERT INTO storefront.schema_version(version) VALUES(9) ON CONFLICT DO NOTHING;
