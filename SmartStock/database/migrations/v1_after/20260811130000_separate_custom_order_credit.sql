ALTER TABLE customer_account_transactions ADD COLUMN IF NOT EXISTS credit_applied_amount numeric(12,2) NOT NULL DEFAULT 0;
ALTER TABLE sync_cross_store_customer_history_cache ADD COLUMN IF NOT EXISTS credit_applied_amount numeric(12,2) NOT NULL DEFAULT 0;
ALTER TABLE sync_cross_store_customer_history_cache ADD COLUMN IF NOT EXISTS document_balance numeric(12,2) NOT NULL DEFAULT 0;

UPDATE customer_account_transactions SET transaction_type='CUSTOM_ORDER_BALANCE',credit_applied_amount=0
WHERE transaction_type='CUSTOM_ORDER_CREDIT';

UPDATE customer_account_transactions t SET credit_applied_amount=CASE
 WHEN t.transaction_type='PAYMENT' AND t.custom_order_id IS NULL THEN GREATEST(ABS(COALESCE(t.amount,0))-COALESCE((
   SELECT SUM(a.amount) FROM customer_account_payment_allocations a
   WHERE a.payment_transaction_id=t.transaction_id AND a.custom_order_id IS NOT NULL),0),0)
 ELSE 0 END;

UPDATE customer_account_transactions SET transaction_type='CUSTOM_ORDER_PAYMENT',credit_applied_amount=0
WHERE transaction_type='PAYMENT' AND custom_order_id IS NOT NULL;

WITH deltas AS (
  SELECT customer_id,CASE
    WHEN transaction_type IN ('SALE_CREDIT','INVOICE_CREDIT','MANUAL_CHARGE') THEN ABS(COALESCE(amount,0))
    WHEN transaction_type='PAYMENT' THEN -ABS(COALESCE(credit_applied_amount,0))
    WHEN transaction_type IN ('RETURN','CUSTOM_ORDER_REFUND') THEN -ABS(COALESCE(amount,0))
    WHEN transaction_type IN ('SALE_PAID','CUSTOM_ORDER_PAID','CUSTOM_ORDER_BALANCE','CUSTOM_ORDER_PAYMENT') THEN 0
    WHEN COALESCE(amount,0)<0 THEN amount ELSE COALESCE(amount,0) END delta
  FROM customer_account_transactions
), balances AS (SELECT customer_id,GREATEST(COALESCE(SUM(delta),0),0) balance FROM deltas GROUP BY customer_id)
UPDATE customer_accounts ca SET current_balance=COALESCE(b.balance,0),updated_at=CURRENT_TIMESTAMP
FROM balances b WHERE b.customer_id=ca.customer_id;

UPDATE customer_accounts ca SET current_balance=0,updated_at=CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM customer_account_transactions t WHERE t.customer_id=ca.customer_id);
