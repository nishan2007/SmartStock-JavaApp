-- Minutes are local service sales, never inventory items.
CREATE TABLE IF NOT EXISTS public.phone_minutes_sales (
 minutes_id bigserial PRIMARY KEY,
 sync_uuid uuid NOT NULL DEFAULT gen_random_uuid() UNIQUE,
 location_id integer NOT NULL REFERENCES public.locations(location_id),
 customer_id integer REFERENCES public.customer_accounts(customer_id),
 destination_phone text NOT NULL CHECK (destination_phone ~ '^\+?[0-9]{7,15}$'),
 amount numeric(12,2) NOT NULL CHECK (amount>0 AND amount=trunc(amount)),
 amount_paid numeric(12,2) NOT NULL DEFAULT 0 CHECK (amount_paid>=0 AND amount_paid<=amount),
 payment_method text NOT NULL CHECK (payment_method IN ('CASH','CREDIT')),
 user_id integer NOT NULL REFERENCES public.users(user_id), user_name text NOT NULL,
 device_id text NOT NULL, device_name text NOT NULL DEFAULT '',
 cash_drawer_id bigint, cash_drawer_name text,
 cash_drawer_session_id bigint REFERENCES public.cash_drawer_sessions(cash_drawer_session_id),
 created_at timestamptz NOT NULL DEFAULT now(),
 reversed_at timestamptz, reversed_by integer REFERENCES public.users(user_id), reversed_by_name text,
 reversal_reason text, reversal_device_id text, reversal_device_name text,
 reversal_cash_drawer_id bigint, reversal_cash_drawer_name text,
 reversal_cash_drawer_session_id bigint REFERENCES public.cash_drawer_sessions(cash_drawer_session_id),
 credit_remaining numeric(12,2) NOT NULL DEFAULT 0 CHECK (credit_remaining>=0 AND credit_remaining<=amount_paid),
 CHECK (payment_method<>'CREDIT' OR customer_id IS NOT NULL),
 CHECK (payment_method<>'CASH' OR amount_paid=amount),
 CHECK (credit_remaining=0 OR (reversed_at IS NOT NULL AND payment_method='CREDIT')),
 CHECK (reversed_at IS NULL OR length(trim(reversal_reason))>0)
);
ALTER TABLE public.customer_account_transactions ADD COLUMN IF NOT EXISTS minutes_id bigint REFERENCES public.phone_minutes_sales(minutes_id);
ALTER TABLE public.customer_account_payment_allocations ADD COLUMN IF NOT EXISTS minutes_id bigint REFERENCES public.phone_minutes_sales(minutes_id);
CREATE TABLE IF NOT EXISTS public.phone_minutes_credit_uses (
 credit_use_id bigserial PRIMARY KEY,
 sync_uuid uuid NOT NULL DEFAULT gen_random_uuid() UNIQUE,
 minutes_id bigint NOT NULL REFERENCES public.phone_minutes_sales(minutes_id),
 payment_transaction_id bigint NOT NULL REFERENCES public.customer_account_transactions(transaction_id),
 amount numeric(12,2) NOT NULL CHECK (amount>0 AND amount=trunc(amount)),
 created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS phone_minutes_credit_uses_source_idx ON public.phone_minutes_credit_uses(minutes_id,payment_transaction_id);
CREATE INDEX IF NOT EXISTS phone_minutes_credit_uses_payment_idx ON public.phone_minutes_credit_uses(payment_transaction_id);
CREATE INDEX IF NOT EXISTS phone_minutes_customer_idx ON public.phone_minutes_sales(customer_id,created_at DESC,minutes_id);
CREATE INDEX IF NOT EXISTS phone_minutes_store_idx ON public.phone_minutes_sales(location_id,created_at DESC);
CREATE INDEX IF NOT EXISTS phone_minutes_phone_idx ON public.phone_minutes_sales(destination_phone);
CREATE INDEX IF NOT EXISTS phone_minutes_drawer_idx ON public.phone_minutes_sales(cash_drawer_session_id);
CREATE INDEX IF NOT EXISTS phone_minutes_reversal_drawer_idx ON public.phone_minutes_sales(reversal_cash_drawer_session_id);
CREATE INDEX IF NOT EXISTS account_allocations_minutes_idx ON public.customer_account_payment_allocations(minutes_id,payment_transaction_id);
CREATE INDEX IF NOT EXISTS account_transactions_minutes_idx ON public.customer_account_transactions(minutes_id);

CREATE OR REPLACE VIEW public.sales_tender_activity AS
SELECT s.* FROM public.sales s WHERE NOT EXISTS (SELECT 1 FROM public.sales_tasks t WHERE t.sale_id=s.sale_id)
UNION ALL
SELECT (jsonb_populate_record(NULL::public.sales,jsonb_build_object(
    'sale_id',-p.payment_id,'location_id',p.location_id,'customer_id',p.customer_id,
    'user_id',p.user_id,'user_name',p.user_name,'created_at',p.created_at,
    'amount_paid',CASE WHEN p.payment_action='REFUND' THEN -p.amount ELSE p.amount END,
    'total_amount',p.amount,'payment_method',p.payment_method,'payment_reference',p.payment_reference,
    'payment_status','PAID','status','COMPLETED','receipt_number','ST-'||p.task_id||'/P-'||p.payment_id,
    'device_id',p.device_id,'device_name',p.device_name,'receipt_device_id',p.device_id,
    'cash_drawer_id',p.cash_drawer_id,'cash_drawer_name',p.cash_drawer_name,'cash_drawer_session_id',p.cash_drawer_session_id
))).* FROM public.sales_task_payments p
UNION ALL
SELECT (jsonb_populate_record(NULL::public.sales,jsonb_build_object(
 'sale_id',-1000000000-m.minutes_id,'location_id',m.location_id,'customer_id',m.customer_id,
 'user_id',m.user_id,'user_name',m.user_name,'created_at',m.created_at,
 'amount_paid',m.amount,'total_amount',m.amount,'payment_method','CASH','payment_status','PAID',
 'status','COMPLETED','receipt_number','MIN-'||m.minutes_id,'device_id',m.device_id,'device_name',m.device_name,
 'cash_drawer_id',m.cash_drawer_id,'cash_drawer_name',m.cash_drawer_name,'cash_drawer_session_id',m.cash_drawer_session_id
))).* FROM public.phone_minutes_sales m WHERE m.payment_method='CASH'
UNION ALL
SELECT (jsonb_populate_record(NULL::public.sales,jsonb_build_object(
 'sale_id',-1500000000-m.minutes_id,'location_id',m.location_id,'customer_id',m.customer_id,
 'user_id',m.reversed_by,'user_name',m.reversed_by_name,'created_at',m.reversed_at,
 'amount_paid',-m.amount,'total_amount',-m.amount,'payment_method','CASH','payment_status','PAID',
 'status','COMPLETED','receipt_number','MIN-'||m.minutes_id||'/REV','device_id',m.reversal_device_id,'device_name',m.reversal_device_name,
 'cash_drawer_id',m.reversal_cash_drawer_id,'cash_drawer_name',m.reversal_cash_drawer_name,'cash_drawer_session_id',m.reversal_cash_drawer_session_id
))).* FROM public.phone_minutes_sales m WHERE m.payment_method='CASH' AND m.reversed_at IS NOT NULL;

INSERT INTO public.permissions(permission_key,permission_name,description,permission_group,permission_subgroup)
VALUES ('REVERSE_PHONE_MINUTES','Reverse Phone Minutes','Reverse minutes with a reason and release paid credit or refund cash.','Point of Sale','Phone Minutes')
ON CONFLICT(permission_key) DO NOTHING;
INSERT INTO public.role_permissions(role_id,permission_id,updated_at)
SELECT r.role_id,p.permission_id,now() FROM public.roles r CROSS JOIN public.permissions p
WHERE upper(r.role_name)='ADMIN' AND p.permission_key='REVERSE_PHONE_MINUTES'
ON CONFLICT(role_id,permission_id) DO NOTHING;
