-- Base SmartStock schema for a local network database.
-- Feature setup scripts add newer columns/tables on top of this foundation.

CREATE TABLE IF NOT EXISTS roles (
    role_id SERIAL PRIMARY KEY,
    role_name TEXT NOT NULL UNIQUE,
    description TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS permissions (
    permission_id SERIAL PRIMARY KEY,
    permission_key TEXT NOT NULL UNIQUE,
    permission_name TEXT,
    description TEXT,
    permission_group TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS role_permissions (
    role_id INTEGER NOT NULL REFERENCES roles(role_id) ON DELETE CASCADE,
    permission_id INTEGER NOT NULL REFERENCES permissions(permission_id) ON DELETE CASCADE,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (role_id, permission_id)
);

CREATE TABLE IF NOT EXISTS mobile_permissions (
    permission_key TEXT PRIMARY KEY,
    permission_name TEXT,
    description TEXT,
    permission_group TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS role_mobile_permissions (
    role_id INTEGER NOT NULL REFERENCES roles(role_id) ON DELETE CASCADE,
    permission_key TEXT NOT NULL REFERENCES mobile_permissions(permission_key) ON DELETE CASCADE,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (role_id, permission_key)
);

CREATE TABLE IF NOT EXISTS sync_tombstones (
    tombstone_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    table_name TEXT NOT NULL,
    key_data JSONB NOT NULL DEFAULT '{}'::jsonb,
    deleted_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    origin_device_id TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(table_name, key_data)
);

CREATE INDEX IF NOT EXISTS sync_tombstones_deleted_idx
ON sync_tombstones(deleted_at DESC);

CREATE INDEX IF NOT EXISTS sync_tombstones_table_idx
ON sync_tombstones(table_name);

CREATE TABLE IF NOT EXISTS locations (
    location_id SERIAL PRIMARY KEY,
    name TEXT NOT NULL,
    address TEXT,
    company_address_line1 TEXT NOT NULL DEFAULT '',
    company_address_line2 TEXT NOT NULL DEFAULT '',
    company_address_line3 TEXT NOT NULL DEFAULT '',
    company_phone_line1 TEXT NOT NULL DEFAULT '',
    company_phone_line2 TEXT NOT NULL DEFAULT '',
    company_email_line1 TEXT NOT NULL DEFAULT '',
    company_email_line2 TEXT NOT NULL DEFAULT '',
    receipt_store_code TEXT,
    timezone TEXT NOT NULL DEFAULT 'America/New_York',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS company_info (
    company_info_id INTEGER PRIMARY KEY DEFAULT 1,
    company_name TEXT NOT NULL DEFAULT 'SmartStock',
    company_motto_line1 TEXT NOT NULL DEFAULT '',
    company_motto_line2 TEXT NOT NULL DEFAULT '',
    company_logo_url TEXT NOT NULL DEFAULT '',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT company_info_singleton_chk CHECK (company_info_id = 1)
);

INSERT INTO company_info (company_info_id, company_name)
VALUES (1, 'SmartStock')
ON CONFLICT (company_info_id) DO NOTHING;

CREATE TABLE IF NOT EXISTS users (
    user_id SERIAL PRIMARY KEY,
    username TEXT NOT NULL UNIQUE,
    password_hash TEXT,
    first_name TEXT,
    middle_name TEXT,
    last_name TEXT,
    full_name TEXT,
    email TEXT,
    phone TEXT,
    employee_photo_url TEXT,
    employee_id_card_document_url TEXT,
    date_of_birth DATE,
    badge_id TEXT,
    badge_secret_salt TEXT,
    badge_secret_hash TEXT,
    badge_generated_at TIMESTAMPTZ,
    badge_print_count INTEGER NOT NULL DEFAULT 0,
    compensation_type TEXT,
    salary NUMERIC(12,2) NOT NULL DEFAULT 0,
    role_id INTEGER REFERENCES roles(role_id),
    auth_user_id UUID,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    deactivated_at TIMESTAMPTZ,
    deactivated_by_user_id INTEGER,
    deactivated_by_name TEXT,
    password_cache_invalidated_at TIMESTAMPTZ,
    employee_pin_salt TEXT,
    employee_pin_hash TEXT,
    employee_pin_updated_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS user_locations (
    user_id INTEGER NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    location_id INTEGER NOT NULL REFERENCES locations(location_id) ON DELETE CASCADE,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, location_id)
);

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

ALTER TABLE users
ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP;

ALTER TABLE users
ADD COLUMN IF NOT EXISTS deactivated_at TIMESTAMPTZ;

ALTER TABLE users
ADD COLUMN IF NOT EXISTS deactivated_by_user_id INTEGER;

ALTER TABLE users
ADD COLUMN IF NOT EXISTS deactivated_by_name TEXT;

ALTER TABLE users
ADD COLUMN IF NOT EXISTS password_cache_invalidated_at TIMESTAMPTZ;

ALTER TABLE users
ADD COLUMN IF NOT EXISTS employee_pin_salt TEXT;

ALTER TABLE users
ADD COLUMN IF NOT EXISTS employee_pin_hash TEXT;

ALTER TABLE users
ADD COLUMN IF NOT EXISTS employee_pin_updated_at TIMESTAMPTZ;

ALTER TABLE users
ADD COLUMN IF NOT EXISTS date_of_birth DATE;

ALTER TABLE users
ADD COLUMN IF NOT EXISTS employee_photo_url TEXT;

ALTER TABLE users
ADD COLUMN IF NOT EXISTS employee_id_card_document_url TEXT;

ALTER TABLE users
ADD COLUMN IF NOT EXISTS badge_secret_salt TEXT;

ALTER TABLE users
ADD COLUMN IF NOT EXISTS badge_secret_hash TEXT;

ALTER TABLE users
ADD COLUMN IF NOT EXISTS badge_generated_at TIMESTAMPTZ;

ALTER TABLE users
ADD COLUMN IF NOT EXISTS badge_print_count INTEGER NOT NULL DEFAULT 0;

ALTER TABLE user_locations
ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP;

CREATE OR REPLACE FUNCTION set_users_updated_at()
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

DROP TRIGGER IF EXISTS users_set_updated_at ON users;
CREATE TRIGGER users_set_updated_at
BEFORE INSERT OR UPDATE ON users
FOR EACH ROW
EXECUTE FUNCTION set_users_updated_at();

CREATE OR REPLACE FUNCTION set_user_locations_updated_at()
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

DROP TRIGGER IF EXISTS user_locations_set_updated_at ON user_locations;
CREATE TRIGGER user_locations_set_updated_at
BEFORE INSERT OR UPDATE ON user_locations
FOR EACH ROW
EXECUTE FUNCTION set_user_locations_updated_at();

CREATE INDEX IF NOT EXISTS users_updated_at_idx
ON users(updated_at DESC);

CREATE INDEX IF NOT EXISTS user_locations_updated_at_idx
ON user_locations(updated_at DESC);

CREATE TABLE IF NOT EXISTS categories (
    category_id SERIAL PRIMARY KEY,
    name TEXT NOT NULL UNIQUE,
    description TEXT,
    vat_rate_percent NUMERIC(6, 2) NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS vendors (
    vendor_id SERIAL PRIMARY KEY,
    name TEXT NOT NULL UNIQUE,
    contact_name TEXT,
    phone TEXT,
    email TEXT,
    address TEXT,
    notes TEXT,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS products (
    product_id SERIAL PRIMARY KEY,
    name TEXT NOT NULL,
    size TEXT,
    sku TEXT,
    barcode TEXT,
    description TEXT,
    cost_price NUMERIC(12,2) NOT NULL DEFAULT 0,
    price NUMERIC(12,2) NOT NULL DEFAULT 0,
    product_type TEXT NOT NULL DEFAULT 'INVENTORY',
    category_id INTEGER REFERENCES categories(category_id),
    vendor_id INTEGER REFERENCES vendors(vendor_id),
    image_url TEXT,
    created_by_user_id INTEGER REFERENCES users(user_id),
    created_by_name TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE IF NOT EXISTS product_barcodes (
    product_barcode_id INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    product_id INTEGER NOT NULL REFERENCES products(product_id) ON DELETE CASCADE,
    barcode TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(barcode)
);

ALTER TABLE products
ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP;

ALTER TABLE product_barcodes
ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'product_barcodes'
          AND column_name = 'barcode_id'
    ) AND NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'product_barcodes'
          AND column_name = 'product_barcode_id'
    ) THEN
        ALTER TABLE product_barcodes RENAME COLUMN barcode_id TO product_barcode_id;
    END IF;
END
$$;

DELETE FROM product_barcodes older
USING product_barcodes newer
WHERE older.barcode = newer.barcode
  AND older.product_barcode_id < newer.product_barcode_id;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM pg_constraint con
        JOIN pg_class rel ON rel.oid = con.conrelid
        JOIN pg_namespace nsp ON nsp.oid = rel.relnamespace
        WHERE nsp.nspname = 'public'
          AND rel.relname = 'product_barcodes'
          AND con.contype = 'u'
          AND pg_get_constraintdef(con.oid) = 'UNIQUE (barcode)'
    ) THEN
        DROP INDEX IF EXISTS product_barcodes_barcode_uidx;
    ELSIF NOT EXISTS (
        SELECT 1
        FROM pg_index i
        JOIN pg_class rel ON rel.oid = i.indrelid
        JOIN pg_namespace nsp ON nsp.oid = rel.relnamespace
        JOIN pg_class idx ON idx.oid = i.indexrelid
        WHERE nsp.nspname = 'public'
          AND rel.relname = 'product_barcodes'
          AND i.indisunique
          AND pg_get_indexdef(idx.oid) ILIKE '%(barcode)%'
    ) THEN
        CREATE UNIQUE INDEX product_barcodes_barcode_uidx
        ON product_barcodes(barcode);
    END IF;
END
$$;

CREATE INDEX IF NOT EXISTS idx_product_barcodes_product_id
ON product_barcodes(product_id);

CREATE OR REPLACE FUNCTION set_products_updated_at()
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

DROP TRIGGER IF EXISTS products_set_updated_at ON products;
CREATE TRIGGER products_set_updated_at
BEFORE INSERT OR UPDATE ON products
FOR EACH ROW
EXECUTE FUNCTION set_products_updated_at();

CREATE OR REPLACE FUNCTION set_product_barcodes_updated_at()
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

DROP TRIGGER IF EXISTS product_barcodes_set_updated_at ON product_barcodes;
CREATE TRIGGER product_barcodes_set_updated_at
BEFORE INSERT OR UPDATE ON product_barcodes
FOR EACH ROW
EXECUTE FUNCTION set_product_barcodes_updated_at();

CREATE INDEX IF NOT EXISTS products_updated_at_idx
ON products(updated_at DESC);

CREATE INDEX IF NOT EXISTS product_barcodes_updated_at_idx
ON product_barcodes(updated_at DESC);

CREATE TABLE IF NOT EXISTS inventory (
    product_id INTEGER NOT NULL REFERENCES products(product_id) ON DELETE CASCADE,
    location_id INTEGER NOT NULL REFERENCES locations(location_id) ON DELETE CASCADE,
    quantity_on_hand INTEGER NOT NULL DEFAULT 0,
    reorder_level INTEGER NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (product_id, location_id)
);

CREATE TABLE IF NOT EXISTS receiving_batches (
    receive_id TEXT PRIMARY KEY,
    location_id INTEGER REFERENCES locations(location_id),
    user_id INTEGER REFERENCES users(user_id),
    user_name TEXT,
    receive_device_id TEXT,
    receive_sequence INTEGER,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS sales (
    sale_id SERIAL PRIMARY KEY,
    location_id INTEGER REFERENCES locations(location_id),
    user_id INTEGER REFERENCES users(user_id),
    customer_id INTEGER,
    total_amount NUMERIC(12,2) NOT NULL DEFAULT 0,
    status TEXT NOT NULL DEFAULT 'COMPLETED',
    payment_method TEXT,
    payment_status TEXT,
    amount_paid NUMERIC(12,2) NOT NULL DEFAULT 0,
    user_name TEXT,
    receipt_number TEXT,
    receipt_device_id TEXT,
    receipt_sequence INTEGER,
    subtotal_amount NUMERIC(12,2) NOT NULL DEFAULT 0,
    discount_percent NUMERIC(6,2) NOT NULL DEFAULT 0,
    discount_amount NUMERIC(12,2) NOT NULL DEFAULT 0,
    vat_amount NUMERIC(12,2) NOT NULL DEFAULT 0,
    vat_rate_percent NUMERIC(6,2) NOT NULL DEFAULT 0,
    vat_mode TEXT NOT NULL DEFAULT '',
    payment_reference TEXT,
    transaction_source TEXT,
    device_id TEXT,
    completed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS sale_items (
    sale_item_id SERIAL PRIMARY KEY,
    sale_id INTEGER NOT NULL REFERENCES sales(sale_id) ON DELETE CASCADE,
    product_id INTEGER REFERENCES products(product_id),
    quantity INTEGER NOT NULL DEFAULT 1,
    unit_price NUMERIC(12,2) NOT NULL DEFAULT 0,
    original_unit_price NUMERIC(12,2),
    discount_percent NUMERIC(6,2) NOT NULL DEFAULT 0,
    discount_amount NUMERIC(12,2) NOT NULL DEFAULT 0,
    price_override_reason TEXT,
    price_override_by_user_id INTEGER REFERENCES users(user_id),
    price_override_by_name TEXT,
    product_type TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE categories
ADD COLUMN IF NOT EXISTS vat_rate_percent NUMERIC(6, 2) NOT NULL DEFAULT 0;

ALTER TABLE categories
DROP CONSTRAINT IF EXISTS categories_vat_rate_percent_chk;

ALTER TABLE categories
ADD CONSTRAINT categories_vat_rate_percent_chk
CHECK (vat_rate_percent >= 0 AND vat_rate_percent <= 100);

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

CREATE TABLE IF NOT EXISTS inventory_movements (
    movement_id BIGSERIAL PRIMARY KEY,
    product_id INTEGER REFERENCES products(product_id),
    location_id INTEGER REFERENCES locations(location_id),
    change_qty INTEGER NOT NULL DEFAULT 0,
    reason TEXT,
    note TEXT,
    user_name TEXT,
    sale_id INTEGER REFERENCES sales(sale_id),
    sale_item_id INTEGER REFERENCES sale_items(sale_item_id),
    device_id TEXT,
    device_name TEXT,
    user_id INTEGER REFERENCES users(user_id),
    receive_id TEXT,
    receive_device_id TEXT,
    receive_sequence INTEGER,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS customer_accounts (
    customer_id SERIAL PRIMARY KEY,
    account_number TEXT,
    name TEXT NOT NULL,
    customer_type_id INTEGER,
    phone TEXT,
    email TEXT,
    credit_limit NUMERIC(12,2) NOT NULL DEFAULT 0,
    current_balance NUMERIC(12,2) NOT NULL DEFAULT 0,
    is_business BOOLEAN NOT NULL DEFAULT FALSE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    account_notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

DO $$
DECLARE
    table_name TEXT;
    function_name TEXT;
    trigger_name TEXT;
BEGIN
    FOREACH table_name IN ARRAY ARRAY[
        'roles',
        'role_permissions',
        'role_mobile_permissions',
        'locations',
        'users',
        'user_locations',
        'products',
        'product_barcodes',
        'inventory',
        'customer_accounts'
    ]
    LOOP
        function_name := 'set_' || table_name || '_updated_at';
        trigger_name := table_name || '_set_updated_at';
        EXECUTE format('ALTER TABLE %I ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP', table_name);
        EXECUTE format($fn$
            CREATE OR REPLACE FUNCTION %I()
            RETURNS TRIGGER AS $body$
            BEGIN
                IF TG_OP = 'INSERT' THEN
                    NEW.updated_at = COALESCE(NEW.updated_at, CURRENT_TIMESTAMP);
                ELSIF NEW.updated_at IS NOT DISTINCT FROM OLD.updated_at THEN
                    NEW.updated_at = CURRENT_TIMESTAMP;
                END IF;
                RETURN NEW;
            END;
            $body$ LANGUAGE plpgsql
        $fn$, function_name);
        EXECUTE format('DROP TRIGGER IF EXISTS %I ON %I', trigger_name, table_name);
        EXECUTE format(
            'CREATE TRIGGER %I BEFORE INSERT OR UPDATE ON %I FOR EACH ROW EXECUTE FUNCTION %I()',
            trigger_name,
            table_name,
            function_name
        );
        EXECUTE format('CREATE INDEX IF NOT EXISTS %I ON %I(updated_at DESC)', table_name || '_updated_at_idx', table_name);
    END LOOP;
END;
$$;

CREATE TABLE IF NOT EXISTS customer_account_transactions (
    transaction_id BIGSERIAL PRIMARY KEY,
    customer_id INTEGER REFERENCES customer_accounts(customer_id),
    sale_id INTEGER REFERENCES sales(sale_id),
    custom_order_id BIGINT,
    payment_id TEXT,
    location_id INTEGER,
    amount NUMERIC(12,2) NOT NULL DEFAULT 0,
    transaction_type TEXT NOT NULL,
    note TEXT,
    user_name TEXT,
    device_id TEXT,
    device_name TEXT,
    payment_method TEXT,
    payment_reference TEXT,
    cash_drawer_id BIGINT,
    cash_drawer_name TEXT,
    cash_drawer_session_id BIGINT,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS customer_account_payment_allocations (
    allocation_id BIGSERIAL PRIMARY KEY,
    payment_transaction_id BIGINT NOT NULL REFERENCES customer_account_transactions(transaction_id) ON DELETE CASCADE,
    customer_id INTEGER NOT NULL REFERENCES customer_accounts(customer_id),
    sale_id INTEGER REFERENCES sales(sale_id),
    custom_order_id BIGINT,
    amount NUMERIC(12,2) NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE customer_account_transactions
ADD COLUMN IF NOT EXISTS payment_id TEXT;

ALTER TABLE customer_account_transactions
ADD COLUMN IF NOT EXISTS location_id INTEGER;

ALTER TABLE customer_account_transactions
ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP;

ALTER TABLE customer_account_payment_allocations
ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP;

UPDATE customer_account_transactions
SET payment_id = 'PAY-' || LPAD(transaction_id::text, 6, '0')
WHERE COALESCE(transaction_type, '') = 'PAYMENT'
  AND COALESCE(payment_id, '') = '';

UPDATE customer_account_transactions
SET payment_id = NULL
WHERE COALESCE(transaction_type, '') <> 'PAYMENT'
  AND TRIM(COALESCE(payment_id, '')) = '';

CREATE UNIQUE INDEX IF NOT EXISTS idx_customer_account_transactions_payment_id
ON customer_account_transactions(payment_id)
WHERE payment_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS customer_account_transactions_customer_created_idx
ON customer_account_transactions(customer_id, created_at DESC);

CREATE INDEX IF NOT EXISTS customer_account_transactions_location_created_idx
ON customer_account_transactions(location_id, created_at DESC);

DROP INDEX IF EXISTS customer_account_transactions_payment_id_idx;

CREATE TABLE IF NOT EXISTS notification_user_state (
    user_id INTEGER NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    notification_key TEXT NOT NULL,
    read_at TIMESTAMPTZ,
    snoozed_until TIMESTAMPTZ,
    dismissed_at TIMESTAMPTZ,
    dismissed_until TIMESTAMPTZ,
    last_seen_at TIMESTAMPTZ,
    last_seen_severity TEXT,
    last_seen_source TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, notification_key)
);

ALTER TABLE notification_user_state
ADD COLUMN IF NOT EXISTS read_at TIMESTAMPTZ;

ALTER TABLE notification_user_state
ADD COLUMN IF NOT EXISTS snoozed_until TIMESTAMPTZ;

ALTER TABLE notification_user_state
ADD COLUMN IF NOT EXISTS dismissed_at TIMESTAMPTZ;

ALTER TABLE notification_user_state
ADD COLUMN IF NOT EXISTS dismissed_until TIMESTAMPTZ;

ALTER TABLE notification_user_state
ADD COLUMN IF NOT EXISTS last_seen_at TIMESTAMPTZ;

ALTER TABLE notification_user_state
ADD COLUMN IF NOT EXISTS last_seen_severity TEXT;

ALTER TABLE notification_user_state
ADD COLUMN IF NOT EXISTS last_seen_source TEXT;

ALTER TABLE notification_user_state
ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP;

CREATE INDEX IF NOT EXISTS notification_user_state_snoozed_idx
ON notification_user_state(user_id, snoozed_until);

CREATE INDEX IF NOT EXISTS notification_user_state_dismissed_idx
ON notification_user_state(user_id, dismissed_until);

CREATE INDEX IF NOT EXISTS notification_user_state_updated_idx
ON notification_user_state(updated_at DESC);

SELECT setval(pg_get_serial_sequence('roles', 'role_id'), COALESCE((SELECT MAX(role_id) FROM roles), 1), (SELECT COUNT(*) FROM roles) > 0);
SELECT setval(pg_get_serial_sequence('locations', 'location_id'), COALESCE((SELECT MAX(location_id) FROM locations), 1), (SELECT COUNT(*) FROM locations) > 0);

INSERT INTO roles (role_name, description)
VALUES ('ADMIN', 'Administrator'), ('MANAGER', 'Manager'), ('USER', 'User')
ON CONFLICT (role_name) DO NOTHING;

INSERT INTO locations (name, receipt_store_code, timezone)
SELECT 'Default Store', '0001', 'America/New_York'
WHERE NOT EXISTS (SELECT 1 FROM locations);

CREATE INDEX IF NOT EXISTS users_username_idx ON users(LOWER(username));
CREATE INDEX IF NOT EXISTS users_email_idx ON users(LOWER(email));
CREATE INDEX IF NOT EXISTS users_badge_idx ON users(LOWER(badge_id));
CREATE INDEX IF NOT EXISTS users_badge_normalized_idx ON users(UPPER(REGEXP_REPLACE(COALESCE(badge_id, ''), '[^a-zA-Z0-9]', '', 'g')));
CREATE INDEX IF NOT EXISTS products_name_idx ON products(LOWER(name));
CREATE INDEX IF NOT EXISTS products_sku_idx ON products(sku);
CREATE INDEX IF NOT EXISTS products_barcode_idx ON products(barcode);
CREATE INDEX IF NOT EXISTS receiving_batches_location_created_idx ON receiving_batches(location_id, created_at DESC);
CREATE INDEX IF NOT EXISTS sales_location_created_idx ON sales(location_id, created_at DESC);
CREATE INDEX IF NOT EXISTS inventory_movements_product_created_idx ON inventory_movements(product_id, created_at DESC);
CREATE INDEX IF NOT EXISTS customer_account_payment_allocations_payment_idx ON customer_account_payment_allocations(payment_transaction_id);
CREATE INDEX IF NOT EXISTS customer_account_payment_allocations_sale_idx ON customer_account_payment_allocations(sale_id);

DROP INDEX IF EXISTS idx_customer_account_transactions_location_created;
DROP INDEX IF EXISTS idx_customer_payment_allocations_payment;
DROP INDEX IF EXISTS idx_customer_payment_allocations_sale;
