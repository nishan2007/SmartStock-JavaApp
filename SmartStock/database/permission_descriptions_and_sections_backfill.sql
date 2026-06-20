-- Backfill permission descriptions and section/group labels for desktop + app permissions.

ALTER TABLE permissions
    ADD COLUMN IF NOT EXISTS description TEXT;

ALTER TABLE permissions
    ADD COLUMN IF NOT EXISTS permission_group TEXT;

ALTER TABLE permissions
    ADD COLUMN IF NOT EXISTS permission_subgroup TEXT;

ALTER TABLE mobile_permissions
    ADD COLUMN IF NOT EXISTS description TEXT;

ALTER TABLE mobile_permissions
    ADD COLUMN IF NOT EXISTS permission_group TEXT;

ALTER TABLE mobile_permissions
    ADD COLUMN IF NOT EXISTS permission_subgroup TEXT;

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
    ('SYNC_NOTIFICATIONS', 'Allows receiving sync health notifications for offline cloud, failed events, conflicts, and backlogs.'),
    ('VIEW_EMPLOYEE_SCHEDULE', 'Allows viewing who is scheduled to work each day.'),
    ('EDIT_EMPLOYEE_SCHEDULE', 'Allows adding and removing employees from the weekly schedule.')
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
        'PROCESS_RETURNS','VIEW_SALES'
    ) THEN 'Sales'
    WHEN UPPER(permission_key) IN (
        'QUOTATIONS_ORDERS','CREATE_QUOTATION','MANAGE_INVOICES','POST_INVOICE_DELIVERY',
        'SALES_QUOTES_ORDERS','CREATE_SALES_QUOTE','MANAGE_SALES_ORDERS','POST_SALES_ORDER_DELIVERY'
    ) THEN 'Quotations & Invoices'
    WHEN UPPER(permission_key) IN (
        'CREATE_CUSTOM_ORDER','MANAGE_CUSTOM_ORDERS','VIEW_ASSIGNED_CUSTOM_ORDERS',
        'ORDERS_MANAGER_DASHBOARD','CUSTOM_ORDER_WORK_NOTIFICATIONS','CUSTOM_ORDER_EXCEPTION_NOTIFICATIONS',
        'ORDERS_END_OF_DAY','CUSTOM_ORDER_REFUNDS',
        'CUSTOM_ORDER_LINE_RETURNS','CUSTOM_ORDER_LINE_DELIVERY','CUSTOM_ORDER_LINE_DISCOUNT',
        'CUSTOM_ORDER_DEPOSIT_OVERRIDE','CUSTOM_ORDER_DEPOSIT_SETTINGS',
        'CUSTOM_ORDER_REFUND_APPROVAL','CUSTOM_ORDER_REFUND_APPROVAL_SETTINGS',
        'CUSTOM_ORDER_PRODUCTION_STEPS','CUSTOM_ORDER_CANCEL','CUSTOM_ORDER_OVERRIDES',
        'CUSTOM_ORDER_ITEMS','CUSTOM_ORDER_PRINT_MATERIALS'
    ) THEN 'Custom Orders'
    WHEN UPPER(permission_key) IN (
        'NEW_ITEM','EDIT_ITEM','RECEIVING_INVENTORY','RECEIVING_STOCK_OVERRIDE','VIEW_RECEIVING_HISTORY','STORE_TRANSFER',
        'VIEW_INVENTORY','INVENTORY_STOCK_NOTIFICATIONS','VIEW_ITEM_DETAILS','VIEW_COST_PRICE','VIEW_VENDOR','VIEW_CREATED_BY',
        'MANUAL_ADJUSTMENT','DEPARTMENT_MANAGEMENT','VENDOR_MANAGEMENT'
    ) THEN 'Inventory'
    WHEN UPPER(permission_key) IN (
        'MAINTENANCE_MANAGEMENT','MACHINE_MANAGEMENT','PARTS_MANAGEMENT','MAINTENANCE_TECHNICIAN'
    ) THEN 'Maintenance'
    WHEN UPPER(permission_key) IN (
        'EMPLOYEE_MANAGEMENT','TIME_CLOCK','TIME_CLOCK_MANAGEMENT','PAYROLL_DASHBOARD',
        'VIEW_EMPLOYEE_SCHEDULE','EDIT_EMPLOYEE_SCHEDULE'
    ) THEN 'People'
    WHEN UPPER(permission_key) IN (
        'CUSTOMER_ACCOUNTS','SET_CREDIT_LIMIT','EDIT_ACCOUNT_NUMBER'
    ) THEN 'Customers'
    WHEN UPPER(permission_key) IN (
        'END_OF_DAY','BALANCE_DRAWER','BALANCE_SHEET','CASH_DRAWER_MANAGEMENT','CHANGE_STORE',
        'VIEW_REPORTS','SYNC_NOTIFICATIONS','LOCAL_DEVICE_SETTINGS','HARDWARE_SETUP','APP_UPDATES'
    ) THEN 'Operations'
    WHEN UPPER(permission_key) IN (
        'ROLE_MANAGEMENT','LOCATION_MANAGEMENT','COMPANY_PREFERENCES','DEVICE_MANAGEMENT'
    ) THEN 'Administration'
    ELSE COALESCE(NULLIF(permission_group, ''), 'General')
