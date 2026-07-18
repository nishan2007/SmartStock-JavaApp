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
    permission_subgroup TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS role_permissions (
    role_id INTEGER NOT NULL REFERENCES roles(role_id) ON DELETE CASCADE,
    permission_id INTEGER NOT NULL REFERENCES permissions(permission_id) ON DELETE CASCADE,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (role_id, permission_id)
);

ALTER TABLE permissions ADD COLUMN IF NOT EXISTS permission_name TEXT;
ALTER TABLE permissions ADD COLUMN IF NOT EXISTS description TEXT;
ALTER TABLE permissions ADD COLUMN IF NOT EXISTS permission_group TEXT;
ALTER TABLE permissions ADD COLUMN IF NOT EXISTS permission_subgroup TEXT;

CREATE TABLE IF NOT EXISTS mobile_permissions (
    permission_key TEXT PRIMARY KEY,
    permission_name TEXT,
    description TEXT,
    permission_group TEXT,
    permission_subgroup TEXT,
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
    email_sender_address TEXT NOT NULL DEFAULT '',
    email_sender_name TEXT NOT NULL DEFAULT '',
    email_bcc_address TEXT NOT NULL DEFAULT '',
    balance_sheet_recipient_email TEXT NOT NULL DEFAULT '',
    email_receipts_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    email_order_confirmations_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    email_quotes_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    email_invoices_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    email_delivery_bills_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    email_connected_at TIMESTAMPTZ,
    email_last_tested_at TIMESTAMPTZ,
    receipt_store_code TEXT NOT NULL DEFAULT '0001',
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
    full_name TEXT NOT NULL,
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
);

CREATE TABLE IF NOT EXISTS user_locations (
    user_id INTEGER NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    location_id INTEGER NOT NULL REFERENCES locations(location_id) ON DELETE CASCADE,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, location_id)
);

UPDATE users SET full_name = COALESCE(NULLIF(BTRIM(full_name), ''), username)
WHERE full_name IS NULL OR BTRIM(full_name) = '';
UPDATE users SET compensation_type = 'HOURLY' WHERE compensation_type IS NULL;
UPDATE users SET salary = 0 WHERE salary IS NULL;
UPDATE users SET is_active = TRUE WHERE is_active IS NULL;
ALTER TABLE users ALTER COLUMN full_name SET NOT NULL;
ALTER TABLE users ALTER COLUMN compensation_type SET DEFAULT 'HOURLY';
ALTER TABLE users ALTER COLUMN compensation_type SET NOT NULL;
ALTER TABLE users ALTER COLUMN salary SET DEFAULT 0;
ALTER TABLE users ALTER COLUMN salary SET NOT NULL;
ALTER TABLE users ALTER COLUMN is_active SET DEFAULT TRUE;
ALTER TABLE users ALTER COLUMN is_active SET NOT NULL;

UPDATE locations SET receipt_store_code = '0001'
WHERE receipt_store_code IS NULL OR BTRIM(receipt_store_code) = '';
ALTER TABLE locations ALTER COLUMN receipt_store_code SET DEFAULT '0001';
ALTER TABLE locations ALTER COLUMN receipt_store_code SET NOT NULL;


CREATE TABLE IF NOT EXISTS employee_payroll_settings (
    setting_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id INTEGER NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    period_type TEXT NOT NULL DEFAULT 'SEMI_MONTHLY',
    work_hour_limit NUMERIC(8,2) NOT NULL DEFAULT 80.00,
    effective_from DATE NOT NULL,
    created_by_user_id INTEGER REFERENCES users(user_id),
    created_by_name TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT employee_payroll_settings_period_type_chk
        CHECK (period_type IN ('SEMI_MONTHLY', 'WEEKLY', 'FOUR_MONTH_BLOCKS')),
    CONSTRAINT employee_payroll_settings_hour_limit_chk CHECK (work_hour_limit > 0),
    CONSTRAINT employee_payroll_settings_user_effective_key UNIQUE (user_id, effective_from)
);

CREATE INDEX IF NOT EXISTS employee_payroll_settings_user_effective_idx
ON employee_payroll_settings(user_id, effective_from DESC);

