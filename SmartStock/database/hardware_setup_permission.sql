INSERT INTO permissions (permission_key, permission_name)
VALUES ('HARDWARE_SETUP', 'Hardware Setup')
ON CONFLICT (permission_key) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.role_id, p.permission_id
FROM roles r
JOIN permissions p ON p.permission_key = 'HARDWARE_SETUP'
WHERE UPPER(r.role_name) = 'ADMIN'
ON CONFLICT DO NOTHING;
