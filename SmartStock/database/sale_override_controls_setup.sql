-- Sale override controls for SmartStock.
-- Adds configurable sale limits and override metadata for discount/return/price overrides.

ALTER TABLE company_customization
ADD COLUMN IF NOT EXISTS sale_discount_limit_percent NUMERIC(7, 4) NOT NULL DEFAULT 5;

ALTER TABLE company_customization
ADD COLUMN IF NOT EXISTS sale_return_approval_limit NUMERIC(12, 2) NOT NULL DEFAULT 0;

ALTER TABLE company_customization
DROP CONSTRAINT IF EXISTS company_customization_sale_discount_limit_chk;

ALTER TABLE company_customization
ADD CONSTRAINT company_customization_sale_discount_limit_chk
CHECK (sale_discount_limit_percent >= 0 AND sale_discount_limit_percent <= 100);

ALTER TABLE company_customization
DROP CONSTRAINT IF EXISTS company_customization_sale_return_approval_limit_chk;

ALTER TABLE company_customization
ADD CONSTRAINT company_customization_sale_return_approval_limit_chk
CHECK (sale_return_approval_limit >= 0);

ALTER TABLE sales
ADD COLUMN IF NOT EXISTS discount_override_reason TEXT;

ALTER TABLE sales
ADD COLUMN IF NOT EXISTS discount_override_by_user_id INTEGER REFERENCES users(user_id);

ALTER TABLE sales
ADD COLUMN IF NOT EXISTS discount_override_by_name TEXT;

ALTER TABLE sale_items
ADD COLUMN IF NOT EXISTS price_override_reason TEXT;

ALTER TABLE sale_items
ADD COLUMN IF NOT EXISTS price_override_by_user_id INTEGER REFERENCES users(user_id);

ALTER TABLE sale_items
ADD COLUMN IF NOT EXISTS price_override_by_name TEXT;

ALTER TABLE sale_returns
ADD COLUMN IF NOT EXISTS override_reason TEXT;

ALTER TABLE sale_returns
ADD COLUMN IF NOT EXISTS override_by_user_id INTEGER REFERENCES users(user_id);

ALTER TABLE sale_returns
ADD COLUMN IF NOT EXISTS override_by_name TEXT;

INSERT INTO permissions (permission_key, permission_name)
SELECT 'SALE_DISCOUNT_OVERRIDE', 'Sale Discount Override'
WHERE NOT EXISTS (
    SELECT 1 FROM permissions WHERE UPPER(permission_key) = 'SALE_DISCOUNT_OVERRIDE'
);

INSERT INTO permissions (permission_key, permission_name)
SELECT 'RETURN_OVERRIDE', 'Return Override'
WHERE NOT EXISTS (
    SELECT 1 FROM permissions WHERE UPPER(permission_key) = 'RETURN_OVERRIDE'
);

INSERT INTO permissions (permission_key, permission_name)
SELECT 'SALE_DISCOUNT_LIMIT_SETTINGS', 'Sale Discount Limit Settings'
WHERE NOT EXISTS (
    SELECT 1 FROM permissions WHERE UPPER(permission_key) = 'SALE_DISCOUNT_LIMIT_SETTINGS'
);

INSERT INTO permissions (permission_key, permission_name)
SELECT 'SALE_RETURN_APPROVAL_SETTINGS', 'Sale Return Approval Settings'
WHERE NOT EXISTS (
    SELECT 1 FROM permissions WHERE UPPER(permission_key) = 'SALE_RETURN_APPROVAL_SETTINGS'
);

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.role_id, p.permission_id
FROM roles r
JOIN permissions p ON UPPER(p.permission_key) IN (
    'SALE_DISCOUNT_OVERRIDE',
    'RETURN_OVERRIDE',
    'SALE_DISCOUNT_LIMIT_SETTINGS',
    'SALE_RETURN_APPROVAL_SETTINGS'
)
WHERE UPPER(r.role_name) IN ('ADMIN', 'MANAGER')
  AND NOT EXISTS (
      SELECT 1
      FROM role_permissions rp
      WHERE rp.role_id = r.role_id
        AND rp.permission_id = p.permission_id
  );
