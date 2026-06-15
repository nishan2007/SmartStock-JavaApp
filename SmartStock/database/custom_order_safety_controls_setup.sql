-- Custom order production checklist, refund approval limit, and audit visibility support.
-- Safe to run more than once.

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
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (location_id)
);

ALTER TABLE company_customization
ADD COLUMN IF NOT EXISTS custom_order_refund_approval_limit NUMERIC(12, 2) NOT NULL DEFAULT 0;

ALTER TABLE company_customization
ADD COLUMN IF NOT EXISTS vat_enabled BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE company_customization
ADD COLUMN IF NOT EXISTS vat_use_department_rates BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE company_customization
ADD COLUMN IF NOT EXISTS vat_fixed_rate_percent NUMERIC(6, 2) NOT NULL DEFAULT 0;

ALTER TABLE company_customization
DROP CONSTRAINT IF EXISTS company_customization_refund_approval_limit_chk;

ALTER TABLE company_customization
ADD CONSTRAINT company_customization_refund_approval_limit_chk
CHECK (custom_order_refund_approval_limit >= 0);

ALTER TABLE custom_order_lines
ADD COLUMN IF NOT EXISTS production_status TEXT NOT NULL DEFAULT 'NOT_STARTED';

ALTER TABLE custom_order_lines
ADD COLUMN IF NOT EXISTS production_updated_at TIMESTAMPTZ;

ALTER TABLE custom_order_lines
ADD COLUMN IF NOT EXISTS production_updated_by_user_id INTEGER REFERENCES users(user_id);

ALTER TABLE custom_order_lines
ADD COLUMN IF NOT EXISTS production_updated_by_name TEXT;

ALTER TABLE custom_order_lines
DROP CONSTRAINT IF EXISTS custom_order_lines_production_status_chk;

ALTER TABLE custom_order_lines
ADD CONSTRAINT custom_order_lines_production_status_chk
CHECK (production_status IN ('NOT_STARTED', 'DESIGN_APPROVED', 'PRINTED', 'FINISHED', 'QUALITY_CHECKED', 'READY'));

CREATE TABLE IF NOT EXISTS custom_order_line_production_history (
    custom_order_line_production_history_id BIGSERIAL PRIMARY KEY,
    custom_order_id BIGINT NOT NULL REFERENCES custom_orders(custom_order_id) ON DELETE CASCADE,
    custom_order_line_id BIGINT NOT NULL REFERENCES custom_order_lines(custom_order_line_id) ON DELETE CASCADE,
    custom_item_id BIGINT REFERENCES custom_order_items(custom_item_id),
    custom_variant_id BIGINT REFERENCES custom_order_item_variants(custom_variant_id),
    item_name TEXT NOT NULL,
    variant_name TEXT,
    old_status TEXT,
    new_status TEXT NOT NULL,
    notes TEXT,
    updated_by_user_id INTEGER REFERENCES users(user_id),
    updated_by_name TEXT,
    device_id TEXT,
    device_name TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS custom_order_line_production_history_order_idx
ON custom_order_line_production_history(custom_order_id, created_at DESC);

CREATE INDEX IF NOT EXISTS custom_order_line_production_history_line_idx
ON custom_order_line_production_history(custom_order_line_id, created_at DESC);

INSERT INTO permissions (permission_key, permission_name)
SELECT 'CUSTOM_ORDER_REFUND_APPROVAL', 'Custom Order Refund Approval'
WHERE NOT EXISTS (
    SELECT 1 FROM permissions WHERE UPPER(permission_key) = 'CUSTOM_ORDER_REFUND_APPROVAL'
);

INSERT INTO permissions (permission_key, permission_name)
SELECT 'CUSTOM_ORDER_REFUND_APPROVAL_SETTINGS', 'Custom Order Refund Approval Settings'
WHERE NOT EXISTS (
    SELECT 1 FROM permissions WHERE UPPER(permission_key) = 'CUSTOM_ORDER_REFUND_APPROVAL_SETTINGS'
);

INSERT INTO permissions (permission_key, permission_name)
SELECT 'CUSTOM_ORDER_PRODUCTION_STEPS', 'Custom Order Production Steps'
WHERE NOT EXISTS (
    SELECT 1 FROM permissions WHERE UPPER(permission_key) = 'CUSTOM_ORDER_PRODUCTION_STEPS'
);

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.role_id, p.permission_id
FROM roles r
JOIN permissions p ON UPPER(p.permission_key) IN (
    'CUSTOM_ORDER_REFUND_APPROVAL',
    'CUSTOM_ORDER_REFUND_APPROVAL_SETTINGS',
    'CUSTOM_ORDER_PRODUCTION_STEPS'
)
WHERE UPPER(r.role_name) IN ('ADMIN', 'MANAGER')
  AND NOT EXISTS (
      SELECT 1
      FROM role_permissions rp
      WHERE rp.role_id = r.role_id
        AND rp.permission_id = p.permission_id
  );
