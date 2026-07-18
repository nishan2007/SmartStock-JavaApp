-- Quotations and Invoices workflow.
-- Quotations are non-final B2B documents. Accepted quotations create separate invoices.

CREATE EXTENSION IF NOT EXISTS pgcrypto;

ALTER TABLE company_customization
ADD COLUMN IF NOT EXISTS next_quotation_counter INTEGER NOT NULL DEFAULT 1;

ALTER TABLE company_customization
ADD COLUMN IF NOT EXISTS next_invoice_counter INTEGER NOT NULL DEFAULT 1;

ALTER TABLE company_customization
ADD COLUMN IF NOT EXISTS next_invoice_delivery_counter INTEGER NOT NULL DEFAULT 1;

ALTER TABLE company_customization
ADD COLUMN IF NOT EXISTS quotation_default_valid_days INTEGER NOT NULL DEFAULT 30;

ALTER TABLE locations
ADD COLUMN IF NOT EXISTS company_address_line1 TEXT NOT NULL DEFAULT '';

ALTER TABLE locations
ADD COLUMN IF NOT EXISTS company_address_line2 TEXT NOT NULL DEFAULT '';

ALTER TABLE locations
ADD COLUMN IF NOT EXISTS company_address_line3 TEXT NOT NULL DEFAULT '';

ALTER TABLE locations
ADD COLUMN IF NOT EXISTS company_phone_line1 TEXT NOT NULL DEFAULT '';

ALTER TABLE locations
ADD COLUMN IF NOT EXISTS company_phone_line2 TEXT NOT NULL DEFAULT '';

ALTER TABLE locations
ADD COLUMN IF NOT EXISTS company_email_line1 TEXT NOT NULL DEFAULT '';

ALTER TABLE locations
ADD COLUMN IF NOT EXISTS company_email_line2 TEXT NOT NULL DEFAULT '';

ALTER TABLE company_customization
ADD COLUMN IF NOT EXISTS quotation_print_title TEXT NOT NULL DEFAULT 'QUOTE / NOT FINAL SALE';

ALTER TABLE company_customization
ADD COLUMN IF NOT EXISTS quotation_print_validity_note TEXT NOT NULL DEFAULT 'This is a quote only and is not a final sale. Prices are valid until the valid-until date shown above unless superseded or cancelled.';

ALTER TABLE company_customization
ADD COLUMN IF NOT EXISTS invoice_print_title TEXT NOT NULL DEFAULT 'SALES ORDER CONFIRMATION';

ALTER TABLE company_customization
ADD COLUMN IF NOT EXISTS invoice_delivery_print_title TEXT NOT NULL DEFAULT 'DELIVERY BILL';

ALTER TABLE company_customization
ADD COLUMN IF NOT EXISTS quotation_invoice_print_footer_note TEXT NOT NULL DEFAULT '';

ALTER TABLE company_customization
ADD COLUMN IF NOT EXISTS quotation_invoice_print_show_signatures BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE company_customization
ALTER COLUMN quotation_print_title SET DEFAULT 'QUOTE / NOT FINAL SALE';

ALTER TABLE company_customization
ALTER COLUMN quotation_print_validity_note SET DEFAULT 'This is a quote only and is not a final sale. Prices are valid until the valid-until date shown above unless superseded or cancelled.';

ALTER TABLE company_customization
ALTER COLUMN invoice_print_title SET DEFAULT 'SALES ORDER CONFIRMATION';

UPDATE company_customization
SET quotation_print_title = 'QUOTE / NOT FINAL SALE',
    updated_at = NOW()
WHERE quotation_print_title = 'QUOTATION / NOT FINAL SALE';

UPDATE company_customization
SET quotation_print_validity_note = 'This is a quote only and is not a final sale. Prices are valid until the valid-until date shown above unless superseded or cancelled.',
    updated_at = NOW()
WHERE quotation_print_validity_note = 'This is a quotation only and is not a final sale. Prices are valid until the valid-until date shown above unless superseded or cancelled.';

UPDATE company_customization
SET invoice_print_title = 'SALES ORDER CONFIRMATION',
    updated_at = NOW()
WHERE invoice_print_title = 'INVOICE';

ALTER TABLE company_customization
DROP CONSTRAINT IF EXISTS company_customization_quotation_days_chk;

ALTER TABLE company_customization
ADD CONSTRAINT company_customization_quotation_days_chk
CHECK (quotation_default_valid_days BETWEEN 1 AND 365);

