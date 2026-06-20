CREATE TABLE IF NOT EXISTS company_info (
    company_info_id INTEGER PRIMARY KEY DEFAULT 1,
    company_name TEXT NOT NULL DEFAULT 'SmartStock',
    company_motto_line1 TEXT NOT NULL DEFAULT '',
    company_motto_line2 TEXT NOT NULL DEFAULT '',
    company_logo_url TEXT NOT NULL DEFAULT '',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT company_info_singleton_chk CHECK (company_info_id = 1)
);

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
    next_receipt_counter INTEGER NOT NULL DEFAULT 1,
    change_basket_target_amount NUMERIC(12, 2) NOT NULL DEFAULT 60000,
    account_payment_receipt_title TEXT NOT NULL DEFAULT 'CUSTOMER ACCOUNT PAYMENT',
    account_payment_receipt_show_user BOOLEAN NOT NULL DEFAULT TRUE,
    account_payment_receipt_show_customer BOOLEAN NOT NULL DEFAULT TRUE,
    account_payment_receipt_show_account_number BOOLEAN NOT NULL DEFAULT TRUE,
    account_payment_receipt_show_method BOOLEAN NOT NULL DEFAULT TRUE,
    account_payment_receipt_show_reference BOOLEAN NOT NULL DEFAULT TRUE,
    account_payment_receipt_show_device BOOLEAN NOT NULL DEFAULT TRUE,
    account_payment_receipt_show_drawer BOOLEAN NOT NULL DEFAULT TRUE,
    account_payment_receipt_show_allocations BOOLEAN NOT NULL DEFAULT TRUE,
    account_payment_receipt_show_balance BOOLEAN NOT NULL DEFAULT TRUE,
    account_payment_receipt_show_barcode BOOLEAN NOT NULL DEFAULT TRUE,
    custom_order_minimum_deposit_percent NUMERIC(7, 4) NOT NULL DEFAULT 0,
    custom_order_refund_approval_limit NUMERIC(12, 2) NOT NULL DEFAULT 0,
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
    badge_template_company_name TEXT NOT NULL DEFAULT 'SmartStock',
    badge_template_logo_url TEXT NOT NULL DEFAULT '',
    badge_template_quote TEXT NOT NULL DEFAULT '"Sales goes up and down, Service is Forever"',
    badge_template_signatory_name TEXT NOT NULL DEFAULT 'Authorized Signature',
    badge_template_signatory_title TEXT NOT NULL DEFAULT 'Management',
    badge_template_back_instructions TEXT NOT NULL DEFAULT 'Scan or swipe this badge for SmartStock access.',
    badge_template_show_quote BOOLEAN NOT NULL DEFAULT TRUE,
    badge_template_show_employee_id BOOLEAN NOT NULL DEFAULT TRUE,
    badge_template_show_issue_date BOOLEAN NOT NULL DEFAULT TRUE,
    badge_template_show_barcode BOOLEAN NOT NULL DEFAULT TRUE,
    badge_template_show_badge_text BOOLEAN NOT NULL DEFAULT FALSE,
    badge_template_magstripe_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    badge_template_magstripe_track1 TEXT NOT NULL DEFAULT '{badge_id}',
    badge_template_magstripe_track2 TEXT NOT NULL DEFAULT '{badge_id}',
    badge_template_magstripe_track3 TEXT NOT NULL DEFAULT '',
    badge_template_magstripe_command TEXT NOT NULL DEFAULT '',
    badge_template_layout_data TEXT NOT NULL DEFAULT '',
    price_tag_show_company BOOLEAN NOT NULL DEFAULT TRUE,
    price_tag_show_sku BOOLEAN NOT NULL DEFAULT TRUE,
    price_tag_show_barcode BOOLEAN NOT NULL DEFAULT TRUE,
    price_tag_width_inches NUMERIC(5, 2) NOT NULL DEFAULT 2.25,
    price_tag_height_inches NUMERIC(5, 2) NOT NULL DEFAULT 1.25,
    price_tag_templates TEXT NOT NULL DEFAULT '',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (location_id)
);

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'company_customization'
          AND column_name = 'company_name'
    ) THEN
        EXECUTE $sql$
            INSERT INTO company_info (
                company_info_id,
                company_name,
                company_motto_line1,
                company_motto_line2,
                company_logo_url,
                updated_at
            )
            SELECT 1,
                   COALESCE(NULLIF(company_name, ''), 'SmartStock'),
                   COALESCE(company_motto_line1, ''),
                   COALESCE(company_motto_line2, ''),
                   COALESCE(receipt_logo_url, ''),
                   NOW()
            FROM company_customization
            ORDER BY location_id
            LIMIT 1
            ON CONFLICT (company_info_id) DO UPDATE SET
                company_name = COALESCE(NULLIF(company_info.company_name, ''), EXCLUDED.company_name),
                company_motto_line1 = COALESCE(NULLIF(company_info.company_motto_line1, ''), EXCLUDED.company_motto_line1),
                company_motto_line2 = COALESCE(NULLIF(company_info.company_motto_line2, ''), EXCLUDED.company_motto_line2),
                company_logo_url = COALESCE(NULLIF(company_info.company_logo_url, ''), EXCLUDED.company_logo_url),
                updated_at = NOW()
        $sql$;
    END IF;
