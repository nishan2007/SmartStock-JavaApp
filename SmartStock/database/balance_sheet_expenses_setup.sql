-- Balance Sheet and expense ledger setup for SmartStock.
-- Run this in Supabase SQL Editor before using the Balance Sheet screen.

CREATE TABLE IF NOT EXISTS expenses (
    expense_id BIGSERIAL PRIMARY KEY,
    location_id INTEGER REFERENCES locations(location_id),
    expense_date DATE NOT NULL DEFAULT CURRENT_DATE,
    category TEXT NOT NULL,
    payee TEXT,
    description TEXT,
    amount NUMERIC(12, 2) NOT NULL,
    payment_method TEXT,
    payment_reference TEXT,
    status TEXT NOT NULL DEFAULT 'PAID',
    source_type TEXT,
    source_id TEXT,
    created_by_user_id INTEGER REFERENCES users(user_id),
    created_by_name TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT expenses_amount_chk CHECK (amount >= 0),
    CONSTRAINT expenses_status_chk CHECK (status IN ('PAID', 'UNPAID'))
);

ALTER TABLE expenses
ADD COLUMN IF NOT EXISTS payment_reference TEXT;

ALTER TABLE expenses
ADD COLUMN IF NOT EXISTS source_type TEXT;

ALTER TABLE expenses
ADD COLUMN IF NOT EXISTS source_id TEXT;

ALTER TABLE expenses
ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP;

CREATE UNIQUE INDEX IF NOT EXISTS expenses_source_unique_idx
ON expenses(source_type, source_id)
WHERE source_type IS NOT NULL AND source_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS expenses_location_date_idx
ON expenses(location_id, expense_date DESC);

CREATE INDEX IF NOT EXISTS expenses_created_by_user_idx
ON expenses(created_by_user_id);

CREATE TABLE IF NOT EXISTS other_income_entries (
    other_income_id BIGSERIAL PRIMARY KEY,
    location_id INTEGER REFERENCES locations(location_id),
    income_date DATE NOT NULL DEFAULT CURRENT_DATE,
    source_name TEXT NOT NULL,
    description TEXT,
    amount NUMERIC(12, 2) NOT NULL,
    payment_method TEXT NOT NULL DEFAULT 'CASH',
    payment_reference TEXT,
    created_by_user_id INTEGER REFERENCES users(user_id),
    created_by_name TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT other_income_amount_chk CHECK (amount > 0),
    CONSTRAINT other_income_whole_gyd_chk CHECK (amount = TRUNC(amount)),
    CONSTRAINT other_income_payment_method_chk CHECK (payment_method = 'CASH')
);

ALTER TABLE other_income_entries
DROP CONSTRAINT IF EXISTS other_income_whole_gyd_chk;

ALTER TABLE other_income_entries
ADD CONSTRAINT other_income_whole_gyd_chk CHECK (amount = TRUNC(amount));

CREATE INDEX IF NOT EXISTS other_income_location_date_idx
ON other_income_entries(location_id, income_date DESC);

CREATE INDEX IF NOT EXISTS other_income_created_by_user_idx
ON other_income_entries(created_by_user_id);

CREATE TABLE IF NOT EXISTS cheque_bank_deposits (
    cheque_bank_deposit_id BIGSERIAL PRIMARY KEY,
    location_id INTEGER REFERENCES locations(location_id),
    source_type TEXT NOT NULL,
    source_id TEXT NOT NULL,
    amount NUMERIC(12, 2) NOT NULL DEFAULT 0,
    payment_reference TEXT,
    deposited_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deposited_by_user_id INTEGER REFERENCES users(user_id),
    deposited_by_name TEXT,
    notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT cheque_bank_deposits_amount_chk CHECK (amount >= 0),
    CONSTRAINT cheque_bank_deposits_source_unique UNIQUE (source_type, source_id)
);

CREATE INDEX IF NOT EXISTS cheque_bank_deposits_location_deposited_idx
ON cheque_bank_deposits(location_id, deposited_at DESC);