END
WHERE permission_key IS NOT NULL
  AND TRIM(permission_key) <> '';

UPDATE permissions p
SET permission_group = v.permission_group
FROM (VALUES
    ('BALANCE_DRAWER', 'Operations'),
    ('BALANCE_SHEET', 'Operations'),
    ('DEVICE_MANAGEMENT', 'Administration'),
    ('CUSTOM_ORDER_OVERRIDES', 'Custom Orders'),
    ('CUSTOM_ORDER_ITEMS', 'Custom Orders'),
    ('CUSTOM_ORDER_PRINT_MATERIALS', 'Custom Orders'),
    ('MAINTENANCE_MANAGEMENT', 'Maintenance'),
    ('MACHINE_MANAGEMENT', 'Maintenance'),
    ('PARTS_MANAGEMENT', 'Maintenance'),
    ('MAINTENANCE_TECHNICIAN', 'Maintenance'),
    ('INVENTORY_STOCK_NOTIFICATIONS', 'Inventory'),
    ('CUSTOM_ORDER_WORK_NOTIFICATIONS', 'Custom Orders'),
    ('CUSTOM_ORDER_EXCEPTION_NOTIFICATIONS', 'Custom Orders'),
    ('SYNC_NOTIFICATIONS', 'Operations'),
    ('APP_UPDATES', 'Operations')
    ,('VIEW_EMPLOYEE_SCHEDULE', 'People')
    ,('EDIT_EMPLOYEE_SCHEDULE', 'People')
) AS v(permission_key, permission_group)
WHERE UPPER(p.permission_key) = UPPER(v.permission_key);

