-- Product type setup for SmartStock.
-- INVENTORY items affect stock. SERVICE and NON_INVENTORY items can be sold without stock checks.

ALTER TABLE products
ADD COLUMN IF NOT EXISTS product_type TEXT NOT NULL DEFAULT 'INVENTORY';

UPDATE products
SET product_type = 'INVENTORY'
WHERE product_type IS NULL OR TRIM(product_type) = '';

ALTER TABLE products
DROP CONSTRAINT IF EXISTS products_product_type_check;

ALTER TABLE products
ADD CONSTRAINT products_product_type_check
CHECK (product_type IN ('INVENTORY', 'SERVICE', 'NON_INVENTORY'));

ALTER TABLE sale_items
ADD COLUMN IF NOT EXISTS product_type TEXT NOT NULL DEFAULT 'INVENTORY';

UPDATE sale_items si
SET product_type = COALESCE(p.product_type, 'INVENTORY')
FROM products p
WHERE p.product_id = si.product_id
  AND (si.product_type IS NULL OR si.product_type = 'INVENTORY');

ALTER TABLE sale_items
DROP CONSTRAINT IF EXISTS sale_items_product_type_check;

ALTER TABLE sale_items
ADD CONSTRAINT sale_items_product_type_check
CHECK (product_type IN ('INVENTORY', 'SERVICE', 'NON_INVENTORY'));

ALTER TABLE held_cart_items
ADD COLUMN IF NOT EXISTS product_type TEXT NOT NULL DEFAULT 'INVENTORY';

UPDATE held_cart_items hci
SET product_type = COALESCE(p.product_type, 'INVENTORY')
FROM products p
WHERE p.product_id = hci.product_id
  AND (hci.product_type IS NULL OR hci.product_type = 'INVENTORY');

ALTER TABLE held_cart_items
DROP CONSTRAINT IF EXISTS held_cart_items_product_type_check;

ALTER TABLE held_cart_items
ADD CONSTRAINT held_cart_items_product_type_check
CHECK (product_type IN ('INVENTORY', 'SERVICE', 'NON_INVENTORY'));
