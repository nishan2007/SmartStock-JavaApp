-- Notification-specific permissions and descriptions.

ALTER TABLE permissions
    ADD COLUMN IF NOT EXISTS description TEXT;

ALTER TABLE permissions
    ADD COLUMN IF NOT EXISTS permission_group TEXT;

ALTER TABLE permissions
    ADD COLUMN IF NOT EXISTS permission_subgroup TEXT;

INSERT INTO permissions (permission_key, permission_name, description, permission_group, permission_subgroup)
VALUES
    ('INVENTORY_STOCK_NOTIFICATIONS', 'Inventory Stock Notifications',
     'Allows receiving low-stock and out-of-stock notifications for inventory and custom-order items.', 'Inventory', 'Notifications'),
    ('CUSTOM_ORDER_WORK_NOTIFICATIONS', 'Custom Order Work Notifications',
     'Allows receiving operational notifications for due, overdue, ready, unassigned, and balance-due custom orders.', 'Custom Orders', 'Notifications'),
    ('CUSTOM_ORDER_EXCEPTION_NOTIFICATIONS', 'Custom Order Exception Notifications',
     'Allows receiving custom-order exception notifications such as recent refunds.', 'Custom Orders', 'Notifications'),
    ('SYNC_NOTIFICATIONS', 'Sync Notifications',
     'Allows receiving sync health notifications for offline cloud, failed events, conflicts, and backlogs.', 'Operations', 'Sync')
ON CONFLICT (permission_key) DO UPDATE
SET permission_name = EXCLUDED.permission_name,
    description = EXCLUDED.description,
    permission_group = EXCLUDED.permission_group,
    permission_subgroup = EXCLUDED.permission_subgroup;

UPDATE permissions
SET description = 'Allows balancing drawer sessions, submitting counted cash totals, and receiving drawer-start notifications.',
    permission_group = 'Operations',
    permission_subgroup = 'Cash Drawer'
WHERE UPPER(permission_key) = 'BALANCE_DRAWER';

UPDATE permissions
SET description = 'Allows managing approved, pending, and blocked devices.',
    permission_group = 'Administration',
    permission_subgroup = 'Devices'
WHERE UPPER(permission_key) = 'DEVICE_MANAGEMENT';

UPDATE permissions
SET description = 'Allows managing maintenance parts and receiving maintenance part reorder notifications.',
    permission_group = 'Inventory',
    permission_subgroup = 'Maintenance'
WHERE UPPER(permission_key) = 'PARTS_MANAGEMENT';

UPDATE permissions
SET description = 'Allows receiving open maintenance ticket notifications and working maintenance tickets.',
    permission_group = 'Inventory',
    permission_subgroup = 'Maintenance'
WHERE UPPER(permission_key) = 'MAINTENANCE_TECHNICIAN';

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.role_id, p.permission_id
FROM roles r
JOIN permissions p ON UPPER(p.permission_key) IN (
    'INVENTORY_STOCK_NOTIFICATIONS',
    'CUSTOM_ORDER_WORK_NOTIFICATIONS',
    'CUSTOM_ORDER_EXCEPTION_NOTIFICATIONS',
    'SYNC_NOTIFICATIONS'
)
WHERE UPPER(r.role_name) = 'ADMIN'
  AND NOT EXISTS (
      SELECT 1
      FROM role_permissions rp
      WHERE rp.role_id = r.role_id
        AND rp.permission_id = p.permission_id
  );
