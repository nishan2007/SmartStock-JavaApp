-- Adds percentage discounts to custom order lines for audit and verification.

ALTER TABLE custom_order_lines
ADD COLUMN IF NOT EXISTS original_line_total NUMERIC(12, 2);

ALTER TABLE custom_order_lines
ADD COLUMN IF NOT EXISTS line_discount_percent NUMERIC(7, 4) NOT NULL DEFAULT 0;

ALTER TABLE custom_order_lines
ADD COLUMN IF NOT EXISTS line_discount_amount NUMERIC(12, 2) NOT NULL DEFAULT 0;

ALTER TABLE custom_order_lines
ADD COLUMN IF NOT EXISTS line_discount_by_user_id INTEGER REFERENCES users(user_id);

ALTER TABLE custom_order_lines
ADD COLUMN IF NOT EXISTS line_discount_by_name TEXT;

ALTER TABLE custom_order_lines
DROP CONSTRAINT IF EXISTS custom_order_lines_discount_percent_chk;

ALTER TABLE custom_order_lines
ADD CONSTRAINT custom_order_lines_discount_percent_chk
CHECK (line_discount_percent >= 0 AND line_discount_percent <= 100);

ALTER TABLE custom_order_lines
DROP CONSTRAINT IF EXISTS custom_order_lines_discount_amount_chk;

ALTER TABLE custom_order_lines
ADD CONSTRAINT custom_order_lines_discount_amount_chk
CHECK (line_discount_amount >= 0);

UPDATE custom_order_lines
SET original_line_total = COALESCE(original_line_total, line_total + COALESCE(line_discount_amount, 0))
WHERE original_line_total IS NULL;

INSERT INTO permissions (permission_key, permission_name)
SELECT 'CUSTOM_ORDER_LINE_DISCOUNT', 'Custom Order Line Discount'
WHERE NOT EXISTS (
    SELECT 1 FROM permissions WHERE UPPER(permission_key) = 'CUSTOM_ORDER_LINE_DISCOUNT'
);