CREATE TABLE IF NOT EXISTS quotations (
    quotation_id BIGSERIAL PRIMARY KEY,
    quotation_number TEXT NOT NULL UNIQUE,
    customer_id INTEGER NOT NULL REFERENCES customer_accounts(customer_id),
    customer_name TEXT NOT NULL,
    customer_phone TEXT,
    customer_email TEXT,
    status TEXT NOT NULL DEFAULT 'DRAFT',
    issue_date DATE NOT NULL DEFAULT CURRENT_DATE,
    valid_until DATE NOT NULL DEFAULT (CURRENT_DATE + 30),
    quotation_notes TEXT,
    subtotal_amount NUMERIC(12, 2) NOT NULL DEFAULT 0,
    discount_amount NUMERIC(12, 2) NOT NULL DEFAULT 0,
    vat_amount NUMERIC(12, 2) NOT NULL DEFAULT 0,
    vat_rate_percent NUMERIC(6, 2) NOT NULL DEFAULT 0,
    vat_mode TEXT NOT NULL DEFAULT '',
    total_amount NUMERIC(12, 2) NOT NULL DEFAULT 0,
    accepted_at TIMESTAMPTZ,
    accepted_by_user_id INTEGER REFERENCES users(user_id),
    accepted_by_name TEXT,
    superseded_by_quotation_id BIGINT REFERENCES quotations(quotation_id),
    location_id INTEGER REFERENCES locations(location_id),
    location_name TEXT,
    device_id TEXT,
    device_name TEXT,
    created_by_user_id INTEGER REFERENCES users(user_id),
    created_by_name TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT quotations_status_chk
        CHECK (status IN ('DRAFT', 'ISSUED', 'ACCEPTED', 'EXPIRED', 'CANCELLED', 'SUPERSEDED')),
    CONSTRAINT quotations_amounts_chk
        CHECK (subtotal_amount >= 0 AND discount_amount >= 0 AND vat_amount >= 0 AND total_amount >= 0)
);

CREATE TABLE IF NOT EXISTS quotation_lines (
    quotation_line_id BIGSERIAL PRIMARY KEY,
    sync_uuid UUID NOT NULL DEFAULT gen_random_uuid(),
    quotation_id BIGINT NOT NULL REFERENCES quotations(quotation_id) ON DELETE CASCADE,
    product_id INTEGER REFERENCES products(product_id),
    item_name TEXT NOT NULL,
    sku TEXT,
    quantity INTEGER NOT NULL DEFAULT 1,
    unit_price NUMERIC(12, 2) NOT NULL DEFAULT 0,
    original_unit_price NUMERIC(12, 2),
    price_override_reason TEXT,
    price_override_by_user_id INTEGER REFERENCES users(user_id),
    price_override_by_name TEXT,
    category_id INTEGER REFERENCES categories(category_id),
    discount_percent NUMERIC(7, 4) NOT NULL DEFAULT 0,
    discount_amount NUMERIC(12, 2) NOT NULL DEFAULT 0,
    vat_rate_percent NUMERIC(6, 2) NOT NULL DEFAULT 0,
    vat_amount NUMERIC(12, 2) NOT NULL DEFAULT 0,
    line_total NUMERIC(12, 2) NOT NULL DEFAULT 0,
    delivery_method TEXT NOT NULL DEFAULT 'PICKUP',
    line_notes TEXT,
    sort_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT quotation_lines_qty_chk CHECK (quantity > 0),
    CONSTRAINT quotation_lines_delivery_method_chk
        CHECK (delivery_method IN ('PICKUP', 'LOCAL_DELIVERY', 'SHIP', 'INSTALLATION')),
    CONSTRAINT quotation_lines_discount_percent_chk CHECK (discount_percent >= 0 AND discount_percent <= 100),
    CONSTRAINT quotation_lines_vat_chk CHECK (vat_rate_percent >= 0 AND vat_rate_percent <= 100 AND vat_amount >= 0),
    CONSTRAINT quotation_lines_amounts_chk
        CHECK (unit_price >= 0 AND discount_amount >= 0 AND line_total >= 0)
);