UPDATE permissions p
SET permission_subgroup = v.permission_subgroup
FROM (VALUES
    ('MAKE_SALE', 'Checkout'),
    ('VIEW_SALES', 'Sales History'),
    ('PROCESS_RETURNS', 'Returns'),
    ('APPLY_SALE_DISCOUNT', 'Discounts'),
    ('CHANGE_SALE_ITEM_PRICE', 'Discounts'),
    ('SALE_DISCOUNT_OVERRIDE', 'Overrides'),
    ('RETURN_OVERRIDE', 'Overrides'),
    ('SALE_DISCOUNT_LIMIT_SETTINGS', 'Settings'),
    ('SALE_RETURN_APPROVAL_SETTINGS', 'Settings'),
    ('QUOTATIONS_ORDERS', 'General'),
    ('CREATE_QUOTATION', 'General'),
    ('MANAGE_INVOICES', 'General'),
    ('POST_INVOICE_DELIVERY', 'General'),
    ('SALES_QUOTES_ORDERS', 'General'),
    ('CREATE_SALES_QUOTE', 'General'),
    ('MANAGE_SALES_ORDERS', 'General'),
    ('POST_SALES_ORDER_DELIVERY', 'General'),
    ('CREATE_CUSTOM_ORDER', 'Order Access'),
    ('MANAGE_CUSTOM_ORDERS', 'Order Access'),
    ('VIEW_ASSIGNED_CUSTOM_ORDERS', 'Order Access'),
    ('ORDERS_MANAGER_DASHBOARD', 'Management'),
    ('ORDERS_END_OF_DAY', 'Reports'),
    ('CUSTOM_ORDER_WORK_NOTIFICATIONS', 'Notifications'),
    ('CUSTOM_ORDER_EXCEPTION_NOTIFICATIONS', 'Notifications'),
    ('CUSTOM_ORDER_PRODUCTION_STEPS', 'Workflow'),
    ('CUSTOM_ORDER_LINE_DELIVERY', 'Workflow'),
    ('CUSTOM_ORDER_REFUNDS', 'Refunds & Returns'),
    ('CUSTOM_ORDER_LINE_RETURNS', 'Refunds & Returns'),
    ('CUSTOM_ORDER_CANCEL', 'Refunds & Returns'),
    ('CUSTOM_ORDER_LINE_DISCOUNT', 'Pricing & Deposits'),
    ('CUSTOM_ORDER_DEPOSIT_OVERRIDE', 'Pricing & Deposits'),
    ('CUSTOM_ORDER_DEPOSIT_SETTINGS', 'Settings'),
    ('CUSTOM_ORDER_REFUND_APPROVAL', 'Approvals'),
    ('CUSTOM_ORDER_REFUND_APPROVAL_SETTINGS', 'Settings'),
    ('CUSTOM_ORDER_OVERRIDES', 'Approvals'),
    ('CUSTOM_ORDER_ITEMS', 'Order Items'),
    ('CUSTOM_ORDER_PRINT_MATERIALS', 'Order Items'),
    ('NEW_ITEM', 'Item Maintenance'),
    ('EDIT_ITEM', 'Item Maintenance'),
    ('VIEW_INVENTORY', 'Item Visibility'),
    ('VIEW_ITEM_DETAILS', 'Item Visibility'),
    ('VIEW_COST_PRICE', 'Sensitive Fields'),
    ('VIEW_VENDOR', 'Sensitive Fields'),
    ('VIEW_CREATED_BY', 'Sensitive Fields'),
    ('RECEIVING_INVENTORY', 'Receiving'),
    ('RECEIVING_STOCK_OVERRIDE', 'Receiving'),
    ('VIEW_RECEIVING_HISTORY', 'Receiving'),
    ('STORE_TRANSFER', 'Transfers'),
    ('MANUAL_ADJUSTMENT', 'Adjustments'),
    ('DEPARTMENT_MANAGEMENT', 'Setup'),
    ('VENDOR_MANAGEMENT', 'Setup'),
    ('INVENTORY_STOCK_NOTIFICATIONS', 'Notifications'),
    ('MAINTENANCE_MANAGEMENT', 'General'),
    ('MACHINE_MANAGEMENT', 'General'),
    ('PARTS_MANAGEMENT', 'General'),
    ('MAINTENANCE_TECHNICIAN', 'General'),
    ('CUSTOMER_ACCOUNTS', 'Accounts'),
    ('SET_CREDIT_LIMIT', 'Credit Controls'),
    ('EDIT_ACCOUNT_NUMBER', 'Account Controls'),
    ('END_OF_DAY', 'Closeout'),
    ('BALANCE_DRAWER', 'Cash Drawer'),
    ('BALANCE_SHEET', 'Cash Drawer'),
    ('CASH_DRAWER_MANAGEMENT', 'Cash Drawer'),
    ('CHANGE_STORE', 'Store Context'),
    ('VIEW_REPORTS', 'Reports'),
    ('SYNC_NOTIFICATIONS', 'Sync'),
    ('LOCAL_DEVICE_SETTINGS', 'Device & Hardware'),
    ('HARDWARE_SETUP', 'Device & Hardware'),
    ('APP_UPDATES', 'App Updates'),
    ('EMPLOYEE_MANAGEMENT', 'Employees'),
    ('TIME_CLOCK', 'Time Clock'),
    ('TIME_CLOCK_MANAGEMENT', 'Time Clock'),
    ('TIME_CLOCK_OVERRIDE', 'Time Clock'),
    ('PAYROLL_DASHBOARD', 'Payroll'),
    ('VIEW_EMPLOYEE_SCHEDULE', 'Scheduling'),
    ('EDIT_EMPLOYEE_SCHEDULE', 'Scheduling'),
    ('ROLE_MANAGEMENT', 'Roles & Security'),
    ('LOCATION_MANAGEMENT', 'Locations'),
    ('COMPANY_PREFERENCES', 'Company Setup'),
    ('DEVICE_MANAGEMENT', 'Devices')
) AS v(permission_key, permission_subgroup)
WHERE UPPER(p.permission_key) = UPPER(v.permission_key);