CREATE OR REPLACE FUNCTION set_employee_payroll_settings_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.updated_at IS NOT DISTINCT FROM OLD.updated_at THEN
        NEW.updated_at = CURRENT_TIMESTAMP;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS employee_payroll_settings_set_updated_at ON employee_payroll_settings;
CREATE TRIGGER employee_payroll_settings_set_updated_at
BEFORE UPDATE ON employee_payroll_settings
FOR EACH ROW EXECUTE FUNCTION set_employee_payroll_settings_updated_at();

INSERT INTO employee_payroll_settings (
    setting_id, user_id, period_type, work_hour_limit, effective_from, created_by_name
)
SELECT (
    SUBSTR(md5('smartstock-employee-payroll-default:' || u.user_id), 1, 8) || '-' ||
    SUBSTR(md5('smartstock-employee-payroll-default:' || u.user_id), 9, 4) || '-' ||
    SUBSTR(md5('smartstock-employee-payroll-default:' || u.user_id), 13, 4) || '-' ||
    SUBSTR(md5('smartstock-employee-payroll-default:' || u.user_id), 17, 4) || '-' ||
    SUBSTR(md5('smartstock-employee-payroll-default:' || u.user_id), 21, 12)
)::uuid,
u.user_id, 'SEMI_MONTHLY', 80.00, DATE '1900-01-01', 'System default'
FROM users u
WHERE NOT EXISTS (
    SELECT 1 FROM employee_payroll_settings existing WHERE existing.user_id = u.user_id
);


DO $$
BEGIN
    ALTER TABLE public.employee_payroll_settings ENABLE ROW LEVEL SECURITY;
    REVOKE ALL ON TABLE public.employee_payroll_settings FROM PUBLIC;
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'anon') THEN
        REVOKE ALL ON TABLE public.employee_payroll_settings FROM anon;
    END IF;
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'authenticated') THEN
        REVOKE ALL ON TABLE public.employee_payroll_settings FROM authenticated;
    END IF;
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'service_role') THEN
        GRANT ALL ON TABLE public.employee_payroll_settings TO service_role;
        DROP POLICY IF EXISTS employee_payroll_settings_service_role_all ON public.employee_payroll_settings;
        CREATE POLICY employee_payroll_settings_service_role_all
            ON public.employee_payroll_settings FOR ALL TO service_role
            USING (true) WITH CHECK (true);
    END IF;
END $$;



CREATE TABLE IF NOT EXISTS employee_schedule_shifts (
    shift_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    location_id INTEGER NOT NULL REFERENCES locations(location_id) ON DELETE CASCADE,
    shift_name TEXT NOT NULL,
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    display_order INTEGER NOT NULL DEFAULT 0,
    created_by_user_id INTEGER REFERENCES users(user_id),
    created_by_name TEXT,
    updated_by_user_id INTEGER REFERENCES users(user_id),
    updated_by_name TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT employee_schedule_shifts_daytime_check CHECK (end_time > start_time),
    CONSTRAINT employee_schedule_shifts_location_identity UNIQUE (location_id, shift_id)
);

ALTER TABLE employee_schedule_shifts ADD COLUMN IF NOT EXISTS updated_by_user_id INTEGER REFERENCES users(user_id);
ALTER TABLE employee_schedule_shifts ADD COLUMN IF NOT EXISTS updated_by_name TEXT;

CREATE UNIQUE INDEX IF NOT EXISTS employee_schedule_shifts_location_name_idx
ON employee_schedule_shifts(location_id, LOWER(TRIM(shift_name)));

CREATE INDEX IF NOT EXISTS employee_schedule_shifts_location_order_idx
ON employee_schedule_shifts(location_id, is_active DESC, display_order, start_time);

ALTER TABLE employee_schedule_shifts ENABLE ROW LEVEL SECURITY;

CREATE OR REPLACE FUNCTION set_employee_schedule_shifts_updated_at()
RETURNS TRIGGER
LANGUAGE plpgsql
SET search_path = ''
AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        NEW.updated_at = COALESCE(NEW.updated_at, CURRENT_TIMESTAMP);
    ELSIF NEW.updated_at IS NOT DISTINCT FROM OLD.updated_at THEN
        NEW.updated_at = CURRENT_TIMESTAMP;
    END IF;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS employee_schedule_shifts_set_updated_at ON employee_schedule_shifts;
