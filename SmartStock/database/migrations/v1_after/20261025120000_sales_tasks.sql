-- Service work is local store authority. Payments are separate from the eventual sale.
CREATE TABLE IF NOT EXISTS public.sales_tasks (
    task_id bigserial PRIMARY KEY,
    location_id integer NOT NULL REFERENCES public.locations(location_id),
    customer_id integer REFERENCES public.customer_accounts(customer_id),
    customer_name text NOT NULL CHECK (length(trim(customer_name)) > 0),
    phone text NOT NULL DEFAULT '',
    instructions text NOT NULL CHECK (length(trim(instructions)) > 0),
    due_at timestamptz,
    assigned_user_id integer REFERENCES public.users(user_id),
    status text NOT NULL DEFAULT 'RECEIVED' CHECK (status IN ('RECEIVED','IN_PROGRESS','COMPLETED','COLLECTED','CANCELLED')),
    billing_status text NOT NULL DEFAULT 'PROVISIONAL' CHECK (billing_status IN ('PROVISIONAL','FINALIZED')),
    checkout_request jsonb NOT NULL DEFAULT '{"lines":[]}',
    finalized_quote jsonb,
    final_total numeric(12,2) CHECK (final_total > 0),
    sale_id integer UNIQUE REFERENCES public.sales(sale_id),
    version integer NOT NULL DEFAULT 1,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    sync_uuid uuid NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    CHECK ((billing_status='FINALIZED') = (final_total IS NOT NULL AND finalized_quote IS NOT NULL)),
    CHECK ((status='COLLECTED') = (sale_id IS NOT NULL))
);
CREATE TABLE IF NOT EXISTS public.sales_task_payments (
    payment_id bigserial PRIMARY KEY,
    task_id bigint NOT NULL REFERENCES public.sales_tasks(task_id),
    location_id integer NOT NULL REFERENCES public.locations(location_id),
    customer_id integer REFERENCES public.customer_accounts(customer_id),
    amount numeric(12,2) NOT NULL CHECK (amount > 0),
    payment_action text NOT NULL CHECK (payment_action IN ('PAYMENT','REFUND')),
    payment_method text NOT NULL CHECK (payment_method IN ('CASH','CARD','CHEQUE','MMG','BANK_TRANSFER')),
    payment_reference text NOT NULL DEFAULT '',
    reason text NOT NULL DEFAULT '',
    user_id integer NOT NULL REFERENCES public.users(user_id),
    user_name text NOT NULL,
    device_id text NOT NULL,
    device_name text NOT NULL DEFAULT '',
    cash_drawer_id bigint,
    cash_drawer_name text,
    cash_drawer_session_id bigint REFERENCES public.cash_drawer_sessions(cash_drawer_session_id),
    created_at timestamptz NOT NULL DEFAULT now(),
    sync_uuid uuid NOT NULL DEFAULT gen_random_uuid() UNIQUE
);
CREATE TABLE IF NOT EXISTS public.sales_task_events (
    event_id bigserial PRIMARY KEY,
    task_id bigint NOT NULL REFERENCES public.sales_tasks(task_id),
    action text NOT NULL,
    reason text NOT NULL DEFAULT '',
    snapshot jsonb NOT NULL,
    user_id integer NOT NULL REFERENCES public.users(user_id),
    user_name text NOT NULL,
    device_id text NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    sync_uuid uuid NOT NULL DEFAULT gen_random_uuid() UNIQUE
);
CREATE TABLE IF NOT EXISTS public.sales_task_files (
    file_id bigserial PRIMARY KEY,
    task_id bigint NOT NULL REFERENCES public.sales_tasks(task_id),
    filename text NOT NULL,
    content bytea NOT NULL CHECK (octet_length(content) <= 5242880),
    sha256 text NOT NULL,
    user_id integer NOT NULL REFERENCES public.users(user_id),
    created_at timestamptz NOT NULL DEFAULT now(),
    sync_uuid uuid NOT NULL DEFAULT gen_random_uuid() UNIQUE
);
CREATE INDEX IF NOT EXISTS sales_tasks_queue_idx ON public.sales_tasks(location_id,status,due_at,task_id);
CREATE INDEX IF NOT EXISTS sales_tasks_customer_idx ON public.sales_tasks(customer_id,created_at DESC);
CREATE INDEX IF NOT EXISTS sales_tasks_assignee_idx ON public.sales_tasks(assigned_user_id,status);
CREATE INDEX IF NOT EXISTS sales_task_payments_task_idx ON public.sales_task_payments(task_id,payment_id);
CREATE INDEX IF NOT EXISTS sales_task_payments_drawer_idx ON public.sales_task_payments(cash_drawer_session_id,created_at);
CREATE INDEX IF NOT EXISTS sales_task_payments_location_idx ON public.sales_task_payments(location_id,created_at);
CREATE INDEX IF NOT EXISTS sales_task_events_task_idx ON public.sales_task_events(task_id,event_id);
CREATE INDEX IF NOT EXISTS sales_task_files_task_idx ON public.sales_task_files(task_id,file_id);

-- Money received, rather than revenue earned. A collected task's sale is excluded:
-- all its actual tender was already recorded in sales_task_payments.
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
))).* FROM public.sales_task_payments p;

INSERT INTO public.permissions(permission_key,permission_name,description,permission_group,permission_subgroup)
VALUES ('MANAGE_SALES_TASKS','Manage Sales Tasks','Create, assign, complete, price, reopen and cancel service work. Financial operations also require their existing permissions.','Point of Sale','Sales Tasks')
ON CONFLICT(permission_key) DO NOTHING;
INSERT INTO public.role_permissions(role_id,permission_id,updated_at)
SELECT r.role_id,p.permission_id,now() FROM public.roles r CROSS JOIN public.permissions p
WHERE upper(r.role_name)='ADMIN' AND p.permission_key='MANAGE_SALES_TASKS'
ON CONFLICT(role_id,permission_id) DO NOTHING;
