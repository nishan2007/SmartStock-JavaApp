-- Noncash stock use and receiving shortages. Monetary expenses remain in expenses.
CREATE TABLE IF NOT EXISTS public.stock_ledger_entries (
    entry_id uuid PRIMARY KEY,
    location_id integer NOT NULL REFERENCES public.locations(location_id),
    ledger_type text NOT NULL CHECK (ledger_type IN ('EXPENSE','LOSS')),
    item_type text NOT NULL CHECK (item_type IN ('PRODUCT','CUSTOM_ITEM','CUSTOM_VARIANT')),
    item_id integer NOT NULL,
    item_name text NOT NULL,
    quantity integer NOT NULL CHECK (quantity > 0),
    system_quantity integer NOT NULL,
    counted_quantity integer NOT NULL,
    selling_price numeric(12,2) NOT NULL CHECK (selling_price >= 0),
    unit_cost numeric(12,2) CHECK (unit_cost >= 0),
    category text NOT NULL,
    description text NOT NULL,
    reason text NOT NULL CHECK (length(trim(reason)) > 0),
    user_id integer NOT NULL REFERENCES public.users(user_id),
    user_name text NOT NULL,
    device_id uuid NOT NULL,
    receive_id text,
    signature_png bytea,
    reversal_of uuid UNIQUE REFERENCES public.stock_ledger_entries(entry_id) DEFERRABLE INITIALLY DEFERRED,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK (ledger_type <> 'EXPENSE' OR reversal_of IS NOT NULL OR signature_png IS NOT NULL)
);
ALTER TABLE public.stock_ledger_entries ENABLE ROW LEVEL SECURITY;
CREATE INDEX IF NOT EXISTS stock_ledger_location_date_idx ON public.stock_ledger_entries(location_id,created_at,entry_id);
CREATE INDEX IF NOT EXISTS stock_ledger_item_idx ON public.stock_ledger_entries(location_id,item_type,item_id,created_at);
CREATE INDEX IF NOT EXISTS stock_ledger_employee_idx ON public.stock_ledger_entries(location_id,user_id,created_at);
SELECT pg_catalog.setval(pg_get_serial_sequence('public.permissions','permission_id'),GREATEST((SELECT MAX(permission_id) FROM public.permissions),1),TRUE);
INSERT INTO public.permissions(permission_key,permission_name,description,permission_group,permission_subgroup)
VALUES ('RECORD_STOCK_EXPENSE','Record stock expenses','Record signed company use and donations from stock.','Reports','Ledgers'),
       ('REVERSE_STOCK_LEDGER','Reverse stock ledger entries','Reverse noncash stock expense and loss entries with a reason.','Reports','Ledgers')
ON CONFLICT(permission_key) DO NOTHING;
INSERT INTO public.role_permissions(role_id,permission_id,updated_at)
SELECT r.role_id,p.permission_id,CURRENT_TIMESTAMP FROM public.roles r CROSS JOIN public.permissions p
WHERE upper(r.role_name)='ADMIN' AND p.permission_key IN ('RECORD_STOCK_EXPENSE','REVERSE_STOCK_LEDGER')
ON CONFLICT(role_id,permission_id) DO NOTHING;
