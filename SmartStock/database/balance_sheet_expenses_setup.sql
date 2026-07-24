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
    FOREACH target_table IN ARRAY ARRAY['balance_sheet_submissions', 'balance_sheet_bf_overrides', 'expenses', 'cheque_bank_deposits', 'bank_transactions']
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

INSERT INTO permissions (permission_key, permission_name)
SELECT 'BALANCE_SHEET', 'Balance Sheet'
WHERE NOT EXISTS (
    SELECT 1 FROM permissions WHERE UPPER(permission_key) = 'BALANCE_SHEET'
);

UPDATE permissions
SET description = 'Allows viewing balance sheet totals and logging business expenses.',
    permission_group = 'Operations',
    permission_subgroup = 'Cash Drawer'
WHERE UPPER(permission_key) = 'BALANCE_SHEET';

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
