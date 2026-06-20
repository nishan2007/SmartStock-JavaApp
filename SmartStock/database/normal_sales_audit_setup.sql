-- Normal sales audit trail for SmartStock.
-- Safe to run more than once.

ALTER TABLE sales
ADD COLUMN IF NOT EXISTS device_id TEXT;

ALTER TABLE sales
ADD COLUMN IF NOT EXISTS device_name TEXT;

ALTER TABLE sales
ADD COLUMN IF NOT EXISTS completed_at TIMESTAMPTZ;

ALTER TABLE sale_returns
ADD COLUMN IF NOT EXISTS device_name TEXT;

ALTER TABLE customer_account_transactions
ADD COLUMN IF NOT EXISTS device_id TEXT;

ALTER TABLE customer_account_transactions
ADD COLUMN IF NOT EXISTS device_name TEXT;

ALTER TABLE inventory_movements
ADD COLUMN IF NOT EXISTS sale_id INTEGER REFERENCES sales(sale_id);

ALTER TABLE inventory_movements
ADD COLUMN IF NOT EXISTS sale_item_id INTEGER REFERENCES sale_items(sale_item_id);

ALTER TABLE inventory_movements
ADD COLUMN IF NOT EXISTS sale_return_id BIGINT REFERENCES sale_returns(return_id);

ALTER TABLE inventory_movements
ADD COLUMN IF NOT EXISTS device_id TEXT;

ALTER TABLE inventory_movements
ADD COLUMN IF NOT EXISTS device_name TEXT;

ALTER TABLE inventory_movements
ADD COLUMN IF NOT EXISTS user_id INTEGER REFERENCES users(user_id);

