-- Adds MMG as a tracked payment method for normal sales and custom orders.

ALTER TABLE sales
ADD COLUMN IF NOT EXISTS payment_reference TEXT;

ALTER TABLE custom_orders
DROP CONSTRAINT IF EXISTS custom_orders_payment_method_chk;

ALTER TABLE custom_orders
ADD CONSTRAINT custom_orders_payment_method_chk
CHECK (payment_method IS NULL OR payment_method IN ('CASH', 'CARD', 'CHEQUE', 'MMG', 'ACCOUNT'));

ALTER TABLE custom_order_payments
DROP CONSTRAINT IF EXISTS custom_order_payments_method_chk;

ALTER TABLE custom_order_payments
ADD CONSTRAINT custom_order_payments_method_chk
CHECK (payment_method IN ('CASH', 'CARD', 'CHEQUE', 'MMG', 'ACCOUNT'));