END $$;

INSERT INTO company_info (company_info_id, company_name)
VALUES (1, 'SmartStock')
ON CONFLICT (company_info_id) DO NOTHING;

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

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'company_customization'
          AND column_name = 'company_address_line1'
    ) THEN
        EXECUTE $sql$
            UPDATE locations l
            SET company_address_line1 = COALESCE(NULLIF(l.company_address_line1, ''), cc.company_address_line1, ''),
                company_address_line2 = COALESCE(NULLIF(l.company_address_line2, ''), cc.company_address_line2, ''),
                company_address_line3 = COALESCE(NULLIF(l.company_address_line3, ''), cc.company_address_line3, ''),
                company_phone_line1 = COALESCE(NULLIF(l.company_phone_line1, ''), cc.company_phone_line1, ''),
                company_phone_line2 = COALESCE(NULLIF(l.company_phone_line2, ''), cc.company_phone_line2, ''),
                company_email_line1 = COALESCE(NULLIF(l.company_email_line1, ''), cc.company_email_line1, ''),
                company_email_line2 = COALESCE(NULLIF(l.company_email_line2, ''), cc.company_email_line2, '')
            FROM company_customization cc
            WHERE cc.location_id = l.location_id
              AND (l.company_address_line1 = '' OR l.company_address_line2 = '' OR l.company_address_line3 = ''
                   OR l.company_phone_line1 = '' OR l.company_phone_line2 = ''
                   OR l.company_email_line1 = '' OR l.company_email_line2 = '')
        $sql$;
    END IF;
END $$;

ALTER TABLE company_customization
DROP COLUMN IF EXISTS company_address_line1,
DROP COLUMN IF EXISTS company_address_line2,
DROP COLUMN IF EXISTS company_address_line3,
DROP COLUMN IF EXISTS company_phone_line1,
DROP COLUMN IF EXISTS company_phone_line2,
DROP COLUMN IF EXISTS company_email_line1,
DROP COLUMN IF EXISTS company_email_line2,
DROP COLUMN IF EXISTS company_name,
DROP COLUMN IF EXISTS company_motto_line1,
DROP COLUMN IF EXISTS company_motto_line2,
DROP COLUMN IF EXISTS receipt_logo_url;

ALTER TABLE company_customization
ADD COLUMN IF NOT EXISTS custom_order_minimum_deposit_percent NUMERIC(7, 4) NOT NULL DEFAULT 0;
ALTER TABLE company_customization
ADD COLUMN IF NOT EXISTS next_receipt_counter INTEGER NOT NULL DEFAULT 1;

ALTER TABLE company_customization
ADD COLUMN IF NOT EXISTS change_basket_target_amount NUMERIC(12, 2) NOT NULL DEFAULT 60000;

ALTER TABLE company_customization
ADD COLUMN IF NOT EXISTS account_payment_receipt_title TEXT NOT NULL DEFAULT 'CUSTOMER ACCOUNT PAYMENT';
ALTER TABLE company_customization
ADD COLUMN IF NOT EXISTS account_payment_receipt_show_user BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE company_customization
ADD COLUMN IF NOT EXISTS account_payment_receipt_show_customer BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE company_customization
ADD COLUMN IF NOT EXISTS account_payment_receipt_show_account_number BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE company_customization
ADD COLUMN IF NOT EXISTS account_payment_receipt_show_method BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE company_customization
ADD COLUMN IF NOT EXISTS account_payment_receipt_show_reference BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE company_customization
ADD COLUMN IF NOT EXISTS account_payment_receipt_show_device BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE company_customization
ADD COLUMN IF NOT EXISTS account_payment_receipt_show_drawer BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE company_customization
ADD COLUMN IF NOT EXISTS account_payment_receipt_show_allocations BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE company_customization
ADD COLUMN IF NOT EXISTS account_payment_receipt_show_balance BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE company_customization
ADD COLUMN IF NOT EXISTS account_payment_receipt_show_barcode BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE company_customization
ADD COLUMN IF NOT EXISTS vat_enabled BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE company_customization
ADD COLUMN IF NOT EXISTS vat_use_department_rates BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE company_customization
ADD COLUMN IF NOT EXISTS vat_fixed_rate_percent NUMERIC(6, 2) NOT NULL DEFAULT 0;

ALTER TABLE company_customization
ADD COLUMN IF NOT EXISTS custom_order_refund_approval_limit NUMERIC(12, 2) NOT NULL DEFAULT 0;

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
ADD COLUMN IF NOT EXISTS badge_template_company_name TEXT NOT NULL DEFAULT 'SmartStock';

ALTER TABLE company_customization
ADD COLUMN IF NOT EXISTS badge_template_logo_url TEXT NOT NULL DEFAULT '';

