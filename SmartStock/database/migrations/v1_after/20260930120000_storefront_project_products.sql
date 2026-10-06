-- Staff-curated SmartStock product references for Made at Deckers projects.
DO $$ BEGIN
 IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                WHERE table_schema='storefront' AND table_name='projects' AND column_name='product_ids') THEN
  ALTER TABLE storefront.projects ADD COLUMN product_ids jsonb NOT NULL DEFAULT '[]'::jsonb
   CHECK (jsonb_typeof(product_ids)='array');
 END IF;
END $$;
INSERT INTO storefront.schema_version(version) VALUES(9) ON CONFLICT DO NOTHING;
