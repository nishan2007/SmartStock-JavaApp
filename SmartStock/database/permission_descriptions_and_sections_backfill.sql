-- Backfill permission descriptions and section/group labels for desktop + app permissions.

ALTER TABLE permissions
    ADD COLUMN IF NOT EXISTS description TEXT;

ALTER TABLE permissions
    ADD COLUMN IF NOT EXISTS permission_group TEXT;

ALTER TABLE mobile_permissions
    ADD COLUMN IF NOT EXISTS description TEXT;

ALTER TABLE mobile_permissions
    ADD COLUMN IF NOT EXISTS permission_group TEXT;

-- Fill any missing descriptions using readable permission key text.
UPDATE permissions
SET description = 'Allows ' || initcap(replace(lower(permission_key), '_', ' ')) || '.'
WHERE permission_key IS NOT NULL
  AND TRIM(permission_key) <> ''
  AND (description IS NULL OR TRIM(description) = '');

UPDATE permissions p
SET description = v.description
FROM (VALUES
    ('BALANCE_DRAWER', 'Allows balancing drawer sessions, submitting counted cash totals, and receiving drawer-start notifications.'),
    ('DEVICE_MANAGEMENT', 'Allows managing approved, pending, and blocked devices.'),
    ('PARTS_MANAGEMENT', 'Allows managing maintenance parts and receiving maintenance part reorder notifications.'),
    ('MAINTENANCE_TECHNICIAN', 'Allows receiving open maintenance ticket notifications and working maintenance tickets.'),
    ('INVENTORY_STOCK_NOTIFICATIONS', 'Allows receiving low-stock and out-of-stock notifications for inventory and custom-order items.'),
    ('CUSTOM_ORDER_WORK_NOTIFICATIONS', 'Allows receiving operational notifications for due, overdue, ready, unassigned, and balance-due custom orders.'),
    ('CUSTOM_ORDER_EXCEPTION_NOTIFICATIONS', 'Allows receiving custom-order exception notifications such as recent refunds.'),
    ('SYNC_NOTIFICATIONS', 'Allows receiving sync health notifications for offline cloud, failed events, conflicts, and backlogs.')
) AS v(permission_key, description)
WHERE UPPER(p.permission_key) = UPPER(v.permission_key);

UPDATE mobile_permissions
SET description = 'Allows ' || initcap(replace(lower(permission_key), '_', ' ')) || ' in the mobile app.'
WHERE permission_key IS NOT NULL
  AND TRIM(permission_key) <> ''
  AND (description IS NULL OR TRIM(description) = '');

-- Fill desktop permission sections when missing.
UPDATE permissions
SET permission_group = CASE
    WHEN UPPER(permission_key) IN (
        'MAKE_SALE','APPLY_SALE_DISCOUNT','SALE_DISCOUNT_OVERRIDE','RETURN_OVERRIDE',
        'SALE_DISCOUNT_LIMIT_SETTINGS','SALE_RETURN_APPROVAL_SETTINGS','CHANGE_SALE_ITEM_PRICE',
        'PROCESS_RETURNS','END_OF_DAY','BALANCE_DRAWER','BALANCE_SHEET','VIEW_SALES','CUSTOMER_ACCOUNTS',
        'SET_CREDIT_LIMIT','EDIT_ACCOUNT_NUMBER'
    ) THEN 'Sales'
    WHEN UPPER(permission_key) IN (
        'CREATE_CUSTOM_ORDER','MANAGE_CUSTOM_ORDERS','VIEW_ASSIGNED_CUSTOM_ORDERS',
        'ORDERS_MANAGER_DASHBOARD','CUSTOM_ORDER_WORK_NOTIFICATIONS','CUSTOM_ORDER_EXCEPTION_NOTIFICATIONS',
        'ORDERS_END_OF_DAY','CUSTOM_ORDER_REFUNDS',
        'CUSTOM_ORDER_LINE_RETURNS','CUSTOM_ORDER_LINE_DELIVERY','CUSTOM_ORDER_LINE_DISCOUNT',
        'CUSTOM_ORDER_DEPOSIT_OVERRIDE','CUSTOM_ORDER_DEPOSIT_SETTINGS',
        'CUSTOM_ORDER_REFUND_APPROVAL','CUSTOM_ORDER_REFUND_APPROVAL_SETTINGS',
        'CUSTOM_ORDER_PRODUCTION_STEPS','CUSTOM_ORDER_CANCEL'
    ) THEN 'Orders'
    WHEN UPPER(permission_key) IN (
        'NEW_ITEM','EDIT_ITEM','RECEIVING_INVENTORY','RECEIVING_STOCK_OVERRIDE','VIEW_RECEIVING_HISTORY','STORE_TRANSFER',
        'VIEW_INVENTORY','INVENTORY_STOCK_NOTIFICATIONS','VIEW_ITEM_DETAILS','VIEW_COST_PRICE','VIEW_VENDOR','VIEW_CREATED_BY',
        'MANUAL_ADJUSTMENT','DEPARTMENT_MANAGEMENT','VENDOR_MANAGEMENT','PARTS_MANAGEMENT','MAINTENANCE_TECHNICIAN'
    ) THEN 'Inventory'
    WHEN UPPER(permission_key) IN (
        'EMPLOYEE_MANAGEMENT','TIME_CLOCK','TIME_CLOCK_MANAGEMENT','PAYROLL_DASHBOARD'
    ) THEN 'People'
    WHEN UPPER(permission_key) IN (
        'ROLE_MANAGEMENT','LOCATION_MANAGEMENT','CASH_DRAWER_MANAGEMENT','COMPANY_PREFERENCES',
        'CHANGE_STORE','VIEW_REPORTS','SYNC_NOTIFICATIONS','LOCAL_DEVICE_SETTINGS','HARDWARE_SETUP','DEVICE_MANAGEMENT'
    ) THEN 'Administration'
    ELSE COALESCE(NULLIF(permission_group, ''), 'General')