ALTER TABLE company_customization
ADD COLUMN IF NOT EXISTS badge_template_quote TEXT NOT NULL DEFAULT '"Sales goes up and down, Service is Forever"';

ALTER TABLE company_customization
ADD COLUMN IF NOT EXISTS badge_template_signatory_name TEXT NOT NULL DEFAULT 'Authorized Signature';

ALTER TABLE company_customization
ADD COLUMN IF NOT EXISTS badge_template_signatory_title TEXT NOT NULL DEFAULT 'Management';

ALTER TABLE company_customization
ADD COLUMN IF NOT EXISTS badge_template_back_instructions TEXT NOT NULL DEFAULT 'Scan or swipe this badge for SmartStock access.';

ALTER TABLE company_customization
ADD COLUMN IF NOT EXISTS badge_template_show_quote BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE company_customization
ADD COLUMN IF NOT EXISTS badge_template_show_employee_id BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE company_customization
ADD COLUMN IF NOT EXISTS badge_template_show_issue_date BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE company_customization
ADD COLUMN IF NOT EXISTS badge_template_show_barcode BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE company_customization
ADD COLUMN IF NOT EXISTS badge_template_show_badge_text BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE company_customization
ADD COLUMN IF NOT EXISTS badge_template_magstripe_enabled BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE company_customization
ADD COLUMN IF NOT EXISTS badge_template_magstripe_track1 TEXT NOT NULL DEFAULT '{badge_id}';

ALTER TABLE company_customization
ADD COLUMN IF NOT EXISTS badge_template_magstripe_track2 TEXT NOT NULL DEFAULT '{badge_id}';

ALTER TABLE company_customization
ADD COLUMN IF NOT EXISTS badge_template_magstripe_track3 TEXT NOT NULL DEFAULT '';

ALTER TABLE company_customization
ADD COLUMN IF NOT EXISTS badge_template_magstripe_command TEXT NOT NULL DEFAULT '';

ALTER TABLE company_customization
ADD COLUMN IF NOT EXISTS badge_template_layout_data TEXT NOT NULL DEFAULT '';

ALTER TABLE company_customization
ADD COLUMN IF NOT EXISTS price_tag_show_company BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE company_customization
ADD COLUMN IF NOT EXISTS price_tag_show_sku BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE company_customization
ADD COLUMN IF NOT EXISTS price_tag_show_barcode BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE company_customization
ADD COLUMN IF NOT EXISTS price_tag_width_inches NUMERIC(5, 2) NOT NULL DEFAULT 2.25;
ALTER TABLE company_customization
ADD COLUMN IF NOT EXISTS price_tag_height_inches NUMERIC(5, 2) NOT NULL DEFAULT 1.25;
ALTER TABLE company_customization
ADD COLUMN IF NOT EXISTS price_tag_templates TEXT NOT NULL DEFAULT '';

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
DROP CONSTRAINT IF EXISTS company_customization_vat_fixed_rate_percent_chk;

ALTER TABLE company_customization
ADD CONSTRAINT company_customization_vat_fixed_rate_percent_chk
CHECK (vat_fixed_rate_percent >= 0 AND vat_fixed_rate_percent <= 100);

ALTER TABLE company_customization
DROP CONSTRAINT IF EXISTS company_customization_slip_blank_detail_lines_chk;

ALTER TABLE company_customization
ADD CONSTRAINT company_customization_slip_blank_detail_lines_chk
CHECK (custom_order_slip_blank_detail_lines >= 0 AND custom_order_slip_blank_detail_lines <= 20);

UPDATE permissions
SET permission_key = 'COMPANY_PREFERENCES',
    permission_name = 'Company Preferences'
WHERE UPPER(permission_key) = 'COMPANY_CUSTOMIZATION'
  AND NOT EXISTS (
      SELECT 1 FROM permissions WHERE UPPER(permission_key) = 'COMPANY_PREFERENCES'
  );

UPDATE permissions
SET permission_name = 'Company Preferences'
WHERE UPPER(permission_key) = 'COMPANY_PREFERENCES';

INSERT INTO permissions (permission_key, permission_name)
VALUES ('COMPANY_PREFERENCES', 'Company Preferences')
ON CONFLICT (permission_key) DO NOTHING;

INSERT INTO permissions (permission_key, permission_name)
VALUES ('CUSTOM_ORDER_DEPOSIT_SETTINGS', 'Custom Order Deposit Settings')
ON CONFLICT (permission_key) DO NOTHING;

INSERT INTO permissions (permission_key, permission_name)
VALUES ('CUSTOM_ORDER_REFUND_APPROVAL_SETTINGS', 'Custom Order Refund Approval Settings')
ON CONFLICT (permission_key) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.role_id, p.permission_id
FROM roles r
JOIN permissions p ON p.permission_key IN ('COMPANY_PREFERENCES', 'CUSTOM_ORDER_DEPOSIT_SETTINGS', 'CUSTOM_ORDER_REFUND_APPROVAL_SETTINGS')
WHERE UPPER(r.role_name) = 'ADMIN'
ON CONFLICT DO NOTHING;
