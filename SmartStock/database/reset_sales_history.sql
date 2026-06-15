BEGIN;

ALTER TABLE sale_items DISABLE TRIGGER USER;
ALTER TABLE sale_returns DISABLE TRIGGER USER;
ALTER TABLE sale_return_items DISABLE TRIGGER USER;
ALTER TABLE sales DISABLE TRIGGER USER;

DELETE FROM customer_account_payment_allocations
WHERE sale_id IS NOT NULL
   OR payment_transaction_id IN (
        SELECT transaction_id
        FROM customer_account_transactions
        WHERE sale_id IS NOT NULL
           OR COALESCE(transaction_type, '') IN ('SALE_CREDIT', 'SALE_PAID', 'SALE_RETURN', 'ACCOUNT_RETURN_APPLIED')
   );

DELETE FROM customer_account_transactions
WHERE sale_id IS NOT NULL
   OR COALESCE(transaction_type, '') IN ('SALE_CREDIT', 'SALE_PAID', 'SALE_RETURN', 'ACCOUNT_RETURN_APPLIED');

DELETE FROM inventory_movements
WHERE sale_id IS NOT NULL
   OR sale_item_id IS NOT NULL
   OR sale_return_id IS NOT NULL
   OR COALESCE(reason, '') IN ('SALE', 'RETURN_INVENTORY_RESTOCKED');

DELETE FROM sale_audit_log
WHERE sale_id IS NOT NULL
   OR sale_item_id IS NOT NULL
   OR return_id IS NOT NULL
   OR return_item_id IS NOT NULL;

DELETE FROM sale_return_items;
DELETE FROM sale_returns;
DELETE FROM sale_items;
DELETE FROM sales;

ALTER TABLE sale_items ENABLE TRIGGER USER;
ALTER TABLE sale_returns ENABLE TRIGGER USER;
ALTER TABLE sale_return_items ENABLE TRIGGER USER;
ALTER TABLE sales ENABLE TRIGGER USER;

DELETE FROM sale_audit_log
WHERE sale_id IS NOT NULL
   OR sale_item_id IS NOT NULL
   OR return_id IS NOT NULL
   OR return_item_id IS NOT NULL;

SELECT setval(pg_get_serial_sequence('sales', 'sale_id'), 1, false);
SELECT setval(pg_get_serial_sequence('sale_items', 'sale_item_id'), 1, false);
SELECT setval(pg_get_serial_sequence('sale_returns', 'return_id'), 1, false);
SELECT setval(pg_get_serial_sequence('sale_return_items', 'return_item_id'), 1, false);
SELECT setval(pg_get_serial_sequence('sale_audit_log', 'sale_audit_id'), 1, false);
SELECT setval(pg_get_serial_sequence('inventory_movements', 'movement_id'), COALESCE((SELECT MAX(movement_id) FROM inventory_movements), 1), COALESCE((SELECT MAX(movement_id) FROM inventory_movements), 0) > 0);
SELECT setval(pg_get_serial_sequence('customer_account_transactions', 'transaction_id'), COALESCE((SELECT MAX(transaction_id) FROM customer_account_transactions), 1), COALESCE((SELECT MAX(transaction_id) FROM customer_account_transactions), 0) > 0);
SELECT setval(pg_get_serial_sequence('customer_account_payment_allocations', 'allocation_id'), COALESCE((SELECT MAX(allocation_id) FROM customer_account_payment_allocations), 1), COALESCE((SELECT MAX(allocation_id) FROM customer_account_payment_allocations), 0) > 0);

UPDATE customer_accounts ca
SET current_balance = COALESCE((
    SELECT SUM(t.amount)
    FROM customer_account_transactions t
    WHERE t.customer_id = ca.customer_id
), 0);

UPDATE company_customization
SET next_receipt_counter = 1,
    updated_at = CURRENT_TIMESTAMP;

DELETE FROM sync_id_map WHERE table_name IN ('sales','sale_items','sale_returns','sale_return_items');
DELETE FROM sync_outbox WHERE event_type IN ('SALE_COMPLETED','SALE_RETURN_CREATED');
DELETE FROM sync_applied_events WHERE event_type IN ('SALE_COMPLETED','SALE_RETURN_CREATED');

COMMIT;

SELECT 'sales' AS table_name, COUNT(*) AS rows FROM sales
UNION ALL SELECT 'sale_items', COUNT(*) FROM sale_items
UNION ALL SELECT 'sale_returns', COUNT(*) FROM sale_returns
UNION ALL SELECT 'sale_return_items', COUNT(*) FROM sale_return_items
UNION ALL SELECT 'sale_audit_log', COUNT(*) FROM sale_audit_log
UNION ALL SELECT 'sale_linked_inventory_movements', COUNT(*) FROM inventory_movements WHERE sale_id IS NOT NULL OR sale_item_id IS NOT NULL OR sale_return_id IS NOT NULL
UNION ALL SELECT 'sale_linked_account_transactions', COUNT(*) FROM customer_account_transactions WHERE sale_id IS NOT NULL
UNION ALL SELECT 'sale_linked_payment_allocations', COUNT(*) FROM customer_account_payment_allocations WHERE sale_id IS NOT NULL;