CREATE TABLE IF NOT EXISTS bank_transactions (
    bank_transaction_id BIGSERIAL PRIMARY KEY,
    location_id INTEGER REFERENCES locations(location_id),
    transaction_date DATE NOT NULL DEFAULT CURRENT_DATE,
    transaction_name TEXT NOT NULL,
    transaction_direction TEXT NOT NULL,
    amount NUMERIC(12, 2) NOT NULL,
    payment_reference TEXT,
    source_type TEXT,
    source_id TEXT,
    created_by_user_id INTEGER REFERENCES users(user_id),
    created_by_name TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT bank_transactions_amount_chk CHECK (amount >= 0),
    CONSTRAINT bank_transactions_direction_chk CHECK (transaction_direction IN ('PAID', 'RECEIVED'))
);

CREATE UNIQUE INDEX IF NOT EXISTS bank_transactions_source_unique_idx
ON bank_transactions(source_type, source_id)
WHERE source_type IS NOT NULL AND source_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS bank_transactions_location_date_idx
ON bank_transactions(location_id, transaction_date DESC);

CREATE SEQUENCE IF NOT EXISTS bank_transactions_bank_transaction_id_seq;
ALTER SEQUENCE bank_transactions_bank_transaction_id_seq
OWNED BY bank_transactions.bank_transaction_id;
ALTER TABLE bank_transactions
ALTER COLUMN bank_transaction_id SET DEFAULT nextval('bank_transactions_bank_transaction_id_seq');
SELECT setval(
    'bank_transactions_bank_transaction_id_seq',
    GREATEST(COALESCE((SELECT MAX(bank_transaction_id) FROM bank_transactions), 0), 1),
    COALESCE((SELECT MAX(bank_transaction_id) FROM bank_transactions), 0) > 0
);

CREATE TABLE IF NOT EXISTS balance_sheet_submissions (
    balance_sheet_submission_id BIGSERIAL PRIMARY KEY,
    location_id INTEGER REFERENCES locations(location_id),
    location_name TEXT,
    period_start DATE NOT NULL,
    period_end DATE NOT NULL,
    store_timezone TEXT,
    balance_bf NUMERIC(12, 2) NOT NULL DEFAULT 0,
    cash_in_hand NUMERIC(12, 2) NOT NULL DEFAULT 0,
    total_income NUMERIC(12, 2) NOT NULL DEFAULT 0,
    total_receivables NUMERIC(12, 2) NOT NULL DEFAULT 0,
    total_expenses NUMERIC(12, 2) NOT NULL DEFAULT 0,
    total_payables NUMERIC(12, 2) NOT NULL DEFAULT 0,
    balance_cf NUMERIC(12, 2) NOT NULL DEFAULT 0,
    income_lines TEXT,
    receivable_lines TEXT,
    expense_lines TEXT,
    payable_lines TEXT,
    drawer_cash_lines TEXT,
    device_sales_lines TEXT,
    device_order_lines TEXT,
    device_payment_lines TEXT,
    account_payment_lines TEXT,
    bank_transaction_lines TEXT,
    pending_cheque_lines TEXT,
    drawer_check_lines TEXT,
    submitted_by_user_id INTEGER REFERENCES users(user_id),
    submitted_by_name TEXT,
    submitted_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    notes TEXT
);

ALTER TABLE balance_sheet_submissions
ADD COLUMN IF NOT EXISTS drawer_cash_lines TEXT;

ALTER TABLE balance_sheet_submissions
ADD COLUMN IF NOT EXISTS device_sales_lines TEXT;

ALTER TABLE balance_sheet_submissions
ADD COLUMN IF NOT EXISTS device_order_lines TEXT;

ALTER TABLE balance_sheet_submissions
ADD COLUMN IF NOT EXISTS device_payment_lines TEXT;

ALTER TABLE balance_sheet_submissions
ADD COLUMN IF NOT EXISTS account_payment_lines TEXT;

ALTER TABLE balance_sheet_submissions
ADD COLUMN IF NOT EXISTS bank_transaction_lines TEXT;

ALTER TABLE balance_sheet_submissions
ADD COLUMN IF NOT EXISTS pending_cheque_lines TEXT;

ALTER TABLE balance_sheet_submissions
ADD COLUMN IF NOT EXISTS drawer_check_lines TEXT;

ALTER TABLE IF EXISTS customer_account_transactions
ADD COLUMN IF NOT EXISTS device_id TEXT;

ALTER TABLE IF EXISTS customer_account_transactions
ADD COLUMN IF NOT EXISTS device_name TEXT;