-- Fill app permission sections when missing.
UPDATE mobile_permissions
SET permission_group = CASE
    WHEN UPPER(permission_key) IN (
        'MAKE_SALE','VIEW_SALES','RETURNS','APPLY_SALE_DISCOUNT','CHANGE_SALE_ITEM_PRICE',
        'VIEW_SALE_AUDIT','EXPORT_SALE_AUDIT'
    ) THEN 'Sales'
    WHEN UPPER(permission_key) IN (
        'CREATE_CUSTOM_ORDER','MANAGE_CUSTOM_ORDERS','VIEW_ASSIGNED_CUSTOM_ORDERS',
        'ORDERS_MANAGER_DASHBOARD','ORDERS_END_OF_DAY','CUSTOM_ORDER_REFUNDS',
        'CUSTOM_ORDER_LINE_RETURNS','CUSTOM_ORDER_LINE_DELIVERY','CUSTOM_ORDER_LINE_DISCOUNT',
        'CUSTOM_ORDER_DEPOSIT_OVERRIDE','CUSTOM_ORDER_REFUND_APPROVAL',
        'CUSTOM_ORDER_PRODUCTION_STEPS','CUSTOM_ORDER_CANCEL','CUSTOM_ORDER_OVERRIDES',
        'CUSTOM_ORDER_DEPOSIT_SETTINGS','CUSTOM_ORDER_REFUND_APPROVAL_SETTINGS',
        'CUSTOM_ORDER_ITEMS','CUSTOM_ORDER_PRINT_MATERIALS'
    ) THEN 'Custom Orders'
    WHEN UPPER(permission_key) IN (
        'INVENTORY','RECEIVING','STORE_TRANSFER','VERIFY_STORE_TRANSFER_QUANTITY','EDIT_ITEM','NEW_ITEM',
        'ADJUST_INVENTORY_QUANTITY','VIEW_COST_PRICE','VIEW_ALL_STORES_INVENTORY','VIEW_ITEM_DETAILS',
        'VIEW_CREATED_BY','DEPARTMENT_MANAGEMENT','VENDOR_MANAGEMENT','VIEW_VENDOR',
        'VIEW_RECEIVING_HISTORY'
    ) THEN 'Inventory'
    WHEN UPPER(permission_key) IN (
        'MAINTENANCE_MANAGEMENT','MACHINE_MANAGEMENT','PARTS_MANAGEMENT','MAINTENANCE_TECHNICIAN'
    ) THEN 'Maintenance'
    WHEN UPPER(permission_key) IN ('CUSTOMERS','MANAGE_CUSTOMERS','EDIT_CUSTOMER_CREDIT_LIMIT','EDIT_ACCOUNT_NUMBER') THEN 'Customers'
    WHEN UPPER(permission_key) IN ('END_OF_DAY','CASH_DRAWER_MANAGEMENT','CHANGE_STORE','DEVICE_RECEIPT_SETTINGS','HARDWARE_SETUP','VIEW_REPORTS') THEN 'Operations'
    WHEN UPPER(permission_key) IN ('TIME_CLOCK','PAYROLL_DASHBOARD') THEN 'People'
    WHEN UPPER(permission_key) IN (
        'EMPLOYEES','ROLE_PERMISSIONS','COMPANY_PREFERENCES','LOCATION_MANAGEMENT'
    ) THEN 'Administration'
    WHEN UPPER(permission_key) IN ('DEVICE_MANAGEMENT') THEN 'Administration'
    ELSE 'General'
END
WHERE permission_key IS NOT NULL
  AND TRIM(permission_key) <> '';

