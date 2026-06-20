-- Adds/updates detailed permission descriptions for desktop and app permissions.

ALTER TABLE permissions
    ADD COLUMN IF NOT EXISTS description TEXT;

ALTER TABLE permissions
    ADD COLUMN IF NOT EXISTS permission_subgroup TEXT;

UPDATE permissions p
SET description = v.description
FROM (VALUES
    ('MAKE_SALE', 'Allows creating and completing normal sales transactions.'),
    ('APPLY_SALE_DISCOUNT', 'Allows applying line and sale-level discounts without manager override.'),
    ('SALE_DISCOUNT_OVERRIDE', 'Allows approving discount overrides above configured limits.'),
    ('RETURN_OVERRIDE', 'Allows approving return amount overrides above configured limits.'),
    ('SALE_DISCOUNT_LIMIT_SETTINGS', 'Allows changing sale discount approval thresholds in company settings.'),
    ('SALE_RETURN_APPROVAL_SETTINGS', 'Allows changing return approval thresholds in company settings.'),
    ('CHANGE_SALE_ITEM_PRICE', 'Allows editing item unit prices during a sale without override.'),
    ('PROCESS_RETURNS', 'Allows creating and completing return transactions.'),
    ('END_OF_DAY', 'Allows access to sales reporting totals.'),
    ('BALANCE_DRAWER', 'Allows balancing drawer sessions, submitting counted cash totals, and receiving drawer-start notifications.'),
    ('BALANCE_SHEET', 'Allows viewing balance sheet totals and logging business expenses.'),
    ('VIEW_SALES', 'Allows viewing past sales and related transaction history.'),
    ('NEW_ITEM', 'Allows creating new inventory items.'),
    ('EDIT_ITEM', 'Allows editing existing inventory item details.'),
    ('RECEIVING_INVENTORY', 'Allows receiving stock into inventory quantities.'),
    ('RECEIVING_STOCK_OVERRIDE', 'Allows correcting counted shelf/storage stock during receiving with an audit trail.'),
    ('VIEW_RECEIVING_HISTORY', 'Allows viewing historical receiving records.'),
    ('STORE_TRANSFER', 'Allows sending and receiving inventory store transfers.'),
    ('VIEW_INVENTORY', 'Allows viewing the inventory list and stock levels.'),
    ('INVENTORY_STOCK_NOTIFICATIONS', 'Allows receiving low-stock and out-of-stock notifications for inventory and custom-order items.'),
    ('VIEW_ITEM_DETAILS', 'Allows opening full item detail records.'),
    ('VIEW_COST_PRICE', 'Allows viewing internal item cost prices.'),
    ('VIEW_VENDOR', 'Allows viewing vendor assignments on items.'),
    ('VIEW_CREATED_BY', 'Allows viewing item creation and ownership metadata.'),
    ('MANUAL_ADJUSTMENT', 'Allows manual quantity adjustments outside normal receiving/transfer flows.'),
    ('DEPARTMENT_MANAGEMENT', 'Allows creating and managing item departments.'),
    ('VENDOR_MANAGEMENT', 'Allows creating and managing vendors.'),
    ('CUSTOMER_ACCOUNTS', 'Allows using customer account balances, credits, and history.'),
    ('CREATE_CUSTOM_ORDER', 'Allows creating new custom orders.'),
    ('MANAGE_CUSTOM_ORDERS', 'Allows full management access across all custom orders.'),
    ('VIEW_ASSIGNED_CUSTOM_ORDERS', 'Allows viewing custom orders assigned to the logged-in user.'),
    ('ORDERS_MANAGER_DASHBOARD', 'Allows access to manager-level custom order dashboard tools.'),
    ('CUSTOM_ORDER_WORK_NOTIFICATIONS', 'Allows receiving operational notifications for due, overdue, ready, unassigned, and balance-due custom orders.'),
    ('CUSTOM_ORDER_EXCEPTION_NOTIFICATIONS', 'Allows receiving custom-order exception notifications such as recent refunds.'),
    ('ORDERS_END_OF_DAY', 'Allows access to custom-order reporting totals.'),
    ('CUSTOM_ORDER_REFUNDS', 'Allows issuing refunds on custom orders.'),
    ('CUSTOM_ORDER_LINE_RETURNS', 'Allows returning individual custom-order lines.'),
    ('CUSTOM_ORDER_LINE_DELIVERY', 'Allows marking custom-order lines as delivered.'),
    ('CUSTOM_ORDER_LINE_DISCOUNT', 'Allows discounting custom-order lines without override.'),
    ('CUSTOM_ORDER_DEPOSIT_OVERRIDE', 'Allows overriding required custom-order deposit amounts.'),
    ('CUSTOM_ORDER_DEPOSIT_SETTINGS', 'Allows editing custom-order minimum deposit settings.'),
    ('CUSTOM_ORDER_REFUND_APPROVAL', 'Allows approving high-value custom-order refunds.'),
    ('CUSTOM_ORDER_REFUND_APPROVAL_SETTINGS', 'Allows editing custom-order refund approval limits.'),
    ('CUSTOM_ORDER_PRODUCTION_STEPS', 'Allows updating production workflow states for custom-order lines.'),
    ('CUSTOM_ORDER_CANCEL', 'Allows canceling custom orders.'),
    ('SET_CREDIT_LIMIT', 'Allows setting customer credit limits.'),
    ('EDIT_ACCOUNT_NUMBER', 'Allows changing customer account numbers.'),
    ('EMPLOYEE_MANAGEMENT', 'Allows creating and managing employee records.'),
    ('TIME_CLOCK', 'Allows clock-in/clock-out actions.'),
    ('TIME_CLOCK_MANAGEMENT', 'Allows viewing and correcting staff time clock records.'),
    ('TIME_CLOCK_OVERRIDE', 'Allows approving additional employee time clock sessions after a completed session on the same day.'),
    ('PAYROLL_DASHBOARD', 'Allows viewing payroll and labor summary dashboards.'),
    ('VIEW_EMPLOYEE_SCHEDULE', 'Allows viewing who is scheduled to work each day.'),
    ('EDIT_EMPLOYEE_SCHEDULE', 'Allows adding and removing employees from the weekly schedule.'),
    ('ROLE_MANAGEMENT', 'Allows editing role definitions and assigning permissions.'),
    ('LOCATION_MANAGEMENT', 'Allows creating and editing store locations.'),
    ('CASH_DRAWER_MANAGEMENT', 'Allows configuring cash drawer workflows and sessions.'),
    ('COMPANY_PREFERENCES', 'Allows editing company-wide operational preferences.'),
    ('CHANGE_STORE', 'Allows switching the active store context.'),
    ('VIEW_REPORTS', 'Allows opening reporting screens and exports.'),
    ('SYNC_NOTIFICATIONS', 'Allows receiving sync health notifications for offline cloud, failed events, conflicts, and backlogs.'),
    ('LOCAL_DEVICE_SETTINGS', 'Allows changing device-specific app/receipt settings.'),
    ('HARDWARE_SETUP', 'Allows configuring scanner, printer, and hardware integration settings.')
) AS v(permission_key, description)
WHERE UPPER(p.permission_key) = UPPER(v.permission_key)
  AND (p.description IS NULL OR TRIM(p.description) = '');

-- Ensure app permissions table has a description column as well.
ALTER TABLE mobile_permissions
    ADD COLUMN IF NOT EXISTS description TEXT;

ALTER TABLE mobile_permissions
    ADD COLUMN IF NOT EXISTS permission_subgroup TEXT;