CREATE TRIGGER employee_schedule_shifts_set_updated_at
BEFORE INSERT OR UPDATE ON employee_schedule_shifts
FOR EACH ROW EXECUTE FUNCTION set_employee_schedule_shifts_updated_at();

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'anon') THEN
        REVOKE ALL ON employee_schedule_shifts FROM anon;
    END IF;
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'authenticated') THEN
        REVOKE ALL ON employee_schedule_shifts FROM authenticated;
    END IF;
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'service_role') THEN
        GRANT ALL ON employee_schedule_shifts TO service_role;
    END IF;
END;
$$;

INSERT INTO employee_schedule_shifts (shift_id, location_id, shift_name, start_time, end_time, display_order)
SELECT (md5('employee-schedule-shift:' || l.location_id || ':0700-1600'))::uuid,
       l.location_id, '7 AM–4 PM', TIME '07:00', TIME '16:00', 10
FROM locations l
ON CONFLICT (shift_id) DO NOTHING;

INSERT INTO employee_schedule_shifts (shift_id, location_id, shift_name, start_time, end_time, display_order)
SELECT (md5('employee-schedule-shift:' || l.location_id || ':0900-1800'))::uuid,
       l.location_id, '9 AM–6 PM', TIME '09:00', TIME '18:00', 20
FROM locations l
ON CONFLICT (shift_id) DO NOTHING;

CREATE TABLE IF NOT EXISTS employee_schedule_assignments (
    location_id INTEGER NOT NULL REFERENCES locations(location_id) ON DELETE CASCADE,
    user_id INTEGER NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    work_date DATE NOT NULL,
    lunch_start_time TIME,
    shift_id UUID,
    shift_name_snapshot TEXT,
    shift_start_time TIME,
    shift_end_time TIME,
    created_by_user_id INTEGER REFERENCES users(user_id),
    created_by_name TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (location_id, user_id, work_date),
    CONSTRAINT employee_schedule_assignments_location_shift_fk FOREIGN KEY (location_id, shift_id)
        REFERENCES employee_schedule_shifts(location_id, shift_id)
);

CREATE INDEX IF NOT EXISTS employee_schedule_location_date_idx
ON employee_schedule_assignments(location_id, work_date);

CREATE INDEX IF NOT EXISTS employee_schedule_user_date_idx
ON employee_schedule_assignments(user_id, work_date);

ALTER TABLE employee_schedule_assignments
ADD COLUMN IF NOT EXISTS lunch_start_time TIME;

ALTER TABLE employee_schedule_assignments ADD COLUMN IF NOT EXISTS shift_id UUID;
ALTER TABLE employee_schedule_assignments ADD COLUMN IF NOT EXISTS shift_name_snapshot TEXT;
ALTER TABLE employee_schedule_assignments ADD COLUMN IF NOT EXISTS shift_start_time TIME;
ALTER TABLE employee_schedule_assignments ADD COLUMN IF NOT EXISTS shift_end_time TIME;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'employee_schedule_assignments_location_shift_fk'
    ) THEN
        ALTER TABLE employee_schedule_assignments
        ADD CONSTRAINT employee_schedule_assignments_location_shift_fk
        FOREIGN KEY (location_id, shift_id)
        REFERENCES employee_schedule_shifts(location_id, shift_id);
    END IF;
END;
$$;

ALTER TABLE employee_schedule_assignments ENABLE ROW LEVEL SECURITY;

CREATE OR REPLACE FUNCTION set_employee_schedule_assignments_updated_at()
RETURNS TRIGGER
LANGUAGE plpgsql
SET search_path = ''
AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        NEW.updated_at = COALESCE(NEW.updated_at, CURRENT_TIMESTAMP);
    ELSIF NEW.updated_at IS NOT DISTINCT FROM OLD.updated_at THEN
        NEW.updated_at = CURRENT_TIMESTAMP;
    END IF;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS employee_schedule_assignments_set_updated_at ON employee_schedule_assignments;
