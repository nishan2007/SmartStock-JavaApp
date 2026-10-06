ALTER TABLE storefront.projects ADD COLUMN IF NOT EXISTS service_slug text NOT NULL DEFAULT ''
 CHECK (service_slug = '' OR service_slug ~ '^[a-z0-9-]{1,80}$');
INSERT INTO storefront.schema_version(version) VALUES(12) ON CONFLICT DO NOTHING;
