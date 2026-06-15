-- Sale discount support for SmartStock.
-- Adds sale-level discount tracking and a permission for applying discounts.

ALTER TABLE sales
ADD COLUMN IF NOT EXISTS subtotal_amount NUMERIC(12, 2) NOT NULL DEFAULT 0;

ALTER TABLE sales
ADD COLUMN IF NOT EXISTS discount_percent NUMERIC(5, 2) NOT NULL DEFAULT 0;

ALTER TABLE sales
ADD COLUMN IF NOT EXISTS discount_amount NUMERIC(12, 2) NOT NULL DEFAULT 0;

ALTER TABLE sales
ADD COLUMN IF NOT EXISTS vat_amount NUMERIC(12, 2) NOT NULL DEFAULT 0;

ALTER TABLE sales
ADD COLUMN IF NOT EXISTS vat_rate_percent NUMERIC(6, 2) NOT NULL DEFAULT 0;

ALTER TABLE sales
ADD COLUMN IF NOT EXISTS vat_mode TEXT NOT NULL DEFAULT '';

ALTER TABLE sales
DROP CONSTRAINT IF EXISTS sales_vat_amount_chk;

ALTER TABLE sales
ADD CONSTRAINT sales_vat_amount_chk
CHECK (vat_amount >= 0);

ALTER TABLE sales
DROP CONSTRAINT IF EXISTS sales_vat_rate_percent_chk;

ALTER TABLE sales
ADD CONSTRAINT sales_vat_rate_percent_chk
CHECK (vat_rate_percent >= 0 AND vat_rate_percent <= 100);

ALTER TABLE sale_items
ADD COLUMN IF NOT EXISTS original_unit_price NUMERIC(12, 2) NOT NULL DEFAULT 0;

ALTER TABLE sale_items
ADD COLUMN IF NOT EXISTS discount_percent NUMERIC(5, 2) NOT NULL DEFAULT 0;

ALTER TABLE sale_items
ADD COLUMN IF NOT EXISTS discount_amount NUMERIC(12, 2) NOT NULL DEFAULT 0;

ALTER TABLE held_carts
ADD COLUMN IF NOT EXISTS subtotal_amount NUMERIC(12, 2) NOT NULL DEFAULT 0;

ALTER TABLE held_carts
ADD COLUMN IF NOT EXISTS discount_percent NUMERIC(5, 2) NOT NULL DEFAULT 0;

ALTER TABLE held_carts
ADD COLUMN IF NOT EXISTS discount_amount NUMERIC(12, 2) NOT NULL DEFAULT 0;

ALTER TABLE held_cart_items
ADD COLUMN IF NOT EXISTS discount_percent NUMERIC(5, 2) NOT NULL DEFAULT 0;

-- Backfill subtotal for older sales where no subtotal was recorded.
UPDATE sales
SET subtotal_amount = total_amount
WHERE COALESCE(subtotal_amount, 0) = 0
  AND COALESCE(discount_amount, 0) = 0;

UPDATE held_carts
SET subtotal_amount = total_amount
WHERE COALESCE(subtotal_amount, 0) = 0
  AND COALESCE(discount_amount, 0) = 0;

UPDATE sale_items
SET original_unit_price = unit_price
WHERE COALESCE(original_unit_price, 0) = 0;

INSERT INTO permissions (permission_key, permission_name)
SELECT 'APPLY_SALE_DISCOUNT', 'Apply Sale Discount'
WHERE NOT EXISTS (
    SELECT 1 FROM permissions WHERE UPPER(permission_key) = 'APPLY_SALE_DISCOUNT'
);

INSERT INTO permissions (permission_key, permission_name)
SELECT 'CHANGE_SALE_ITEM_PRICE', 'Change Sale Item Price'
WHERE NOT EXISTS (
    SELECT 1 FROM permissions WHERE UPPER(permission_key) = 'CHANGE_SALE_ITEM_PRICE'
);

-- Give existing admin/manager roles access by default. Remove this block if you
-- want to assign the permission manually through role management.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.role_id, p.permission_id
FROM roles r
JOIN permissions p ON UPPER(p.permission_key) IN ('APPLY_SALE_DISCOUNT', 'CHANGE_SALE_ITEM_PRICE')
WHERE UPPER(r.role_name) IN ('ADMIN', 'MANAGER')
  AND NOT EXISTS (
      SELECT 1
      FROM role_permissions rp
      WHERE rp.role_id = r.role_id
        AND rp.permission_id = p.permission_id
  );
