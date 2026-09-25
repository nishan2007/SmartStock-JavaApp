ALTER TABLE custom_order_items
    ADD COLUMN IF NOT EXISTS sell_in_pos boolean NOT NULL DEFAULT false;

ALTER TABLE sale_items
    ADD COLUMN IF NOT EXISTS catalog_source text NOT NULL DEFAULT 'PRODUCT',
    ADD COLUMN IF NOT EXISTS custom_item_id bigint,
    ADD COLUMN IF NOT EXISTS custom_variant_id bigint,
    ADD COLUMN IF NOT EXISTS sku_snapshot text,
    ADD COLUMN IF NOT EXISTS brand_snapshot text,
    ADD COLUMN IF NOT EXISTS size_snapshot text,
    ADD COLUMN IF NOT EXISTS color_snapshot text;

ALTER TABLE sale_items ALTER COLUMN product_id DROP NOT NULL;

ALTER TABLE sale_items DROP CONSTRAINT IF EXISTS sale_items_catalog_source_check;
ALTER TABLE sale_items ADD CONSTRAINT sale_items_catalog_source_check CHECK (
    (catalog_source='PRODUCT' AND product_id IS NOT NULL AND custom_item_id IS NULL AND custom_variant_id IS NULL)
 OR (catalog_source='CUSTOM_ITEM' AND product_id IS NULL AND custom_item_id IS NOT NULL AND custom_variant_id IS NULL)
 OR (catalog_source='CUSTOM_VARIANT' AND product_id IS NULL AND custom_item_id IS NOT NULL AND custom_variant_id IS NOT NULL)
);

ALTER TABLE sale_items DROP CONSTRAINT IF EXISTS sale_items_custom_item_id_fkey;
ALTER TABLE sale_items ADD CONSTRAINT sale_items_custom_item_id_fkey FOREIGN KEY (custom_item_id) REFERENCES custom_order_items(custom_item_id);
ALTER TABLE sale_items DROP CONSTRAINT IF EXISTS sale_items_custom_variant_id_fkey;
ALTER TABLE sale_items ADD CONSTRAINT sale_items_custom_variant_id_fkey FOREIGN KEY (custom_variant_id) REFERENCES custom_order_item_variants(custom_variant_id);

CREATE INDEX IF NOT EXISTS custom_order_items_pos_idx ON custom_order_items (sell_in_pos, is_active, product_type);
CREATE INDEX IF NOT EXISTS sale_items_custom_item_sale_idx ON sale_items (custom_item_id, sale_id) WHERE custom_item_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS sale_items_custom_variant_sale_idx ON sale_items (custom_variant_id, sale_id) WHERE custom_variant_id IS NOT NULL;

ALTER TABLE custom_order_item_movements ADD COLUMN IF NOT EXISTS sale_id integer REFERENCES sales(sale_id);
ALTER TABLE custom_order_item_movements ADD COLUMN IF NOT EXISTS sale_item_id integer REFERENCES sale_items(sale_item_id);
ALTER TABLE sale_return_items ALTER COLUMN product_id DROP NOT NULL;
ALTER TABLE sale_return_items ADD COLUMN IF NOT EXISTS custom_item_id bigint REFERENCES custom_order_items(custom_item_id);
ALTER TABLE sale_return_items ADD COLUMN IF NOT EXISTS custom_variant_id bigint REFERENCES custom_order_item_variants(custom_variant_id);
