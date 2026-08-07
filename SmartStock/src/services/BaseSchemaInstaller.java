package services;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public final class BaseSchemaInstaller {
    private BaseSchemaInstaller() {
    }

    public static void ensureSchema(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS roles (
                        role_id SERIAL PRIMARY KEY,
                        role_name TEXT NOT NULL UNIQUE,
                        description TEXT,
                        created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
                    )
                    """);
            stmt.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS permissions (
                        permission_id SERIAL PRIMARY KEY,
                        permission_key TEXT NOT NULL UNIQUE,
                        permission_name TEXT,
                        description TEXT,
                        permission_group TEXT,
                        permission_subgroup TEXT,
                        created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
                    )
                    """);
            stmt.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS role_permissions (
                        role_id INTEGER NOT NULL REFERENCES roles(role_id) ON DELETE CASCADE,
                        permission_id INTEGER NOT NULL REFERENCES permissions(permission_id) ON DELETE CASCADE,
                        updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        PRIMARY KEY (role_id, permission_id)
                    )
                    """);
            stmt.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS mobile_permissions (
                        permission_key TEXT PRIMARY KEY,
                        permission_name TEXT,
                        description TEXT,
                        permission_group TEXT,
                        permission_subgroup TEXT,
                        created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
                    )
                    """);
            stmt.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS role_mobile_permissions (
                        role_id INTEGER NOT NULL REFERENCES roles(role_id) ON DELETE CASCADE,
                        permission_key TEXT NOT NULL REFERENCES mobile_permissions(permission_key) ON DELETE CASCADE,
                        updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        PRIMARY KEY (role_id, permission_key)
                    )
                    """);
            stmt.executeUpdate("""
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
                        receipt_store_code TEXT NOT NULL DEFAULT '0001',
                        timezone TEXT NOT NULL DEFAULT 'America/New_York',
                        created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
                    )
                    """);
            stmt.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS company_info (
                        company_info_id INTEGER PRIMARY KEY DEFAULT 1,
                        company_name TEXT NOT NULL DEFAULT 'SmartStock',
                        company_motto_line1 TEXT NOT NULL DEFAULT '',
                        company_motto_line2 TEXT NOT NULL DEFAULT '',
                        company_logo_url TEXT NOT NULL DEFAULT '',
                        updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        CONSTRAINT company_info_singleton_chk CHECK (company_info_id = 1)
                    )
                    """);
            stmt.executeUpdate("""
                    INSERT INTO company_info (company_info_id, company_name)
                    VALUES (1, 'SmartStock')
                    ON CONFLICT (company_info_id) DO NOTHING
                    """);
            stmt.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS users (
                        user_id SERIAL PRIMARY KEY,
                        username TEXT NOT NULL UNIQUE,
                        password_hash TEXT,
                        first_name TEXT,
                        middle_name TEXT,
                        last_name TEXT,
                        full_name TEXT NOT NULL,
                        nickname TEXT,
                        email TEXT,
                        phone TEXT,
                        employee_photo_url TEXT,
                        employee_id_card_document_url TEXT,
                        date_of_birth DATE,
                        hire_date DATE NOT NULL DEFAULT CURRENT_DATE,
                        badge_id TEXT,
                        badge_secret_salt TEXT,
                        badge_secret_hash TEXT,
                        badge_generated_at TIMESTAMPTZ,
                        badge_print_count INTEGER NOT NULL DEFAULT 0,
                        badge_rotated_at TIMESTAMPTZ,
                        badge_rotated_by_user_id INTEGER,
                        badge_rotated_by_name TEXT,
                        compensation_type TEXT NOT NULL DEFAULT 'HOURLY',
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
                    )
                    """);
            stmt.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS user_locations (
                        user_id INTEGER NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
                        location_id INTEGER NOT NULL REFERENCES locations(location_id) ON DELETE CASCADE,
                        updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        PRIMARY KEY (user_id, location_id)
                    )
                    """);
            stmt.executeUpdate("ALTER TABLE users ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP");
            stmt.executeUpdate("ALTER TABLE users ADD COLUMN IF NOT EXISTS deactivated_at TIMESTAMPTZ");
            stmt.executeUpdate("ALTER TABLE users ADD COLUMN IF NOT EXISTS deactivated_by_user_id INTEGER");
            stmt.executeUpdate("ALTER TABLE users ADD COLUMN IF NOT EXISTS deactivated_by_name TEXT");
            stmt.executeUpdate("ALTER TABLE users ADD COLUMN IF NOT EXISTS password_cache_invalidated_at TIMESTAMPTZ");
            stmt.executeUpdate("ALTER TABLE users ADD COLUMN IF NOT EXISTS employee_pin_salt TEXT");
            stmt.executeUpdate("ALTER TABLE users ADD COLUMN IF NOT EXISTS employee_pin_hash TEXT");
            stmt.executeUpdate("ALTER TABLE users ADD COLUMN IF NOT EXISTS employee_pin_updated_at TIMESTAMPTZ");
            stmt.executeUpdate("ALTER TABLE users ADD COLUMN IF NOT EXISTS employee_photo_url TEXT");
            stmt.executeUpdate("ALTER TABLE users ADD COLUMN IF NOT EXISTS nickname TEXT");
            stmt.executeUpdate("ALTER TABLE users ADD COLUMN IF NOT EXISTS employee_id_card_document_url TEXT");
            stmt.executeUpdate("ALTER TABLE users ADD COLUMN IF NOT EXISTS date_of_birth DATE");
            stmt.executeUpdate("ALTER TABLE users ADD COLUMN IF NOT EXISTS hire_date DATE");
            stmt.executeUpdate("UPDATE users SET hire_date = COALESCE(created_at::date, CURRENT_DATE) WHERE hire_date IS NULL");
            stmt.executeUpdate("ALTER TABLE users ALTER COLUMN hire_date SET DEFAULT CURRENT_DATE");
            stmt.executeUpdate("ALTER TABLE users ALTER COLUMN hire_date SET NOT NULL");
            stmt.executeUpdate("ALTER TABLE users ADD COLUMN IF NOT EXISTS badge_secret_salt TEXT");
            stmt.executeUpdate("ALTER TABLE users ADD COLUMN IF NOT EXISTS badge_secret_hash TEXT");
            stmt.executeUpdate("ALTER TABLE users ADD COLUMN IF NOT EXISTS badge_generated_at TIMESTAMPTZ");
            stmt.executeUpdate("ALTER TABLE users ADD COLUMN IF NOT EXISTS badge_print_count INTEGER NOT NULL DEFAULT 0");
            stmt.executeUpdate("ALTER TABLE users ADD COLUMN IF NOT EXISTS badge_rotated_at TIMESTAMPTZ");
            stmt.executeUpdate("ALTER TABLE users ADD COLUMN IF NOT EXISTS badge_rotated_by_user_id INTEGER");
            stmt.executeUpdate("ALTER TABLE users ADD COLUMN IF NOT EXISTS badge_rotated_by_name TEXT");
            stmt.executeUpdate("UPDATE users SET full_name = COALESCE(NULLIF(TRIM(full_name), ''), username) WHERE full_name IS NULL OR TRIM(full_name) = ''");
            stmt.executeUpdate("UPDATE users SET compensation_type = 'HOURLY' WHERE compensation_type IS NULL");
            stmt.executeUpdate("UPDATE users SET salary = 0 WHERE salary IS NULL");
            stmt.executeUpdate("UPDATE users SET is_active = TRUE WHERE is_active IS NULL");
            stmt.executeUpdate("ALTER TABLE users ALTER COLUMN full_name SET NOT NULL");
            stmt.executeUpdate("ALTER TABLE users ALTER COLUMN compensation_type SET DEFAULT 'HOURLY'");
            stmt.executeUpdate("ALTER TABLE users ALTER COLUMN compensation_type SET NOT NULL");
            stmt.executeUpdate("ALTER TABLE users ALTER COLUMN salary SET DEFAULT 0");
            stmt.executeUpdate("ALTER TABLE users ALTER COLUMN salary SET NOT NULL");
            stmt.executeUpdate("ALTER TABLE users ALTER COLUMN is_active SET DEFAULT TRUE");
            stmt.executeUpdate("ALTER TABLE users ALTER COLUMN is_active SET NOT NULL");
            stmt.executeUpdate("ALTER TABLE permissions ADD COLUMN IF NOT EXISTS permission_name TEXT");
            stmt.executeUpdate("ALTER TABLE permissions ADD COLUMN IF NOT EXISTS description TEXT");
            stmt.executeUpdate("ALTER TABLE permissions ADD COLUMN IF NOT EXISTS permission_group TEXT");
            stmt.executeUpdate("ALTER TABLE permissions ADD COLUMN IF NOT EXISTS permission_subgroup TEXT");
            stmt.executeUpdate("ALTER TABLE locations ADD COLUMN IF NOT EXISTS company_address_line1 TEXT NOT NULL DEFAULT ''");
            stmt.executeUpdate("ALTER TABLE locations ADD COLUMN IF NOT EXISTS company_address_line2 TEXT NOT NULL DEFAULT ''");
            stmt.executeUpdate("ALTER TABLE locations ADD COLUMN IF NOT EXISTS company_address_line3 TEXT NOT NULL DEFAULT ''");
            stmt.executeUpdate("ALTER TABLE locations ADD COLUMN IF NOT EXISTS company_phone_line1 TEXT NOT NULL DEFAULT ''");
            stmt.executeUpdate("ALTER TABLE locations ADD COLUMN IF NOT EXISTS company_phone_line2 TEXT NOT NULL DEFAULT ''");
            stmt.executeUpdate("ALTER TABLE locations ADD COLUMN IF NOT EXISTS company_email_line1 TEXT NOT NULL DEFAULT ''");
            stmt.executeUpdate("ALTER TABLE locations ADD COLUMN IF NOT EXISTS company_email_line2 TEXT NOT NULL DEFAULT ''");
            stmt.executeUpdate("ALTER TABLE locations ADD COLUMN IF NOT EXISTS balance_sheet_recipient_email TEXT NOT NULL DEFAULT ''");
            stmt.executeUpdate("UPDATE locations SET receipt_store_code = '0001' WHERE receipt_store_code IS NULL OR TRIM(receipt_store_code) = ''");
            stmt.executeUpdate("ALTER TABLE locations ALTER COLUMN receipt_store_code SET DEFAULT '0001'");
            stmt.executeUpdate("ALTER TABLE locations ALTER COLUMN receipt_store_code SET NOT NULL");
            stmt.executeUpdate("ALTER TABLE user_locations ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP");
            stmt.executeUpdate("""
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
                    $$ LANGUAGE plpgsql
                    """);
            stmt.executeUpdate("DROP TRIGGER IF EXISTS users_set_updated_at ON users");
            stmt.executeUpdate("""
                    CREATE OR REPLACE TRIGGER users_set_updated_at
                    BEFORE INSERT OR UPDATE ON users
                    FOR EACH ROW
                    EXECUTE FUNCTION set_users_updated_at()
                    """);
            stmt.executeUpdate("""
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
                    $$ LANGUAGE plpgsql
                    """);
            stmt.executeUpdate("DROP TRIGGER IF EXISTS user_locations_set_updated_at ON user_locations");
            stmt.executeUpdate("""
                    CREATE OR REPLACE TRIGGER user_locations_set_updated_at
                    BEFORE INSERT OR UPDATE ON user_locations
                    FOR EACH ROW
                    EXECUTE FUNCTION set_user_locations_updated_at()
                    """);
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS users_updated_at_idx ON users(updated_at DESC)");
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS users_badge_normalized_idx ON users(UPPER(REGEXP_REPLACE(COALESCE(badge_id, ''), '[^a-zA-Z0-9]', '', 'g')))");
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS user_locations_updated_at_idx ON user_locations(updated_at DESC)");
            stmt.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS categories (
                        category_id SERIAL PRIMARY KEY,
                        name TEXT NOT NULL UNIQUE,
                        description TEXT,
                        vat_rate_percent NUMERIC(6, 2) NOT NULL DEFAULT 0,
                        created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
                    )
                    """);
            stmt.executeUpdate("ALTER TABLE categories ADD COLUMN IF NOT EXISTS vat_rate_percent NUMERIC(6, 2) NOT NULL DEFAULT 0");
            stmt.executeUpdate("ALTER TABLE categories DROP CONSTRAINT IF EXISTS categories_vat_rate_percent_chk");
            stmt.executeUpdate("ALTER TABLE categories ADD CONSTRAINT categories_vat_rate_percent_chk CHECK (vat_rate_percent >= 0 AND vat_rate_percent <= 100)");
            stmt.executeUpdate("""
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
                    )
                    """);
            stmt.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS products (
                        product_id SERIAL PRIMARY KEY,
                        name TEXT NOT NULL,
                        size TEXT,
                        sku TEXT NOT NULL,
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
                    )
                    """);
            stmt.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS product_barcodes (
                        product_barcode_id INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                        product_id INTEGER NOT NULL REFERENCES products(product_id) ON DELETE CASCADE,
                        barcode TEXT NOT NULL,
                        created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        UNIQUE(barcode)
                    )
                    """);
            stmt.executeUpdate("ALTER TABLE products ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP");
            stmt.executeUpdate("UPDATE products SET sku = 'ITEM-' || product_id WHERE sku IS NULL OR TRIM(sku) = ''");
            stmt.executeUpdate("ALTER TABLE products ALTER COLUMN sku SET NOT NULL");
            stmt.executeUpdate("ALTER TABLE product_barcodes ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP");
            ItemDetailsSchemaInstaller.ensureSchema(conn);
            stmt.executeUpdate("""
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
                    $$
                    """);
            stmt.executeUpdate("""
                    DELETE FROM product_barcodes older
                    USING product_barcodes newer
                    WHERE older.barcode = newer.barcode
                      AND older.product_barcode_id < newer.product_barcode_id
                    """);
            stmt.executeUpdate("""
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
                    $$
                    """);
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_product_barcodes_product_id ON product_barcodes(product_id)");
            stmt.executeUpdate("""
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
                    $$ LANGUAGE plpgsql
                    """);
            stmt.executeUpdate("DROP TRIGGER IF EXISTS products_set_updated_at ON products");
            stmt.executeUpdate("""
                    CREATE OR REPLACE TRIGGER products_set_updated_at
                    BEFORE INSERT OR UPDATE ON products
                    FOR EACH ROW
                    EXECUTE FUNCTION set_products_updated_at()
                    """);
            stmt.executeUpdate("""
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
                    $$ LANGUAGE plpgsql
                    """);
            stmt.executeUpdate("DROP TRIGGER IF EXISTS product_barcodes_set_updated_at ON product_barcodes");
            stmt.executeUpdate("""
                    CREATE OR REPLACE TRIGGER product_barcodes_set_updated_at
                    BEFORE INSERT OR UPDATE ON product_barcodes
                    FOR EACH ROW
                    EXECUTE FUNCTION set_product_barcodes_updated_at()
                    """);
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS products_updated_at_idx ON products(updated_at DESC)");
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS product_barcodes_updated_at_idx ON product_barcodes(updated_at DESC)");
            stmt.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS inventory (
                        product_id INTEGER NOT NULL REFERENCES products(product_id) ON DELETE CASCADE,
                        location_id INTEGER NOT NULL REFERENCES locations(location_id) ON DELETE CASCADE,
                        quantity_on_hand INTEGER NOT NULL DEFAULT 0,
                        reorder_level INTEGER NOT NULL DEFAULT 0,
                        updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        PRIMARY KEY (product_id, location_id)
                    )
                    """);
            stmt.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS receiving_batches (
                        receive_id TEXT PRIMARY KEY,
                        location_id INTEGER REFERENCES locations(location_id),
                        user_id INTEGER REFERENCES users(user_id),
                        user_name TEXT,
                        receive_device_id TEXT,
                        receive_sequence INTEGER,
                        created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
                    )
                    """);
            stmt.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS sales (
                        sale_id SERIAL PRIMARY KEY,
                        location_id INTEGER NOT NULL REFERENCES locations(location_id),
                        user_id INTEGER REFERENCES users(user_id),
                        customer_id INTEGER,
                        total_amount NUMERIC(12,2) NOT NULL DEFAULT 0,
                        status TEXT NOT NULL DEFAULT 'COMPLETED',
                        payment_method TEXT NOT NULL DEFAULT 'CASH',
                        payment_status TEXT NOT NULL DEFAULT 'PAID',
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
                    )
                    """);
            stmt.executeUpdate("ALTER TABLE sales ADD COLUMN IF NOT EXISTS vat_amount NUMERIC(12, 2) NOT NULL DEFAULT 0");
            stmt.executeUpdate("ALTER TABLE sales ADD COLUMN IF NOT EXISTS vat_rate_percent NUMERIC(6, 2) NOT NULL DEFAULT 0");
            stmt.executeUpdate("ALTER TABLE sales ADD COLUMN IF NOT EXISTS vat_mode TEXT NOT NULL DEFAULT ''");
            stmt.executeUpdate("UPDATE sales SET payment_method = COALESCE(NULLIF(TRIM(payment_method), ''), 'CASH'), payment_status = COALESCE(NULLIF(TRIM(payment_status), ''), 'PAID'), completed_at = COALESCE(completed_at, created_at, CURRENT_TIMESTAMP)");
            stmt.executeUpdate("ALTER TABLE sales ALTER COLUMN location_id SET NOT NULL");
            stmt.executeUpdate("ALTER TABLE sales ALTER COLUMN payment_method SET DEFAULT 'CASH'");
            stmt.executeUpdate("ALTER TABLE sales ALTER COLUMN payment_method SET NOT NULL");
            stmt.executeUpdate("ALTER TABLE sales ALTER COLUMN payment_status SET DEFAULT 'PAID'");
            stmt.executeUpdate("ALTER TABLE sales ALTER COLUMN payment_status SET NOT NULL");
            stmt.executeUpdate("ALTER TABLE sales ALTER COLUMN completed_at SET DEFAULT CURRENT_TIMESTAMP");
            stmt.executeUpdate("ALTER TABLE sales ALTER COLUMN completed_at SET NOT NULL");
            stmt.executeUpdate("ALTER TABLE sales DROP CONSTRAINT IF EXISTS sales_vat_amount_chk");
            stmt.executeUpdate("ALTER TABLE sales ADD CONSTRAINT sales_vat_amount_chk CHECK (vat_amount >= 0)");
            stmt.executeUpdate("ALTER TABLE sales DROP CONSTRAINT IF EXISTS sales_vat_rate_percent_chk");
            stmt.executeUpdate("ALTER TABLE sales ADD CONSTRAINT sales_vat_rate_percent_chk CHECK (vat_rate_percent >= 0 AND vat_rate_percent <= 100)");
            stmt.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS sale_items (
                        sale_item_id SERIAL PRIMARY KEY,
                        sale_id INTEGER NOT NULL REFERENCES sales(sale_id) ON DELETE CASCADE,
                        product_id INTEGER NOT NULL REFERENCES products(product_id),
                        quantity INTEGER NOT NULL DEFAULT 1,
                        unit_price NUMERIC(12,2) NOT NULL DEFAULT 0,
                        original_unit_price NUMERIC(12,2) NOT NULL DEFAULT 0,
                        discount_percent NUMERIC(6,2) NOT NULL DEFAULT 0,
                        discount_amount NUMERIC(12,2) NOT NULL DEFAULT 0,
                        price_override_reason TEXT,
                        price_override_by_user_id INTEGER REFERENCES users(user_id),
                        price_override_by_name TEXT,
                        product_type TEXT NOT NULL DEFAULT 'INVENTORY',
                        created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
                    )
                    """);
            stmt.executeUpdate("UPDATE sale_items SET original_unit_price = COALESCE(original_unit_price, unit_price, 0), product_type = COALESCE(NULLIF(TRIM(product_type), ''), 'INVENTORY')");
            stmt.executeUpdate("ALTER TABLE sale_items ALTER COLUMN quantity SET DEFAULT 1");
            stmt.executeUpdate("ALTER TABLE sale_items ALTER COLUMN unit_price SET DEFAULT 0");
            stmt.executeUpdate("ALTER TABLE sale_items ALTER COLUMN original_unit_price SET DEFAULT 0");
            stmt.executeUpdate("ALTER TABLE sale_items ALTER COLUMN original_unit_price SET NOT NULL");
            stmt.executeUpdate("ALTER TABLE sale_items ALTER COLUMN product_type SET DEFAULT 'INVENTORY'");
            stmt.executeUpdate("ALTER TABLE sale_items ALTER COLUMN product_type SET NOT NULL");
            stmt.executeUpdate("ALTER TABLE sale_items ALTER COLUMN product_id SET NOT NULL");
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS sale_items_sale_idx ON sale_items(sale_id)");
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS sale_items_product_sale_idx ON sale_items(product_id, sale_id)");
            stmt.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS inventory_movements (
                        movement_id BIGSERIAL PRIMARY KEY,
                        product_id INTEGER NOT NULL REFERENCES products(product_id),
                        location_id INTEGER NOT NULL REFERENCES locations(location_id),
                        change_qty INTEGER NOT NULL DEFAULT 0,
                        reason TEXT NOT NULL DEFAULT 'ADJUSTMENT',
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
                    )
                    """);
            stmt.executeUpdate("UPDATE inventory_movements SET change_qty = COALESCE(change_qty, 0), reason = COALESCE(NULLIF(TRIM(reason), ''), 'ADJUSTMENT')");
            stmt.executeUpdate("ALTER TABLE inventory_movements ALTER COLUMN product_id SET NOT NULL");
            stmt.executeUpdate("ALTER TABLE inventory_movements ALTER COLUMN location_id SET NOT NULL");
            stmt.executeUpdate("ALTER TABLE inventory_movements ALTER COLUMN change_qty SET DEFAULT 0");
            stmt.executeUpdate("ALTER TABLE inventory_movements ALTER COLUMN reason SET DEFAULT 'ADJUSTMENT'");
            stmt.executeUpdate("ALTER TABLE inventory_movements ALTER COLUMN reason SET NOT NULL");
            stmt.executeUpdate("""
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
                    )
                    """);
            stmt.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS customer_account_transactions (
                        transaction_id BIGSERIAL PRIMARY KEY,
                        customer_id INTEGER NOT NULL REFERENCES customer_accounts(customer_id),
                        sale_id INTEGER REFERENCES sales(sale_id),
                        custom_order_id BIGINT,
                        sales_order_id BIGINT,
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
                    )
                    """);
            stmt.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS customer_account_payment_allocations (
                        allocation_id BIGSERIAL PRIMARY KEY,
                        payment_transaction_id BIGINT NOT NULL REFERENCES customer_account_transactions(transaction_id) ON DELETE CASCADE,
                        customer_id INTEGER NOT NULL REFERENCES customer_accounts(customer_id),
                        sale_id INTEGER REFERENCES sales(sale_id),
                        custom_order_id BIGINT,
                        sales_order_id BIGINT,
                        amount NUMERIC(12,2) NOT NULL DEFAULT 0,
                        updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
                    )
                    """);
            ensureUpdatedAtTableSchema(stmt, "role_permissions");
            ensureUpdatedAtTableSchema(stmt, "role_mobile_permissions");
            ensureUpdatedAtTableSchema(stmt, "roles");
            ensureUpdatedAtTableSchema(stmt, "locations");
            ensureUpdatedAtTableSchema(stmt, "users");
            ensureUpdatedAtTableSchema(stmt, "user_locations");
            ensureUpdatedAtTableSchema(stmt, "products");
            ensureUpdatedAtTableSchema(stmt, "item_types");
            ensureUpdatedAtTableSchema(stmt, "item_brands");
            ensureUpdatedAtTableSchema(stmt, "shelf_locations");
            ensureUpdatedAtTableSchema(stmt, "product_shelf_assignments");
            ensureUpdatedAtTableSchema(stmt, "product_barcodes");
            ensureUpdatedAtTableSchema(stmt, "inventory");
            ensureUpdatedAtTableSchema(stmt, "customer_accounts");
            services.CustomerAccountLedgerService.ensureSchema(conn);
            services.ServerNotificationService.ensureSchema(conn);
            stmt.execute("SELECT setval(pg_get_serial_sequence('roles', 'role_id'), COALESCE((SELECT MAX(role_id) FROM roles), 1), (SELECT COUNT(*) FROM roles) > 0)");
            stmt.execute("SELECT setval(pg_get_serial_sequence('locations', 'location_id'), COALESCE((SELECT MAX(location_id) FROM locations), 1), (SELECT COUNT(*) FROM locations) > 0)");
            stmt.executeUpdate("INSERT INTO roles (role_name, description) VALUES ('ADMIN', 'Administrator'), ('MANAGER', 'Manager'), ('USER', 'User') ON CONFLICT (role_name) DO NOTHING");
            stmt.executeUpdate("""
                    INSERT INTO permissions (
                        permission_key, permission_name, description, permission_group, permission_subgroup
                    ) VALUES
                        ('VIEW_EMPLOYEE_SCHEDULE', 'View Employee Schedule', 'Allows viewing who is scheduled to work each day.', 'People', 'Scheduling'),
                        ('EDIT_EMPLOYEE_SCHEDULE', 'Edit Employee Schedule', 'Allows adding and removing employees from the weekly schedule.', 'People', 'Scheduling'),
                        ('SCHEDULE_OTHER_STORES', 'Schedule Other Stores', 'Allows viewing and scheduling employees at stores other than the selected login store.', 'People', 'Scheduling'),
                        ('VIEW_MULTI_STORE_STOCK', 'View Multistore Stock', 'Allows viewing synchronized stock quantities from other stores.', 'Inventory', 'Item Visibility'),
                        ('VIEW_MULTI_STORE_SALES', 'View Multistore Sales', 'Allows viewing synchronized sales and returns from other stores.', 'Point of Sale', 'Sales History'),
                        ('PROCESS_MULTI_STORE_RETURNS', 'Process Multistore Returns', 'Allows paying and queuing returns for sales from another store.', 'Point of Sale', 'Returns'),
                        ('EDIT_BALANCE_SHEET', 'Edit Submitted Balance Sheet', 'Allows revising the latest submitted Balance Sheet during its 48-hour edit window.', 'Operations', 'Cash Drawer')
                    ON CONFLICT (permission_key) DO UPDATE SET
                        permission_name = EXCLUDED.permission_name,
                        description = EXCLUDED.description,
                        permission_group = EXCLUDED.permission_group,
                        permission_subgroup = EXCLUDED.permission_subgroup
                    """);
            stmt.executeUpdate("""
                    INSERT INTO role_permissions(role_id,permission_id)
                    SELECT r.role_id,p.permission_id FROM roles r CROSS JOIN permissions p
                    WHERE UPPER(r.role_name) IN ('ADMIN','OWNER','CEO')
                      AND p.permission_key IN ('VIEW_MULTI_STORE_STOCK','VIEW_MULTI_STORE_SALES','PROCESS_MULTI_STORE_RETURNS')
                    ON CONFLICT(role_id,permission_id) DO NOTHING
                    """);
            stmt.executeUpdate("""
                    INSERT INTO role_permissions(role_id,permission_id)
                    SELECT legacy.role_id,target.permission_id FROM role_permissions legacy
                    JOIN permissions oldp ON oldp.permission_id=legacy.permission_id AND oldp.permission_key='VIEW_ALL_STORES_INVENTORY'
                    JOIN permissions target ON target.permission_key='VIEW_MULTI_STORE_STOCK'
                    ON CONFLICT(role_id,permission_id) DO NOTHING
                    """);
            stmt.executeUpdate("""
                    INSERT INTO role_permissions (role_id, permission_id)
                    SELECT r.role_id, p.permission_id
                    FROM roles r
                    CROSS JOIN permissions p
                    WHERE UPPER(p.permission_key) = 'VIEW_EMPLOYEE_SCHEDULE'
                      AND NOT EXISTS (
                          SELECT 1
                          FROM role_permissions existing
                          JOIN permissions existing_permission ON existing_permission.permission_id = existing.permission_id
                          WHERE UPPER(existing_permission.permission_key) = 'VIEW_EMPLOYEE_SCHEDULE'
                      )
                    ON CONFLICT (role_id, permission_id) DO NOTHING
                    """);
            stmt.executeUpdate("""
                    INSERT INTO role_permissions (role_id, permission_id)
                    SELECT r.role_id, p.permission_id
                    FROM roles r
                    JOIN permissions p ON UPPER(p.permission_key) = 'EDIT_BALANCE_SHEET'
                    WHERE (UPPER(r.role_name) IN ('ADMIN', 'OWNER', 'CEO') OR UPPER(r.role_name) LIKE '%MANAGER%')
                      AND NOT EXISTS (
                          SELECT 1 FROM role_permissions existing
                          JOIN permissions existing_permission ON existing_permission.permission_id=existing.permission_id
                          WHERE existing_permission.permission_key='EDIT_BALANCE_SHEET'
                      )
                    ON CONFLICT (role_id, permission_id) DO NOTHING
                    """);
            stmt.executeUpdate("""
                    INSERT INTO role_permissions (role_id, permission_id)
                    SELECT r.role_id, p.permission_id
                    FROM roles r
                    JOIN permissions p ON UPPER(p.permission_key) = 'SCHEDULE_OTHER_STORES'
                    WHERE UPPER(r.role_name) = 'ADMIN'
                    ON CONFLICT (role_id, permission_id) DO NOTHING
                    """);
            stmt.executeUpdate("""
                    INSERT INTO role_permissions (role_id, permission_id)
                    SELECT r.role_id, p.permission_id
                    FROM roles r
                    CROSS JOIN permissions p
                    WHERE (UPPER(r.role_name) IN ('ADMIN', 'CEO') OR UPPER(r.role_name) LIKE '%MANAGER%')
                      AND UPPER(p.permission_key) = 'EDIT_EMPLOYEE_SCHEDULE'
                      AND NOT EXISTS (
                          SELECT 1
                          FROM role_permissions existing
                          JOIN permissions existing_permission ON existing_permission.permission_id = existing.permission_id
                          WHERE UPPER(existing_permission.permission_key) = 'EDIT_EMPLOYEE_SCHEDULE'
                      )
                    ON CONFLICT (role_id, permission_id) DO NOTHING
                    """);
            ServerEmployeeScheduleService.ensureSchema(conn);
        }
    }

    private static void ensureUpdatedAtTableSchema(Statement stmt, String table) throws SQLException {
        String functionName = "set_" + table + "_updated_at";
        String triggerName = table + "_set_updated_at";
        String indexName = table + "_updated_at_idx";
        stmt.executeUpdate("ALTER TABLE " + quote(table) + " ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP");
        stmt.executeUpdate("""
                CREATE OR REPLACE FUNCTION %s()
                RETURNS TRIGGER AS $$
                BEGIN
                    IF TG_OP = 'INSERT' THEN
                        NEW.updated_at = COALESCE(NEW.updated_at, CURRENT_TIMESTAMP);
                    ELSIF NEW.updated_at IS NOT DISTINCT FROM OLD.updated_at THEN
                        NEW.updated_at = CURRENT_TIMESTAMP;
                    END IF;
                    RETURN NEW;
                END;
                $$ LANGUAGE plpgsql
                """.formatted(quote(functionName)));
        stmt.executeUpdate("DROP TRIGGER IF EXISTS " + quote(triggerName) + " ON " + quote(table));
        stmt.executeUpdate("""
                CREATE OR REPLACE TRIGGER %s
                BEFORE INSERT OR UPDATE ON %s
                FOR EACH ROW
                EXECUTE FUNCTION %s()
                """.formatted(quote(triggerName), quote(table), quote(functionName)));
        stmt.executeUpdate("CREATE INDEX IF NOT EXISTS " + quote(indexName) + " ON " + quote(table) + "(updated_at DESC)");
    }

    private static String quote(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }
}
