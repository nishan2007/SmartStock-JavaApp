-- Customer requests are private store data, never part of public snapshots.
CREATE TABLE storefront.quote_requests (
 request_id uuid PRIMARY KEY,
 location_id integer NOT NULL,
 auth_id uuid NOT NULL,
 email text NOT NULL,
 project_id uuid,
 project_context jsonb NOT NULL DEFAULT '{}'::jsonb,
 topic text NOT NULL CHECK(char_length(topic) BETWEEN 1 AND 150),
 description text NOT NULL CHECK(char_length(description) BETWEEN 1 AND 2000),
 quantity integer NOT NULL CHECK(quantity BETWEEN 1 AND 100000),
 size text NOT NULL DEFAULT '' CHECK(char_length(size)<=120),
 color text NOT NULL DEFAULT '' CHECK(char_length(color)<=120),
 material text NOT NULL DEFAULT '' CHECK(char_length(material)<=120),
 contact_phone text NOT NULL DEFAULT '' CHECK(char_length(contact_phone)<=60),
 desired_date date,
 request_hash text NOT NULL,
 status text NOT NULL DEFAULT 'REQUESTED' CHECK(status IN ('REQUESTED','REVIEWING','AWAITING_ARTWORK','QUOTED','IN_PRODUCTION','READY','COMPLETED','CANCELLED')),
 created_at timestamptz NOT NULL DEFAULT now(),
 updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX storefront_quote_requests_queue ON storefront.quote_requests(location_id,status,created_at DESC);
CREATE INDEX storefront_quote_requests_customer ON storefront.quote_requests(auth_id,created_at DESC);
INSERT INTO storefront.schema_version(version) VALUES(5) ON CONFLICT DO NOTHING;
