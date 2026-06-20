-- Cash drawer management setup for SmartStock.
-- Drawers are store-scoped. Multiple devices can share one drawer, but a device
-- can have only one active drawer assignment per store.

CREATE TABLE IF NOT EXISTS cash_drawers (
    cash_drawer_id BIGSERIAL PRIMARY KEY,
    location_id INTEGER NOT NULL REFERENCES locations(location_id),
    drawer_name TEXT NOT NULL,
    description TEXT,
    starting_cash_amount NUMERIC(12,2) NOT NULL DEFAULT 20000.00,
    float_mix JSONB NOT NULL DEFAULT '{"1000":8,"500":10,"100":50,"20":100}'::jsonb,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by_user_id INTEGER REFERENCES users(user_id),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by_user_id INTEGER REFERENCES users(user_id),
    UNIQUE (location_id, drawer_name)
);

CREATE TABLE IF NOT EXISTS cash_drawer_device_assignments (
    assignment_id BIGSERIAL PRIMARY KEY,
    cash_drawer_id BIGINT NOT NULL REFERENCES cash_drawers(cash_drawer_id) ON DELETE CASCADE,
    location_id INTEGER NOT NULL REFERENCES locations(location_id),
    device_id UUID NOT NULL REFERENCES devices(device_id) ON DELETE CASCADE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    assigned_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    assigned_by_user_id INTEGER REFERENCES users(user_id),
    unassigned_at TIMESTAMPTZ,
    unassigned_by_user_id INTEGER REFERENCES users(user_id),
    notes TEXT,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE cash_drawer_device_assignments
ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP;

CREATE OR REPLACE FUNCTION set_cash_drawers_updated_at()
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

DROP TRIGGER IF EXISTS cash_drawers_set_updated_at ON cash_drawers;
CREATE TRIGGER cash_drawers_set_updated_at
BEFORE INSERT OR UPDATE ON cash_drawers
FOR EACH ROW
EXECUTE FUNCTION set_cash_drawers_updated_at();

CREATE OR REPLACE FUNCTION set_cash_drawer_device_assignments_updated_at()
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

DROP TRIGGER IF EXISTS cash_drawer_device_assignments_set_updated_at ON cash_drawer_device_assignments;
CREATE TRIGGER cash_drawer_device_assignments_set_updated_at
BEFORE INSERT OR UPDATE ON cash_drawer_device_assignments
FOR EACH ROW
EXECUTE FUNCTION set_cash_drawer_device_assignments_updated_at();

CREATE INDEX IF NOT EXISTS cash_drawers_updated_at_idx
ON cash_drawers(updated_at DESC);

CREATE INDEX IF NOT EXISTS cash_drawer_device_assignments_updated_at_idx
ON cash_drawer_device_assignments(updated_at DESC);

CREATE UNIQUE INDEX IF NOT EXISTS cash_drawer_one_active_device_assignment_idx
ON cash_drawer_device_assignments(location_id, device_id)
WHERE is_active = TRUE;

CREATE INDEX IF NOT EXISTS cash_drawers_location_idx
ON cash_drawers(location_id, is_active, drawer_name);

CREATE INDEX IF NOT EXISTS cash_drawer_assignments_drawer_idx
ON cash_drawer_device_assignments(cash_drawer_id, is_active);

CREATE INDEX IF NOT EXISTS cash_drawer_assignments_device_idx
ON cash_drawer_device_assignments(device_id, location_id, is_active);

CREATE INDEX IF NOT EXISTS idx_cash_drawers_created_by_user
ON cash_drawers(created_by_user_id)
WHERE created_by_user_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_cash_drawers_updated_by_user
ON cash_drawers(updated_by_user_id)
WHERE updated_by_user_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_cash_drawer_assignments_location_drawer
ON cash_drawer_device_assignments(location_id, cash_drawer_id);

CREATE INDEX IF NOT EXISTS idx_cash_drawer_assignments_assigned_by_user
ON cash_drawer_device_assignments(assigned_by_user_id)
WHERE assigned_by_user_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_cash_drawer_assignments_unassigned_by_user
ON cash_drawer_device_assignments(unassigned_by_user_id)
WHERE unassigned_by_user_id IS NOT NULL;

ALTER TABLE cash_drawers
ADD COLUMN IF NOT EXISTS starting_cash_amount NUMERIC(12,2) NOT NULL DEFAULT 20000.00;

ALTER TABLE cash_drawers
ADD COLUMN IF NOT EXISTS float_mix JSONB NOT NULL DEFAULT '{"1000":8,"500":10,"100":50,"20":100}'::jsonb;

CREATE TABLE IF NOT EXISTS cash_drawer_sessions (
    cash_drawer_session_id BIGSERIAL PRIMARY KEY,
    cash_drawer_id BIGINT NOT NULL REFERENCES cash_drawers(cash_drawer_id),
    location_id INTEGER NOT NULL REFERENCES locations(location_id),
    device_id UUID NOT NULL REFERENCES devices(device_id),
    drawer_name TEXT NOT NULL,
    device_name TEXT,
    opening_cash NUMERIC(12,2) NOT NULL DEFAULT 0,
    expected_cash NUMERIC(12,2),
    counted_cash NUMERIC(12,2),
    cash_to_remove NUMERIC(12,2),
    variance NUMERIC(12,2),
    status TEXT NOT NULL DEFAULT 'OPEN',
    opened_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    opened_by_user_id INTEGER REFERENCES users(user_id),
    opened_by_name TEXT,
    main_cashier_user_id INTEGER REFERENCES users(user_id),
    main_cashier_name TEXT,
    current_cashier_user_id INTEGER REFERENCES users(user_id),
    current_cashier_name TEXT,
    closed_at TIMESTAMPTZ,
    closed_by_user_id INTEGER REFERENCES users(user_id),
    closed_by_name TEXT,
    balanced_by_user_id INTEGER REFERENCES users(user_id),
    balanced_by_name TEXT,
    opening_notes TEXT,
    closing_notes TEXT,
    closing_report TEXT,
    CONSTRAINT cash_drawer_sessions_status_chk CHECK (status IN ('OPEN', 'CLOSED'))
);

ALTER TABLE cash_drawer_sessions ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS cash_drawer_sessions_service_role_all ON cash_drawer_sessions;
CREATE POLICY cash_drawer_sessions_service_role_all
ON cash_drawer_sessions
FOR ALL
TO service_role
USING (TRUE)
WITH CHECK (TRUE);

DROP POLICY IF EXISTS cash_drawer_sessions_authenticated_all ON cash_drawer_sessions;
CREATE POLICY cash_drawer_sessions_authenticated_all
ON cash_drawer_sessions
FOR ALL
TO authenticated
USING (TRUE)
WITH CHECK (TRUE);

ALTER TABLE cash_drawer_sessions
ADD COLUMN IF NOT EXISTS main_cashier_user_id INTEGER REFERENCES users(user_id);

ALTER TABLE cash_drawer_sessions
ADD COLUMN IF NOT EXISTS main_cashier_name TEXT;

ALTER TABLE cash_drawer_sessions
ADD COLUMN IF NOT EXISTS current_cashier_user_id INTEGER REFERENCES users(user_id);

ALTER TABLE cash_drawer_sessions
ADD COLUMN IF NOT EXISTS current_cashier_name TEXT;

ALTER TABLE cash_drawer_sessions
ADD COLUMN IF NOT EXISTS balanced_by_user_id INTEGER REFERENCES users(user_id);

ALTER TABLE cash_drawer_sessions
ADD COLUMN IF NOT EXISTS balanced_by_name TEXT;

ALTER TABLE cash_drawer_sessions
ADD COLUMN IF NOT EXISTS closing_report TEXT;

UPDATE cash_drawer_sessions
SET main_cashier_user_id = opened_by_user_id,
    main_cashier_name = opened_by_name
WHERE main_cashier_user_id IS NULL
  AND opened_by_user_id IS NOT NULL;

UPDATE cash_drawer_sessions
SET current_cashier_user_id = opened_by_user_id,
    current_cashier_name = opened_by_name
WHERE current_cashier_user_id IS NULL
  AND opened_by_user_id IS NOT NULL
  AND status = 'OPEN';

CREATE TABLE IF NOT EXISTS cash_drawer_handovers (
    cash_drawer_handover_id BIGSERIAL PRIMARY KEY,
    cash_drawer_session_id BIGINT NOT NULL REFERENCES cash_drawer_sessions(cash_drawer_session_id) ON DELETE CASCADE,
    cash_drawer_id BIGINT NOT NULL REFERENCES cash_drawers(cash_drawer_id),
    location_id INTEGER NOT NULL REFERENCES locations(location_id),
    device_id UUID NOT NULL REFERENCES devices(device_id),
    from_user_id INTEGER REFERENCES users(user_id),
    from_user_name TEXT,
    to_user_id INTEGER REFERENCES users(user_id),
    to_user_name TEXT,
    expected_cash NUMERIC(12,2) NOT NULL DEFAULT 0,
    counted_cash NUMERIC(12,2) NOT NULL DEFAULT 0,
    variance NUMERIC(12,2) NOT NULL DEFAULT 0,
    handed_over_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    notes TEXT
);

ALTER TABLE cash_drawer_handovers ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS cash_drawer_handovers_service_role_all ON cash_drawer_handovers;
CREATE POLICY cash_drawer_handovers_service_role_all
ON cash_drawer_handovers
FOR ALL
TO service_role
USING (TRUE)
WITH CHECK (TRUE);

DROP POLICY IF EXISTS cash_drawer_handovers_authenticated_all ON cash_drawer_handovers;
CREATE POLICY cash_drawer_handovers_authenticated_all
ON cash_drawer_handovers
FOR ALL
TO authenticated
USING (TRUE)
WITH CHECK (TRUE);

CREATE INDEX IF NOT EXISTS cash_drawer_handovers_session_idx
ON cash_drawer_handovers(cash_drawer_session_id, handed_over_at DESC);

CREATE UNIQUE INDEX IF NOT EXISTS cash_drawer_one_open_session_idx
ON cash_drawer_sessions(cash_drawer_id, location_id, device_id)
WHERE status = 'OPEN';

CREATE INDEX IF NOT EXISTS cash_drawer_sessions_drawer_idx
ON cash_drawer_sessions(cash_drawer_id, opened_at DESC);

CREATE INDEX IF NOT EXISTS cash_drawer_sessions_location_idx
ON cash_drawer_sessions(location_id, status, opened_at DESC);

CREATE INDEX IF NOT EXISTS cash_drawer_sessions_device_fk_idx
ON cash_drawer_sessions(device_id);

CREATE INDEX IF NOT EXISTS cash_drawer_sessions_opened_by_user_fk_idx
ON cash_drawer_sessions(opened_by_user_id);

CREATE INDEX IF NOT EXISTS cash_drawer_sessions_main_cashier_user_fk_idx
ON cash_drawer_sessions(main_cashier_user_id);

CREATE INDEX IF NOT EXISTS cash_drawer_sessions_current_cashier_user_fk_idx
ON cash_drawer_sessions(current_cashier_user_id);

CREATE INDEX IF NOT EXISTS cash_drawer_sessions_closed_by_user_fk_idx
ON cash_drawer_sessions(closed_by_user_id);

CREATE INDEX IF NOT EXISTS cash_drawer_sessions_balanced_by_user_fk_idx
ON cash_drawer_sessions(balanced_by_user_id);

CREATE INDEX IF NOT EXISTS cash_drawer_handovers_drawer_fk_idx
ON cash_drawer_handovers(cash_drawer_id);

CREATE INDEX IF NOT EXISTS cash_drawer_handovers_location_fk_idx
ON cash_drawer_handovers(location_id);

CREATE INDEX IF NOT EXISTS cash_drawer_handovers_device_fk_idx
ON cash_drawer_handovers(device_id);

CREATE INDEX IF NOT EXISTS cash_drawer_handovers_from_user_fk_idx
ON cash_drawer_handovers(from_user_id);

CREATE INDEX IF NOT EXISTS cash_drawer_handovers_to_user_fk_idx
ON cash_drawer_handovers(to_user_id);

CREATE TABLE IF NOT EXISTS change_basket_updates (
    change_basket_update_id BIGSERIAL PRIMARY KEY,
    location_id INTEGER NOT NULL REFERENCES locations(location_id),
    store_name TEXT,
    target_amount NUMERIC(12,2) NOT NULL DEFAULT 0,
    counted_amount NUMERIC(12,2) NOT NULL DEFAULT 0,
    variance NUMERIC(12,2) NOT NULL DEFAULT 0,
    denomination_counts JSONB NOT NULL DEFAULT '{}'::jsonb,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by_user_id INTEGER REFERENCES users(user_id),
    updated_by_name TEXT,
    device_id UUID REFERENCES devices(device_id),
    device_name TEXT,
    notes TEXT
);

ALTER TABLE change_basket_updates ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS change_basket_updates_service_role_all ON change_basket_updates;
CREATE POLICY change_basket_updates_service_role_all
ON change_basket_updates
FOR ALL
TO service_role
USING (TRUE)
WITH CHECK (TRUE);

DROP POLICY IF EXISTS change_basket_updates_authenticated_all ON change_basket_updates;
CREATE POLICY change_basket_updates_authenticated_all
ON change_basket_updates
FOR ALL
TO authenticated
USING (TRUE)
WITH CHECK (TRUE);

CREATE INDEX IF NOT EXISTS change_basket_updates_location_updated_idx
ON change_basket_updates(location_id, updated_at DESC);

CREATE INDEX IF NOT EXISTS change_basket_updates_updated_by_user_idx
ON change_basket_updates(updated_by_user_id)
WHERE updated_by_user_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS change_basket_updates_device_idx
ON change_basket_updates(device_id)
WHERE device_id IS NOT NULL;

ALTER TABLE sales
ADD COLUMN IF NOT EXISTS cash_drawer_id BIGINT REFERENCES cash_drawers(cash_drawer_id);

ALTER TABLE sales
ADD COLUMN IF NOT EXISTS cash_drawer_name TEXT;

ALTER TABLE sales
ADD COLUMN IF NOT EXISTS cash_drawer_session_id BIGINT REFERENCES cash_drawer_sessions(cash_drawer_session_id);

ALTER TABLE sale_returns
ADD COLUMN IF NOT EXISTS cash_drawer_id BIGINT REFERENCES cash_drawers(cash_drawer_id);

ALTER TABLE sale_returns
ADD COLUMN IF NOT EXISTS cash_drawer_name TEXT;

ALTER TABLE sale_returns
ADD COLUMN IF NOT EXISTS cash_drawer_session_id BIGINT REFERENCES cash_drawer_sessions(cash_drawer_session_id);

ALTER TABLE custom_orders
ADD COLUMN IF NOT EXISTS cash_drawer_id BIGINT REFERENCES cash_drawers(cash_drawer_id);

ALTER TABLE custom_orders
ADD COLUMN IF NOT EXISTS cash_drawer_name TEXT;

ALTER TABLE custom_orders
ADD COLUMN IF NOT EXISTS cash_drawer_session_id BIGINT REFERENCES cash_drawer_sessions(cash_drawer_session_id);

ALTER TABLE custom_order_payments
ADD COLUMN IF NOT EXISTS cash_drawer_id BIGINT REFERENCES cash_drawers(cash_drawer_id);

ALTER TABLE custom_order_payments
ADD COLUMN IF NOT EXISTS cash_drawer_name TEXT;

ALTER TABLE custom_order_payments
ADD COLUMN IF NOT EXISTS cash_drawer_session_id BIGINT REFERENCES cash_drawer_sessions(cash_drawer_session_id);

ALTER TABLE customer_account_transactions
ADD COLUMN IF NOT EXISTS payment_method TEXT;

ALTER TABLE customer_account_transactions
ADD COLUMN IF NOT EXISTS payment_reference TEXT;

ALTER TABLE customer_account_transactions
ADD COLUMN IF NOT EXISTS cash_drawer_id BIGINT REFERENCES cash_drawers(cash_drawer_id);

ALTER TABLE customer_account_transactions
ADD COLUMN IF NOT EXISTS cash_drawer_name TEXT;

ALTER TABLE customer_account_transactions
ADD COLUMN IF NOT EXISTS cash_drawer_session_id BIGINT REFERENCES cash_drawer_sessions(cash_drawer_session_id);

CREATE INDEX IF NOT EXISTS idx_sales_cash_drawer_session_created
ON sales(cash_drawer_session_id, created_at DESC)
WHERE cash_drawer_session_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_sale_returns_cash_drawer_created
ON sale_returns(cash_drawer_id, created_at DESC)
WHERE cash_drawer_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_sale_returns_cash_drawer_session_created
ON sale_returns(cash_drawer_session_id, created_at DESC)
WHERE cash_drawer_session_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_custom_orders_cash_drawer_created
ON custom_orders(cash_drawer_id, created_at DESC)
WHERE cash_drawer_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_custom_orders_cash_drawer_session_created
ON custom_orders(cash_drawer_session_id, created_at DESC)
WHERE cash_drawer_session_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_custom_order_payments_cash_drawer_session_created
ON custom_order_payments(cash_drawer_session_id, created_at DESC)
WHERE cash_drawer_session_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_customer_transactions_cash_drawer_session_created
ON customer_account_transactions(cash_drawer_session_id, created_at DESC)
WHERE cash_drawer_session_id IS NOT NULL;

INSERT INTO permissions (permission_key, permission_name)
SELECT 'CASH_DRAWER_MANAGEMENT', 'Cash Drawer Management'
WHERE NOT EXISTS (
    SELECT 1 FROM permissions WHERE UPPER(permission_key) = 'CASH_DRAWER_MANAGEMENT'
);

INSERT INTO permissions (permission_key, permission_name)
SELECT 'BALANCE_DRAWER', 'Balance Draw'
WHERE NOT EXISTS (
    SELECT 1 FROM permissions WHERE UPPER(permission_key) = 'BALANCE_DRAWER'
);

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.role_id, p.permission_id
FROM roles r
JOIN permissions p ON UPPER(p.permission_key) = 'CASH_DRAWER_MANAGEMENT'
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
JOIN permissions p ON UPPER(p.permission_key) = 'BALANCE_DRAWER'
WHERE UPPER(r.role_name) IN ('ADMIN', 'MANAGER', 'CASHIER', 'USER')
  AND NOT EXISTS (
      SELECT 1
      FROM role_permissions rp
      WHERE rp.role_id = r.role_id
        AND rp.permission_id = p.permission_id
  );
