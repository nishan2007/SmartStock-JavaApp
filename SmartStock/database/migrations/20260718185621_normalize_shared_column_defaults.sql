-- Normalize the remaining shared column types, nullability, and defaults.
-- This migration is data-safe: nullable legacy rows are backfilled before each
-- NOT NULL constraint is enforced.

UPDATE public.company_customization SET receipt_header_line='' WHERE receipt_header_line IS NULL;
ALTER TABLE public.company_customization ALTER COLUMN location_id SET NOT NULL;
ALTER TABLE public.company_customization ALTER COLUMN receipt_header_line SET DEFAULT '';
ALTER TABLE public.company_customization ALTER COLUMN receipt_header_line SET NOT NULL;

-- The local base schema uses BIGSERIAL ledger identifiers. Widen the hosted
-- integer copies while preserving the allocation foreign key definition.
DO $$
DECLARE fk_name text;
DECLARE fk_definition text;
BEGIN
    SELECT conname, pg_get_constraintdef(oid,true)
    INTO fk_name, fk_definition
    FROM pg_constraint
    WHERE conrelid='public.customer_account_payment_allocations'::regclass
      AND confrelid='public.customer_account_transactions'::regclass
      AND contype='f'
    LIMIT 1;

    IF fk_name IS NOT NULL THEN
        EXECUTE format('ALTER TABLE public.customer_account_payment_allocations DROP CONSTRAINT %I',fk_name);
    END IF;
    ALTER TABLE public.customer_account_transactions ALTER COLUMN transaction_id TYPE BIGINT;
    ALTER TABLE public.customer_account_payment_allocations ALTER COLUMN allocation_id TYPE BIGINT;
    ALTER TABLE public.customer_account_payment_allocations ALTER COLUMN payment_transaction_id TYPE BIGINT;
    IF fk_name IS NOT NULL THEN
        EXECUTE format('ALTER TABLE public.customer_account_payment_allocations ADD CONSTRAINT %I %s',fk_name,fk_definition);
    END IF;
END $$;

UPDATE public.customer_account_transactions SET amount=0 WHERE amount IS NULL;
UPDATE public.customer_account_payment_allocations SET amount=0 WHERE amount IS NULL;
ALTER TABLE public.customer_account_transactions ALTER COLUMN customer_id SET NOT NULL;
ALTER TABLE public.customer_account_transactions ALTER COLUMN amount SET DEFAULT 0;
ALTER TABLE public.customer_account_payment_allocations ALTER COLUMN amount SET DEFAULT 0;

UPDATE public.device_sessions SET login_time=CURRENT_TIMESTAMP WHERE login_time IS NULL;
ALTER TABLE public.device_sessions ALTER COLUMN login_time SET DEFAULT CURRENT_TIMESTAMP;

UPDATE public.employee_payroll_bonuses
SET user_id=COALESCE(user_id,0), pay_period_start=COALESCE(pay_period_start,CURRENT_DATE),
    pay_period_end=COALESCE(pay_period_end,CURRENT_DATE), amount=COALESCE(amount,0),
    created_at=COALESCE(created_at,CURRENT_TIMESTAMP)
WHERE user_id IS NULL OR pay_period_start IS NULL OR pay_period_end IS NULL OR amount IS NULL OR created_at IS NULL;
ALTER TABLE public.employee_payroll_bonuses ALTER COLUMN user_id SET NOT NULL;
ALTER TABLE public.employee_payroll_bonuses ALTER COLUMN pay_period_start SET NOT NULL;
ALTER TABLE public.employee_payroll_bonuses ALTER COLUMN pay_period_end SET NOT NULL;
ALTER TABLE public.employee_payroll_bonuses ALTER COLUMN amount SET NOT NULL;
ALTER TABLE public.employee_payroll_bonuses ALTER COLUMN created_at SET DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE public.employee_payroll_bonuses ALTER COLUMN created_at SET NOT NULL;

