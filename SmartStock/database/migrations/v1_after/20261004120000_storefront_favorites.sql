CREATE TABLE IF NOT EXISTS storefront.favorites (
 location_id integer NOT NULL,
 auth_id uuid NOT NULL,
 product_id integer NOT NULL,
 created_at timestamptz NOT NULL DEFAULT now(),
 PRIMARY KEY(location_id,auth_id,product_id)
);
CREATE INDEX IF NOT EXISTS storefront_favorites_auth ON storefront.favorites(auth_id,location_id);
INSERT INTO storefront.schema_version(version) VALUES(13) ON CONFLICT DO NOTHING;