CREATE TRIGGER employee_schedule_assignments_set_updated_at
BEFORE INSERT OR UPDATE ON employee_schedule_assignments
FOR EACH ROW EXECUTE FUNCTION set_employee_schedule_assignments_updated_at();

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'anon') THEN
        REVOKE ALL ON employee_schedule_assignments FROM anon;
    END IF;
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'authenticated') THEN
        REVOKE ALL ON employee_schedule_assignments FROM authenticated;
    END IF;
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'service_role') THEN
        GRANT ALL ON employee_schedule_assignments TO service_role;
    END IF;
END;
$$;

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
ALTER TABLE locations
ADD COLUMN IF NOT EXISTS email_sender_address TEXT NOT NULL DEFAULT '';
ALTER TABLE locations
ADD COLUMN IF NOT EXISTS email_sender_name TEXT NOT NULL DEFAULT '';
ALTER TABLE locations
ADD COLUMN IF NOT EXISTS email_bcc_address TEXT NOT NULL DEFAULT '';
ALTER TABLE locations
ADD COLUMN IF NOT EXISTS balance_sheet_recipient_email TEXT NOT NULL DEFAULT '';
ALTER TABLE locations
ADD COLUMN IF NOT EXISTS email_receipts_enabled BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE locations
ADD COLUMN IF NOT EXISTS email_order_confirmations_enabled BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE locations
ADD COLUMN IF NOT EXISTS email_quotes_enabled BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE locations
ADD COLUMN IF NOT EXISTS email_invoices_enabled BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE locations
ADD COLUMN IF NOT EXISTS email_delivery_bills_enabled BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE locations
ADD COLUMN IF NOT EXISTS email_connected_at TIMESTAMPTZ;
ALTER TABLE locations
ADD COLUMN IF NOT EXISTS email_last_tested_at TIMESTAMPTZ;

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
ADD COLUMN IF NOT EXISTS hire_date DATE;

UPDATE users
SET hire_date = COALESCE(created_at::date, CURRENT_DATE)
WHERE hire_date IS NULL;

ALTER TABLE users ALTER COLUMN hire_date SET DEFAULT CURRENT_DATE;
ALTER TABLE users ALTER COLUMN hire_date SET NOT NULL;

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

ALTER TABLE users
ADD COLUMN IF NOT EXISTS badge_rotated_at TIMESTAMPTZ;

ALTER TABLE users
ADD COLUMN IF NOT EXISTS badge_rotated_by_user_id INTEGER;

ALTER TABLE users
ADD COLUMN IF NOT EXISTS badge_rotated_by_name TEXT;

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

ALTER FUNCTION set_email_outbox_updated_at() SET search_path = public;

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

INSERT INTO categories (name, description)
SELECT 'Custom', 'Default department for custom items'
WHERE NOT EXISTS (
    SELECT 1 FROM categories WHERE UPPER(BTRIM(name)) = 'CUSTOM'
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
);

CREATE TABLE IF NOT EXISTS product_barcodes (
    product_barcode_id INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    product_id INTEGER NOT NULL REFERENCES products(product_id) ON DELETE CASCADE,
    barcode TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(barcode)
);

UPDATE products SET sku = 'ITEM-' || product_id
WHERE sku IS NULL OR BTRIM(sku) = '';
ALTER TABLE products ALTER COLUMN sku SET NOT NULL;

