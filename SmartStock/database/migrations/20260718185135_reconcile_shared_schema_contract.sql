-- Reconcile the shared SmartStock business schema across the LAN server database
-- and hosted Supabase. LAN-only credential hashes/audit tables and hosted-only
-- RLS helper functions intentionally remain environment-specific.

ALTER TABLE public.company_customization ADD COLUMN IF NOT EXISTS account_payment_receipt_title TEXT NOT NULL DEFAULT 'CUSTOMER ACCOUNT PAYMENT';
ALTER TABLE public.company_customization ADD COLUMN IF NOT EXISTS account_payment_receipt_show_user BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE public.company_customization ADD COLUMN IF NOT EXISTS account_payment_receipt_show_customer BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE public.company_customization ADD COLUMN IF NOT EXISTS account_payment_receipt_show_account_number BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE public.company_customization ADD COLUMN IF NOT EXISTS account_payment_receipt_show_method BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE public.company_customization ADD COLUMN IF NOT EXISTS account_payment_receipt_show_reference BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE public.company_customization ADD COLUMN IF NOT EXISTS account_payment_receipt_show_device BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE public.company_customization ADD COLUMN IF NOT EXISTS account_payment_receipt_show_drawer BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE public.company_customization ADD COLUMN IF NOT EXISTS account_payment_receipt_show_allocations BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE public.company_customization ADD COLUMN IF NOT EXISTS account_payment_receipt_show_balance BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE public.company_customization ADD COLUMN IF NOT EXISTS account_payment_receipt_show_barcode BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE public.company_customization ADD COLUMN IF NOT EXISTS custom_order_minimum_deposit_percent NUMERIC(7,4) NOT NULL DEFAULT 0;

ALTER TABLE public.custom_order_items ADD COLUMN IF NOT EXISTS minimum_deposit_percent NUMERIC(7,4) NOT NULL DEFAULT 0;
ALTER TABLE public.custom_order_lines ADD COLUMN IF NOT EXISTS width_inches NUMERIC(12,2);
ALTER TABLE public.custom_order_lines ADD COLUMN IF NOT EXISTS length_inches NUMERIC(12,2);
ALTER TABLE public.custom_order_lines ADD COLUMN IF NOT EXISTS square_feet NUMERIC(12,4);

ALTER TABLE public.customer_account_transactions ADD COLUMN IF NOT EXISTS sales_order_id BIGINT;
ALTER TABLE public.customer_account_payment_allocations ADD COLUMN IF NOT EXISTS sales_order_id BIGINT;
CREATE INDEX IF NOT EXISTS customer_account_transactions_sales_order_idx ON public.customer_account_transactions(sales_order_id);
CREATE INDEX IF NOT EXISTS customer_account_payment_allocations_sales_order_idx ON public.customer_account_payment_allocations(sales_order_id);

ALTER TABLE public.employee_time_clock ADD COLUMN IF NOT EXISTS multiple_session_override_required BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE public.employee_time_clock ADD COLUMN IF NOT EXISTS multiple_session_override_reason TEXT;
ALTER TABLE public.employee_time_clock ADD COLUMN IF NOT EXISTS multiple_session_override_by_user_id INTEGER REFERENCES public.users(user_id);
ALTER TABLE public.employee_time_clock ADD COLUMN IF NOT EXISTS multiple_session_override_by_name TEXT;

-- Public pairing state is shared; private LAN credential hashes remain local-only.
ALTER TABLE public.devices ADD COLUMN IF NOT EXISTS pairing_public_key TEXT;
ALTER TABLE public.devices ADD COLUMN IF NOT EXISTS credential_status TEXT NOT NULL DEFAULT 'PENDING';
ALTER TABLE public.devices ADD COLUMN IF NOT EXISTS credential_issued_at TIMESTAMPTZ;
ALTER TABLE public.devices ADD COLUMN IF NOT EXISTS credential_claimed_at TIMESTAMPTZ;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid='public.devices'::regclass AND conname='devices_credential_status_check') THEN
        ALTER TABLE public.devices ADD CONSTRAINT devices_credential_status_check
            CHECK (credential_status IN ('PENDING','ISSUED','CLAIMED','ROTATION_PENDING','REVOKED'));
    END IF;
END $$;

