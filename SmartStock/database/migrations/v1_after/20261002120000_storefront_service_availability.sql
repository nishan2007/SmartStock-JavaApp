CREATE TABLE IF NOT EXISTS storefront.service_availability (
 location_id integer NOT NULL,
 slug text NOT NULL CHECK(slug ~ '^[a-z0-9-]{1,80}$'),
 available boolean NOT NULL DEFAULT true,
 updated_at timestamptz NOT NULL DEFAULT now(),
 PRIMARY KEY(location_id,slug)
);
INSERT INTO storefront.schema_version(version) VALUES(11) ON CONFLICT DO NOTHING;
