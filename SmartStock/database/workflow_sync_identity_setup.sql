-- Stable sync identities for POS sales and custom order workflows.
-- Parent documents keep their human-readable numbers as the business key.
-- Child/history/audit rows get hidden UUID keys so local/cloud serial ids can differ safely.

CREATE EXTENSION IF NOT EXISTS pgcrypto;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM sales
        WHERE COALESCE(receipt_number, '') <> ''
        GROUP BY receipt_number
        HAVING COUNT(*) > 1
    ) THEN
        CREATE UNIQUE INDEX IF NOT EXISTS sales_receipt_number_uidx
        ON sales(receipt_number)
        WHERE COALESCE(receipt_number, '') <> '';
    END IF;
END
$$;

ALTER TABLE sale_items
ADD COLUMN IF NOT EXISTS sync_uuid UUID NOT NULL DEFAULT gen_random_uuid();

ALTER TABLE sale_returns
ADD COLUMN IF NOT EXISTS sync_uuid UUID NOT NULL DEFAULT gen_random_uuid();

ALTER TABLE sale_return_items
ADD COLUMN IF NOT EXISTS sync_uuid UUID NOT NULL DEFAULT gen_random_uuid();

ALTER TABLE sale_audit_log
ADD COLUMN IF NOT EXISTS sync_uuid UUID NOT NULL DEFAULT gen_random_uuid();

ALTER TABLE inventory_movements
ADD COLUMN IF NOT EXISTS sync_uuid UUID NOT NULL DEFAULT gen_random_uuid();

ALTER TABLE customer_account_transactions
ADD COLUMN IF NOT EXISTS sync_uuid UUID NOT NULL DEFAULT gen_random_uuid();

ALTER TABLE customer_account_payment_allocations
ADD COLUMN IF NOT EXISTS sync_uuid UUID NOT NULL DEFAULT gen_random_uuid();

ALTER TABLE custom_order_lines
ADD COLUMN IF NOT EXISTS sync_uuid UUID NOT NULL DEFAULT gen_random_uuid();

ALTER TABLE custom_order_line_print_addons
ADD COLUMN IF NOT EXISTS sync_uuid UUID NOT NULL DEFAULT gen_random_uuid();

ALTER TABLE custom_order_payments
ADD COLUMN IF NOT EXISTS sync_uuid UUID NOT NULL DEFAULT gen_random_uuid();

ALTER TABLE custom_order_inventory_reservations
ADD COLUMN IF NOT EXISTS sync_uuid UUID NOT NULL DEFAULT gen_random_uuid();

ALTER TABLE custom_order_status_history
ADD COLUMN IF NOT EXISTS sync_uuid UUID NOT NULL DEFAULT gen_random_uuid();

ALTER TABLE custom_order_line_deliveries
ADD COLUMN IF NOT EXISTS sync_uuid UUID NOT NULL DEFAULT gen_random_uuid();

ALTER TABLE custom_order_line_production_history
ADD COLUMN IF NOT EXISTS sync_uuid UUID NOT NULL DEFAULT gen_random_uuid();

ALTER TABLE custom_order_line_returns
ADD COLUMN IF NOT EXISTS sync_uuid UUID NOT NULL DEFAULT gen_random_uuid();

ALTER TABLE custom_order_item_movements
ADD COLUMN IF NOT EXISTS sync_uuid UUID NOT NULL DEFAULT gen_random_uuid();

ALTER TABLE custom_order_audit_log
ADD COLUMN IF NOT EXISTS sync_uuid UUID NOT NULL DEFAULT gen_random_uuid();

CREATE UNIQUE INDEX IF NOT EXISTS sale_items_sync_uuid_key ON sale_items(sync_uuid);
CREATE UNIQUE INDEX IF NOT EXISTS sale_returns_sync_uuid_key ON sale_returns(sync_uuid);
CREATE UNIQUE INDEX IF NOT EXISTS sale_return_items_sync_uuid_key ON sale_return_items(sync_uuid);
CREATE UNIQUE INDEX IF NOT EXISTS sale_audit_log_sync_uuid_key ON sale_audit_log(sync_uuid);
CREATE UNIQUE INDEX IF NOT EXISTS inventory_movements_sync_uuid_key ON inventory_movements(sync_uuid);
CREATE UNIQUE INDEX IF NOT EXISTS customer_account_transactions_sync_uuid_key ON customer_account_transactions(sync_uuid);
CREATE UNIQUE INDEX IF NOT EXISTS customer_account_payment_allocations_sync_uuid_key ON customer_account_payment_allocations(sync_uuid);
CREATE UNIQUE INDEX IF NOT EXISTS custom_order_lines_sync_uuid_key ON custom_order_lines(sync_uuid);
CREATE UNIQUE INDEX IF NOT EXISTS custom_order_line_print_addons_sync_uuid_key ON custom_order_line_print_addons(sync_uuid);
CREATE UNIQUE INDEX IF NOT EXISTS custom_order_payments_sync_uuid_key ON custom_order_payments(sync_uuid);
CREATE UNIQUE INDEX IF NOT EXISTS custom_order_inventory_reservations_sync_uuid_key ON custom_order_inventory_reservations(sync_uuid);
CREATE UNIQUE INDEX IF NOT EXISTS custom_order_status_history_sync_uuid_key ON custom_order_status_history(sync_uuid);
CREATE UNIQUE INDEX IF NOT EXISTS custom_order_line_deliveries_sync_uuid_key ON custom_order_line_deliveries(sync_uuid);
CREATE UNIQUE INDEX IF NOT EXISTS custom_order_line_production_history_sync_uuid_key ON custom_order_line_production_history(sync_uuid);
CREATE UNIQUE INDEX IF NOT EXISTS custom_order_line_returns_sync_uuid_key ON custom_order_line_returns(sync_uuid);
CREATE UNIQUE INDEX IF NOT EXISTS custom_order_item_movements_sync_uuid_key ON custom_order_item_movements(sync_uuid);
CREATE UNIQUE INDEX IF NOT EXISTS custom_order_audit_log_sync_uuid_key ON custom_order_audit_log(sync_uuid);