-- Columns defined by the tracked base schema but absent from legacy hosted copies.
ALTER TABLE public.roles ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE public.permissions ADD COLUMN IF NOT EXISTS permission_name TEXT;
ALTER TABLE public.permissions ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE public.mobile_permissions ADD COLUMN IF NOT EXISTS permission_name TEXT;
ALTER TABLE public.mobile_permissions ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE public.products ADD COLUMN IF NOT EXISTS is_active BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE public.product_barcodes ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE public.sale_items ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='mobile_permissions' AND column_name='display_name') THEN
        UPDATE public.mobile_permissions
        SET permission_name=COALESCE(NULLIF(BTRIM(permission_name),''),NULLIF(BTRIM(display_name),''),INITCAP(REPLACE(permission_key,'_',' ')))
        WHERE permission_name IS NULL OR BTRIM(permission_name)='';
    ELSE
        UPDATE public.mobile_permissions
        SET permission_name=COALESCE(NULLIF(BTRIM(permission_name),''),INITCAP(REPLACE(permission_key,'_',' ')))
        WHERE permission_name IS NULL OR BTRIM(permission_name)='';
    END IF;
END $$;

-- Restore sequence-backed defaults missing on older local databases.
CREATE SEQUENCE IF NOT EXISTS public.cheque_bank_deposits_cheque_bank_deposit_id_seq;
ALTER SEQUENCE public.cheque_bank_deposits_cheque_bank_deposit_id_seq OWNED BY public.cheque_bank_deposits.cheque_bank_deposit_id;
ALTER TABLE public.cheque_bank_deposits ALTER COLUMN cheque_bank_deposit_id SET DEFAULT nextval('public.cheque_bank_deposits_cheque_bank_deposit_id_seq');
SELECT setval('public.cheque_bank_deposits_cheque_bank_deposit_id_seq',COALESCE((SELECT MAX(cheque_bank_deposit_id) FROM public.cheque_bank_deposits),0)+1,false);

CREATE SEQUENCE IF NOT EXISTS public.employee_payroll_bonuses_employee_payroll_bonus_id_seq;
ALTER SEQUENCE public.employee_payroll_bonuses_employee_payroll_bonus_id_seq OWNED BY public.employee_payroll_bonuses.employee_payroll_bonus_id;
ALTER TABLE public.employee_payroll_bonuses ALTER COLUMN employee_payroll_bonus_id SET DEFAULT nextval('public.employee_payroll_bonuses_employee_payroll_bonus_id_seq');
SELECT setval('public.employee_payroll_bonuses_employee_payroll_bonus_id_seq',COALESCE((SELECT MAX(employee_payroll_bonus_id) FROM public.employee_payroll_bonuses),0)+1,false);
UPDATE public.employee_payroll_bonuses SET sync_uuid=gen_random_uuid() WHERE sync_uuid IS NULL;
ALTER TABLE public.employee_payroll_bonuses ALTER COLUMN sync_uuid SET DEFAULT gen_random_uuid();
ALTER TABLE public.employee_payroll_bonuses ALTER COLUMN sync_uuid SET NOT NULL;

-- Canonical data-safe defaults and nullability used by current write paths.
UPDATE public.bank_transactions
SET transaction_date=COALESCE(transaction_date,CURRENT_DATE),
    transaction_name=COALESCE(NULLIF(BTRIM(transaction_name),''),'Bank transaction'),
    transaction_direction=COALESCE(NULLIF(BTRIM(transaction_direction),''),'PAID'),
    amount=COALESCE(amount,0),
    created_at=COALESCE(created_at,transaction_date::timestamp AT TIME ZONE 'UTC',CURRENT_TIMESTAMP);
ALTER TABLE public.bank_transactions ALTER COLUMN transaction_date SET DEFAULT CURRENT_DATE;
ALTER TABLE public.bank_transactions ALTER COLUMN transaction_date SET NOT NULL;
ALTER TABLE public.bank_transactions ALTER COLUMN transaction_name SET NOT NULL;
ALTER TABLE public.bank_transactions ALTER COLUMN transaction_direction SET NOT NULL;
ALTER TABLE public.bank_transactions ALTER COLUMN amount SET NOT NULL;
ALTER TABLE public.bank_transactions ALTER COLUMN created_at SET DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE public.bank_transactions ALTER COLUMN created_at SET NOT NULL;

