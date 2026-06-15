-- Department setup for SmartStock.
-- Departments are stored in the existing categories table used by products.category_id.

CREATE TABLE IF NOT EXISTS categories (
    category_id SERIAL PRIMARY KEY,
    name TEXT NOT NULL UNIQUE,
    description TEXT,
    vat_rate_percent NUMERIC(6, 2) NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE categories
ADD COLUMN IF NOT EXISTS description TEXT;

ALTER TABLE categories
ADD COLUMN IF NOT EXISTS vat_rate_percent NUMERIC(6, 2) NOT NULL DEFAULT 0;

ALTER TABLE categories
DROP CONSTRAINT IF EXISTS categories_vat_rate_percent_chk;

ALTER TABLE categories
ADD CONSTRAINT categories_vat_rate_percent_chk
CHECK (vat_rate_percent >= 0 AND vat_rate_percent <= 100);

CREATE UNIQUE INDEX IF NOT EXISTS categories_name_unique_idx
ON categories(LOWER(name));

INSERT INTO permissions (permission_key, permission_name)
SELECT 'DEPARTMENT_MANAGEMENT', 'Department Management'
WHERE NOT EXISTS (
    SELECT 1 FROM permissions WHERE UPPER(permission_key) = 'DEPARTMENT_MANAGEMENT'
);

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.role_id, p.permission_id
FROM roles r
JOIN permissions p ON UPPER(p.permission_key) = 'DEPARTMENT_MANAGEMENT'
WHERE UPPER(r.role_name) IN ('ADMIN', 'MANAGER')
  AND NOT EXISTS (
      SELECT 1
      FROM role_permissions rp
      WHERE rp.role_id = r.role_id
        AND rp.permission_id = p.permission_id
  );