CREATE TABLE IF NOT EXISTS invoices (
    invoice_id BIGSERIAL PRIMARY KEY,
    invoice_number TEXT NOT NULL UNIQUE,
    quotation_id BIGINT REFERENCES quotations(quotation_id),
    quotation_number TEXT,
    customer_id INTEGER NOT NULL REFERENCES customer_accounts(customer_id),
    customer_name TEXT NOT NULL,
    customer_phone TEXT,
    customer_email TEXT,
    status TEXT NOT NULL DEFAULT 'OPEN',
    invoice_date DATE NOT NULL DEFAULT CURRENT_DATE,
    invoice_notes TEXT,
    subtotal_amount NUMERIC(12, 2) NOT NULL DEFAULT 0,
    discount_amount NUMERIC(12, 2) NOT NULL DEFAULT 0,
    vat_amount NUMERIC(12, 2) NOT NULL DEFAULT 0,
    vat_rate_percent NUMERIC(6, 2) NOT NULL DEFAULT 0,
    vat_mode TEXT NOT NULL DEFAULT '',
    total_amount NUMERIC(12, 2) NOT NULL DEFAULT 0,
    amount_paid NUMERIC(12, 2) NOT NULL DEFAULT 0,
    balance_due NUMERIC(12, 2) NOT NULL DEFAULT 0,
    payment_status TEXT NOT NULL DEFAULT 'UNPAID',
    payment_method TEXT,
    payment_reference TEXT,
    delivered_at TIMESTAMPTZ,
    location_id INTEGER REFERENCES locations(location_id),
    location_name TEXT,
    device_id TEXT,
    device_name TEXT,
    cash_drawer_id BIGINT,
    cash_drawer_name TEXT,
    cash_drawer_session_id BIGINT,
    created_by_user_id INTEGER REFERENCES users(user_id),
    created_by_name TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT invoices_status_chk
        CHECK (status IN ('OPEN', 'PARTIALLY_DELIVERED', 'DELIVERED', 'CANCELLED')),
    CONSTRAINT invoices_payment_status_chk CHECK (payment_status IN ('UNPAID', 'PARTIAL', 'PAID')),
    CONSTRAINT invoices_payment_method_chk
        CHECK (payment_method IS NULL OR payment_method IN ('CASH', 'CARD', 'CHEQUE', 'MMG', 'ACCOUNT')),
    CONSTRAINT invoices_amounts_chk
        CHECK (subtotal_amount >= 0 AND discount_amount >= 0 AND vat_amount >= 0
            AND total_amount >= 0 AND amount_paid >= 0 AND balance_due >= 0)
);

CREATE TABLE IF NOT EXISTS invoice_lines (
    invoice_line_id BIGSERIAL PRIMARY KEY,
    sync_uuid UUID NOT NULL DEFAULT gen_random_uuid(),
    invoice_id BIGINT NOT NULL REFERENCES invoices(invoice_id) ON DELETE CASCADE,
    quotation_line_id BIGINT REFERENCES quotation_lines(quotation_line_id),
    product_id INTEGER REFERENCES products(product_id),
    item_name TEXT NOT NULL,
    sku TEXT,
    quantity_invoiced INTEGER NOT NULL DEFAULT 1,
    quantity_delivered INTEGER NOT NULL DEFAULT 0,
    unit_price NUMERIC(12, 2) NOT NULL DEFAULT 0,
    original_unit_price NUMERIC(12, 2),
    price_override_reason TEXT,
    price_override_by_user_id INTEGER REFERENCES users(user_id),
    price_override_by_name TEXT,
    category_id INTEGER REFERENCES categories(category_id),
    discount_percent NUMERIC(7, 4) NOT NULL DEFAULT 0,
    discount_amount NUMERIC(12, 2) NOT NULL DEFAULT 0,
    vat_rate_percent NUMERIC(6, 2) NOT NULL DEFAULT 0,
    vat_amount NUMERIC(12, 2) NOT NULL DEFAULT 0,
    line_total NUMERIC(12, 2) NOT NULL DEFAULT 0,
    delivery_method TEXT NOT NULL DEFAULT 'PICKUP',
    delivery_status TEXT NOT NULL DEFAULT 'PENDING',
    line_notes TEXT,
    sort_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT invoice_lines_qty_chk
        CHECK (quantity_invoiced > 0 AND quantity_delivered >= 0 AND quantity_delivered <= quantity_invoiced),
    CONSTRAINT invoice_lines_delivery_method_chk
        CHECK (delivery_method IN ('PICKUP', 'LOCAL_DELIVERY', 'SHIP', 'INSTALLATION')),
    CONSTRAINT invoice_lines_delivery_status_chk
        CHECK (delivery_status IN ('PENDING', 'PARTIAL', 'DELIVERED')),
    CONSTRAINT invoice_lines_discount_percent_chk CHECK (discount_percent >= 0 AND discount_percent <= 100),
    CONSTRAINT invoice_lines_vat_chk CHECK (vat_rate_percent >= 0 AND vat_rate_percent <= 100 AND vat_amount >= 0),
    CONSTRAINT invoice_lines_amounts_chk
        CHECK (unit_price >= 0 AND discount_amount >= 0 AND line_total >= 0)
);