CREATE TABLE IF NOT EXISTS sale_audit_log (
    sale_audit_id BIGSERIAL PRIMARY KEY,
    sale_id INTEGER REFERENCES sales(sale_id) ON DELETE SET NULL,
    sale_item_id INTEGER REFERENCES sale_items(sale_item_id) ON DELETE SET NULL,
    return_id BIGINT REFERENCES sale_returns(return_id) ON DELETE SET NULL,
    return_item_id BIGINT REFERENCES sale_return_items(return_item_id) ON DELETE SET NULL,
    customer_id INTEGER REFERENCES customer_accounts(customer_id),
    product_id INTEGER REFERENCES products(product_id),
    location_id INTEGER REFERENCES locations(location_id),
    action_type TEXT NOT NULL,
    action_scope TEXT NOT NULL DEFAULT 'SALE',
    field_name TEXT,
    old_value TEXT,
    new_value TEXT,
    amount NUMERIC(12, 2),
    quantity INTEGER,
    reason TEXT,
    note TEXT,
    user_id INTEGER REFERENCES users(user_id),
    user_name TEXT,
    device_id TEXT,
    device_name TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS sale_audit_log_sale_idx
ON sale_audit_log(sale_id, created_at DESC);

CREATE INDEX IF NOT EXISTS sale_audit_log_item_idx
ON sale_audit_log(sale_item_id, created_at DESC);

CREATE INDEX IF NOT EXISTS sale_audit_log_return_idx
ON sale_audit_log(return_id, created_at DESC);

CREATE INDEX IF NOT EXISTS sale_audit_log_product_idx
ON sale_audit_log(product_id, created_at DESC);

CREATE INDEX IF NOT EXISTS sale_audit_log_user_idx
ON sale_audit_log(user_id, created_at DESC);

CREATE INDEX IF NOT EXISTS sale_audit_log_device_idx
ON sale_audit_log(device_id, created_at DESC);

CREATE INDEX IF NOT EXISTS sale_audit_log_location_idx
ON sale_audit_log(location_id, created_at DESC);

CREATE INDEX IF NOT EXISTS sale_audit_log_action_idx
ON sale_audit_log(action_type, created_at DESC);

CREATE INDEX IF NOT EXISTS sales_device_idx
ON sales(device_id, created_at DESC);

CREATE INDEX IF NOT EXISTS sales_device_name_idx
ON sales(device_name, created_at DESC);

CREATE INDEX IF NOT EXISTS sale_returns_device_idx
ON sale_returns(device_id, created_at DESC);

CREATE INDEX IF NOT EXISTS sale_returns_device_name_idx
ON sale_returns(device_name, created_at DESC);

CREATE INDEX IF NOT EXISTS inventory_movements_sale_idx
ON inventory_movements(sale_id, created_at DESC);

CREATE INDEX IF NOT EXISTS inventory_movements_sale_return_idx
ON inventory_movements(sale_return_id, created_at DESC);

CREATE INDEX IF NOT EXISTS inventory_movements_device_idx
ON inventory_movements(device_id, created_at DESC);

INSERT INTO permissions (permission_key, permission_name)
SELECT 'VIEW_SALE_AUDIT', 'View Sale Audit'
WHERE NOT EXISTS (
    SELECT 1 FROM permissions WHERE UPPER(permission_key) = 'VIEW_SALE_AUDIT'
);

INSERT INTO permissions (permission_key, permission_name)
SELECT 'EXPORT_SALE_AUDIT', 'Export Sale Audit'
WHERE NOT EXISTS (
    SELECT 1 FROM permissions WHERE UPPER(permission_key) = 'EXPORT_SALE_AUDIT'
);

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.role_id, p.permission_id
FROM roles r
JOIN permissions p ON UPPER(p.permission_key) IN ('VIEW_SALE_AUDIT', 'EXPORT_SALE_AUDIT')
WHERE UPPER(r.role_name) IN ('ADMIN', 'MANAGER')
  AND NOT EXISTS (
      SELECT 1
      FROM role_permissions rp
      WHERE rp.role_id = r.role_id
        AND rp.permission_id = p.permission_id
  );

CREATE OR REPLACE FUNCTION record_sale_table_audit()
RETURNS TRIGGER AS $$
DECLARE
    row_sale_id INTEGER;
    row_sale_item_id INTEGER;
    row_return_id BIGINT;
    row_return_item_id BIGINT;
    row_customer_id INTEGER;
    row_product_id INTEGER;
    row_location_id INTEGER;
    action_scope_value TEXT;
    old_value TEXT;
    new_value TEXT;
BEGIN
    IF TG_OP = 'UPDATE' AND NEW IS NOT DISTINCT FROM OLD THEN
        RETURN NEW;
    END IF;

    IF TG_OP = 'UPDATE' AND TG_TABLE_NAME IN ('sales', 'sale_items') THEN
        RETURN NEW;
    END IF;

    row_sale_item_id := NULL;
    row_return_id := NULL;
    row_return_item_id := NULL;
    row_customer_id := NULL;
    row_product_id := NULL;
    row_location_id := NULL;
    action_scope_value := TG_TABLE_NAME;

    IF TG_TABLE_NAME = 'sales' THEN
        row_sale_id := CASE WHEN TG_OP = 'DELETE' THEN OLD.sale_id ELSE NEW.sale_id END;
        row_customer_id := CASE WHEN TG_OP = 'DELETE' THEN OLD.customer_id ELSE NEW.customer_id END;
        row_location_id := CASE WHEN TG_OP = 'DELETE' THEN OLD.location_id ELSE NEW.location_id END;
        old_value := CASE WHEN TG_OP = 'DELETE' THEN ROW_TO_JSON(OLD)::TEXT ELSE NULL END;
        new_value := CASE WHEN TG_OP <> 'DELETE' THEN ROW_TO_JSON(NEW)::TEXT ELSE NULL END;
    ELSIF TG_TABLE_NAME = 'sale_items' THEN
        row_sale_id := CASE WHEN TG_OP = 'DELETE' THEN OLD.sale_id ELSE NEW.sale_id END;
        row_sale_item_id := CASE WHEN TG_OP = 'DELETE' THEN OLD.sale_item_id ELSE NEW.sale_item_id END;
        row_product_id := CASE WHEN TG_OP = 'DELETE' THEN OLD.product_id ELSE NEW.product_id END;
        old_value := CASE WHEN TG_OP = 'DELETE' THEN ROW_TO_JSON(OLD)::TEXT ELSE NULL END;
        new_value := CASE WHEN TG_OP <> 'DELETE' THEN ROW_TO_JSON(NEW)::TEXT ELSE NULL END;
    ELSIF TG_TABLE_NAME = 'sale_returns' THEN
        row_sale_id := CASE WHEN TG_OP = 'DELETE' THEN OLD.sale_id ELSE NEW.sale_id END;
        row_return_id := CASE WHEN TG_OP = 'DELETE' THEN OLD.return_id ELSE NEW.return_id END;
        row_location_id := CASE WHEN TG_OP = 'DELETE' THEN OLD.location_id ELSE NEW.location_id END;
        old_value := CASE WHEN TG_OP = 'DELETE' THEN ROW_TO_JSON(OLD)::TEXT ELSE NULL END;
        new_value := CASE WHEN TG_OP <> 'DELETE' THEN ROW_TO_JSON(NEW)::TEXT ELSE NULL END;
    ELSIF TG_TABLE_NAME = 'sale_return_items' THEN
        row_return_item_id := CASE WHEN TG_OP = 'DELETE' THEN OLD.return_item_id ELSE NEW.return_item_id END;
        row_return_id := CASE WHEN TG_OP = 'DELETE' THEN OLD.return_id ELSE NEW.return_id END;
        row_sale_item_id := CASE WHEN TG_OP = 'DELETE' THEN OLD.sale_item_id ELSE NEW.sale_item_id END;
        row_product_id := CASE WHEN TG_OP = 'DELETE' THEN OLD.product_id ELSE NEW.product_id END;
        SELECT sr.sale_id, sr.location_id
        INTO row_sale_id, row_location_id
        FROM sale_returns sr
        WHERE sr.return_id = row_return_id;
        old_value := CASE WHEN TG_OP = 'DELETE' THEN ROW_TO_JSON(OLD)::TEXT ELSE NULL END;
        new_value := CASE WHEN TG_OP <> 'DELETE' THEN ROW_TO_JSON(NEW)::TEXT ELSE NULL END;
    END IF;

    INSERT INTO sale_audit_log (
        sale_id, sale_item_id, return_id, return_item_id,
        customer_id, product_id, location_id,
        action_type, action_scope, old_value, new_value, note
    )
    VALUES (
        row_sale_id, row_sale_item_id, row_return_id, row_return_item_id,
        row_customer_id, row_product_id, row_location_id,
        'DB_' || TG_OP, action_scope_value, old_value, new_value,
        'Automatic database safety audit for ' || TG_TABLE_NAME
    );

    RETURN COALESCE(NEW, OLD);
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION suppress_duplicate_sale_db_update_audit()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.action_type = 'DB_UPDATE'
       AND NEW.action_scope IN ('sales', 'sale_items')
       AND EXISTS (
           SELECT 1
           FROM sale_audit_log existing
           WHERE existing.action_type = NEW.action_type
             AND existing.action_scope = NEW.action_scope
             AND existing.sale_id IS NOT DISTINCT FROM NEW.sale_id
             AND existing.sale_item_id IS NOT DISTINCT FROM NEW.sale_item_id
           LIMIT 1
       ) THEN
        RETURN NULL;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS suppress_duplicate_sale_db_update_audit ON sale_audit_log;
CREATE TRIGGER suppress_duplicate_sale_db_update_audit
BEFORE INSERT ON sale_audit_log
FOR EACH ROW
EXECUTE FUNCTION suppress_duplicate_sale_db_update_audit();

DROP TRIGGER IF EXISTS sales_update_delete_audit ON sales;
CREATE TRIGGER sales_update_delete_audit
AFTER UPDATE OR DELETE ON sales
FOR EACH ROW
EXECUTE FUNCTION record_sale_table_audit();

DROP TRIGGER IF EXISTS sale_items_update_delete_audit ON sale_items;
CREATE TRIGGER sale_items_update_delete_audit
AFTER UPDATE OR DELETE ON sale_items
FOR EACH ROW
EXECUTE FUNCTION record_sale_table_audit();

DROP TRIGGER IF EXISTS sale_returns_update_delete_audit ON sale_returns;
CREATE TRIGGER sale_returns_update_delete_audit
AFTER UPDATE OR DELETE ON sale_returns
FOR EACH ROW
EXECUTE FUNCTION record_sale_table_audit();

DROP TRIGGER IF EXISTS sale_return_items_update_delete_audit ON sale_return_items;
CREATE TRIGGER sale_return_items_update_delete_audit
AFTER UPDATE OR DELETE ON sale_return_items
FOR EACH ROW
EXECUTE FUNCTION record_sale_table_audit();
