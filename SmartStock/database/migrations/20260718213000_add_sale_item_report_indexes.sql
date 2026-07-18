-- Supports report and sales-history joins without scanning all sale_items rows.
CREATE INDEX IF NOT EXISTS sale_items_sale_idx
    ON sale_items(sale_id);

CREATE INDEX IF NOT EXISTS sale_items_product_sale_idx
    ON sale_items(product_id, sale_id);
