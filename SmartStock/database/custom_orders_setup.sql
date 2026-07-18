-- Custom orders setup for SmartStock.
-- Run this in Supabase SQL Editor before opening the Custom Orders screen.

CREATE TABLE IF NOT EXISTS customer_types (
    customer_type_id SERIAL PRIMARY KEY,
    name TEXT NOT NULL UNIQUE,
    description TEXT,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO customer_types (name, description)
VALUES ('General', 'Default customer category')
ON CONFLICT (name) DO NOTHING;

ALTER TABLE customer_accounts
ADD COLUMN IF NOT EXISTS customer_type_id INTEGER REFERENCES customer_types(customer_type_id);

CREATE TABLE IF NOT EXISTS company_customization (
    customization_id SERIAL PRIMARY KEY,
    location_id INTEGER NOT NULL REFERENCES locations(location_id) ON DELETE CASCADE,
    receipt_header_line TEXT NOT NULL DEFAULT '',
    receipt_footer_line TEXT NOT NULL DEFAULT 'Thank you',
    show_logo BOOLEAN NOT NULL DEFAULT FALSE,
    show_sale_id BOOLEAN NOT NULL DEFAULT TRUE,
    show_device BOOLEAN NOT NULL DEFAULT TRUE,
    show_customer BOOLEAN NOT NULL DEFAULT TRUE,
    show_sku BOOLEAN NOT NULL DEFAULT TRUE,
    show_item_discount BOOLEAN NOT NULL DEFAULT TRUE,
    show_payment_status BOOLEAN NOT NULL DEFAULT TRUE,
    vat_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    vat_use_department_rates BOOLEAN NOT NULL DEFAULT FALSE,
    vat_fixed_rate_percent NUMERIC(6, 2) NOT NULL DEFAULT 0,
    custom_order_slip_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    custom_order_slip_auto_print BOOLEAN NOT NULL DEFAULT TRUE,
    custom_order_slip_title TEXT NOT NULL DEFAULT 'CUSTOMER''S ORDER SLIP',
    custom_order_slip_contact_line TEXT NOT NULL DEFAULT '',
    custom_order_slip_email_line TEXT NOT NULL DEFAULT '',
    custom_order_slip_footer_note TEXT NOT NULL DEFAULT 'NB: The management is NOT responsible for any LOSS or DAMAGE to your personal property.',
    custom_order_slip_blank_detail_lines INTEGER NOT NULL DEFAULT 8,
    custom_order_slip_show_logo BOOLEAN NOT NULL DEFAULT TRUE,
    custom_order_slip_show_order_number BOOLEAN NOT NULL DEFAULT TRUE,
    custom_order_slip_show_due_date BOOLEAN NOT NULL DEFAULT TRUE,
    custom_order_slip_show_customer_phone BOOLEAN NOT NULL DEFAULT TRUE,
    custom_order_slip_show_customer_account BOOLEAN NOT NULL DEFAULT TRUE,
    custom_order_slip_show_store BOOLEAN NOT NULL DEFAULT TRUE,
    custom_order_slip_show_device BOOLEAN NOT NULL DEFAULT TRUE,
    custom_order_slip_show_cashier BOOLEAN NOT NULL DEFAULT TRUE,
    custom_order_slip_show_line_items BOOLEAN NOT NULL DEFAULT TRUE,
    custom_order_slip_show_pricing BOOLEAN NOT NULL DEFAULT TRUE,
    custom_order_slip_show_payment_summary BOOLEAN NOT NULL DEFAULT TRUE,
    custom_order_slip_show_payment_reference BOOLEAN NOT NULL DEFAULT TRUE,
    custom_order_slip_show_taken_by BOOLEAN NOT NULL DEFAULT TRUE,
    custom_order_slip_show_signatures BOOLEAN NOT NULL DEFAULT TRUE,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (location_id)
);

ALTER TABLE company_customization
ADD COLUMN IF NOT EXISTS custom_order_minimum_deposit_percent NUMERIC(7, 4) NOT NULL DEFAULT 0;

ALTER TABLE company_customization
ADD COLUMN IF NOT EXISTS custom_order_refund_approval_limit NUMERIC(12, 2) NOT NULL DEFAULT 0;

ALTER TABLE company_customization
ADD COLUMN IF NOT EXISTS vat_enabled BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE company_customization
ADD COLUMN IF NOT EXISTS vat_use_department_rates BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE company_customization
ADD COLUMN IF NOT EXISTS vat_fixed_rate_percent NUMERIC(6, 2) NOT NULL DEFAULT 0;

ALTER TABLE company_customization
ADD COLUMN IF NOT EXISTS custom_order_slip_enabled BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE company_customization
ADD COLUMN IF NOT EXISTS custom_order_slip_auto_print BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE company_customization
ADD COLUMN IF NOT EXISTS custom_order_slip_title TEXT NOT NULL DEFAULT 'CUSTOMER''S ORDER SLIP';

ALTER TABLE company_customization
ADD COLUMN IF NOT EXISTS custom_order_slip_contact_line TEXT NOT NULL DEFAULT '';

ALTER TABLE company_customization
ADD COLUMN IF NOT EXISTS custom_order_slip_email_line TEXT NOT NULL DEFAULT '';

ALTER TABLE company_customization
ADD COLUMN IF NOT EXISTS custom_order_slip_footer_note TEXT NOT NULL DEFAULT 'NB: The management is NOT responsible for any LOSS or DAMAGE to your personal property.';

ALTER TABLE company_customization
ADD COLUMN IF NOT EXISTS custom_order_slip_blank_detail_lines INTEGER NOT NULL DEFAULT 8;

ALTER TABLE company_customization
ADD COLUMN IF NOT EXISTS custom_order_slip_show_logo BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE company_customization
ADD COLUMN IF NOT EXISTS custom_order_slip_show_order_number BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE company_customization
ADD COLUMN IF NOT EXISTS custom_order_slip_show_due_date BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE company_customization
ADD COLUMN IF NOT EXISTS custom_order_slip_show_customer_phone BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE company_customization
ADD COLUMN IF NOT EXISTS custom_order_slip_show_customer_account BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE company_customization
ADD COLUMN IF NOT EXISTS custom_order_slip_show_store BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE company_customization
ADD COLUMN IF NOT EXISTS custom_order_slip_show_device BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE company_customization
ADD COLUMN IF NOT EXISTS custom_order_slip_show_cashier BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE company_customization
ADD COLUMN IF NOT EXISTS custom_order_slip_show_line_items BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE company_customization
ADD COLUMN IF NOT EXISTS custom_order_slip_show_pricing BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE company_customization
ADD COLUMN IF NOT EXISTS custom_order_slip_show_payment_summary BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE company_customization
ADD COLUMN IF NOT EXISTS custom_order_slip_show_payment_reference BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE company_customization
ADD COLUMN IF NOT EXISTS custom_order_slip_show_taken_by BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE company_customization
ADD COLUMN IF NOT EXISTS custom_order_slip_show_signatures BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE company_customization
DROP CONSTRAINT IF EXISTS company_customization_custom_order_deposit_percent_chk;

ALTER TABLE company_customization
ADD CONSTRAINT company_customization_custom_order_deposit_percent_chk
CHECK (custom_order_minimum_deposit_percent >= 0 AND custom_order_minimum_deposit_percent <= 100);

ALTER TABLE company_customization
DROP CONSTRAINT IF EXISTS company_customization_refund_approval_limit_chk;

ALTER TABLE company_customization
ADD CONSTRAINT company_customization_refund_approval_limit_chk
CHECK (custom_order_refund_approval_limit >= 0);

ALTER TABLE company_customization
DROP CONSTRAINT IF EXISTS company_customization_slip_blank_detail_lines_chk;

ALTER TABLE company_customization
ADD CONSTRAINT company_customization_slip_blank_detail_lines_chk
CHECK (custom_order_slip_blank_detail_lines >= 0 AND custom_order_slip_blank_detail_lines <= 20);

CREATE TABLE IF NOT EXISTS custom_order_items (
    custom_item_id BIGSERIAL PRIMARY KEY,
    item_name TEXT NOT NULL UNIQUE,
    barcode TEXT UNIQUE,
    description TEXT,
    image_url TEXT,
    category_id INTEGER REFERENCES categories(category_id),
    item_type_id INTEGER REFERENCES item_types(item_type_id),
    brand_id INTEGER REFERENCES item_brands(brand_id),
    product_type TEXT NOT NULL DEFAULT 'INVENTORY',
    pricing_type TEXT NOT NULL DEFAULT 'VARIABLE',
    fixed_price NUMERIC(12, 2),
    area_price NUMERIC(12, 2),
    area_price_unit TEXT,
    dimension_unit TEXT,
    max_width NUMERIC(12, 2),
    max_length NUMERIC(12, 2),
    has_variants BOOLEAN NOT NULL DEFAULT FALSE,
    quantity_on_hand NUMERIC(12, 2) NOT NULL DEFAULT 0,
    sold_quantity NUMERIC(12, 2) NOT NULL DEFAULT 0,
    reorder_level NUMERIC(12, 2) NOT NULL DEFAULT 0,
    minimum_deposit_percent NUMERIC(7, 4) NOT NULL DEFAULT 0,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT custom_order_items_pricing_type_chk
        CHECK (pricing_type IN ('FIXED', 'VARIABLE', 'AREA')),
    CONSTRAINT custom_order_items_product_type_chk
        CHECK (product_type IN ('INVENTORY', 'SERVICE', 'NON_INVENTORY')),
    CONSTRAINT custom_order_items_fixed_price_chk
        CHECK (pricing_type NOT IN ('FIXED', 'AREA') OR has_variants = TRUE OR fixed_price IS NOT NULL),
    CONSTRAINT custom_order_items_area_price_chk
        CHECK (pricing_type <> 'AREA' OR has_variants = TRUE OR fixed_price IS NOT NULL)
);

CREATE INDEX IF NOT EXISTS custom_order_items_active_idx
ON custom_order_items(is_active, item_name);

ALTER TABLE custom_order_items
ADD COLUMN IF NOT EXISTS quantity_on_hand NUMERIC(12, 2) NOT NULL DEFAULT 0;

ALTER TABLE custom_order_items
ADD COLUMN IF NOT EXISTS reorder_level NUMERIC(12, 2) NOT NULL DEFAULT 0;

ALTER TABLE custom_order_items
ADD COLUMN IF NOT EXISTS minimum_deposit_percent NUMERIC(7, 4) NOT NULL DEFAULT 0;

ALTER TABLE custom_order_items
DROP CONSTRAINT IF EXISTS custom_order_items_deposit_percent_chk;

ALTER TABLE custom_order_items
ADD CONSTRAINT custom_order_items_deposit_percent_chk
CHECK (minimum_deposit_percent >= 0 AND minimum_deposit_percent <= 100);

ALTER TABLE custom_order_items
ADD COLUMN IF NOT EXISTS barcode TEXT;

ALTER TABLE custom_order_items
ADD COLUMN IF NOT EXISTS image_url TEXT;

ALTER TABLE custom_order_items
ADD COLUMN IF NOT EXISTS category_id INTEGER REFERENCES categories(category_id);

ALTER TABLE custom_order_items
ADD COLUMN IF NOT EXISTS item_type_id INTEGER REFERENCES item_types(item_type_id);

ALTER TABLE custom_order_items
ADD COLUMN IF NOT EXISTS brand_id INTEGER REFERENCES item_brands(brand_id);

ALTER TABLE custom_order_items
ADD COLUMN IF NOT EXISTS product_type TEXT NOT NULL DEFAULT 'INVENTORY';

ALTER TABLE custom_order_items
ADD COLUMN IF NOT EXISTS sold_quantity NUMERIC(12, 2) NOT NULL DEFAULT 0;

ALTER TABLE custom_order_items
ADD COLUMN IF NOT EXISTS has_variants BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE custom_order_items
ADD COLUMN IF NOT EXISTS area_price NUMERIC(12, 2);

ALTER TABLE custom_order_items
ADD COLUMN IF NOT EXISTS area_price_unit TEXT;

ALTER TABLE custom_order_items
ADD COLUMN IF NOT EXISTS dimension_unit TEXT;

ALTER TABLE custom_order_items
ADD COLUMN IF NOT EXISTS max_width NUMERIC(12, 2);

ALTER TABLE custom_order_items
ADD COLUMN IF NOT EXISTS max_length NUMERIC(12, 2);

UPDATE custom_order_items
SET pricing_type = 'AREA'
WHERE pricing_type = 'SQUARE_FOOT';

UPDATE custom_order_items
SET fixed_price = COALESCE(fixed_price, area_price),
    area_price = COALESCE(area_price, fixed_price),
    area_price_unit = COALESCE(area_price_unit, 'SQ_FT'),
    dimension_unit = COALESCE(dimension_unit, 'IN'),
    max_width = max_width,
    max_length = max_length
WHERE pricing_type = 'AREA'
  AND (fixed_price IS NULL
   OR area_price IS NULL
   OR area_price_unit IS NULL
   OR dimension_unit IS NULL
   OR max_width IS NULL
   OR max_length IS NULL);

ALTER TABLE custom_order_items
DROP CONSTRAINT IF EXISTS custom_order_items_pricing_type_chk;

ALTER TABLE custom_order_items
ADD CONSTRAINT custom_order_items_pricing_type_chk
CHECK (pricing_type IN ('FIXED', 'VARIABLE', 'AREA'));

ALTER TABLE custom_order_items
DROP CONSTRAINT IF EXISTS custom_order_items_product_type_chk;

ALTER TABLE custom_order_items
ADD CONSTRAINT custom_order_items_product_type_chk
CHECK (product_type IN ('INVENTORY', 'SERVICE', 'NON_INVENTORY'));

ALTER TABLE custom_order_items
DROP CONSTRAINT IF EXISTS custom_order_items_fixed_price_chk;

ALTER TABLE custom_order_items
ADD CONSTRAINT custom_order_items_fixed_price_chk
CHECK (pricing_type NOT IN ('FIXED', 'AREA') OR has_variants = TRUE OR fixed_price IS NOT NULL);

ALTER TABLE custom_order_items
DROP CONSTRAINT IF EXISTS custom_order_items_area_price_chk;

ALTER TABLE custom_order_items
ADD CONSTRAINT custom_order_items_area_price_chk
CHECK (pricing_type <> 'AREA' OR has_variants = TRUE OR fixed_price IS NOT NULL);

CREATE UNIQUE INDEX IF NOT EXISTS custom_order_items_barcode_uidx
ON custom_order_items(barcode)
WHERE barcode IS NOT NULL AND barcode <> '';

CREATE INDEX IF NOT EXISTS custom_order_items_low_stock_idx
ON custom_order_items(is_active, quantity_on_hand, reorder_level);

CREATE INDEX IF NOT EXISTS custom_order_items_category_idx
ON custom_order_items(category_id);

CREATE INDEX IF NOT EXISTS custom_order_items_item_type_idx
ON custom_order_items(item_type_id);

CREATE INDEX IF NOT EXISTS custom_order_items_brand_idx
ON custom_order_items(brand_id);

CREATE TABLE IF NOT EXISTS custom_order_item_barcodes (
    custom_item_barcode_id BIGSERIAL PRIMARY KEY,
    custom_item_id BIGINT NOT NULL REFERENCES custom_order_items(custom_item_id) ON DELETE CASCADE,
    barcode TEXT NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS custom_order_item_barcodes_item_idx
ON custom_order_item_barcodes(custom_item_id);

CREATE TABLE IF NOT EXISTS custom_order_item_variants (
    custom_variant_id BIGSERIAL PRIMARY KEY,
    custom_item_id BIGINT NOT NULL REFERENCES custom_order_items(custom_item_id) ON DELETE CASCADE,
    variant_name TEXT NOT NULL,
    barcode TEXT UNIQUE,
    image_url TEXT,
    fixed_price NUMERIC(12, 2),
    quantity_on_hand NUMERIC(12, 2) NOT NULL DEFAULT 0,
    sold_quantity NUMERIC(12, 2) NOT NULL DEFAULT 0,
    reorder_level NUMERIC(12, 2) NOT NULL DEFAULT 0,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT custom_order_item_variants_item_name_uidx UNIQUE (custom_item_id, variant_name)
);

CREATE INDEX IF NOT EXISTS custom_order_item_variants_item_idx
ON custom_order_item_variants(custom_item_id, is_active, variant_name);

ALTER TABLE custom_order_item_variants
ADD COLUMN IF NOT EXISTS fixed_price NUMERIC(12, 2);

ALTER TABLE custom_order_item_variants
ADD COLUMN IF NOT EXISTS image_url TEXT;

ALTER TABLE custom_order_item_variants
ADD COLUMN IF NOT EXISTS sold_quantity NUMERIC(12, 2) NOT NULL DEFAULT 0;

CREATE UNIQUE INDEX IF NOT EXISTS custom_order_item_variants_barcode_uidx
ON custom_order_item_variants(barcode)
WHERE barcode IS NOT NULL AND barcode <> '';

CREATE INDEX IF NOT EXISTS custom_order_item_variants_low_stock_idx
ON custom_order_item_variants(is_active, quantity_on_hand, reorder_level);

CREATE OR REPLACE FUNCTION refresh_custom_order_item_variant_totals()
RETURNS TRIGGER AS $$
DECLARE
    affected_item_id BIGINT;
BEGIN
    affected_item_id := COALESCE(NEW.custom_item_id, OLD.custom_item_id);

    UPDATE custom_order_items coi
    SET quantity_on_hand = COALESCE((
            SELECT SUM(coiv.quantity_on_hand)
            FROM custom_order_item_variants coiv
            WHERE coiv.custom_item_id = affected_item_id
              AND coiv.is_active = TRUE
        ), 0),
        reorder_level = COALESCE((
            SELECT SUM(coiv.reorder_level)
            FROM custom_order_item_variants coiv
            WHERE coiv.custom_item_id = affected_item_id
              AND coiv.is_active = TRUE
        ), 0),
        updated_at = CURRENT_TIMESTAMP
    WHERE coi.custom_item_id = affected_item_id
      AND coi.has_variants = TRUE;

    RETURN COALESCE(NEW, OLD);
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS custom_order_item_variants_refresh_totals ON custom_order_item_variants;
CREATE TRIGGER custom_order_item_variants_refresh_totals
AFTER INSERT OR UPDATE OR DELETE ON custom_order_item_variants
FOR EACH ROW
EXECUTE FUNCTION refresh_custom_order_item_variant_totals();

UPDATE custom_order_items coi
SET has_variants = TRUE,
    quantity_on_hand = COALESCE(v.total_quantity, 0),
    reorder_level = COALESCE(v.total_reorder, 0),
    updated_at = CURRENT_TIMESTAMP
FROM (
    SELECT custom_item_id,
           SUM(quantity_on_hand) FILTER (WHERE is_active = TRUE) AS total_quantity,
           SUM(reorder_level) FILTER (WHERE is_active = TRUE) AS total_reorder
    FROM custom_order_item_variants
    GROUP BY custom_item_id
) v
WHERE coi.custom_item_id = v.custom_item_id
  AND EXISTS (
      SELECT 1
      FROM custom_order_item_variants coiv
      WHERE coiv.custom_item_id = coi.custom_item_id
  );

CREATE TABLE IF NOT EXISTS custom_order_item_movements (
    movement_id BIGSERIAL PRIMARY KEY,
    custom_item_id BIGINT NOT NULL REFERENCES custom_order_items(custom_item_id),
    custom_variant_id BIGINT REFERENCES custom_order_item_variants(custom_variant_id),
    variant_name TEXT,
    location_id INTEGER REFERENCES locations(location_id),
    change_qty NUMERIC(12, 2) NOT NULL,
    reason TEXT NOT NULL,
    note TEXT,
    user_name TEXT,
    user_id INTEGER REFERENCES users(user_id),
    device_id TEXT,
    device_name TEXT,
    receive_id TEXT,
    receive_device_id TEXT,
    receive_sequence INTEGER,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS custom_order_item_movements_item_idx
ON custom_order_item_movements(custom_item_id, created_at DESC);

CREATE INDEX IF NOT EXISTS custom_order_item_movements_variant_idx
ON custom_order_item_movements(custom_variant_id, created_at DESC);

ALTER TABLE custom_order_item_movements
ADD COLUMN IF NOT EXISTS custom_variant_id BIGINT REFERENCES custom_order_item_variants(custom_variant_id);

ALTER TABLE custom_order_item_movements
ADD COLUMN IF NOT EXISTS variant_name TEXT;

ALTER TABLE custom_order_item_movements
ADD COLUMN IF NOT EXISTS location_id INTEGER REFERENCES locations(location_id);

ALTER TABLE custom_order_item_movements
ADD COLUMN IF NOT EXISTS user_id INTEGER REFERENCES users(user_id);

ALTER TABLE custom_order_item_movements
ADD COLUMN IF NOT EXISTS device_id TEXT;

ALTER TABLE custom_order_item_movements
ADD COLUMN IF NOT EXISTS device_name TEXT;

ALTER TABLE custom_order_item_movements
ADD COLUMN IF NOT EXISTS receive_id TEXT;

ALTER TABLE custom_order_item_movements
ADD COLUMN IF NOT EXISTS receive_device_id TEXT;

ALTER TABLE custom_order_item_movements
ADD COLUMN IF NOT EXISTS receive_sequence INTEGER;

CREATE INDEX IF NOT EXISTS custom_order_item_movements_user_idx
ON custom_order_item_movements(user_id, created_at DESC);

CREATE INDEX IF NOT EXISTS custom_order_item_movements_device_idx
ON custom_order_item_movements(device_id, created_at DESC);

CREATE INDEX IF NOT EXISTS custom_order_item_movements_location_idx
ON custom_order_item_movements(location_id, created_at DESC);

CREATE INDEX IF NOT EXISTS custom_order_item_movements_receive_idx
ON custom_order_item_movements(receive_id, created_at DESC);

CREATE TABLE IF NOT EXISTS custom_orders (
    custom_order_id BIGSERIAL PRIMARY KEY,
    order_number TEXT NOT NULL UNIQUE,
    customer_id INTEGER NOT NULL REFERENCES customer_accounts(customer_id),
    customer_name TEXT NOT NULL,
    customer_phone TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'NEW',
    due_date DATE,
    order_notes TEXT,
    total_amount NUMERIC(12, 2) NOT NULL DEFAULT 0,
    amount_paid NUMERIC(12, 2) NOT NULL DEFAULT 0,
    balance_due NUMERIC(12, 2) NOT NULL DEFAULT 0,
    payment_method TEXT,
    payment_reference TEXT,
    payment_status TEXT NOT NULL DEFAULT 'UNPAID',
    taken_by_user_id INTEGER REFERENCES users(user_id),
    taken_by_name TEXT,
    location_id INTEGER REFERENCES locations(location_id),
    location_name TEXT,
    device_id TEXT,
    device_name TEXT,
    cash_drawer_id BIGINT,
    cash_drawer_name TEXT,
    cash_drawer_session_id BIGINT,
    minimum_deposit_required NUMERIC(12, 2) NOT NULL DEFAULT 0,
    deposit_override_reason TEXT,
    deposit_override_by_user_id INTEGER REFERENCES users(user_id),
    deposit_override_by_name TEXT,
    assigned_to_user_id INTEGER REFERENCES users(user_id),
    assigned_to_name TEXT,
    assigned_by_user_id INTEGER REFERENCES users(user_id),
    assigned_by_name TEXT,
    assigned_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    delivered_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT custom_orders_status_chk
        CHECK (status IN ('NEW', 'ASSIGNED', 'IN_PROGRESS', 'READY', 'COMPLETED', 'DELIVERED', 'CANCELLED'))
);

ALTER TABLE custom_orders
ADD COLUMN IF NOT EXISTS delivered_at TIMESTAMPTZ;

ALTER TABLE custom_orders
DROP CONSTRAINT IF EXISTS custom_orders_status_chk;

ALTER TABLE custom_orders
ADD CONSTRAINT custom_orders_status_chk
CHECK (status IN ('NEW', 'ASSIGNED', 'IN_PROGRESS', 'READY', 'COMPLETED', 'DELIVERED', 'CANCELLED'));

ALTER TABLE custom_orders
ADD COLUMN IF NOT EXISTS payment_method TEXT;

ALTER TABLE custom_orders
ADD COLUMN IF NOT EXISTS payment_reference TEXT;

ALTER TABLE custom_orders
ADD COLUMN IF NOT EXISTS amount_paid NUMERIC(12, 2) NOT NULL DEFAULT 0;

ALTER TABLE custom_orders
ADD COLUMN IF NOT EXISTS balance_due NUMERIC(12, 2) NOT NULL DEFAULT 0;

ALTER TABLE custom_orders
ADD COLUMN IF NOT EXISTS payment_status TEXT NOT NULL DEFAULT 'UNPAID';

ALTER TABLE custom_orders
ADD COLUMN IF NOT EXISTS location_id INTEGER REFERENCES locations(location_id);

ALTER TABLE custom_orders
ADD COLUMN IF NOT EXISTS location_name TEXT;

ALTER TABLE custom_orders
ADD COLUMN IF NOT EXISTS device_id TEXT;

ALTER TABLE custom_orders
ADD COLUMN IF NOT EXISTS device_name TEXT;

ALTER TABLE custom_orders
ADD COLUMN IF NOT EXISTS cash_drawer_id BIGINT;

ALTER TABLE custom_orders
ADD COLUMN IF NOT EXISTS cash_drawer_name TEXT;

ALTER TABLE custom_orders
ADD COLUMN IF NOT EXISTS cash_drawer_session_id BIGINT;

ALTER TABLE custom_orders
ADD COLUMN IF NOT EXISTS minimum_deposit_required NUMERIC(12, 2) NOT NULL DEFAULT 0;

ALTER TABLE custom_orders
ADD COLUMN IF NOT EXISTS deposit_override_reason TEXT;

ALTER TABLE custom_orders
ADD COLUMN IF NOT EXISTS deposit_override_by_user_id INTEGER REFERENCES users(user_id);

ALTER TABLE custom_orders
ADD COLUMN IF NOT EXISTS deposit_override_by_name TEXT;

UPDATE custom_orders
SET amount_paid = COALESCE(amount_paid, 0),
    balance_due = GREATEST(COALESCE(total_amount, 0) - COALESCE(amount_paid, 0), 0),
    payment_status = CASE
        WHEN COALESCE(amount_paid, 0) <= 0 THEN 'UNPAID'
        WHEN GREATEST(COALESCE(total_amount, 0) - COALESCE(amount_paid, 0), 0) <= 0 THEN 'PAID'
        ELSE 'PARTIAL'
    END;

ALTER TABLE custom_orders
ALTER COLUMN payment_status SET DEFAULT 'UNPAID';

ALTER TABLE custom_orders
ALTER COLUMN payment_status SET NOT NULL;

ALTER TABLE custom_orders
DROP CONSTRAINT IF EXISTS custom_orders_payment_method_chk;

ALTER TABLE custom_orders
ADD CONSTRAINT custom_orders_payment_method_chk
CHECK (payment_method IS NULL OR payment_method IN ('CASH', 'CARD', 'CHEQUE', 'MMG', 'ACCOUNT'));

ALTER TABLE custom_orders
DROP CONSTRAINT IF EXISTS custom_orders_payment_status_chk;

ALTER TABLE custom_orders
ADD CONSTRAINT custom_orders_payment_status_chk
CHECK (payment_status IN ('PAID', 'PARTIAL', 'UNPAID'));

ALTER TABLE custom_orders
DROP CONSTRAINT IF EXISTS custom_orders_amount_paid_chk;

ALTER TABLE custom_orders
ADD CONSTRAINT custom_orders_amount_paid_chk
CHECK (amount_paid >= 0 AND balance_due >= 0);

CREATE TABLE IF NOT EXISTS custom_order_payments (
    custom_order_payment_id BIGSERIAL PRIMARY KEY,
    custom_order_id BIGINT NOT NULL REFERENCES custom_orders(custom_order_id) ON DELETE CASCADE,
    payment_amount NUMERIC(12, 2) NOT NULL,
    payment_method TEXT NOT NULL,
    payment_reference TEXT,
    taken_by_user_id INTEGER REFERENCES users(user_id),
    taken_by_name TEXT,
    payment_action TEXT NOT NULL DEFAULT 'PAYMENT',
    void_reason TEXT,
    device_id TEXT,
    device_name TEXT,
    cash_drawer_id BIGINT,
    cash_drawer_name TEXT,
    cash_drawer_session_id BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT custom_order_payments_amount_chk CHECK (payment_amount > 0),
    CONSTRAINT custom_order_payments_method_chk CHECK (payment_method IN ('CASH', 'CARD', 'CHEQUE', 'MMG', 'ACCOUNT'))
);

ALTER TABLE custom_order_payments
DROP CONSTRAINT IF EXISTS custom_order_payments_method_chk;

ALTER TABLE custom_order_payments
ADD CONSTRAINT custom_order_payments_method_chk
CHECK (payment_method IN ('CASH', 'CARD', 'CHEQUE', 'MMG', 'ACCOUNT'));

ALTER TABLE custom_order_payments
ADD COLUMN IF NOT EXISTS payment_action TEXT NOT NULL DEFAULT 'PAYMENT';

ALTER TABLE custom_order_payments
ADD COLUMN IF NOT EXISTS void_reason TEXT;

ALTER TABLE custom_order_payments
ADD COLUMN IF NOT EXISTS device_id TEXT;

ALTER TABLE custom_order_payments
ADD COLUMN IF NOT EXISTS device_name TEXT;

ALTER TABLE custom_order_payments
ADD COLUMN IF NOT EXISTS cash_drawer_id BIGINT;

ALTER TABLE custom_order_payments
ADD COLUMN IF NOT EXISTS cash_drawer_name TEXT;

ALTER TABLE custom_order_payments
ADD COLUMN IF NOT EXISTS cash_drawer_session_id BIGINT;

ALTER TABLE custom_order_payments
DROP CONSTRAINT IF EXISTS custom_order_payments_action_chk;

ALTER TABLE custom_order_payments
ADD CONSTRAINT custom_order_payments_action_chk
CHECK (payment_action IN ('PAYMENT', 'REFUND', 'REVERSAL'));

CREATE INDEX IF NOT EXISTS custom_order_payments_order_idx
ON custom_order_payments(custom_order_id, created_at);

CREATE INDEX IF NOT EXISTS custom_order_payments_taken_by_user_fk_idx
ON custom_order_payments(taken_by_user_id)
WHERE taken_by_user_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS custom_order_payments_voided_by_user_fk_idx
ON custom_order_payments(voided_by_user_id)
WHERE voided_by_user_id IS NOT NULL;

ALTER TABLE customer_account_transactions
ADD COLUMN IF NOT EXISTS custom_order_id BIGINT REFERENCES custom_orders(custom_order_id);

ALTER TABLE customer_account_transactions
ADD COLUMN IF NOT EXISTS payment_method TEXT;

ALTER TABLE customer_account_transactions
ADD COLUMN IF NOT EXISTS payment_reference TEXT;

ALTER TABLE customer_account_transactions
ADD COLUMN IF NOT EXISTS cash_drawer_id BIGINT;

ALTER TABLE customer_account_transactions
ADD COLUMN IF NOT EXISTS cash_drawer_name TEXT;

ALTER TABLE customer_account_transactions
ADD COLUMN IF NOT EXISTS cash_drawer_session_id BIGINT;

CREATE INDEX IF NOT EXISTS customer_account_transactions_custom_order_idx
ON customer_account_transactions(custom_order_id);

ALTER TABLE customer_account_payment_allocations
ADD COLUMN IF NOT EXISTS custom_order_id BIGINT REFERENCES custom_orders(custom_order_id);

ALTER TABLE customer_account_payment_allocations
ALTER COLUMN sale_id DROP NOT NULL;

CREATE INDEX IF NOT EXISTS customer_account_payment_allocations_custom_order_idx
ON customer_account_payment_allocations(custom_order_id);

CREATE INDEX IF NOT EXISTS custom_orders_customer_idx
ON custom_orders(customer_id, created_at DESC);

CREATE INDEX IF NOT EXISTS custom_orders_assigned_by_user_fk_idx
ON custom_orders(assigned_by_user_id)
WHERE assigned_by_user_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS custom_orders_cancelled_by_user_fk_idx
ON custom_orders(cancelled_by_user_id)
WHERE cancelled_by_user_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS custom_orders_deposit_override_by_user_fk_idx
ON custom_orders(deposit_override_by_user_id)
WHERE deposit_override_by_user_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS custom_orders_status_idx
ON custom_orders(status, due_date);

CREATE TABLE IF NOT EXISTS custom_order_print_materials (
    print_material_id BIGSERIAL PRIMARY KEY,
    material_name TEXT NOT NULL UNIQUE,
    description TEXT,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS custom_order_print_size_presets (
    print_size_preset_id BIGSERIAL PRIMARY KEY,
    print_material_id BIGINT NOT NULL REFERENCES custom_order_print_materials(print_material_id) ON DELETE CASCADE,
    preset_name TEXT NOT NULL,
    pricing_mode TEXT NOT NULL DEFAULT 'FIXED_PRESET',
    fixed_price NUMERIC(12, 2) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT custom_order_print_size_presets_name_uidx UNIQUE (print_material_id, preset_name),
    CONSTRAINT custom_order_print_size_presets_pricing_mode_chk CHECK (pricing_mode IN ('FIXED_PRESET', 'PER_LINE')),
    CONSTRAINT custom_order_print_size_presets_price_chk CHECK (fixed_price >= 0)
);

ALTER TABLE custom_order_print_size_presets
ADD COLUMN IF NOT EXISTS pricing_mode TEXT NOT NULL DEFAULT 'FIXED_PRESET';

ALTER TABLE custom_order_print_materials
ADD COLUMN IF NOT EXISTS pricing_mode TEXT NOT NULL DEFAULT 'FIXED_PRESET';

ALTER TABLE custom_order_print_materials
ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP;

ALTER TABLE custom_order_print_size_presets
ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP;

UPDATE custom_order_print_size_presets p
SET pricing_mode = m.pricing_mode
FROM custom_order_print_materials m
WHERE p.print_material_id = m.print_material_id
  AND m.pricing_mode = 'PER_LINE'
  AND p.pricing_mode = 'FIXED_PRESET';

ALTER TABLE custom_order_print_size_presets
DROP CONSTRAINT IF EXISTS custom_order_print_size_presets_pricing_mode_chk;

ALTER TABLE custom_order_print_size_presets
ADD CONSTRAINT custom_order_print_size_presets_pricing_mode_chk
CHECK (pricing_mode IN ('FIXED_PRESET', 'PER_LINE'));

ALTER TABLE custom_order_print_materials
DROP CONSTRAINT IF EXISTS custom_order_print_materials_pricing_mode_chk;

CREATE INDEX IF NOT EXISTS custom_order_print_size_presets_material_idx
ON custom_order_print_size_presets(print_material_id, is_active, preset_name);

CREATE OR REPLACE FUNCTION set_custom_order_print_materials_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        NEW.updated_at = COALESCE(NEW.updated_at, CURRENT_TIMESTAMP);
    ELSIF NEW.updated_at IS NOT DISTINCT FROM OLD.updated_at THEN
        NEW.updated_at = CURRENT_TIMESTAMP;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS custom_order_print_materials_set_updated_at ON custom_order_print_materials;
CREATE TRIGGER custom_order_print_materials_set_updated_at
BEFORE INSERT OR UPDATE ON custom_order_print_materials
FOR EACH ROW
EXECUTE FUNCTION set_custom_order_print_materials_updated_at();

CREATE INDEX IF NOT EXISTS custom_order_print_materials_updated_at_idx
ON custom_order_print_materials(updated_at DESC);

CREATE OR REPLACE FUNCTION set_custom_order_print_size_presets_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        NEW.updated_at = COALESCE(NEW.updated_at, CURRENT_TIMESTAMP);
    ELSIF NEW.updated_at IS NOT DISTINCT FROM OLD.updated_at THEN
        NEW.updated_at = CURRENT_TIMESTAMP;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS custom_order_print_size_presets_set_updated_at ON custom_order_print_size_presets;
CREATE TRIGGER custom_order_print_size_presets_set_updated_at
BEFORE INSERT OR UPDATE ON custom_order_print_size_presets
FOR EACH ROW
EXECUTE FUNCTION set_custom_order_print_size_presets_updated_at();

CREATE INDEX IF NOT EXISTS custom_order_print_size_presets_updated_at_idx
ON custom_order_print_size_presets(updated_at DESC);

CREATE TABLE IF NOT EXISTS custom_order_design_placements (
    design_placement_id BIGSERIAL PRIMARY KEY,
    placement_name TEXT NOT NULL UNIQUE,
    sort_order INTEGER NOT NULL DEFAULT 0,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE custom_order_design_placements
ADD COLUMN IF NOT EXISTS sort_order INTEGER NOT NULL DEFAULT 0;

ALTER TABLE custom_order_design_placements
ADD COLUMN IF NOT EXISTS is_active BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE custom_order_design_placements
ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP;

CREATE INDEX IF NOT EXISTS custom_order_design_placements_active_idx
ON custom_order_design_placements(is_active, sort_order, placement_name);

INSERT INTO custom_order_design_placements (placement_name, sort_order)
VALUES
    ('Line 1', 10),
    ('Line 2', 20),
    ('Line 3', 30),
    ('Top', 40),
    ('Middle', 50),
    ('Bottom', 60),
    ('Pocket', 70),
    ('Chest', 80),
    ('Left Chest', 90),
    ('Right Chest', 100),
    ('Front', 110),
    ('Back', 120),
    ('Left Sleeve', 130),
    ('Right Sleeve', 140)
ON CONFLICT (placement_name) DO NOTHING;

CREATE TABLE IF NOT EXISTS custom_order_lines (
    custom_order_line_id BIGSERIAL PRIMARY KEY,
    custom_order_id BIGINT NOT NULL REFERENCES custom_orders(custom_order_id) ON DELETE CASCADE,
    custom_item_id BIGINT REFERENCES custom_order_items(custom_item_id),
    item_name TEXT NOT NULL,
    pricing_type TEXT NOT NULL,
    unit_price NUMERIC(12, 2) NOT NULL,
    line_total NUMERIC(12, 2) NOT NULL,
    width_inches NUMERIC(12, 2),
    length_inches NUMERIC(12, 2),
    square_feet NUMERIC(12, 4),
    width_value NUMERIC(12, 2),
    length_value NUMERIC(12, 2),
    dimension_unit TEXT,
    area_value NUMERIC(12, 4),
    area_unit TEXT,
    area_price NUMERIC(12, 2),
    base_item_price NUMERIC(12, 2),
    print_material_id BIGINT REFERENCES custom_order_print_materials(print_material_id),
    print_material_name TEXT,
    print_size_preset_id BIGINT REFERENCES custom_order_print_size_presets(print_size_preset_id),
    print_size_name TEXT,
    print_charge NUMERIC(12, 2) NOT NULL DEFAULT 0,
    print_line_count INTEGER NOT NULL DEFAULT 1,
    customization_details TEXT NOT NULL,
    order_instructions TEXT,
    sort_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT custom_order_lines_pricing_type_chk
        CHECK (pricing_type IN ('FIXED', 'VARIABLE', 'AREA'))
);

CREATE INDEX IF NOT EXISTS custom_order_lines_order_idx
ON custom_order_lines(custom_order_id, sort_order);

CREATE INDEX IF NOT EXISTS custom_order_lines_custom_item_fk_idx
ON custom_order_lines(custom_item_id)
WHERE custom_item_id IS NOT NULL;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_name = 'custom_order_lines'
          AND column_name = 'line_notes'
    )
    AND NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_name = 'custom_order_lines'
          AND column_name = 'order_instructions'
    ) THEN
        ALTER TABLE custom_order_lines RENAME COLUMN line_notes TO order_instructions;
    END IF;
END $$;

ALTER TABLE custom_order_lines
ADD COLUMN IF NOT EXISTS order_instructions TEXT;

ALTER TABLE custom_order_lines
ADD COLUMN IF NOT EXISTS returned_amount NUMERIC(12, 2) NOT NULL DEFAULT 0;

ALTER TABLE custom_order_lines
ADD COLUMN IF NOT EXISTS return_status TEXT NOT NULL DEFAULT 'NONE';

ALTER TABLE custom_order_lines
ADD COLUMN IF NOT EXISTS delivery_status TEXT NOT NULL DEFAULT 'PENDING';

ALTER TABLE custom_order_lines
ADD COLUMN IF NOT EXISTS delivered_at TIMESTAMPTZ;

ALTER TABLE custom_order_lines
ADD COLUMN IF NOT EXISTS delivered_by_user_id INTEGER REFERENCES users(user_id);

ALTER TABLE custom_order_lines
ADD COLUMN IF NOT EXISTS delivered_by_name TEXT;

CREATE INDEX IF NOT EXISTS custom_order_lines_delivered_by_user_fk_idx
ON custom_order_lines(delivered_by_user_id)
WHERE delivered_by_user_id IS NOT NULL;

ALTER TABLE custom_order_lines
DROP CONSTRAINT IF EXISTS custom_order_lines_return_status_chk;

ALTER TABLE custom_order_lines
ADD CONSTRAINT custom_order_lines_return_status_chk
CHECK (return_status IN ('NONE', 'PARTIAL', 'FULL'));

ALTER TABLE custom_order_lines
DROP CONSTRAINT IF EXISTS custom_order_lines_delivery_status_chk;

ALTER TABLE custom_order_lines
ADD CONSTRAINT custom_order_lines_delivery_status_chk
CHECK (delivery_status IN ('PENDING', 'DELIVERED'));

CREATE TABLE IF NOT EXISTS custom_order_line_print_addons (
    custom_order_line_print_addon_id BIGSERIAL PRIMARY KEY,
    custom_order_line_id BIGINT NOT NULL REFERENCES custom_order_lines(custom_order_line_id) ON DELETE CASCADE,
    print_material_id BIGINT REFERENCES custom_order_print_materials(print_material_id),
    print_material_name TEXT NOT NULL,
    print_size_preset_id BIGINT REFERENCES custom_order_print_size_presets(print_size_preset_id),
    print_size_name TEXT,
    pricing_mode TEXT NOT NULL DEFAULT 'FIXED_PRESET',
    print_description TEXT,
    print_charge NUMERIC(12, 2) NOT NULL DEFAULT 0,
    print_line_count INTEGER NOT NULL DEFAULT 1,
    sort_order INTEGER NOT NULL DEFAULT 1,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT custom_order_line_print_addons_pricing_mode_chk CHECK (pricing_mode IN ('FIXED_PRESET', 'PER_LINE')),
    CONSTRAINT custom_order_line_print_addons_charge_chk CHECK (print_charge >= 0),
    CONSTRAINT custom_order_line_print_addons_line_count_chk CHECK (print_line_count > 0)
);

CREATE INDEX IF NOT EXISTS custom_order_line_print_addons_line_idx
ON custom_order_line_print_addons(custom_order_line_id, sort_order);

ALTER TABLE custom_order_line_print_addons
ADD COLUMN IF NOT EXISTS print_description TEXT;

ALTER TABLE custom_order_lines
ADD COLUMN IF NOT EXISTS custom_variant_id BIGINT REFERENCES custom_order_item_variants(custom_variant_id);

CREATE INDEX IF NOT EXISTS custom_order_lines_custom_variant_fk_idx
ON custom_order_lines(custom_variant_id)
WHERE custom_variant_id IS NOT NULL;

ALTER TABLE custom_order_lines
ADD COLUMN IF NOT EXISTS variant_name TEXT;

ALTER TABLE custom_order_lines
ADD COLUMN IF NOT EXISTS width_inches NUMERIC(12, 2);

ALTER TABLE custom_order_lines
ADD COLUMN IF NOT EXISTS length_inches NUMERIC(12, 2);

ALTER TABLE custom_order_lines
ADD COLUMN IF NOT EXISTS square_feet NUMERIC(12, 4);

ALTER TABLE custom_order_lines
ADD COLUMN IF NOT EXISTS width_value NUMERIC(12, 2);

ALTER TABLE custom_order_lines
ADD COLUMN IF NOT EXISTS length_value NUMERIC(12, 2);

ALTER TABLE custom_order_lines
ADD COLUMN IF NOT EXISTS dimension_unit TEXT;

ALTER TABLE custom_order_lines
ADD COLUMN IF NOT EXISTS area_value NUMERIC(12, 4);

ALTER TABLE custom_order_lines
ADD COLUMN IF NOT EXISTS area_unit TEXT;

ALTER TABLE custom_order_lines
ADD COLUMN IF NOT EXISTS area_price NUMERIC(12, 2);

ALTER TABLE custom_order_lines
ADD COLUMN IF NOT EXISTS base_item_price NUMERIC(12, 2);

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

CREATE INDEX IF NOT EXISTS custom_order_lines_line_discount_by_user_fk_idx
ON custom_order_lines(line_discount_by_user_id)
WHERE line_discount_by_user_id IS NOT NULL;

ALTER TABLE custom_order_lines
ADD COLUMN IF NOT EXISTS line_discount_reason TEXT;

ALTER TABLE custom_order_lines
ADD COLUMN IF NOT EXISTS minimum_deposit_percent NUMERIC(7, 4) NOT NULL DEFAULT 0;

ALTER TABLE custom_order_lines
ADD COLUMN IF NOT EXISTS original_base_price NUMERIC(12, 2);

ALTER TABLE custom_order_lines
ADD COLUMN IF NOT EXISTS price_override_price NUMERIC(12, 2);

ALTER TABLE custom_order_lines
ADD COLUMN IF NOT EXISTS price_override_reason TEXT;

ALTER TABLE custom_order_lines
ADD COLUMN IF NOT EXISTS price_override_by_user_id INTEGER REFERENCES users(user_id);

ALTER TABLE custom_order_lines
ADD COLUMN IF NOT EXISTS price_override_by_name TEXT;

CREATE INDEX IF NOT EXISTS custom_order_lines_price_override_by_user_fk_idx
ON custom_order_lines(price_override_by_user_id)
WHERE price_override_by_user_id IS NOT NULL;

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

ALTER TABLE custom_order_lines
DROP CONSTRAINT IF EXISTS custom_order_lines_min_deposit_percent_chk;

ALTER TABLE custom_order_lines
ADD CONSTRAINT custom_order_lines_min_deposit_percent_chk
CHECK (minimum_deposit_percent >= 0 AND minimum_deposit_percent <= 100);

ALTER TABLE custom_order_lines
ADD COLUMN IF NOT EXISTS print_material_id BIGINT REFERENCES custom_order_print_materials(print_material_id);

ALTER TABLE custom_order_lines
ADD COLUMN IF NOT EXISTS print_material_name TEXT;

CREATE INDEX IF NOT EXISTS custom_order_lines_print_material_fk_idx
ON custom_order_lines(print_material_id)
WHERE print_material_id IS NOT NULL;

ALTER TABLE custom_order_lines
ADD COLUMN IF NOT EXISTS print_size_preset_id BIGINT REFERENCES custom_order_print_size_presets(print_size_preset_id);

CREATE INDEX IF NOT EXISTS custom_order_lines_print_size_preset_fk_idx
ON custom_order_lines(print_size_preset_id)
WHERE print_size_preset_id IS NOT NULL;

ALTER TABLE custom_order_lines
ADD COLUMN IF NOT EXISTS print_size_name TEXT;

ALTER TABLE custom_order_lines
ADD COLUMN IF NOT EXISTS print_charge NUMERIC(12, 2) NOT NULL DEFAULT 0;

ALTER TABLE custom_order_lines
ADD COLUMN IF NOT EXISTS print_line_count INTEGER NOT NULL DEFAULT 1;

UPDATE custom_order_lines
SET base_item_price = COALESCE(base_item_price, unit_price - COALESCE(print_charge, 0))
WHERE base_item_price IS NULL;

UPDATE custom_order_lines
SET original_line_total = COALESCE(original_line_total, line_total + COALESCE(line_discount_amount, 0))
WHERE original_line_total IS NULL;

UPDATE custom_order_lines
SET pricing_type = 'AREA'
WHERE pricing_type = 'SQUARE_FOOT';

ALTER TABLE custom_order_lines
DROP CONSTRAINT IF EXISTS custom_order_lines_pricing_type_chk;

ALTER TABLE custom_order_lines
ADD CONSTRAINT custom_order_lines_pricing_type_chk
CHECK (pricing_type IN ('FIXED', 'VARIABLE', 'AREA'));

INSERT INTO permissions (permission_key, permission_name)
SELECT 'CREATE_CUSTOM_ORDER', 'Create Custom Order'
WHERE NOT EXISTS (
    SELECT 1 FROM permissions WHERE UPPER(permission_key) = 'CREATE_CUSTOM_ORDER'
);

INSERT INTO permissions (permission_key, permission_name)
SELECT 'MANAGE_CUSTOM_ORDERS', 'Manage Custom Orders'
WHERE NOT EXISTS (
    SELECT 1 FROM permissions WHERE UPPER(permission_key) = 'MANAGE_CUSTOM_ORDERS'
);

INSERT INTO permissions (permission_key, permission_name)
SELECT 'VIEW_ASSIGNED_CUSTOM_ORDERS', 'View Assigned Custom Orders'
WHERE NOT EXISTS (
    SELECT 1 FROM permissions WHERE UPPER(permission_key) = 'VIEW_ASSIGNED_CUSTOM_ORDERS'
);

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.role_id, p.permission_id
FROM roles r
JOIN permissions p ON UPPER(p.permission_key) IN ('CREATE_CUSTOM_ORDER', 'MANAGE_CUSTOM_ORDERS', 'VIEW_ASSIGNED_CUSTOM_ORDERS')
WHERE UPPER(r.role_name) IN ('ADMIN', 'MANAGER')
  AND NOT EXISTS (
      SELECT 1
      FROM role_permissions rp
      WHERE rp.role_id = r.role_id
        AND rp.permission_id = p.permission_id
  );

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.role_id, p.permission_id
FROM roles r
JOIN permissions p ON UPPER(p.permission_key) IN ('CREATE_CUSTOM_ORDER', 'VIEW_ASSIGNED_CUSTOM_ORDERS')
WHERE UPPER(r.role_name) IN ('CASHIER', 'USER')
  AND NOT EXISTS (
      SELECT 1
      FROM role_permissions rp
      WHERE rp.role_id = r.role_id
        AND rp.permission_id = p.permission_id
  );

-- Custom order controls, audit trail, payment safety, and order reporting.
ALTER TABLE custom_orders
ADD COLUMN IF NOT EXISTS location_id INTEGER REFERENCES locations(location_id);

ALTER TABLE custom_orders
ADD COLUMN IF NOT EXISTS location_name TEXT;

CREATE INDEX IF NOT EXISTS custom_orders_location_idx
ON custom_orders(location_id, created_at DESC);

ALTER TABLE custom_orders
ADD COLUMN IF NOT EXISTS device_id TEXT;

ALTER TABLE custom_orders
ADD COLUMN IF NOT EXISTS device_name TEXT;

CREATE INDEX IF NOT EXISTS custom_orders_device_name_idx
ON custom_orders(device_name, created_at DESC);

ALTER TABLE custom_orders
ADD COLUMN IF NOT EXISTS cancellation_reason TEXT;

ALTER TABLE custom_orders
ADD COLUMN IF NOT EXISTS cancelled_at TIMESTAMPTZ;

ALTER TABLE custom_orders
ADD COLUMN IF NOT EXISTS cancelled_by_user_id INTEGER REFERENCES users(user_id);

ALTER TABLE custom_orders
ADD COLUMN IF NOT EXISTS cancelled_by_name TEXT;

ALTER TABLE custom_order_payments
ADD COLUMN IF NOT EXISTS payment_action TEXT NOT NULL DEFAULT 'PAYMENT';

ALTER TABLE custom_order_payments
ADD COLUMN IF NOT EXISTS voided_at TIMESTAMPTZ;

ALTER TABLE custom_order_payments
ADD COLUMN IF NOT EXISTS voided_by_user_id INTEGER REFERENCES users(user_id);

ALTER TABLE custom_order_payments
ADD COLUMN IF NOT EXISTS voided_by_name TEXT;

ALTER TABLE custom_order_payments
ADD COLUMN IF NOT EXISTS void_reason TEXT;

ALTER TABLE custom_order_payments
ADD COLUMN IF NOT EXISTS device_id TEXT;

ALTER TABLE custom_order_payments
ADD COLUMN IF NOT EXISTS device_name TEXT;

CREATE INDEX IF NOT EXISTS custom_order_payments_device_idx
ON custom_order_payments(device_id, created_at DESC);

CREATE INDEX IF NOT EXISTS custom_order_payments_device_name_idx
ON custom_order_payments(device_name, created_at DESC);

ALTER TABLE customer_account_transactions
ADD COLUMN IF NOT EXISTS device_id TEXT;

ALTER TABLE customer_account_transactions
ADD COLUMN IF NOT EXISTS device_name TEXT;

CREATE INDEX IF NOT EXISTS customer_account_transactions_device_idx
ON customer_account_transactions(device_id, created_at DESC);

CREATE INDEX IF NOT EXISTS customer_account_transactions_device_name_idx
ON customer_account_transactions(device_name, created_at DESC);

ALTER TABLE custom_order_payments
DROP CONSTRAINT IF EXISTS custom_order_payments_action_chk;

ALTER TABLE custom_order_payments
ADD CONSTRAINT custom_order_payments_action_chk
CHECK (payment_action IN ('PAYMENT', 'REFUND', 'REVERSAL'));

CREATE TABLE IF NOT EXISTS custom_order_audit_log (
    custom_order_audit_id BIGSERIAL PRIMARY KEY,
    custom_order_id BIGINT NOT NULL REFERENCES custom_orders(custom_order_id) ON DELETE CASCADE,
    action_type TEXT NOT NULL,
    field_name TEXT,
    old_value TEXT,
    new_value TEXT,
    reason TEXT,
    user_id INTEGER REFERENCES users(user_id),
    user_name TEXT,
    device_id TEXT,
    device_name TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE custom_order_audit_log
ADD COLUMN IF NOT EXISTS device_id TEXT;

ALTER TABLE custom_order_audit_log
ADD COLUMN IF NOT EXISTS device_name TEXT;

CREATE INDEX IF NOT EXISTS custom_order_audit_order_idx
ON custom_order_audit_log(custom_order_id, created_at DESC);

CREATE INDEX IF NOT EXISTS custom_order_audit_device_idx
ON custom_order_audit_log(device_id, created_at DESC);

CREATE INDEX IF NOT EXISTS custom_order_audit_device_name_idx
ON custom_order_audit_log(device_name, created_at DESC);

CREATE INDEX IF NOT EXISTS custom_order_audit_log_user_fk_idx
ON custom_order_audit_log(user_id)
WHERE user_id IS NOT NULL;

CREATE TABLE IF NOT EXISTS custom_order_status_history (
    custom_order_status_history_id BIGSERIAL PRIMARY KEY,
    custom_order_id BIGINT NOT NULL REFERENCES custom_orders(custom_order_id) ON DELETE CASCADE,
    old_status TEXT,
    new_status TEXT NOT NULL,
    reason TEXT,
    user_id INTEGER REFERENCES users(user_id),
    user_name TEXT,
    device_id TEXT,
    device_name TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE custom_order_status_history
ADD COLUMN IF NOT EXISTS device_id TEXT;

ALTER TABLE custom_order_status_history
ADD COLUMN IF NOT EXISTS device_name TEXT;

CREATE INDEX IF NOT EXISTS custom_order_status_history_order_idx
ON custom_order_status_history(custom_order_id, created_at DESC);

CREATE INDEX IF NOT EXISTS custom_order_status_history_device_idx
ON custom_order_status_history(device_id, created_at DESC);

CREATE INDEX IF NOT EXISTS custom_order_status_history_device_name_idx
ON custom_order_status_history(device_name, created_at DESC);

CREATE INDEX IF NOT EXISTS custom_order_status_history_user_fk_idx
ON custom_order_status_history(user_id)
WHERE user_id IS NOT NULL;

CREATE TABLE IF NOT EXISTS custom_order_inventory_reservations (
    custom_order_inventory_reservation_id BIGSERIAL PRIMARY KEY,
    custom_order_id BIGINT NOT NULL REFERENCES custom_orders(custom_order_id) ON DELETE CASCADE,
    custom_order_line_id BIGINT REFERENCES custom_order_lines(custom_order_line_id) ON DELETE CASCADE,
    custom_item_id BIGINT NOT NULL REFERENCES custom_order_items(custom_item_id),
    custom_variant_id BIGINT REFERENCES custom_order_item_variants(custom_variant_id),
    item_name TEXT NOT NULL,
    variant_name TEXT,
    reserved_qty NUMERIC(12, 2) NOT NULL DEFAULT 1,
    released_qty NUMERIC(12, 2) NOT NULL DEFAULT 0,
    status TEXT NOT NULL DEFAULT 'RESERVED',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    released_at TIMESTAMPTZ,
    release_reason TEXT,
    CONSTRAINT custom_order_inventory_reservations_status_chk
        CHECK (status IN ('RESERVED', 'RELEASED', 'CONSUMED'))
);

CREATE INDEX IF NOT EXISTS custom_order_inventory_reservations_order_idx
ON custom_order_inventory_reservations(custom_order_id, status);

CREATE INDEX IF NOT EXISTS custom_order_inventory_reservations_item_idx
ON custom_order_inventory_reservations(custom_item_id, custom_variant_id, status);

CREATE INDEX IF NOT EXISTS custom_order_inventory_reservations_line_fk_idx
ON custom_order_inventory_reservations(custom_order_line_id)
WHERE custom_order_line_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS custom_order_inventory_reservations_variant_fk_idx
ON custom_order_inventory_reservations(custom_variant_id)
WHERE custom_variant_id IS NOT NULL;

CREATE TABLE IF NOT EXISTS custom_order_line_returns (
    custom_order_line_return_id BIGSERIAL PRIMARY KEY,
    custom_order_id BIGINT NOT NULL REFERENCES custom_orders(custom_order_id) ON DELETE CASCADE,
    custom_order_line_id BIGINT NOT NULL REFERENCES custom_order_lines(custom_order_line_id) ON DELETE CASCADE,
    custom_item_id BIGINT REFERENCES custom_order_items(custom_item_id),
    custom_variant_id BIGINT REFERENCES custom_order_item_variants(custom_variant_id),
    item_name TEXT NOT NULL,
    variant_name TEXT,
    return_type TEXT NOT NULL DEFAULT 'FULL',
    restock_action TEXT NOT NULL DEFAULT 'NO_RESTOCK',
    refund_amount NUMERIC(12, 2) NOT NULL DEFAULT 0,
    balance_reduction NUMERIC(12, 2) NOT NULL DEFAULT 0,
    payout_amount NUMERIC(12, 2) NOT NULL DEFAULT 0,
    reason TEXT NOT NULL,
    notes TEXT,
    created_by_user_id INTEGER REFERENCES users(user_id),
    created_by_name TEXT,
    device_id TEXT,
    device_name TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT custom_order_line_returns_type_chk CHECK (return_type IN ('FULL', 'PARTIAL')),
    CONSTRAINT custom_order_line_returns_restock_chk CHECK (restock_action IN ('RESTOCK', 'DAMAGED', 'CUSTOMER_KEPT', 'WASTE', 'NO_RESTOCK')),
    CONSTRAINT custom_order_line_returns_amount_chk CHECK (refund_amount >= 0 AND balance_reduction >= 0 AND payout_amount >= 0)
);

CREATE INDEX IF NOT EXISTS custom_order_line_returns_order_idx
ON custom_order_line_returns(custom_order_id, created_at DESC);

CREATE INDEX IF NOT EXISTS custom_order_line_returns_line_idx
ON custom_order_line_returns(custom_order_line_id, created_at DESC);

CREATE INDEX IF NOT EXISTS custom_order_line_returns_device_idx
ON custom_order_line_returns(device_id, created_at DESC);

CREATE INDEX IF NOT EXISTS custom_order_line_returns_device_name_idx
ON custom_order_line_returns(device_name, created_at DESC);

CREATE INDEX IF NOT EXISTS custom_order_line_returns_created_by_user_fk_idx
ON custom_order_line_returns(created_by_user_id)
WHERE created_by_user_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS custom_order_line_returns_custom_item_fk_idx
ON custom_order_line_returns(custom_item_id)
WHERE custom_item_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS custom_order_line_returns_custom_variant_fk_idx
ON custom_order_line_returns(custom_variant_id)
WHERE custom_variant_id IS NOT NULL;

ALTER TABLE custom_order_item_movements
ADD COLUMN IF NOT EXISTS custom_order_id BIGINT REFERENCES custom_orders(custom_order_id);

ALTER TABLE custom_order_item_movements
ADD COLUMN IF NOT EXISTS custom_order_line_id BIGINT REFERENCES custom_order_lines(custom_order_line_id);

ALTER TABLE custom_order_item_movements
ADD COLUMN IF NOT EXISTS custom_order_line_return_id BIGINT REFERENCES custom_order_line_returns(custom_order_line_return_id);

CREATE INDEX IF NOT EXISTS custom_order_item_movements_order_idx
ON custom_order_item_movements(custom_order_id, created_at DESC);

CREATE INDEX IF NOT EXISTS custom_order_item_movements_line_idx
ON custom_order_item_movements(custom_order_line_id, created_at DESC);

CREATE INDEX IF NOT EXISTS custom_order_item_movements_line_return_idx
ON custom_order_item_movements(custom_order_line_return_id, created_at DESC);

CREATE TABLE IF NOT EXISTS custom_order_line_deliveries (
    custom_order_line_delivery_id BIGSERIAL PRIMARY KEY,
    custom_order_id BIGINT NOT NULL REFERENCES custom_orders(custom_order_id) ON DELETE CASCADE,
    custom_order_line_id BIGINT NOT NULL REFERENCES custom_order_lines(custom_order_line_id) ON DELETE CASCADE,
    custom_item_id BIGINT REFERENCES custom_order_items(custom_item_id),
    custom_variant_id BIGINT REFERENCES custom_order_item_variants(custom_variant_id),
    item_name TEXT NOT NULL,
    variant_name TEXT,
    delivered_by_user_id INTEGER REFERENCES users(user_id),
    delivered_by_name TEXT,
    delivery_notes TEXT,
    device_id TEXT,
    device_name TEXT,
    delivered_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS custom_order_line_deliveries_order_idx
ON custom_order_line_deliveries(custom_order_id, delivered_at DESC);

CREATE INDEX IF NOT EXISTS custom_order_line_deliveries_line_idx
ON custom_order_line_deliveries(custom_order_line_id, delivered_at DESC);

CREATE INDEX IF NOT EXISTS custom_order_line_deliveries_device_idx
ON custom_order_line_deliveries(device_id, delivered_at DESC);

CREATE INDEX IF NOT EXISTS custom_order_line_deliveries_device_name_idx
ON custom_order_line_deliveries(device_name, delivered_at DESC);

CREATE INDEX IF NOT EXISTS custom_order_line_deliveries_custom_item_fk_idx
ON custom_order_line_deliveries(custom_item_id)
WHERE custom_item_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS custom_order_line_deliveries_custom_variant_fk_idx
ON custom_order_line_deliveries(custom_variant_id)
WHERE custom_variant_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS custom_order_line_deliveries_delivered_by_user_fk_idx
ON custom_order_line_deliveries(delivered_by_user_id)
WHERE delivered_by_user_id IS NOT NULL;

INSERT INTO permissions (permission_key, permission_name)
SELECT 'CUSTOM_ORDER_OVERRIDES', 'Custom Order Overrides'
WHERE NOT EXISTS (
    SELECT 1 FROM permissions WHERE UPPER(permission_key) = 'CUSTOM_ORDER_OVERRIDES'
);

INSERT INTO permissions (permission_key, permission_name)
SELECT 'ORDERS_MANAGER_DASHBOARD', 'Orders Manager Dashboard'
WHERE NOT EXISTS (
    SELECT 1 FROM permissions WHERE UPPER(permission_key) = 'ORDERS_MANAGER_DASHBOARD'
);

UPDATE permissions
SET permission_name = 'Orders Manager Dashboard'
WHERE UPPER(permission_key) = 'ORDERS_MANAGER_DASHBOARD';

INSERT INTO role_permissions (role_id, permission_id)
SELECT rp.role_id, new_perm.permission_id
FROM role_permissions rp
JOIN permissions old_perm ON old_perm.permission_id = rp.permission_id
JOIN permissions new_perm ON UPPER(new_perm.permission_key) = 'ORDERS_MANAGER_DASHBOARD'
WHERE UPPER(old_perm.permission_key) = 'MANAGER_DASHBOARD'
  AND NOT EXISTS (
      SELECT 1
      FROM role_permissions existing
      WHERE existing.role_id = rp.role_id
        AND existing.permission_id = new_perm.permission_id
  );

INSERT INTO permissions (permission_key, permission_name)
SELECT 'ORDERS_END_OF_DAY', 'Order Reports'
WHERE NOT EXISTS (
    SELECT 1 FROM permissions WHERE UPPER(permission_key) = 'ORDERS_END_OF_DAY'
);

INSERT INTO permissions (permission_key, permission_name)
SELECT 'CUSTOM_ORDER_REFUNDS', 'Custom Order Refunds'
WHERE NOT EXISTS (
    SELECT 1 FROM permissions WHERE UPPER(permission_key) = 'CUSTOM_ORDER_REFUNDS'
);

INSERT INTO permissions (permission_key, permission_name)
SELECT 'CUSTOM_ORDER_LINE_RETURNS', 'Custom Order Line Returns'
WHERE NOT EXISTS (
    SELECT 1 FROM permissions WHERE UPPER(permission_key) = 'CUSTOM_ORDER_LINE_RETURNS'
);

INSERT INTO permissions (permission_key, permission_name)
SELECT 'CUSTOM_ORDER_LINE_DELIVERY', 'Custom Order Line Delivery'
WHERE NOT EXISTS (
    SELECT 1 FROM permissions WHERE UPPER(permission_key) = 'CUSTOM_ORDER_LINE_DELIVERY'
);

INSERT INTO permissions (permission_key, permission_name)
SELECT 'CUSTOM_ORDER_LINE_DISCOUNT', 'Custom Order Line Discount'
WHERE NOT EXISTS (
    SELECT 1 FROM permissions WHERE UPPER(permission_key) = 'CUSTOM_ORDER_LINE_DISCOUNT'
);

INSERT INTO permissions (permission_key, permission_name)
SELECT 'CUSTOM_ORDER_DEPOSIT_OVERRIDE', 'Custom Order Deposit Override'
WHERE NOT EXISTS (
    SELECT 1 FROM permissions WHERE UPPER(permission_key) = 'CUSTOM_ORDER_DEPOSIT_OVERRIDE'
);

INSERT INTO permissions (permission_key, permission_name)
SELECT 'CUSTOM_ORDER_DEPOSIT_SETTINGS', 'Custom Order Deposit Settings'
WHERE NOT EXISTS (
    SELECT 1 FROM permissions WHERE UPPER(permission_key) = 'CUSTOM_ORDER_DEPOSIT_SETTINGS'
);

INSERT INTO permissions (permission_key, permission_name)
SELECT 'CUSTOM_ORDER_CANCEL', 'Cancel Custom Orders'
WHERE NOT EXISTS (
    SELECT 1 FROM permissions WHERE UPPER(permission_key) = 'CUSTOM_ORDER_CANCEL'
);

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.role_id, p.permission_id
FROM roles r
JOIN permissions p ON UPPER(p.permission_key) IN ('CUSTOM_ORDER_OVERRIDES', 'ORDERS_MANAGER_DASHBOARD', 'ORDERS_END_OF_DAY', 'CUSTOM_ORDER_REFUNDS', 'CUSTOM_ORDER_LINE_RETURNS', 'CUSTOM_ORDER_LINE_DELIVERY', 'CUSTOM_ORDER_LINE_DISCOUNT', 'CUSTOM_ORDER_DEPOSIT_OVERRIDE', 'CUSTOM_ORDER_DEPOSIT_SETTINGS', 'CUSTOM_ORDER_CANCEL')
WHERE UPPER(r.role_name) IN ('ADMIN', 'MANAGER')
  AND NOT EXISTS (
      SELECT 1
      FROM role_permissions rp
      WHERE rp.role_id = r.role_id
        AND rp.permission_id = p.permission_id
  );

-- Generated SKU support for custom order items and variants.
ALTER TABLE custom_order_items
ADD COLUMN IF NOT EXISTS sku TEXT;

ALTER TABLE custom_order_item_variants
ADD COLUMN IF NOT EXISTS sku TEXT;

CREATE OR REPLACE FUNCTION custom_order_words(input_name TEXT)
RETURNS TEXT[] AS $$
DECLARE
    result TEXT[];
BEGIN
    SELECT ARRAY_AGG(word ORDER BY ord)
    INTO result
    FROM REGEXP_SPLIT_TO_TABLE(UPPER(COALESCE(input_name, '')), '[^A-Z0-9]+') WITH ORDINALITY AS parts(word, ord)
    WHERE word <> ''
      AND word NOT IN ('A', 'AN', 'AND', 'FOR', 'IN', 'OF', 'THE', 'TO', 'WITH');

    RETURN COALESCE(result, ARRAY[]::TEXT[]);
END;
$$ LANGUAGE plpgsql IMMUTABLE SET search_path = public, pg_temp;

CREATE OR REPLACE FUNCTION custom_order_abbreviate_word(input_word TEXT)
RETURNS TEXT AS $$
DECLARE
    word TEXT := UPPER(COALESCE(input_word, ''));
    result TEXT;
BEGIN
    IF word = '' THEN
        RETURN '';
    END IF;

    result := CASE word
        WHEN 'ADHESIVE' THEN 'ADH'
        WHEN 'BANNER' THEN 'BNR'
        WHEN 'BOTTLE' THEN 'BTL'
        WHEN 'CANVAS' THEN 'CNV'
        WHEN 'GLOSSY' THEN 'GLSY'
        WHEN 'MARKER' THEN 'MRKR'
        WHEN 'MATTE' THEN 'MAT'
        WHEN 'MEDIUM' THEN 'MED'
        WHEN 'PAPER' THEN 'PPR'
        WHEN 'PEN' THEN 'PEN'
        WHEN 'PENCIL' THEN 'PNCL'
        WHEN 'PURPLE' THEN 'PRPL'
        WHEN 'SHIRT' THEN 'SHRT'
        WHEN 'SMALL' THEN 'SML'
        WHEN 'STICKER' THEN 'STKR'
        WHEN 'VINYL' THEN 'VNL'
        ELSE NULL
    END;

    IF result IS NOT NULL THEN
        RETURN result;
    END IF;
    IF LENGTH(word) <= 4 THEN
        RETURN word;
    END IF;

    result := SUBSTRING(word FROM 1 FOR 1)
        || SUBSTRING(REGEXP_REPLACE(SUBSTRING(word FROM 2), '[AEIOU]', '', 'g') FROM 1 FOR 3);
    IF LENGTH(result) < 4 THEN
        result := result || SUBSTRING(REGEXP_REPLACE(SUBSTRING(word FROM 2), '[^AEIOU]', '', 'g') FROM 1 FOR 4 - LENGTH(result));
    END IF;
    RETURN SUBSTRING(result FROM 1 FOR 4);
END;
$$ LANGUAGE plpgsql IMMUTABLE SET search_path = public, pg_temp;

CREATE OR REPLACE FUNCTION custom_order_right_size(input_value TEXT, input_words TEXT[])
RETURNS TEXT AS $$
DECLARE
    sized TEXT := COALESCE(input_value, '');
    word TEXT;
    i INTEGER;
    ch TEXT;
BEGIN
    FOREACH word IN ARRAY COALESCE(input_words, ARRAY[]::TEXT[]) LOOP
        i := 1;
        WHILE i <= LENGTH(word) AND LENGTH(sized) < 3 LOOP
            ch := SUBSTRING(word FROM i FOR 1);
            IF POSITION(ch IN sized) = 0 THEN
                sized := sized || ch;
            END IF;
            i := i + 1;
        END LOOP;
    END LOOP;

    i := 1;
    WHILE i <= LENGTH('ITEM') AND LENGTH(sized) < 3 LOOP
        sized := sized || SUBSTRING('ITEM' FROM i FOR 1);
        i := i + 1;
    END LOOP;

    RETURN SUBSTRING(sized FROM 1 FOR LEAST(4, LENGTH(sized)));
END;
$$ LANGUAGE plpgsql IMMUTABLE SET search_path = public, pg_temp;

CREATE OR REPLACE FUNCTION custom_order_sku_prefix(input_name TEXT)
RETURNS TEXT AS $$
DECLARE
    words TEXT[] := custom_order_words(input_name);
    word_count INTEGER := COALESCE(ARRAY_LENGTH(words, 1), 0);
    initials TEXT;
    first_part TEXT;
    second_part TEXT;
BEGIN
    IF word_count = 0 THEN
        RETURN 'ITEM';
    END IF;
    IF word_count = 1 THEN
        RETURN custom_order_abbreviate_word(words[1]);
    END IF;

    SELECT STRING_AGG(SUBSTRING(word FROM 1 FOR 1), '' ORDER BY ord)
    INTO initials
    FROM UNNEST(words) WITH ORDINALITY AS parts(word, ord);
    IF LENGTH(initials) >= 3 THEN
        RETURN SUBSTRING(initials FROM 1 FOR LEAST(4, LENGTH(initials)));
    END IF;

    first_part := custom_order_abbreviate_word(words[1]);
    second_part := custom_order_abbreviate_word(words[2]);
    RETURN custom_order_right_size(
        SUBSTRING(first_part FROM 1 FOR LEAST(2, LENGTH(first_part)))
        || SUBSTRING(second_part FROM 1 FOR LEAST(2, LENGTH(second_part))),
        words
    );
END;
$$ LANGUAGE plpgsql IMMUTABLE SET search_path = public, pg_temp;

CREATE OR REPLACE FUNCTION custom_order_variant_sku_prefix(input_item_name TEXT, input_variant_name TEXT)
RETURNS TEXT AS $$
DECLARE
    item_words TEXT[] := custom_order_words(input_item_name);
    variant_words TEXT[] := custom_order_words(input_variant_name);
    item_count INTEGER := COALESCE(ARRAY_LENGTH(item_words, 1), 0);
    variant_count INTEGER := COALESCE(ARRAY_LENGTH(variant_words, 1), 0);
    first_part TEXT;
    second_part TEXT;
BEGIN
    IF variant_count = 0 THEN
        RETURN custom_order_sku_prefix(input_item_name);
    END IF;
    IF item_count = 0 THEN
        RETURN custom_order_sku_prefix(input_variant_name);
    END IF;

    first_part := custom_order_abbreviate_word(item_words[1]);
    second_part := custom_order_abbreviate_word(variant_words[1]);
    RETURN custom_order_right_size(
        SUBSTRING(first_part FROM 1 FOR LEAST(2, LENGTH(first_part)))
        || SUBSTRING(second_part FROM 1 FOR LEAST(2, LENGTH(second_part))),
        item_words || variant_words
    );
END;
$$ LANGUAGE plpgsql IMMUTABLE SET search_path = public, pg_temp;

CREATE OR REPLACE FUNCTION custom_order_item_sku(input_name TEXT)
RETURNS TEXT AS $$
BEGIN
    RETURN custom_order_sku_prefix(input_name) || '-0001';
END;
$$ LANGUAGE plpgsql IMMUTABLE SET search_path = public, pg_temp;

CREATE OR REPLACE FUNCTION custom_order_variant_sku(input_item_name TEXT, input_variant_name TEXT)
RETURNS TEXT AS $$
BEGIN
    RETURN custom_order_variant_sku_prefix(input_item_name, input_variant_name) || '-0001';
END;
$$ LANGUAGE plpgsql IMMUTABLE SET search_path = public, pg_temp;

CREATE OR REPLACE FUNCTION custom_order_next_item_sku(input_prefix TEXT, current_item_id BIGINT)
RETURNS TEXT AS $$
DECLARE
    existing_sku TEXT;
    matches TEXT[];
    max_number INTEGER := 0;
BEGIN
    FOR existing_sku IN
        SELECT sku
        FROM custom_order_items
        WHERE UPPER(sku) LIKE UPPER(input_prefix) || '-%'
          AND custom_item_id <> COALESCE(current_item_id, -1)
    LOOP
        matches := REGEXP_MATCH(existing_sku, '^' || input_prefix || '-([0-9]+)$', 'i');
        IF matches IS NOT NULL THEN
            max_number := GREATEST(max_number, matches[1]::INTEGER);
        END IF;
    END LOOP;

    RETURN input_prefix || '-' || LPAD((max_number + 1)::TEXT, 4, '0');
END;
$$ LANGUAGE plpgsql SET search_path = public, pg_temp;

CREATE OR REPLACE FUNCTION custom_order_next_variant_sku(input_prefix TEXT, current_variant_id BIGINT)
RETURNS TEXT AS $$
DECLARE
    existing_sku TEXT;
    matches TEXT[];
    max_number INTEGER := 0;
BEGIN
    FOR existing_sku IN
        SELECT sku
        FROM custom_order_item_variants
        WHERE UPPER(sku) LIKE UPPER(input_prefix) || '-%'
          AND custom_variant_id <> COALESCE(current_variant_id, -1)
    LOOP
        matches := REGEXP_MATCH(existing_sku, '^' || input_prefix || '-([0-9]+)$', 'i');
        IF matches IS NOT NULL THEN
            max_number := GREATEST(max_number, matches[1]::INTEGER);
        END IF;
    END LOOP;

    RETURN input_prefix || '-' || LPAD((max_number + 1)::TEXT, 4, '0');
END;
$$ LANGUAGE plpgsql SET search_path = public, pg_temp;

CREATE OR REPLACE FUNCTION set_custom_order_item_sku()
RETURNS TRIGGER AS $$
BEGIN
    NEW.sku := custom_order_next_item_sku(custom_order_sku_prefix(NEW.item_name), NEW.custom_item_id);
    RETURN NEW;
END;
$$ LANGUAGE plpgsql SET search_path = public, pg_temp;

CREATE OR REPLACE FUNCTION set_custom_order_variant_sku()
RETURNS TRIGGER AS $$
DECLARE
    parent_name TEXT;
BEGIN
    SELECT item_name
    INTO parent_name
    FROM custom_order_items
    WHERE custom_item_id = NEW.custom_item_id;

    NEW.sku := custom_order_next_variant_sku(custom_order_variant_sku_prefix(parent_name, NEW.variant_name), NEW.custom_variant_id);
    RETURN NEW;
END;
$$ LANGUAGE plpgsql SET search_path = public, pg_temp;

CREATE OR REPLACE FUNCTION refresh_custom_order_variant_skus_for_item()
RETURNS TRIGGER AS $$
BEGIN
    IF TG_OP = 'INSERT' OR NEW.item_name IS DISTINCT FROM OLD.item_name THEN
        UPDATE custom_order_item_variants
        SET variant_name = variant_name,
            updated_at = CURRENT_TIMESTAMP
        WHERE custom_item_id = NEW.custom_item_id;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql SET search_path = public, pg_temp;

DROP TRIGGER IF EXISTS custom_order_items_set_sku ON custom_order_items;
CREATE TRIGGER custom_order_items_set_sku
BEFORE INSERT OR UPDATE OF item_name ON custom_order_items
FOR EACH ROW
EXECUTE FUNCTION set_custom_order_item_sku();

DROP TRIGGER IF EXISTS custom_order_item_variants_set_sku ON custom_order_item_variants;
CREATE TRIGGER custom_order_item_variants_set_sku
BEFORE INSERT OR UPDATE OF variant_name, custom_item_id ON custom_order_item_variants
FOR EACH ROW
EXECUTE FUNCTION set_custom_order_variant_sku();

DROP TRIGGER IF EXISTS custom_order_items_refresh_variant_skus ON custom_order_items;
CREATE TRIGGER custom_order_items_refresh_variant_skus
AFTER INSERT OR UPDATE OF item_name ON custom_order_items
FOR EACH ROW
EXECUTE FUNCTION refresh_custom_order_variant_skus_for_item();

UPDATE custom_order_items
SET item_name = item_name;

UPDATE custom_order_item_variants
SET variant_name = variant_name;

CREATE UNIQUE INDEX IF NOT EXISTS custom_order_items_sku_uidx
ON custom_order_items(UPPER(sku))
WHERE sku IS NOT NULL AND sku <> '';

CREATE UNIQUE INDEX IF NOT EXISTS custom_order_item_variants_sku_uidx
ON custom_order_item_variants(UPPER(sku))
WHERE sku IS NOT NULL AND sku <> '';

CREATE INDEX IF NOT EXISTS custom_order_items_sku_search_idx
ON custom_order_items(sku);

CREATE INDEX IF NOT EXISTS custom_order_item_variants_sku_search_idx
ON custom_order_item_variants(sku);
