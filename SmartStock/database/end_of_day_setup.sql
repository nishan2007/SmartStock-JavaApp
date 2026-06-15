-- Sales reports setup for SmartStock.

INSERT INTO permissions (permission_key, permission_name)
SELECT 'END_OF_DAY', 'Sales Reports'
WHERE NOT EXISTS (
    SELECT 1 FROM permissions WHERE UPPER(permission_key) = 'END_OF_DAY'
);

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.role_id, p.permission_id
FROM roles r
JOIN permissions p ON UPPER(p.permission_key) = 'END_OF_DAY'
WHERE UPPER(r.role_name) IN ('ADMIN', 'MANAGER')
  AND NOT EXISTS (
      SELECT 1
      FROM role_permissions rp
      WHERE rp.role_id = r.role_id
        AND rp.permission_id = p.permission_id
  );
