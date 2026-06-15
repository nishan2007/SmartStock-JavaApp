-- Vendor setup for SmartStock.

CREATE TABLE IF NOT EXISTS vendors (
    vendor_id SERIAL PRIMARY KEY,
    name TEXT NOT NULL UNIQUE,
    contact_name TEXT,
    phone TEXT,
    email TEXT,
    address TEXT,
    notes TEXT,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE products
ADD COLUMN IF NOT EXISTS vendor_id INTEGER REFERENCES vendors(vendor_id);

CREATE UNIQUE INDEX IF NOT EXISTS vendors_name_unique_idx
ON vendors(LOWER(name));

CREATE INDEX IF NOT EXISTS products_vendor_id_idx
ON products(vendor_id);

INSERT INTO permissions (permission_key, permission_name)
SELECT 'VENDOR_MANAGEMENT', 'Vendor Management'
WHERE NOT EXISTS (
    SELECT 1 FROM permissions WHERE UPPER(permission_key) = 'VENDOR_MANAGEMENT'
);

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.role_id, p.permission_id
FROM roles r
JOIN permissions p ON UPPER(p.permission_key) = 'VENDOR_MANAGEMENT'
WHERE UPPER(r.role_name) IN ('ADMIN', 'MANAGER')
  AND NOT EXISTS (
      SELECT 1
      FROM role_permissions rp
      WHERE rp.role_id = r.role_id
        AND rp.permission_id = p.permission_id
  );
