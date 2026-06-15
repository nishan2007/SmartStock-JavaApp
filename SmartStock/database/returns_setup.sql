-- Returns setup for SmartStock.
-- Returns are recorded separately from sales so reports can show gross sales, returns, and net sales.

ALTER TABLE sales
ADD COLUMN IF NOT EXISTS returned_amount NUMERIC(12, 2) NOT NULL DEFAULT 0;

CREATE TABLE IF NOT EXISTS sale_returns (
    return_id BIGSERIAL PRIMARY KEY,
    sale_id INTEGER NOT NULL REFERENCES sales(sale_id),
    location_id INTEGER NOT NULL REFERENCES locations(location_id),
    user_id INTEGER REFERENCES users(user_id),
    user_name TEXT,
    refund_method TEXT,
    refund_amount NUMERIC(12, 2) NOT NULL DEFAULT 0,
    reason TEXT,
    device_id TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS sale_return_items (
    return_item_id BIGSERIAL PRIMARY KEY,
    return_id BIGINT NOT NULL REFERENCES sale_returns(return_id) ON DELETE CASCADE,
    sale_item_id INTEGER NOT NULL REFERENCES sale_items(sale_item_id),
    product_id INTEGER NOT NULL REFERENCES products(product_id),
    quantity INTEGER NOT NULL CHECK (quantity > 0),
    unit_price NUMERIC(12, 2) NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS sale_returns_sale_idx
ON sale_returns(sale_id);

CREATE INDEX IF NOT EXISTS sale_returns_location_created_idx
ON sale_returns(location_id, created_at DESC);

CREATE INDEX IF NOT EXISTS sale_return_items_sale_item_idx
ON sale_return_items(sale_item_id);

INSERT INTO permissions (permission_key, permission_name)
SELECT 'PROCESS_RETURNS', 'Process Returns'
WHERE NOT EXISTS (
    SELECT 1 FROM permissions WHERE UPPER(permission_key) = 'PROCESS_RETURNS'
);

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.role_id, p.permission_id
FROM roles r
JOIN permissions p ON UPPER(p.permission_key) = 'PROCESS_RETURNS'
WHERE UPPER(r.role_name) IN ('ADMIN', 'MANAGER')
  AND NOT EXISTS (
      SELECT 1
      FROM role_permissions rp
      WHERE rp.role_id = r.role_id
        AND rp.permission_id = p.permission_id
  );