UPDATE mobile_permissions m
SET permission_subgroup = v.permission_subgroup
FROM (VALUES
    ('MAKE_SALE', 'Checkout'),
    ('VIEW_SALES', 'Sales History'),
    ('RETURNS', 'Returns'),
    ('APPLY_SALE_DISCOUNT', 'Discounts'),
    ('CHANGE_SALE_ITEM_PRICE', 'Discounts'),
    ('VIEW_SALE_AUDIT', 'Sales History'),
    ('EXPORT_SALE_AUDIT', 'Sales History'),
    ('CREATE_CUSTOM_ORDER', 'Order Access'),
    ('MANAGE_CUSTOM_ORDERS', 'Order Access'),
    ('VIEW_ASSIGNED_CUSTOM_ORDERS', 'Order Access'),
    ('ORDERS_MANAGER_DASHBOARD', 'Management'),
    ('ORDERS_END_OF_DAY', 'Reports'),
    ('CUSTOM_ORDER_REFUNDS', 'Refunds & Returns'),
    ('CUSTOM_ORDER_LINE_RETURNS', 'Refunds & Returns'),
    ('CUSTOM_ORDER_LINE_DELIVERY', 'Workflow'),
    ('CUSTOM_ORDER_LINE_DISCOUNT', 'Pricing & Deposits'),
    ('CUSTOM_ORDER_DEPOSIT_OVERRIDE', 'Pricing & Deposits'),
    ('CUSTOM_ORDER_REFUND_APPROVAL', 'Approvals'),
    ('CUSTOM_ORDER_PRODUCTION_STEPS', 'Workflow'),
    ('CUSTOM_ORDER_CANCEL', 'Refunds & Returns'),
    ('CUSTOM_ORDER_OVERRIDES', 'Approvals'),
    ('CUSTOM_ORDER_DEPOSIT_SETTINGS', 'Settings'),
    ('CUSTOM_ORDER_REFUND_APPROVAL_SETTINGS', 'Settings'),
    ('INVENTORY', 'Item Visibility'),
    ('RECEIVING', 'Receiving'),
    ('STORE_TRANSFER', 'Transfers'),
    ('VERIFY_STORE_TRANSFER_QUANTITY', 'Transfers'),
    ('EDIT_ITEM', 'Item Maintenance'),
    ('NEW_ITEM', 'Item Maintenance'),
    ('ADJUST_INVENTORY_QUANTITY', 'Adjustments'),
    ('VIEW_COST_PRICE', 'Sensitive Fields'),
    ('VIEW_ALL_STORES_INVENTORY', 'Item Visibility'),
    ('VIEW_ITEM_DETAILS', 'Item Visibility'),
    ('VIEW_CREATED_BY', 'Sensitive Fields'),
    ('DEPARTMENT_MANAGEMENT', 'Setup'),
    ('VENDOR_MANAGEMENT', 'Setup'),
    ('VIEW_VENDOR', 'Sensitive Fields'),
    ('VIEW_RECEIVING_HISTORY', 'Receiving'),
    ('MAINTENANCE_MANAGEMENT', 'General'),
    ('MACHINE_MANAGEMENT', 'General'),
    ('PARTS_MANAGEMENT', 'General'),
    ('MAINTENANCE_TECHNICIAN', 'General'),
    ('CUSTOM_ORDER_ITEMS', 'Order Items'),
    ('CUSTOM_ORDER_PRINT_MATERIALS', 'Order Items'),
    ('CUSTOMERS', 'Accounts'),
    ('MANAGE_CUSTOMERS', 'Accounts'),
    ('EDIT_CUSTOMER_CREDIT_LIMIT', 'Credit Controls'),
    ('EDIT_ACCOUNT_NUMBER', 'Account Controls'),
    ('END_OF_DAY', 'Closeout'),
    ('CASH_DRAWER_MANAGEMENT', 'Cash Drawer'),
    ('CHANGE_STORE', 'Store Context'),
    ('DEVICE_RECEIPT_SETTINGS', 'Device & Hardware'),
    ('HARDWARE_SETUP', 'Device & Hardware'),
    ('TIME_CLOCK', 'Time Clock'),
    ('EMPLOYEES', 'Employees'),
    ('ROLE_PERMISSIONS', 'Roles & Security'),
    ('COMPANY_PREFERENCES', 'Company Setup'),
    ('LOCATION_MANAGEMENT', 'Locations'),
    ('PAYROLL_DASHBOARD', 'Payroll'),
    ('VIEW_REPORTS', 'Reports'),
    ('DEVICE_MANAGEMENT', 'Devices')
) AS v(permission_key, permission_subgroup)
WHERE UPPER(m.permission_key) = UPPER(v.permission_key);