UPDATE public.cheque_bank_deposits
SET source_type=COALESCE(NULLIF(BTRIM(source_type),''),'UNKNOWN'),
    source_id=COALESCE(NULLIF(BTRIM(source_id),''),cheque_bank_deposit_id::text),
    amount=COALESCE(amount,0), deposited_at=COALESCE(deposited_at,CURRENT_TIMESTAMP),
    created_at=COALESCE(created_at,deposited_at,CURRENT_TIMESTAMP);
ALTER TABLE public.cheque_bank_deposits ALTER COLUMN source_type SET NOT NULL;
ALTER TABLE public.cheque_bank_deposits ALTER COLUMN source_id SET NOT NULL;
ALTER TABLE public.cheque_bank_deposits ALTER COLUMN amount SET DEFAULT 0;
ALTER TABLE public.cheque_bank_deposits ALTER COLUMN amount SET NOT NULL;
ALTER TABLE public.cheque_bank_deposits ALTER COLUMN deposited_at SET DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE public.cheque_bank_deposits ALTER COLUMN deposited_at SET NOT NULL;
ALTER TABLE public.cheque_bank_deposits ALTER COLUMN created_at SET DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE public.cheque_bank_deposits ALTER COLUMN created_at SET NOT NULL;

UPDATE public.users SET compensation_type='HOURLY' WHERE compensation_type IS NULL;
UPDATE public.users SET salary=0 WHERE salary IS NULL;
UPDATE public.users SET is_active=TRUE WHERE is_active IS NULL;
ALTER TABLE public.users ALTER COLUMN compensation_type SET DEFAULT 'HOURLY';
ALTER TABLE public.users ALTER COLUMN compensation_type SET NOT NULL;
ALTER TABLE public.users ALTER COLUMN salary SET DEFAULT 0;
ALTER TABLE public.users ALTER COLUMN salary SET NOT NULL;
ALTER TABLE public.users ALTER COLUMN is_active SET DEFAULT TRUE;
ALTER TABLE public.users ALTER COLUMN is_active SET NOT NULL;

UPDATE public.sale_items SET original_unit_price=COALESCE(original_unit_price,unit_price,0);
UPDATE public.sale_items SET product_type='INVENTORY' WHERE product_type IS NULL;
ALTER TABLE public.sale_items ALTER COLUMN original_unit_price SET DEFAULT 0;
ALTER TABLE public.sale_items ALTER COLUMN original_unit_price SET NOT NULL;
ALTER TABLE public.sale_items ALTER COLUMN product_type SET DEFAULT 'INVENTORY';
ALTER TABLE public.sale_items ALTER COLUMN product_type SET NOT NULL;

-- Legacy hosted timestamp values were written in UTC; interpret them explicitly.
DO $$
DECLARE target record;
BEGIN
    FOR target IN SELECT * FROM (VALUES
        ('categories','created_at'),('inventory','updated_at'),('locations','created_at'),
        ('products','created_at'),('products','updated_at'),('receiving_batches','created_at'),('users','created_at')
    ) AS columns_to_fix(table_name,column_name)
    LOOP
        IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name=target.table_name AND column_name=target.column_name AND data_type='timestamp without time zone') THEN
            EXECUTE format('ALTER TABLE public.%I ALTER COLUMN %I TYPE timestamptz USING %I AT TIME ZONE ''UTC''',target.table_name,target.column_name,target.column_name);
        END IF;
    END LOOP;
END $$;

ALTER TABLE public.company_customization DROP CONSTRAINT IF EXISTS company_customization_custom_order_minimum_deposit_percent_chk;
ALTER TABLE public.company_customization ADD CONSTRAINT company_customization_custom_order_minimum_deposit_percent_chk CHECK (custom_order_minimum_deposit_percent BETWEEN 0 AND 100);
ALTER TABLE public.custom_order_items DROP CONSTRAINT IF EXISTS custom_order_items_minimum_deposit_percent_chk;
ALTER TABLE public.custom_order_items ADD CONSTRAINT custom_order_items_minimum_deposit_percent_chk CHECK (minimum_deposit_percent BETWEEN 0 AND 100);