CREATE TABLE IF NOT EXISTS item_types (
    item_type_id SERIAL PRIMARY KEY,
    category_id INTEGER NOT NULL REFERENCES categories(category_id) ON DELETE CASCADE,
    name TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX IF NOT EXISTS item_types_category_name_unique_idx ON item_types(category_id, LOWER(name));

CREATE TABLE IF NOT EXISTS item_brands (
    brand_id SERIAL PRIMARY KEY,
    name TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX IF NOT EXISTS item_brands_name_unique_idx ON item_brands(LOWER(name));

CREATE TABLE IF NOT EXISTS shelf_locations (
    shelf_location_id SERIAL PRIMARY KEY,
    location_id INTEGER NOT NULL REFERENCES locations(location_id) ON DELETE CASCADE,
    name TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (shelf_location_id, location_id)
);
CREATE UNIQUE INDEX IF NOT EXISTS shelf_locations_location_name_unique_idx ON shelf_locations(location_id, LOWER(name));
CREATE UNIQUE INDEX IF NOT EXISTS shelf_locations_id_location_unique_idx ON shelf_locations(shelf_location_id, location_id);

UPDATE item_types SET name = UPPER(REGEXP_REPLACE(BTRIM(name), '\s+', ' ', 'g'))
WHERE name IS DISTINCT FROM UPPER(REGEXP_REPLACE(BTRIM(name), '\s+', ' ', 'g'));
UPDATE item_brands SET name = UPPER(REGEXP_REPLACE(BTRIM(name), '\s+', ' ', 'g'))
WHERE name IS DISTINCT FROM UPPER(REGEXP_REPLACE(BTRIM(name), '\s+', ' ', 'g'));
UPDATE shelf_locations SET name = UPPER(REGEXP_REPLACE(BTRIM(name), '\s+', ' ', 'g'))
WHERE name IS DISTINCT FROM UPPER(REGEXP_REPLACE(BTRIM(name), '\s+', ' ', 'g'));

CREATE UNIQUE INDEX IF NOT EXISTS item_types_normalized_name_unique_idx ON item_types(category_id, UPPER(REGEXP_REPLACE(BTRIM(name), '\s+', ' ', 'g')));
CREATE UNIQUE INDEX IF NOT EXISTS item_brands_normalized_name_unique_idx ON item_brands(UPPER(REGEXP_REPLACE(BTRIM(name), '\s+', ' ', 'g')));
CREATE UNIQUE INDEX IF NOT EXISTS shelf_locations_normalized_name_unique_idx ON shelf_locations(location_id, UPPER(REGEXP_REPLACE(BTRIM(name), '\s+', ' ', 'g')));

ALTER TABLE products ADD COLUMN IF NOT EXISTS item_type_id INTEGER REFERENCES item_types(item_type_id);
ALTER TABLE products ADD COLUMN IF NOT EXISTS brand_id INTEGER REFERENCES item_brands(brand_id);

CREATE TABLE IF NOT EXISTS product_shelf_assignments (
    product_id INTEGER NOT NULL REFERENCES products(product_id) ON DELETE CASCADE,
    location_id INTEGER NOT NULL REFERENCES locations(location_id) ON DELETE CASCADE,
    shelf_location_id INTEGER NOT NULL,
    storage_shelf_location_id INTEGER,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (product_id, location_id),
    FOREIGN KEY (shelf_location_id, location_id) REFERENCES shelf_locations(shelf_location_id, location_id),
    FOREIGN KEY (storage_shelf_location_id, location_id) REFERENCES shelf_locations(shelf_location_id, location_id)
);
ALTER TABLE product_shelf_assignments ALTER COLUMN storage_shelf_location_id DROP NOT NULL;
ALTER TABLE item_types ENABLE ROW LEVEL SECURITY;
ALTER TABLE item_brands ENABLE ROW LEVEL SECURITY;
ALTER TABLE shelf_locations ENABLE ROW LEVEL SECURITY;
ALTER TABLE product_shelf_assignments ENABLE ROW LEVEL SECURITY;

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
);

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
);

UPDATE sales
SET payment_method = COALESCE(NULLIF(BTRIM(payment_method), ''), 'CASH'),
    payment_status = COALESCE(NULLIF(BTRIM(payment_status), ''), 'PAID'),
    completed_at = COALESCE(completed_at, created_at, CURRENT_TIMESTAMP);
ALTER TABLE sales ALTER COLUMN location_id SET NOT NULL;
ALTER TABLE sales ALTER COLUMN payment_method SET DEFAULT 'CASH';
ALTER TABLE sales ALTER COLUMN payment_method SET NOT NULL;
ALTER TABLE sales ALTER COLUMN payment_status SET DEFAULT 'PAID';
ALTER TABLE sales ALTER COLUMN payment_status SET NOT NULL;
ALTER TABLE sales ALTER COLUMN completed_at SET DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE sales ALTER COLUMN completed_at SET NOT NULL;

