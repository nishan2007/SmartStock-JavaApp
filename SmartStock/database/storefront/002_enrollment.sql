-- Upgrade early storefront schemas without changing authoritative business data.
CREATE TABLE IF NOT EXISTS storefront.rejected_commands (
 order_id uuid PRIMARY KEY, location_id integer NOT NULL, rejected_at timestamptz NOT NULL DEFAULT now()
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
INSERT INTO storefront.schema_version(version) VALUES(2) ON CONFLICT DO NOTHING;