CREATE TABLE IF NOT EXISTS invoice_payments (
    invoice_payment_id BIGSERIAL PRIMARY KEY,
    sync_uuid UUID NOT NULL DEFAULT gen_random_uuid(),
    invoice_id BIGINT NOT NULL REFERENCES invoices(invoice_id) ON DELETE CASCADE,
    customer_id INTEGER REFERENCES customer_accounts(customer_id),
    payment_amount NUMERIC(12, 2) NOT NULL,
    payment_method TEXT NOT NULL,
    payment_reference TEXT,
    payment_action TEXT NOT NULL DEFAULT 'PAYMENT',
    voided_at TIMESTAMPTZ,
    voided_by_user_id INTEGER REFERENCES users(user_id),
    voided_by_name TEXT,
    void_reason TEXT,
    taken_by_user_id INTEGER REFERENCES users(user_id),
    taken_by_name TEXT,
    location_id INTEGER REFERENCES locations(location_id),
    device_id TEXT,
    device_name TEXT,
    cash_drawer_id BIGINT,
    cash_drawer_name TEXT,
    cash_drawer_session_id BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT invoice_payments_amount_chk CHECK (payment_amount > 0),
    CONSTRAINT invoice_payments_method_chk CHECK (payment_method IN ('CASH', 'CARD', 'CHEQUE', 'MMG', 'ACCOUNT')),
    CONSTRAINT invoice_payments_action_chk CHECK (payment_action IN ('PAYMENT', 'REFUND', 'REVERSAL'))
);

CREATE TABLE IF NOT EXISTS invoice_delivery_events (
    invoice_delivery_event_id BIGSERIAL PRIMARY KEY,
    invoice_id BIGINT NOT NULL REFERENCES invoices(invoice_id) ON DELETE CASCADE,
    delivery_number TEXT NOT NULL UNIQUE,
    delivery_method TEXT NOT NULL DEFAULT 'PICKUP',
    receiver_name TEXT,
    delivery_notes TEXT,
    remaining_balance NUMERIC(12, 2) NOT NULL DEFAULT 0,
    delivered_by_user_id INTEGER REFERENCES users(user_id),
    delivered_by_name TEXT,
    location_id INTEGER REFERENCES locations(location_id),
    device_id TEXT,
    device_name TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT invoice_delivery_events_method_chk
        CHECK (delivery_method IN ('PICKUP', 'LOCAL_DELIVERY', 'SHIP', 'INSTALLATION'))
);

CREATE TABLE IF NOT EXISTS invoice_delivery_lines (
    invoice_delivery_line_id BIGSERIAL PRIMARY KEY,
    sync_uuid UUID NOT NULL DEFAULT gen_random_uuid(),
    invoice_delivery_event_id BIGINT NOT NULL REFERENCES invoice_delivery_events(invoice_delivery_event_id) ON DELETE CASCADE,
    invoice_id BIGINT NOT NULL REFERENCES invoices(invoice_id) ON DELETE CASCADE,
    invoice_line_id BIGINT NOT NULL REFERENCES invoice_lines(invoice_line_id) ON DELETE CASCADE,
    product_id INTEGER REFERENCES products(product_id),
    item_name TEXT NOT NULL,
    quantity_delivered INTEGER NOT NULL,
    quantity_remaining INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT invoice_delivery_lines_qty_chk CHECK (quantity_delivered > 0 AND quantity_remaining >= 0)
);