UPDATE sale_items
SET original_unit_price = COALESCE(original_unit_price, unit_price, 0),
    product_type = COALESCE(NULLIF(BTRIM(product_type), ''), 'INVENTORY');
ALTER TABLE sale_items ALTER COLUMN quantity SET DEFAULT 1;
ALTER TABLE sale_items ALTER COLUMN unit_price SET DEFAULT 0;
ALTER TABLE sale_items ALTER COLUMN original_unit_price SET DEFAULT 0;
ALTER TABLE sale_items ALTER COLUMN original_unit_price SET NOT NULL;
ALTER TABLE sale_items ALTER COLUMN product_type SET DEFAULT 'INVENTORY';
ALTER TABLE sale_items ALTER COLUMN product_type SET NOT NULL;
ALTER TABLE sale_items ALTER COLUMN product_id SET NOT NULL;

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
);

UPDATE inventory_movements
SET change_qty = COALESCE(change_qty, 0),
    reason = COALESCE(NULLIF(BTRIM(reason), ''), 'ADJUSTMENT');
ALTER TABLE inventory_movements ALTER COLUMN product_id SET NOT NULL;
ALTER TABLE inventory_movements ALTER COLUMN location_id SET NOT NULL;
ALTER TABLE inventory_movements ALTER COLUMN change_qty SET DEFAULT 0;
ALTER TABLE inventory_movements ALTER COLUMN reason SET DEFAULT 'ADJUSTMENT';
ALTER TABLE inventory_movements ALTER COLUMN reason SET NOT NULL;

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

CREATE TABLE IF NOT EXISTS email_outbox (
    email_outbox_id BIGSERIAL PRIMARY KEY,
    location_id INTEGER REFERENCES locations(location_id),
    sender_email TEXT NOT NULL,
    sender_name TEXT NOT NULL DEFAULT '',
    recipient_email TEXT NOT NULL,
    bcc_email TEXT,
    subject TEXT NOT NULL,
    body_text TEXT NOT NULL DEFAULT '',
    body_html TEXT NOT NULL DEFAULT '',
    attachment_name TEXT,
    attachment_content_type TEXT,
    attachment_body TEXT,
    document_type TEXT NOT NULL,
    document_id TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'QUEUED',
    attempts INTEGER NOT NULL DEFAULT 0,
    max_attempts INTEGER NOT NULL DEFAULT 3,
    last_error TEXT,
    sent_at TIMESTAMPTZ,
    queued_by_user_id INTEGER REFERENCES users(user_id),
    queued_by_name TEXT,
    device_id TEXT,
    device_name TEXT,
    sync_uuid UUID NOT NULL DEFAULT gen_random_uuid(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT email_outbox_status_chk CHECK (status IN ('QUEUED', 'SENDING', 'SENT', 'FAILED', 'CANCELLED')),
    CONSTRAINT email_outbox_attempts_chk CHECK (attempts >= 0 AND max_attempts > 0)
);

CREATE TABLE IF NOT EXISTS email_outbox_events (
    email_outbox_event_id BIGSERIAL PRIMARY KEY,
    email_outbox_id BIGINT NOT NULL REFERENCES email_outbox(email_outbox_id) ON DELETE CASCADE,
    event_type TEXT NOT NULL,
    message TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    user_id INTEGER REFERENCES users(user_id),
    user_name TEXT,
    device_id TEXT,
    device_name TEXT,
    sync_uuid UUID NOT NULL DEFAULT gen_random_uuid()
);

CREATE OR REPLACE FUNCTION set_email_outbox_updated_at()
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

DROP TRIGGER IF EXISTS email_outbox_set_updated_at ON email_outbox;
CREATE TRIGGER email_outbox_set_updated_at
BEFORE INSERT OR UPDATE ON email_outbox
FOR EACH ROW
EXECUTE FUNCTION set_email_outbox_updated_at();

CREATE INDEX IF NOT EXISTS email_outbox_status_idx
ON email_outbox(status, created_at);

CREATE INDEX IF NOT EXISTS email_outbox_document_idx
ON email_outbox(document_type, document_id, created_at DESC);

CREATE INDEX IF NOT EXISTS email_outbox_location_idx
ON email_outbox(location_id, created_at DESC);

CREATE UNIQUE INDEX IF NOT EXISTS email_outbox_sync_uuid_key
ON email_outbox(sync_uuid);

CREATE INDEX IF NOT EXISTS email_outbox_events_outbox_idx
ON email_outbox_events(email_outbox_id, created_at DESC);

CREATE UNIQUE INDEX IF NOT EXISTS email_outbox_events_sync_uuid_key
ON email_outbox_events(sync_uuid);

ALTER TABLE email_outbox ENABLE ROW LEVEL SECURITY;
ALTER TABLE email_outbox_events ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS email_outbox_service_role_all ON email_outbox;
CREATE POLICY email_outbox_service_role_all
ON email_outbox
FOR ALL
TO service_role
USING (true)
WITH CHECK (true);

DROP POLICY IF EXISTS email_outbox_events_service_role_all ON email_outbox_events;
CREATE POLICY email_outbox_events_service_role_all
ON email_outbox_events
FOR ALL
TO service_role
USING (true)
WITH CHECK (true);

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
);

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
);