CREATE INDEX IF NOT EXISTS balance_sheet_submissions_location_period_idx
ON balance_sheet_submissions(location_id, period_start DESC, period_end DESC);

CREATE INDEX IF NOT EXISTS balance_sheet_submissions_submitted_by_user_idx
ON balance_sheet_submissions(submitted_by_user_id);

ALTER TABLE balance_sheet_submissions
ADD COLUMN IF NOT EXISTS revision_no INTEGER NOT NULL DEFAULT 0;

ALTER TABLE balance_sheet_submissions
ADD COLUMN IF NOT EXISTS last_edited_at TIMESTAMPTZ;

ALTER TABLE balance_sheet_submissions
ADD COLUMN IF NOT EXISTS last_edited_by_user_id INTEGER REFERENCES users(user_id);

ALTER TABLE balance_sheet_submissions
ADD COLUMN IF NOT EXISTS last_edited_by_name TEXT;

CREATE TABLE IF NOT EXISTS balance_sheet_submission_revisions (
    balance_sheet_revision_id BIGSERIAL PRIMARY KEY,
    balance_sheet_submission_id BIGINT NOT NULL REFERENCES balance_sheet_submissions(balance_sheet_submission_id),
    location_id INTEGER NOT NULL REFERENCES locations(location_id),
    revision_no INTEGER NOT NULL,
    action_type TEXT NOT NULL DEFAULT 'EDIT',
    reason TEXT NOT NULL,
    change_summary TEXT NOT NULL,
    before_snapshot JSONB NOT NULL,
    after_snapshot JSONB NOT NULL,
    changed_by_user_id INTEGER REFERENCES users(user_id),
    changed_by_name TEXT,
    device_id TEXT,
    device_name TEXT,
    changed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT balance_sheet_revision_reason_chk CHECK (LENGTH(TRIM(reason)) > 0),
    CONSTRAINT balance_sheet_revision_unique UNIQUE (balance_sheet_submission_id, revision_no)
);

CREATE INDEX IF NOT EXISTS balance_sheet_revision_submission_idx
ON balance_sheet_submission_revisions(balance_sheet_submission_id, revision_no DESC);

CREATE INDEX IF NOT EXISTS balance_sheet_revision_location_idx
ON balance_sheet_submission_revisions(location_id);

CREATE INDEX IF NOT EXISTS balance_sheet_revision_changed_by_idx
ON balance_sheet_submission_revisions(changed_by_user_id);

CREATE INDEX IF NOT EXISTS balance_sheet_submission_last_editor_idx
ON balance_sheet_submissions(last_edited_by_user_id);

ALTER TABLE balance_sheet_submission_revisions
ADD COLUMN IF NOT EXISTS change_summary TEXT NOT NULL DEFAULT 'Balance Sheet revised';

CREATE OR REPLACE FUNCTION prevent_balance_sheet_revision_changes()
RETURNS TRIGGER LANGUAGE plpgsql
SET search_path = pg_catalog, public
AS $$
BEGIN
    RAISE EXCEPTION 'Balance sheet revision history is immutable';
END;
$$;

DROP TRIGGER IF EXISTS balance_sheet_revisions_immutable ON balance_sheet_submission_revisions;
CREATE TRIGGER balance_sheet_revisions_immutable
BEFORE UPDATE OR DELETE ON balance_sheet_submission_revisions
FOR EACH ROW EXECUTE FUNCTION prevent_balance_sheet_revision_changes();

CREATE TABLE IF NOT EXISTS balance_sheet_bf_overrides (
    balance_sheet_bf_override_id BIGSERIAL PRIMARY KEY,
    location_id INTEGER NOT NULL REFERENCES locations(location_id),
    period_start DATE NOT NULL,
    amount NUMERIC(12, 2) NOT NULL,
    updated_by_user_id INTEGER REFERENCES users(user_id),
    updated_by_name TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT balance_sheet_bf_overrides_location_period_unique
        UNIQUE (location_id, period_start)
);

CREATE INDEX IF NOT EXISTS balance_sheet_bf_overrides_location_period_idx
ON balance_sheet_bf_overrides(location_id, period_start DESC);

