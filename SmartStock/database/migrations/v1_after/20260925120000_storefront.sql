-- Independent, versioned local-store extension. Never expose this schema through PostgREST.
CREATE SCHEMA IF NOT EXISTS storefront;
REVOKE ALL ON SCHEMA storefront FROM PUBLIC;
CREATE TABLE IF NOT EXISTS storefront.schema_version(version integer PRIMARY KEY, installed_at timestamptz NOT NULL DEFAULT now());
CREATE TABLE IF NOT EXISTS storefront.settings (
 location_id integer PRIMARY KEY, enabled boolean NOT NULL DEFAULT false,
 pickup_hours integer NOT NULL DEFAULT 48 CHECK (pickup_hours BETWEEN 1 AND 8760),
 currency text NOT NULL DEFAULT 'GYD' CHECK (currency ~ '^[A-Z]{3}$'),
 welcome text NOT NULL DEFAULT 'Everyday essentials. A little extraordinary.',
 updated_at timestamptz NOT NULL DEFAULT now(),
 campaign_topic text NOT NULL DEFAULT '3D Printing' CHECK (char_length(campaign_topic) BETWEEN 1 AND 100),
 campaign_eyebrow text NOT NULL DEFAULT 'Now at Deckers · 3D Printing' CHECK (char_length(campaign_eyebrow) BETWEEN 1 AND 120),
 campaign_headline text NOT NULL DEFAULT 'Your Ideas. Made Real.' CHECK (char_length(campaign_headline) BETWEEN 1 AND 150),
 campaign_description text NOT NULL DEFAULT 'Custom 3D printing is now available at Deckers.' CHECK (char_length(campaign_description) BETWEEN 1 AND 300),
 campaign_steps text NOT NULL DEFAULT 'Upload your design · Choose your material · We print it' CHECK (char_length(campaign_steps) BETWEEN 1 AND 200),
 campaign_primary text NOT NULL DEFAULT 'Start a 3D Print' CHECK (char_length(campaign_primary) BETWEEN 1 AND 60),
 campaign_secondary text NOT NULL DEFAULT 'Explore 3D Printing' CHECK (char_length(campaign_secondary) BETWEEN 1 AND 60),
 campaign_visual text NOT NULL DEFAULT '3D_PRINT' CHECK (campaign_visual IN ('3D_PRINT','EDITORIAL'))
);
CREATE TABLE IF NOT EXISTS storefront.products (
 location_id integer NOT NULL, product_id integer NOT NULL,
 published boolean NOT NULL DEFAULT false, featured boolean NOT NULL DEFAULT false,
 description text NOT NULL DEFAULT '', updated_at timestamptz NOT NULL DEFAULT now(),
 PRIMARY KEY(location_id,product_id)
);
CREATE TABLE IF NOT EXISTS storefront.customer_links (
 auth_id uuid NOT NULL, location_id integer NOT NULL, customer_uuid uuid NOT NULL,
 email text NOT NULL, created_at timestamptz NOT NULL DEFAULT now(),
 PRIMARY KEY(auth_id,location_id), UNIQUE(location_id,customer_uuid)
);
CREATE TABLE IF NOT EXISTS storefront.enrollments (
 enrollment_id uuid PRIMARY KEY, auth_id uuid NOT NULL, location_id integer NOT NULL,
 email text NOT NULL, name text NOT NULL, source_server integer NOT NULL,
 status text NOT NULL DEFAULT 'PENDING' CHECK(status IN ('PENDING','LINKED','NEEDS_ATTENTION')),
 customer_uuid uuid, note text NOT NULL DEFAULT '', revision bigint NOT NULL DEFAULT 1,
 created_at timestamptz NOT NULL DEFAULT now(), updated_at timestamptz NOT NULL DEFAULT now(),
 UNIQUE(auth_id,location_id)
);
CREATE INDEX IF NOT EXISTS storefront_enrollments_pending ON storefront.enrollments(location_id,status);
CREATE TABLE IF NOT EXISTS storefront.orders (
 order_id uuid PRIMARY KEY, auth_id uuid NOT NULL, location_id integer NOT NULL,
 customer_uuid uuid NOT NULL, email text NOT NULL, name text NOT NULL,
 request_hash text NOT NULL, status text NOT NULL DEFAULT 'CONFIRMED'
 CHECK(status IN ('CONFIRMED','PREPARING','READY','COLLECTED','CANCELLED','EXPIRED','NEEDS_ATTENTION')),
 quote jsonb NOT NULL, total numeric(12,2) NOT NULL CHECK(total>0),
 source_server integer NOT NULL, sale_id integer, receipt_number text,
 created_at timestamptz NOT NULL DEFAULT now(), updated_at timestamptz NOT NULL DEFAULT now(),
 ready_at timestamptz, expires_at timestamptz, note text NOT NULL DEFAULT '',
 revision bigint NOT NULL DEFAULT 1, UNIQUE(location_id,sale_id)
);
CREATE INDEX IF NOT EXISTS storefront_orders_customer ON storefront.orders(auth_id,created_at DESC);
CREATE INDEX IF NOT EXISTS storefront_orders_queue ON storefront.orders(location_id,status,created_at);
CREATE INDEX IF NOT EXISTS storefront_orders_expiry ON storefront.orders(expires_at) WHERE status='READY';
-- Durable negative acknowledgement: delayed HTTP requests cannot resurrect a resolved command.
CREATE TABLE IF NOT EXISTS storefront.rejected_commands (
 order_id uuid PRIMARY KEY, location_id integer NOT NULL, rejected_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE IF NOT EXISTS storefront.reservations (
 order_id uuid NOT NULL REFERENCES storefront.orders(order_id), product_id integer NOT NULL,
 location_id integer NOT NULL, quantity integer NOT NULL CHECK(quantity>0),
 PRIMARY KEY(order_id,product_id)
);
CREATE INDEX IF NOT EXISTS storefront_reservations_stock ON storefront.reservations(location_id,product_id);
CREATE TABLE IF NOT EXISTS storefront.snapshots (
 location_id integer PRIMARY KEY, captured_at timestamptz NOT NULL, payload jsonb NOT NULL,
 refreshed_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE IF NOT EXISTS storefront.events (
 event_id uuid PRIMARY KEY, order_id uuid NOT NULL, revision bigint NOT NULL,
 payload jsonb NOT NULL, created_at timestamptz NOT NULL DEFAULT now(), delivered_at timestamptz,
 UNIQUE(order_id,revision)
);
CREATE INDEX IF NOT EXISTS storefront_events_pending ON storefront.events(created_at) WHERE delivered_at IS NULL;
CREATE TABLE IF NOT EXISTS storefront.audit (
 id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY, user_id integer,
 action text NOT NULL, detail text NOT NULL, created_at timestamptz NOT NULL DEFAULT now()
);
INSERT INTO storefront.schema_version(version) VALUES(1) ON CONFLICT DO NOTHING;
INSERT INTO storefront.schema_version(version) VALUES(2) ON CONFLICT DO NOTHING;
INSERT INTO storefront.schema_version(version) VALUES(3) ON CONFLICT DO NOTHING;
CREATE TABLE storefront.projects (
 project_id uuid PRIMARY KEY,
 location_id integer NOT NULL,
 source_custom_order_id bigint,
 status text NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT','PUBLISHED','FEATURED','ARCHIVED')),
 title text NOT NULL DEFAULT '' CHECK (char_length(title)<=150),
 summary text NOT NULL DEFAULT '' CHECK (char_length(summary)<=600),
 category text NOT NULL DEFAULT '' CHECK (char_length(category)<=80),
 materials text NOT NULL DEFAULT '' CHECK (char_length(materials)<=300),
 production_method text NOT NULL DEFAULT '' CHECK (char_length(production_method)<=300),
 customization text NOT NULL DEFAULT '' CHECK (char_length(customization)<=300),
 tags jsonb NOT NULL DEFAULT '[]'::jsonb CHECK (jsonb_typeof(tags)='array'),
 starting_price numeric(12,2) CHECK (starting_price>=0),
 cover_reference text NOT NULL DEFAULT '',
 created_at timestamptz NOT NULL DEFAULT now(),
 updated_at timestamptz NOT NULL DEFAULT now(),
 UNIQUE(location_id,source_custom_order_id)
);
CREATE INDEX storefront_projects_public ON storefront.projects(location_id,status,updated_at DESC);
INSERT INTO storefront.schema_version(version) VALUES(4) ON CONFLICT DO NOTHING;
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
CREATE TABLE storefront.quote_files (
 file_id uuid PRIMARY KEY,
 request_id uuid NOT NULL REFERENCES storefront.quote_requests(request_id),
 asset_reference text NOT NULL,
 original_filename text NOT NULL CHECK(char_length(original_filename) BETWEEN 1 AND 180),
 content_type text NOT NULL,
 byte_size integer NOT NULL CHECK(byte_size BETWEEN 1 AND 4194304),
 sha256 text NOT NULL CHECK(sha256 ~ '^[a-f0-9]{64}$'),
 created_at timestamptz NOT NULL DEFAULT now(),
 UNIQUE(request_id,asset_reference)
);
CREATE INDEX storefront_quote_files_request ON storefront.quote_files(request_id,created_at);
INSERT INTO storefront.schema_version(version) VALUES(6) ON CONFLICT DO NOTHING;
