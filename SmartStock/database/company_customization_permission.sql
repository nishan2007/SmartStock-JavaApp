-- Kept for compatibility with older setup notes; use COMPANY_PREFERENCES going forward.

UPDATE permissions
SET permission_key = 'COMPANY_PREFERENCES',
    permission_name = 'Company Preferences'
WHERE UPPER(permission_key) = 'COMPANY_CUSTOMIZATION'
  AND NOT EXISTS (
      SELECT 1 FROM permissions WHERE UPPER(permission_key) = 'COMPANY_PREFERENCES'
  );

INSERT INTO permissions (permission_key, permission_name)
VALUES ('COMPANY_PREFERENCES', 'Company Preferences')
ON CONFLICT (permission_key) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.role_id, p.permission_id
FROM roles r
JOIN permissions p ON p.permission_key = 'COMPANY_PREFERENCES'
WHERE UPPER(r.role_name) = 'ADMIN'
ON CONFLICT DO NOTHING;