ALTER TABLE customer_account_transactions
ADD COLUMN IF NOT EXISTS payment_id TEXT;

ALTER TABLE customer_account_transactions
ADD COLUMN IF NOT EXISTS location_id INTEGER;

ALTER TABLE customer_account_transactions
ADD COLUMN IF NOT EXISTS sales_order_id BIGINT;

ALTER TABLE customer_account_transactions
ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP;

ALTER TABLE customer_account_payment_allocations
ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP;

ALTER TABLE customer_account_payment_allocations
ADD COLUMN IF NOT EXISTS sales_order_id BIGINT;

CREATE INDEX IF NOT EXISTS customer_account_transactions_sales_order_idx
ON customer_account_transactions(sales_order_id);

CREATE INDEX IF NOT EXISTS customer_account_payment_allocations_sales_order_idx
ON customer_account_payment_allocations(sales_order_id);

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

INSERT INTO permissions (permission_key, permission_name, description, permission_group, permission_subgroup)
VALUES
    ('VIEW_EMPLOYEE_SCHEDULE', 'View Employee Schedule', 'Allows viewing who is scheduled to work each day.', 'People', 'Scheduling'),
    ('EDIT_EMPLOYEE_SCHEDULE', 'Edit Employee Schedule', 'Allows adding and removing employees from the weekly schedule.', 'People', 'Scheduling'),
    ('SCHEDULE_OTHER_STORES', 'Schedule Other Stores', 'Allows viewing and scheduling employees at stores other than the selected login store.', 'People', 'Scheduling')
ON CONFLICT (permission_key) DO UPDATE SET
    permission_name = EXCLUDED.permission_name,
    description = EXCLUDED.description,
    permission_group = EXCLUDED.permission_group,
    permission_subgroup = EXCLUDED.permission_subgroup;

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
ON CONFLICT (role_id, permission_id) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.role_id, p.permission_id
FROM roles r
JOIN permissions p ON UPPER(p.permission_key) = 'SCHEDULE_OTHER_STORES'
WHERE UPPER(r.role_name) = 'ADMIN'
ON CONFLICT (role_id, permission_id) DO NOTHING;

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
ON CONFLICT (role_id, permission_id) DO NOTHING;

INSERT INTO locations (name, receipt_store_code, timezone)
SELECT 'Default Store', '0001', 'America/New_York'
WHERE NOT EXISTS (SELECT 1 FROM locations);

INSERT INTO employee_schedule_shifts (shift_id, location_id, shift_name, start_time, end_time, display_order)
SELECT (md5('employee-schedule-shift:' || l.location_id || ':0700-1600'))::uuid,
       l.location_id, '7 AM–4 PM', TIME '07:00', TIME '16:00', 10
FROM locations l
ON CONFLICT (shift_id) DO NOTHING;

INSERT INTO employee_schedule_shifts (shift_id, location_id, shift_name, start_time, end_time, display_order)
SELECT (md5('employee-schedule-shift:' || l.location_id || ':0900-1800'))::uuid,
       l.location_id, '9 AM–6 PM', TIME '09:00', TIME '18:00', 20
FROM locations l
ON CONFLICT (shift_id) DO NOTHING;

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
