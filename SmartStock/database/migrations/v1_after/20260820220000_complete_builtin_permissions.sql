-- The original v1 seed was captured from a database whose legacy core
-- permission rows were missing.  Those keys are still enforced by the LAN API
-- and drive main-menu visibility, so clean installs otherwise create an ADMIN
-- account with only a partial application menu.
-- Existing stores may already have allocated IDs above the captured v1 range.
-- Advance the sequence first and let PostgreSQL allocate IDs so this repair
-- cannot overwrite or collide with unrelated permissions.
SELECT pg_catalog.setval(
    pg_get_serial_sequence('public.permissions', 'permission_id'),
    GREATEST((SELECT MAX(permission_id) FROM public.permissions), 1),
    TRUE
);

INSERT INTO public.permissions (
    permission_key, permission_name, description,
    permission_group, permission_subgroup, created_at
)
VALUES
    ('MAKE_SALE', 'Make Sale', 'Allows creating and completing sales transactions.', 'Point of Sale', 'Checkout', CURRENT_TIMESTAMP),
    ('ADD_MISC_SALE_ITEM', 'Add Misc Sale Item', 'Allows adding an arbitrary named and priced non-inventory item to a sale.', 'Point of Sale', 'Checkout', CURRENT_TIMESTAMP),
    ('VIEW_SALES', 'View Sales', 'Allows viewing sales history and transaction details.', 'Point of Sale', 'Sales History', CURRENT_TIMESTAMP),
    ('VIEW_INVENTORY', 'View Inventory', 'Allows viewing item and stock information.', 'Inventory', 'Item Visibility', CURRENT_TIMESTAMP),
    ('NEW_ITEM', 'New Item', 'Allows creating inventory items.', 'Inventory', 'Items', CURRENT_TIMESTAMP),
    ('EDIT_ITEM', 'Edit Item', 'Allows editing inventory items.', 'Inventory', 'Items', CURRENT_TIMESTAMP),
    ('PRODUCT_ARCHIVE', 'Archive and Restore Products', 'Allows globally archiving and restoring products.', 'Inventory', 'Items', CURRENT_TIMESTAMP),
    ('RECEIVING_INVENTORY', 'Receive Inventory', 'Allows receiving stock into inventory.', 'Inventory', 'Receiving', CURRENT_TIMESTAMP),
    ('VIEW_RECEIVING_HISTORY', 'View Receiving History', 'Allows viewing completed inventory receiving activity.', 'Inventory', 'Receiving', CURRENT_TIMESTAMP),
    ('CUSTOMER_ACCOUNTS', 'Customer Accounts', 'Allows viewing and managing customer accounts.', 'Customers', 'Accounts', CURRENT_TIMESTAMP),
    ('SET_CREDIT_LIMIT', 'Set Credit Limit', 'Allows setting and overriding customer credit limits.', 'Customers', 'Accounts', CURRENT_TIMESTAMP),
    ('EDIT_ACCOUNT_NUMBER', 'Edit Account Number', 'Allows editing customer account numbers.', 'Customers', 'Accounts', CURRENT_TIMESTAMP),
    ('VIEW_ITEM_DETAILS', 'View Item Details', 'Allows viewing full item details.', 'Inventory', 'Item Visibility', CURRENT_TIMESTAMP),
    ('EMPLOYEE_MANAGEMENT', 'Employee Management', 'Allows managing employee records.', 'People', 'Employees', CURRENT_TIMESTAMP),
    ('ROLE_MANAGEMENT', 'Roles and Permissions', 'Allows managing roles and permission assignments.', 'Administration', 'Security', CURRENT_TIMESTAMP),
    ('VIEW_REPORTS', 'View Reports', 'Allows access to operational reports.', 'Operations', 'Reports', CURRENT_TIMESTAMP),
    ('CHANGE_STORE', 'Change Store', 'Allows changing the active store where supported.', 'Administration', 'Locations', CURRENT_TIMESTAMP),
    ('COMPANY_CUSTOMIZATION', 'Company Customization', 'Allows editing company branding and customization.', 'Administration', 'Company Setup', CURRENT_TIMESTAMP),
    ('LOCAL_DEVICE_SETTINGS', 'Local Device Settings', 'Allows editing workstation-specific settings.', 'Operations', 'Device & Hardware', CURRENT_TIMESTAMP),
    ('CUSTOM_ORDER_PRICE_OVERRIDE', 'Custom Order Price Override', 'Allows overriding configured custom-item prices.', 'Custom Orders', 'Approvals', CURRENT_TIMESTAMP),
    ('CUSTOM_ORDER_ITEMS', 'Custom Order Items', 'Allows managing the custom-order item catalog.', 'Custom Orders', 'Order Items', CURRENT_TIMESTAMP),
    ('MANAGE_CUSTOM_ORDER_ITEMS', 'Manage Custom Order Items', 'Allows managing the custom-order item catalog.', 'Custom Orders', 'Order Items', CURRENT_TIMESTAMP),
    ('ADVANCED_RETURN_LOOKUP', 'Advanced Return Lookup', 'Allows finding receipts by sale date and item name, SKU, or barcode.', 'Sales', 'Returns', CURRENT_TIMESTAMP)
ON CONFLICT (permission_key) DO UPDATE SET
    permission_name = EXCLUDED.permission_name,
    description = EXCLUDED.description,
    permission_group = EXCLUDED.permission_group,
    permission_subgroup = EXCLUDED.permission_subgroup;

-- ADMIN is the break-glass built-in application administrator.  Keep this
-- server-side and include both existing and future catalog entries present
-- when the migration is applied.
INSERT INTO public.role_permissions (role_id, permission_id, updated_at)
SELECT role.role_id, permission.permission_id, CURRENT_TIMESTAMP
FROM public.roles role
CROSS JOIN public.permissions permission
WHERE UPPER(role.role_name) = 'ADMIN'
ON CONFLICT (role_id, permission_id) DO NOTHING;