DO $$
DECLARE
    target_table TEXT;
    target_sequence TEXT;
    has_anon BOOLEAN := EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'anon');
    has_authenticated BOOLEAN := EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'authenticated');
    has_service_role BOOLEAN := EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'service_role');
BEGIN
    FOREACH target_table IN ARRAY ARRAY['balance_sheet_submissions', 'balance_sheet_submission_revisions', 'balance_sheet_bf_overrides', 'expenses', 'other_income_entries', 'cheque_bank_deposits', 'bank_transactions']
    LOOP
        EXECUTE format('ALTER TABLE public.%I ENABLE ROW LEVEL SECURITY', target_table);
        EXECUTE format('REVOKE ALL ON TABLE public.%I FROM PUBLIC', target_table);
        IF has_anon THEN
            EXECUTE format('REVOKE ALL ON TABLE public.%I FROM anon', target_table);
        END IF;
        IF has_authenticated THEN
            EXECUTE format('REVOKE ALL ON TABLE public.%I FROM authenticated', target_table);
            EXECUTE format('DROP POLICY IF EXISTS %I ON public.%I', target_table || '_authenticated_all', target_table);
            EXECUTE format('DROP POLICY IF EXISTS %I ON public.%I', target_table || '_anon_all', target_table);
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
                IF has_anon THEN
                    EXECUTE format('REVOKE ALL ON SEQUENCE %s FROM anon', target_sequence);
                END IF;
                IF has_authenticated THEN
                    EXECUTE format('REVOKE ALL ON SEQUENCE %s FROM authenticated', target_sequence);
                END IF;
                IF has_service_role THEN
                    EXECUTE format('GRANT ALL ON SEQUENCE %s TO service_role', target_sequence);
                END IF;
            END IF;
        END LOOP;
    END LOOP;
END $$;

REVOKE ALL ON FUNCTION prevent_balance_sheet_revision_changes() FROM PUBLIC;

DO $$ BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'anon') THEN
        REVOKE ALL ON FUNCTION prevent_balance_sheet_revision_changes() FROM anon;
    END IF;
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'authenticated') THEN
        REVOKE ALL ON FUNCTION prevent_balance_sheet_revision_changes() FROM authenticated;
    END IF;
END $$;

INSERT INTO permissions (permission_key, permission_name)
SELECT 'BALANCE_SHEET', 'Balance Sheet'
WHERE NOT EXISTS (
    SELECT 1 FROM permissions WHERE UPPER(permission_key) = 'BALANCE_SHEET'
);

UPDATE permissions
SET description = 'Allows viewing balance sheet totals and logging business expenses or Other income.',
    permission_group = 'Operations',
    permission_subgroup = 'Cash Drawer'
WHERE UPPER(permission_key) = 'BALANCE_SHEET';

INSERT INTO permissions (permission_key, permission_name, description, permission_group, permission_subgroup)
VALUES ('EDIT_BALANCE_SHEET', 'Edit Submitted Balance Sheet',
        'Allows revising the latest submitted Balance Sheet during its 48-hour edit window.',
        'Operations', 'Cash Drawer')
ON CONFLICT (permission_key) DO UPDATE SET
    permission_name = EXCLUDED.permission_name,
    description = EXCLUDED.description,
    permission_group = EXCLUDED.permission_group,
    permission_subgroup = EXCLUDED.permission_subgroup;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.role_id, p.permission_id
FROM roles r
JOIN permissions p ON p.permission_key = 'EDIT_BALANCE_SHEET'
WHERE (UPPER(r.role_name) IN ('ADMIN', 'OWNER', 'CEO') OR UPPER(r.role_name) LIKE '%MANAGER%')
  AND NOT EXISTS (
      SELECT 1 FROM role_permissions existing
      JOIN permissions existing_permission ON existing_permission.permission_id=existing.permission_id
      WHERE existing_permission.permission_key='EDIT_BALANCE_SHEET'
  )
ON CONFLICT (role_id, permission_id) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.role_id, p.permission_id
FROM roles r
JOIN permissions p ON UPPER(p.permission_key) = 'BALANCE_SHEET'
WHERE UPPER(r.role_name) IN ('ADMIN', 'MANAGER')
  AND NOT EXISTS (
      SELECT 1
      FROM role_permissions rp
      WHERE rp.role_id = r.role_id
        AND rp.permission_id = p.permission_id
  );