END
WHERE permission_key IS NOT NULL
  AND TRIM(permission_key) <> ''
  AND (permission_group IS NULL OR TRIM(permission_group) = '');

UPDATE permissions p
SET permission_group = v.permission_group
FROM (VALUES
    ('BALANCE_DRAWER', 'Sales'),
    ('DEVICE_MANAGEMENT', 'Administration'),
    ('PARTS_MANAGEMENT', 'Inventory'),
    ('MAINTENANCE_TECHNICIAN', 'Inventory'),
    ('INVENTORY_STOCK_NOTIFICATIONS', 'Inventory'),
    ('CUSTOM_ORDER_WORK_NOTIFICATIONS', 'Orders'),
    ('CUSTOM_ORDER_EXCEPTION_NOTIFICATIONS', 'Orders'),
    ('SYNC_NOTIFICATIONS', 'Administration')
) AS v(permission_key, permission_group)
WHERE UPPER(p.permission_key) = UPPER(v.permission_key);

-- Fill app permission sections when missing.
UPDATE mobile_permissions
SET permission_group = CASE
    WHEN permission_group IS NOT NULL AND TRIM(permission_group) <> '' THEN permission_group
    WHEN UPPER(permission_key) IN (
        'MAKE_SALE','VIEW_SALES','RETURNS','END_OF_DAY','CUSTOMERS','MANAGE_CUSTOMERS',
        'EDIT_CUSTOMER_CREDIT_LIMIT','EDIT_ACCOUNT_NUMBER','APPLY_SALE_DISCOUNT','CHANGE_SALE_ITEM_PRICE',
        'VIEW_SALE_AUDIT','EXPORT_SALE_AUDIT','CASH_DRAWER_MANAGEMENT'
    ) THEN 'Sales'
    WHEN UPPER(permission_key) IN (
        'CREATE_CUSTOM_ORDER','MANAGE_CUSTOM_ORDERS','VIEW_ASSIGNED_CUSTOM_ORDERS',
        'ORDERS_MANAGER_DASHBOARD','ORDERS_END_OF_DAY','CUSTOM_ORDER_REFUNDS',
        'CUSTOM_ORDER_LINE_RETURNS','CUSTOM_ORDER_LINE_DELIVERY','CUSTOM_ORDER_LINE_DISCOUNT',
        'CUSTOM_ORDER_DEPOSIT_OVERRIDE','CUSTOM_ORDER_REFUND_APPROVAL',
        'CUSTOM_ORDER_PRODUCTION_STEPS','CUSTOM_ORDER_CANCEL','CUSTOM_ORDER_OVERRIDES'
    ) THEN 'Orders'
    WHEN UPPER(permission_key) IN (
        'INVENTORY','RECEIVING','STORE_TRANSFER','VERIFY_STORE_TRANSFER_QUANTITY','EDIT_ITEM','NEW_ITEM',
        'ADJUST_INVENTORY_QUANTITY','VIEW_COST_PRICE','VIEW_ALL_STORES_INVENTORY','VIEW_ITEM_DETAILS',
        'VIEW_CREATED_BY','DEPARTMENT_MANAGEMENT','VENDOR_MANAGEMENT','VIEW_VENDOR',
        'VIEW_RECEIVING_HISTORY','MAINTENANCE_MANAGEMENT','MACHINE_MANAGEMENT','PARTS_MANAGEMENT',
        'CUSTOM_ORDER_ITEMS','CUSTOM_ORDER_PRINT_MATERIALS'
    ) THEN 'Inventory'
    WHEN UPPER(permission_key) IN ('TIME_CLOCK') THEN 'Employee'
    WHEN UPPER(permission_key) IN (
        'EMPLOYEES','ROLE_PERMISSIONS','COMPANY_PREFERENCES','LOCATION_MANAGEMENT','PAYROLL_DASHBOARD',
        'VIEW_REPORTS','CUSTOM_ORDER_DEPOSIT_SETTINGS','CUSTOM_ORDER_REFUND_APPROVAL_SETTINGS'
    ) THEN 'Admin'
    WHEN UPPER(permission_key) IN ('DEVICE_MANAGEMENT','DEVICE_RECEIPT_SETTINGS','HARDWARE_SETUP') THEN 'Device'
    WHEN UPPER(permission_key) IN ('CHANGE_STORE') THEN 'Operations'
    ELSE 'General'
END
WHERE permission_key IS NOT NULL
  AND TRIM(permission_key) <> '';