CREATE TABLE IF NOT EXISTS quotation_status_history (
    quotation_status_history_id BIGSERIAL PRIMARY KEY,
    sync_uuid UUID NOT NULL DEFAULT gen_random_uuid(),
    quotation_id BIGINT NOT NULL REFERENCES quotations(quotation_id) ON DELETE CASCADE,
    old_status TEXT,
    new_status TEXT NOT NULL,
    reason TEXT,
    user_id INTEGER REFERENCES users(user_id),
    user_name TEXT,
    device_id TEXT,
    device_name TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS quotation_audit_log (
    quotation_audit_id BIGSERIAL PRIMARY KEY,
    sync_uuid UUID NOT NULL DEFAULT gen_random_uuid(),
    quotation_id BIGINT NOT NULL REFERENCES quotations(quotation_id) ON DELETE CASCADE,
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

CREATE TABLE IF NOT EXISTS invoice_status_history (
    invoice_status_history_id BIGSERIAL PRIMARY KEY,
    sync_uuid UUID NOT NULL DEFAULT gen_random_uuid(),
    invoice_id BIGINT NOT NULL REFERENCES invoices(invoice_id) ON DELETE CASCADE,
    old_status TEXT,
    new_status TEXT NOT NULL,
    reason TEXT,
    user_id INTEGER REFERENCES users(user_id),
    user_name TEXT,
    device_id TEXT,
    device_name TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS invoice_audit_log (
    invoice_audit_id BIGSERIAL PRIMARY KEY,
    sync_uuid UUID NOT NULL DEFAULT gen_random_uuid(),
    invoice_id BIGINT NOT NULL REFERENCES invoices(invoice_id) ON DELETE CASCADE,
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

ALTER TABLE customer_account_transactions
ADD COLUMN IF NOT EXISTS invoice_id BIGINT REFERENCES invoices(invoice_id);

ALTER TABLE quotations
ADD COLUMN IF NOT EXISTS vat_rate_percent NUMERIC(6, 2) NOT NULL DEFAULT 0;

ALTER TABLE quotations
ADD COLUMN IF NOT EXISTS vat_mode TEXT NOT NULL DEFAULT '';

ALTER TABLE quotation_lines
ADD COLUMN IF NOT EXISTS category_id INTEGER REFERENCES categories(category_id);

ALTER TABLE quotation_lines
ADD COLUMN IF NOT EXISTS sync_uuid UUID NOT NULL DEFAULT gen_random_uuid();

ALTER TABLE quotation_lines
ADD COLUMN IF NOT EXISTS vat_rate_percent NUMERIC(6, 2) NOT NULL DEFAULT 0;

ALTER TABLE quotation_lines
ADD COLUMN IF NOT EXISTS vat_amount NUMERIC(12, 2) NOT NULL DEFAULT 0;

ALTER TABLE invoices
ADD COLUMN IF NOT EXISTS vat_rate_percent NUMERIC(6, 2) NOT NULL DEFAULT 0;

ALTER TABLE invoices
ADD COLUMN IF NOT EXISTS vat_mode TEXT NOT NULL DEFAULT '';

UPDATE quotations
SET vat_mode = ''
WHERE vat_mode IS NULL;

UPDATE invoices
SET vat_mode = ''
WHERE vat_mode IS NULL;

ALTER TABLE invoice_lines
ADD COLUMN IF NOT EXISTS category_id INTEGER REFERENCES categories(category_id);

ALTER TABLE invoice_lines
ADD COLUMN IF NOT EXISTS sync_uuid UUID NOT NULL DEFAULT gen_random_uuid();

ALTER TABLE invoice_lines
ADD COLUMN IF NOT EXISTS vat_rate_percent NUMERIC(6, 2) NOT NULL DEFAULT 0;

ALTER TABLE invoice_lines
ADD COLUMN IF NOT EXISTS vat_amount NUMERIC(12, 2) NOT NULL DEFAULT 0;

ALTER TABLE quotations
DROP CONSTRAINT IF EXISTS quotations_vat_rate_chk;

ALTER TABLE quotations
ADD CONSTRAINT quotations_vat_rate_chk CHECK (vat_rate_percent >= 0 AND vat_rate_percent <= 100);

ALTER TABLE quotation_lines
DROP CONSTRAINT IF EXISTS quotation_lines_vat_chk;

ALTER TABLE quotation_lines
ADD CONSTRAINT quotation_lines_vat_chk CHECK (vat_rate_percent >= 0 AND vat_rate_percent <= 100 AND vat_amount >= 0);

ALTER TABLE invoices
DROP CONSTRAINT IF EXISTS invoices_vat_rate_chk;

ALTER TABLE invoices
ADD CONSTRAINT invoices_vat_rate_chk CHECK (vat_rate_percent >= 0 AND vat_rate_percent <= 100);

ALTER TABLE invoice_lines
DROP CONSTRAINT IF EXISTS invoice_lines_vat_chk;

ALTER TABLE invoice_lines
ADD CONSTRAINT invoice_lines_vat_chk CHECK (vat_rate_percent >= 0 AND vat_rate_percent <= 100 AND vat_amount >= 0);

ALTER TABLE customer_account_payment_allocations
ADD COLUMN IF NOT EXISTS invoice_id BIGINT REFERENCES invoices(invoice_id);

ALTER TABLE inventory_movements
ADD COLUMN IF NOT EXISTS invoice_id BIGINT REFERENCES invoices(invoice_id);

ALTER TABLE inventory_movements
ADD COLUMN IF NOT EXISTS invoice_line_id BIGINT REFERENCES invoice_lines(invoice_line_id);

ALTER TABLE inventory_movements
ADD COLUMN IF NOT EXISTS invoice_delivery_event_id BIGINT REFERENCES invoice_delivery_events(invoice_delivery_event_id);

ALTER TABLE invoice_payments
ADD COLUMN IF NOT EXISTS sync_uuid UUID NOT NULL DEFAULT gen_random_uuid();

ALTER TABLE invoice_delivery_lines
ADD COLUMN IF NOT EXISTS sync_uuid UUID NOT NULL DEFAULT gen_random_uuid();

ALTER TABLE quotation_status_history
ADD COLUMN IF NOT EXISTS sync_uuid UUID NOT NULL DEFAULT gen_random_uuid();

ALTER TABLE quotation_audit_log
ADD COLUMN IF NOT EXISTS sync_uuid UUID NOT NULL DEFAULT gen_random_uuid();

ALTER TABLE invoice_status_history
ADD COLUMN IF NOT EXISTS sync_uuid UUID NOT NULL DEFAULT gen_random_uuid();

ALTER TABLE invoice_audit_log
ADD COLUMN IF NOT EXISTS sync_uuid UUID NOT NULL DEFAULT gen_random_uuid();

CREATE INDEX IF NOT EXISTS quotations_customer_idx ON quotations(customer_id, created_at DESC);
CREATE INDEX IF NOT EXISTS quotations_status_idx ON quotations(status, valid_until);
CREATE INDEX IF NOT EXISTS quotations_location_idx ON quotations(location_id, created_at DESC);
CREATE INDEX IF NOT EXISTS quotation_lines_quotation_idx ON quotation_lines(quotation_id, sort_order);
CREATE INDEX IF NOT EXISTS invoices_customer_idx ON invoices(customer_id, created_at DESC);
CREATE INDEX IF NOT EXISTS invoices_status_idx ON invoices(status, invoice_date DESC);
CREATE INDEX IF NOT EXISTS invoices_quotation_idx ON invoices(quotation_id);
CREATE INDEX IF NOT EXISTS invoices_location_idx ON invoices(location_id, created_at DESC);
CREATE INDEX IF NOT EXISTS invoice_lines_invoice_idx ON invoice_lines(invoice_id, sort_order);
CREATE INDEX IF NOT EXISTS invoice_lines_delivery_idx ON invoice_lines(delivery_status, delivery_method);
CREATE INDEX IF NOT EXISTS invoice_payments_invoice_idx ON invoice_payments(invoice_id, created_at DESC);
CREATE INDEX IF NOT EXISTS invoice_delivery_events_invoice_idx ON invoice_delivery_events(invoice_id, created_at DESC);
CREATE INDEX IF NOT EXISTS invoice_delivery_lines_event_idx ON invoice_delivery_lines(invoice_delivery_event_id);
CREATE INDEX IF NOT EXISTS quotation_audit_quotation_idx ON quotation_audit_log(quotation_id, created_at DESC);
CREATE INDEX IF NOT EXISTS invoice_audit_invoice_idx ON invoice_audit_log(invoice_id, created_at DESC);
CREATE INDEX IF NOT EXISTS quotation_status_quotation_idx ON quotation_status_history(quotation_id, created_at DESC);
CREATE INDEX IF NOT EXISTS invoice_status_invoice_idx ON invoice_status_history(invoice_id, created_at DESC);
CREATE INDEX IF NOT EXISTS customer_account_transactions_invoice_idx ON customer_account_transactions(invoice_id);
CREATE INDEX IF NOT EXISTS customer_account_payment_allocations_invoice_idx ON customer_account_payment_allocations(invoice_id);
CREATE INDEX IF NOT EXISTS inventory_movements_invoice_idx ON inventory_movements(invoice_id, created_at DESC);
CREATE UNIQUE INDEX IF NOT EXISTS quotation_lines_sync_uuid_key ON quotation_lines(sync_uuid);
CREATE UNIQUE INDEX IF NOT EXISTS invoice_lines_sync_uuid_key ON invoice_lines(sync_uuid);
CREATE UNIQUE INDEX IF NOT EXISTS invoice_payments_sync_uuid_key ON invoice_payments(sync_uuid);
CREATE UNIQUE INDEX IF NOT EXISTS invoice_delivery_lines_sync_uuid_key ON invoice_delivery_lines(sync_uuid);
CREATE UNIQUE INDEX IF NOT EXISTS quotation_status_history_sync_uuid_key ON quotation_status_history(sync_uuid);
CREATE UNIQUE INDEX IF NOT EXISTS quotation_audit_log_sync_uuid_key ON quotation_audit_log(sync_uuid);
CREATE UNIQUE INDEX IF NOT EXISTS invoice_status_history_sync_uuid_key ON invoice_status_history(sync_uuid);
CREATE UNIQUE INDEX IF NOT EXISTS invoice_audit_log_sync_uuid_key ON invoice_audit_log(sync_uuid);

CREATE OR REPLACE FUNCTION set_quotation_invoice_updated_at()
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

DROP TRIGGER IF EXISTS quotations_set_updated_at ON quotations;
CREATE TRIGGER quotations_set_updated_at
BEFORE INSERT OR UPDATE ON quotations
FOR EACH ROW EXECUTE FUNCTION set_quotation_invoice_updated_at();

DROP TRIGGER IF EXISTS quotation_lines_set_updated_at ON quotation_lines;
CREATE TRIGGER quotation_lines_set_updated_at
BEFORE INSERT OR UPDATE ON quotation_lines
FOR EACH ROW EXECUTE FUNCTION set_quotation_invoice_updated_at();

DROP TRIGGER IF EXISTS invoices_set_updated_at ON invoices;
CREATE TRIGGER invoices_set_updated_at
BEFORE INSERT OR UPDATE ON invoices
FOR EACH ROW EXECUTE FUNCTION set_quotation_invoice_updated_at();

DROP TRIGGER IF EXISTS invoice_lines_set_updated_at ON invoice_lines;
CREATE TRIGGER invoice_lines_set_updated_at
BEFORE INSERT OR UPDATE ON invoice_lines
FOR EACH ROW EXECUTE FUNCTION set_quotation_invoice_updated_at();

DROP TRIGGER IF EXISTS invoice_payments_set_updated_at ON invoice_payments;
CREATE TRIGGER invoice_payments_set_updated_at
BEFORE INSERT OR UPDATE ON invoice_payments
FOR EACH ROW EXECUTE FUNCTION set_quotation_invoice_updated_at();

DROP TRIGGER IF EXISTS invoice_delivery_events_set_updated_at ON invoice_delivery_events;
CREATE TRIGGER invoice_delivery_events_set_updated_at
BEFORE INSERT OR UPDATE ON invoice_delivery_events
FOR EACH ROW EXECUTE FUNCTION set_quotation_invoice_updated_at();

ALTER TABLE quotation_lines
ADD COLUMN IF NOT EXISTS price_override_reason TEXT;

ALTER TABLE quotation_lines
ADD COLUMN IF NOT EXISTS price_override_by_user_id INTEGER REFERENCES users(user_id);

ALTER TABLE quotation_lines
ADD COLUMN IF NOT EXISTS price_override_by_name TEXT;

ALTER TABLE invoice_lines
ADD COLUMN IF NOT EXISTS price_override_reason TEXT;

ALTER TABLE invoice_lines
ADD COLUMN IF NOT EXISTS price_override_by_user_id INTEGER REFERENCES users(user_id);

ALTER TABLE invoice_lines
ADD COLUMN IF NOT EXISTS price_override_by_name TEXT;

ALTER TABLE permissions
ADD COLUMN IF NOT EXISTS permission_subgroup TEXT;

INSERT INTO permissions (permission_key, permission_name, permission_group, permission_subgroup)
VALUES
    ('CHANGE_SALE_ITEM_PRICE', 'Change Sale Item Price', 'Sales', 'Discounts')
ON CONFLICT (permission_key) DO UPDATE
SET permission_name = EXCLUDED.permission_name,
    permission_group = EXCLUDED.permission_group,
    permission_subgroup = EXCLUDED.permission_subgroup;

INSERT INTO permissions (permission_key, permission_name, permission_group, permission_subgroup)
VALUES
    ('QUOTATIONS_ORDERS', 'Quotations / Invoices', 'Quotations & Invoices', 'General'),
    ('CREATE_QUOTATION', 'Create Quotation', 'Quotations & Invoices', 'General'),
    ('MANAGE_INVOICES', 'Manage Invoices', 'Quotations & Invoices', 'General'),
    ('POST_INVOICE_DELIVERY', 'Post Invoice Delivery', 'Quotations & Invoices', 'General')
ON CONFLICT (permission_key) DO UPDATE
SET permission_name = EXCLUDED.permission_name,
    permission_group = EXCLUDED.permission_group,
    permission_subgroup = EXCLUDED.permission_subgroup;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.role_id, p.permission_id
FROM roles r
JOIN permissions p ON p.permission_key IN (
    'QUOTATIONS_ORDERS',
    'CREATE_QUOTATION',
    'MANAGE_INVOICES',
    'POST_INVOICE_DELIVERY',
    'CHANGE_SALE_ITEM_PRICE'
)
WHERE UPPER(r.role_name) IN ('ADMIN', 'MANAGER')
ON CONFLICT DO NOTHING;

DO $$
DECLARE
    target_table TEXT;
    target_sequence TEXT;
    has_authenticated BOOLEAN := EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'authenticated');
    has_service_role BOOLEAN := EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'service_role');
    is_supabase BOOLEAN := to_regprocedure('auth.uid()') IS NOT NULL;
BEGIN
    FOREACH target_table IN ARRAY ARRAY[
        'quotations',
        'quotation_lines',
        'quotation_status_history',
        'quotation_audit_log',
        'invoices',
        'invoice_lines',
        'invoice_payments',
        'invoice_delivery_events',
        'invoice_delivery_lines',
        'invoice_status_history',
        'invoice_audit_log'
    ]
    LOOP
        EXECUTE format('ALTER TABLE public.%I ENABLE ROW LEVEL SECURITY', target_table);
        EXECUTE format('REVOKE ALL ON TABLE public.%I FROM PUBLIC', target_table);
        IF has_authenticated AND NOT is_supabase THEN
            EXECUTE format('DROP POLICY IF EXISTS %I ON public.%I', target_table || '_authenticated_all', target_table);
            EXECUTE format(
                'CREATE POLICY %I ON public.%I FOR ALL TO authenticated USING (true) WITH CHECK (true)',
                target_table || '_authenticated_all',
                target_table
            );
            EXECUTE format('GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.%I TO authenticated', target_table);
        ELSIF has_authenticated THEN
            EXECUTE format('DROP POLICY IF EXISTS %I ON public.%I', target_table || '_authenticated_all', target_table);
        END IF;
        IF has_service_role THEN
            EXECUTE format('DROP POLICY IF EXISTS %I ON public.%I', target_table || '_service_role_all', target_table);
            EXECUTE format(
                'CREATE POLICY %I ON public.%I FOR ALL TO service_role USING (true) WITH CHECK (true)',
                target_table || '_service_role_all',
                target_table
            );
            EXECUTE format('GRANT ALL ON TABLE public.%I TO service_role', target_table);
        END IF;

        FOR target_sequence IN
            SELECT pg_get_serial_sequence('public.' || c.table_name, c.column_name)
            FROM information_schema.columns c
            WHERE c.table_schema = 'public'
              AND c.table_name = target_table
              AND c.column_default LIKE 'nextval(%'
        LOOP
            IF target_sequence IS NOT NULL THEN
                EXECUTE format('REVOKE ALL ON SEQUENCE %s FROM PUBLIC', target_sequence);
                IF has_authenticated THEN
                    EXECUTE format('GRANT USAGE, SELECT ON SEQUENCE %s TO authenticated', target_sequence);
                END IF;
                IF has_service_role THEN
                    EXECUTE format('GRANT ALL ON SEQUENCE %s TO service_role', target_sequence);
                END IF;
            END IF;
        END LOOP;
    END LOOP;
END $$;
