-- Rename Company Customization permission to Company Preferences.
-- Existing role_permissions remain attached if the old row is updated.

UPDATE permissions
SET permission_key = 'COMPANY_PREFERENCES',
    permission_name = 'Company Preferences'
WHERE UPPER(permission_key) = 'COMPANY_CUSTOMIZATION'
  AND NOT EXISTS (
      SELECT 1 FROM permissions WHERE UPPER(permission_key) = 'COMPANY_PREFERENCES'
  );

UPDATE permissions
SET permission_name = 'Company Preferences'
WHERE UPPER(permission_key) = 'COMPANY_PREFERENCES';

INSERT INTO permissions (permission_key, permission_name)
SELECT 'COMPANY_PREFERENCES', 'Company Preferences'
WHERE NOT EXISTS (
    SELECT 1 FROM permissions WHERE UPPER(permission_key) = 'COMPANY_PREFERENCES'
);

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.role_id, p.permission_id
FROM roles r
JOIN permissions p ON UPPER(p.permission_key) = 'COMPANY_PREFERENCES'
WHERE UPPER(r.role_name) IN ('ADMIN', 'MANAGER')
  AND NOT EXISTS (
      SELECT 1
      FROM role_permissions rp
      WHERE rp.role_id = r.role_id
        AND rp.permission_id = p.permission_id
  );
