-- Add reusable department, item type, and brand classification to custom items.

INSERT INTO categories (name, description)
SELECT 'Custom', 'Default department for custom items'
WHERE NOT EXISTS (
    SELECT 1 FROM categories WHERE UPPER(BTRIM(name)) = 'CUSTOM'
);

ALTER TABLE custom_order_items
ADD COLUMN IF NOT EXISTS category_id INTEGER REFERENCES categories(category_id);

ALTER TABLE custom_order_items
ADD COLUMN IF NOT EXISTS item_type_id INTEGER REFERENCES item_types(item_type_id);

ALTER TABLE custom_order_items
ADD COLUMN IF NOT EXISTS brand_id INTEGER REFERENCES item_brands(brand_id);

CREATE INDEX IF NOT EXISTS custom_order_items_category_idx
ON custom_order_items(category_id);

CREATE INDEX IF NOT EXISTS custom_order_items_item_type_idx
ON custom_order_items(item_type_id);

CREATE INDEX IF NOT EXISTS custom_order_items_brand_idx
ON custom_order_items(brand_id);
