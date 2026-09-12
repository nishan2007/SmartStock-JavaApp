-- Per-customer automatic discounts and immutable transaction snapshots.
ALTER TABLE public.customer_accounts
    ADD COLUMN IF NOT EXISTS sales_discount_enabled boolean NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS sales_discount_percent numeric(7,4) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS invoice_discount_enabled boolean NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS invoice_discount_percent numeric(7,4) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS custom_order_discount_enabled boolean NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS custom_order_discount_percent numeric(7,4) NOT NULL DEFAULT 0;

ALTER TABLE public.customer_accounts DROP CONSTRAINT IF EXISTS customer_accounts_automatic_discounts_chk;
ALTER TABLE public.customer_accounts ADD CONSTRAINT customer_accounts_automatic_discounts_chk CHECK (
    sales_discount_percent BETWEEN 0 AND 100 AND
    invoice_discount_percent BETWEEN 0 AND 100 AND
    custom_order_discount_percent BETWEEN 0 AND 100
);

ALTER TABLE public.sales
    ADD COLUMN IF NOT EXISTS customer_discount_percent numeric(7,4) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS customer_discount_applied boolean NOT NULL DEFAULT false;
ALTER TABLE public.quotations
    ADD COLUMN IF NOT EXISTS customer_discount_percent numeric(7,4) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS customer_discount_applied boolean NOT NULL DEFAULT false;
ALTER TABLE public.invoices
    ADD COLUMN IF NOT EXISTS customer_discount_percent numeric(7,4) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS customer_discount_applied boolean NOT NULL DEFAULT false;
ALTER TABLE public.custom_orders
    ADD COLUMN IF NOT EXISTS customer_discount_percent numeric(7,4) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS customer_discount_applied boolean NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS customer_discount_amount numeric(12,2) NOT NULL DEFAULT 0;
ALTER TABLE public.held_carts ADD COLUMN IF NOT EXISTS apply_customer_discount boolean NOT NULL DEFAULT true;

INSERT INTO public.permissions(permission_key,permission_name,description,permission_group,permission_subgroup)
VALUES('MANAGE_CUSTOMER_DISCOUNTS','Manage Customer Discounts','Allows configuring automatic customer discounts.','Customers','Discounts')
ON CONFLICT(permission_key) DO UPDATE SET permission_name=EXCLUDED.permission_name,description=EXCLUDED.description,
    permission_group=EXCLUDED.permission_group,permission_subgroup=EXCLUDED.permission_subgroup;
INSERT INTO public.role_permissions(role_id,permission_id)
SELECT 1,permission_id FROM public.permissions WHERE permission_key='MANAGE_CUSTOMER_DISCOUNTS'
ON CONFLICT(role_id,permission_id) DO NOTHING;