ALTER TABLE public.inventory_movements ALTER COLUMN movement_id TYPE BIGINT;
UPDATE public.inventory_movements SET change_qty=0 WHERE change_qty IS NULL;
UPDATE public.inventory_movements SET reason='ADJUSTMENT' WHERE reason IS NULL OR BTRIM(reason)='';
ALTER TABLE public.inventory_movements ALTER COLUMN product_id SET NOT NULL;
ALTER TABLE public.inventory_movements ALTER COLUMN location_id SET NOT NULL;
ALTER TABLE public.inventory_movements ALTER COLUMN change_qty SET DEFAULT 0;
ALTER TABLE public.inventory_movements ALTER COLUMN reason SET NOT NULL;

UPDATE public.locations SET receipt_store_code='0001' WHERE receipt_store_code IS NULL OR BTRIM(receipt_store_code)='';
ALTER TABLE public.locations ALTER COLUMN receipt_store_code SET DEFAULT '0001';
ALTER TABLE public.locations ALTER COLUMN receipt_store_code SET NOT NULL;

UPDATE public.mobile_permissions
SET permission_group=COALESCE(NULLIF(BTRIM(permission_group),''),'OTHER')
WHERE permission_group IS NULL OR BTRIM(permission_group)='';
ALTER TABLE public.mobile_permissions ALTER COLUMN permission_group SET NOT NULL;

UPDATE public.permissions
SET permission_name=COALESCE(NULLIF(BTRIM(permission_name),''),INITCAP(REPLACE(permission_key,'_',' ')))
WHERE permission_name IS NULL OR BTRIM(permission_name)='';
ALTER TABLE public.permissions ALTER COLUMN permission_name SET NOT NULL;

-- The tracked base schema intentionally uses an INTEGER product barcode identity.
DO $$
BEGIN
    IF (SELECT COALESCE(MAX(product_barcode_id),0) FROM public.product_barcodes) > 2147483647 THEN
        RAISE EXCEPTION 'product_barcodes.product_barcode_id exceeds INTEGER range';
    END IF;
END $$;
ALTER TABLE public.product_barcodes ALTER COLUMN product_barcode_id TYPE INTEGER;

ALTER TABLE public.products ALTER COLUMN sku SET NOT NULL;
ALTER TABLE public.receiving_batches ALTER COLUMN created_at SET DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE public.receiving_batches ALTER COLUMN created_at SET NOT NULL;

ALTER TABLE public.sale_items ALTER COLUMN quantity SET DEFAULT 1;
ALTER TABLE public.sale_items ALTER COLUMN unit_price SET DEFAULT 0;

UPDATE public.sales
SET payment_method=COALESCE(NULLIF(BTRIM(payment_method),''),'CASH'),
    payment_status=COALESCE(NULLIF(BTRIM(payment_status),''),'PAID'),
    completed_at=COALESCE(completed_at,created_at,CURRENT_TIMESTAMP)
WHERE payment_method IS NULL OR BTRIM(payment_method)='' OR payment_status IS NULL OR BTRIM(payment_status)='' OR completed_at IS NULL;
ALTER TABLE public.sales ALTER COLUMN location_id SET NOT NULL;
ALTER TABLE public.sales ALTER COLUMN payment_method SET DEFAULT 'CASH';
ALTER TABLE public.sales ALTER COLUMN payment_method SET NOT NULL;
ALTER TABLE public.sales ALTER COLUMN payment_status SET DEFAULT 'PAID';
ALTER TABLE public.sales ALTER COLUMN payment_status SET NOT NULL;
ALTER TABLE public.sales ALTER COLUMN completed_at SET DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE public.sales ALTER COLUMN completed_at SET NOT NULL;

UPDATE public.users SET full_name=COALESCE(NULLIF(BTRIM(full_name),''),username) WHERE full_name IS NULL OR BTRIM(full_name)='';
ALTER TABLE public.users ALTER COLUMN full_name SET NOT NULL;

ALTER TABLE IF EXISTS public.wifi_sessions ALTER COLUMN id SET DEFAULT gen_random_uuid();
ALTER TABLE IF EXISTS public.wifi_sessions ALTER COLUMN accepted_terms SET DEFAULT FALSE;
ALTER TABLE IF EXISTS public.wifi_sessions ALTER COLUMN status SET DEFAULT 'pending';
ALTER TABLE IF EXISTS public.wifi_sessions ALTER COLUMN created_at SET DEFAULT CURRENT_TIMESTAMP;
