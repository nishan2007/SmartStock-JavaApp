CREATE TABLE IF NOT EXISTS storefront.quote_order_links (
 request_id uuid PRIMARY KEY REFERENCES storefront.quote_requests(request_id),
 custom_order_id bigint NOT NULL UNIQUE,
 linked_by integer NOT NULL,
 linked_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS storefront_quote_order_links_order ON storefront.quote_order_links(custom_order_id);
INSERT INTO storefront.schema_version(version) VALUES(8) ON CONFLICT DO NOTHING;
