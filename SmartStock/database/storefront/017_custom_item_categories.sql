CREATE TABLE IF NOT EXISTS storefront.custom_item_categories (
 category_id uuid PRIMARY KEY,
 name text NOT NULL CHECK (char_length(btrim(name)) BETWEEN 1 AND 80),
 revision bigint NOT NULL DEFAULT 1 CHECK(revision>0)
);
CREATE UNIQUE INDEX IF NOT EXISTS custom_item_categories_name ON storefront.custom_item_categories(lower(btrim(name)));
CREATE TABLE IF NOT EXISTS storefront.custom_item_category_assignments (
 custom_item_id bigint PRIMARY KEY REFERENCES public.custom_order_items(custom_item_id) ON DELETE CASCADE,
 category_id uuid NOT NULL REFERENCES storefront.custom_item_categories(category_id)
);
CREATE INDEX IF NOT EXISTS custom_item_category_assignments_category ON storefront.custom_item_category_assignments(category_id,custom_item_id);
REVOKE ALL ON storefront.custom_item_categories,storefront.custom_item_category_assignments FROM PUBLIC;
INSERT INTO storefront.schema_version(version) VALUES(17) ON CONFLICT DO NOTHING;
