-- Store Transfer setup for SmartStock.
-- Run this in Supabase SQL Editor before using the Store Transfer screen.

CREATE TABLE IF NOT EXISTS store_transfers (
    transfer_id BIGSERIAL PRIMARY KEY,
    from_location_id INTEGER NOT NULL REFERENCES locations(location_id),
    to_location_id INTEGER NOT NULL REFERENCES locations(location_id),
    user_id INTEGER REFERENCES users(user_id),
    user_name TEXT,
    status TEXT NOT NULL DEFAULT 'PENDING',
    note TEXT,
    received_at TIMESTAMPTZ,
    received_by_user_id INTEGER REFERENCES users(user_id),
    received_by_name TEXT,
    receive_id TEXT REFERENCES receiving_batches(receive_id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT store_transfers_different_locations CHECK (from_location_id <> to_location_id)
);

ALTER TABLE store_transfers
ALTER COLUMN status SET DEFAULT 'PENDING';

ALTER TABLE store_transfers
ADD COLUMN IF NOT EXISTS received_at TIMESTAMPTZ;

ALTER TABLE store_transfers
ADD COLUMN IF NOT EXISTS received_by_user_id INTEGER REFERENCES users(user_id);

ALTER TABLE store_transfers
ADD COLUMN IF NOT EXISTS received_by_name TEXT;

ALTER TABLE store_transfers
ADD COLUMN IF NOT EXISTS receive_id TEXT REFERENCES receiving_batches(receive_id);

CREATE TABLE IF NOT EXISTS store_transfer_items (
    transfer_item_id BIGSERIAL PRIMARY KEY,
    transfer_id BIGINT NOT NULL REFERENCES store_transfers(transfer_id) ON DELETE CASCADE,
    product_id INTEGER NOT NULL REFERENCES products(product_id),
    quantity INTEGER NOT NULL CHECK (quantity > 0)
);

CREATE INDEX IF NOT EXISTS store_transfers_created_at_idx
ON store_transfers(created_at DESC);

CREATE INDEX IF NOT EXISTS store_transfer_items_transfer_idx
ON store_transfer_items(transfer_id);

INSERT INTO permissions (permission_key, permission_name)
SELECT 'STORE_TRANSFER', 'Store Transfer'
WHERE NOT EXISTS (
    SELECT 1 FROM permissions WHERE UPPER(permission_key) = 'STORE_TRANSFER'
);

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.role_id, p.permission_id
FROM roles r
JOIN permissions p ON UPPER(p.permission_key) = 'STORE_TRANSFER'
WHERE UPPER(r.role_name) IN ('ADMIN', 'MANAGER')
  AND NOT EXISTS (
      SELECT 1
      FROM role_permissions rp
      WHERE rp.role_id = r.role_id
        AND rp.permission_id = p.permission_id
  );
