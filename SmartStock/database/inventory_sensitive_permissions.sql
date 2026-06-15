-- Inventory sensitive field permissions for SmartStock.
-- Run this after the base permissions table exists.

INSERT INTO permissions (permission_key, permission_name)
SELECT 'VIEW_COST_PRICE', 'View Cost Price'
WHERE NOT EXISTS (
    SELECT 1 FROM permissions WHERE UPPER(permission_key) = 'VIEW_COST_PRICE'
);

INSERT INTO permissions (permission_key, permission_name)
SELECT 'VIEW_VENDOR', 'View Vendor'
WHERE NOT EXISTS (
    SELECT 1 FROM permissions WHERE UPPER(permission_key) = 'VIEW_VENDOR'
);

INSERT INTO permissions (permission_key, permission_name)
SELECT 'VIEW_CREATED_BY', 'View Created By'
WHERE NOT EXISTS (
    SELECT 1 FROM permissions WHERE UPPER(permission_key) = 'VIEW_CREATED_BY'
);

INSERT INTO permissions (permission_key, permission_name)
SELECT 'MANUAL_ADJUSTMENT', 'Manual Adjustment'
WHERE NOT EXISTS (
    SELECT 1 FROM permissions WHERE UPPER(permission_key) = 'MANUAL_ADJUSTMENT'
);

INSERT INTO permissions (permission_key, permission_name)
SELECT 'RECEIVING_STOCK_OVERRIDE', 'Receiving Stock Override'
WHERE NOT EXISTS (
    SELECT 1 FROM permissions WHERE UPPER(permission_key) = 'RECEIVING_STOCK_OVERRIDE'
);

-- Give existing admin/manager roles access by default. Remove this block if you
-- want to assign the permissions manually through role management.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.role_id, p.permission_id
FROM roles r
JOIN permissions p ON UPPER(p.permission_key) IN (
    'VIEW_COST_PRICE',
    'VIEW_VENDOR',
    'VIEW_CREATED_BY',
    'MANUAL_ADJUSTMENT',
    'RECEIVING_STOCK_OVERRIDE'
)
WHERE UPPER(r.role_name) IN ('ADMIN', 'MANAGER')
  AND NOT EXISTS (
      SELECT 1
      FROM role_permissions rp
      WHERE rp.role_id = r.role_id
        AND rp.permission_id = p.permission_id
  );
