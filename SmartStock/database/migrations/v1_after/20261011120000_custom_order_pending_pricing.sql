-- Store-owned order pricing; existing orders retain finalized prices.
ALTER TABLE public.custom_orders
    ADD COLUMN IF NOT EXISTS pricing_state text NOT NULL DEFAULT 'FINAL',
    ADD COLUMN IF NOT EXISTS pricing_defer_reason text,
    ADD COLUMN IF NOT EXISTS pricing_defer_by_user_id integer,
    ADD COLUMN IF NOT EXISTS pricing_defer_by_name text,
    ADD COLUMN IF NOT EXISTS pricing_finalized_at timestamptz,
    ADD COLUMN IF NOT EXISTS pricing_finalized_by_user_id integer,
    ADD COLUMN IF NOT EXISTS pricing_finalized_by_name text,
    ADD COLUMN IF NOT EXISTS pricing_apply_customer_discount boolean NOT NULL DEFAULT true;
ALTER TABLE public.custom_orders DROP CONSTRAINT IF EXISTS custom_orders_pricing_state_chk;
ALTER TABLE public.custom_orders ADD CONSTRAINT custom_orders_pricing_state_chk CHECK (pricing_state IN ('PENDING','FINAL'));
ALTER TABLE public.custom_orders DROP CONSTRAINT IF EXISTS custom_orders_payment_status_chk;
ALTER TABLE public.custom_orders ADD CONSTRAINT custom_orders_payment_status_chk CHECK (payment_status IN ('PAID','PARTIAL','UNPAID','PRICE_PENDING'));
CREATE INDEX IF NOT EXISTS custom_orders_pending_pricing_idx ON public.custom_orders(location_id,created_at DESC) WHERE pricing_state='PENDING';

INSERT INTO public.permissions(permission_key,permission_name,description,permission_group,permission_subgroup)
VALUES ('CUSTOM_ORDER_DEFER_PRICING','Defer Custom Order Pricing','Save an order before its final price is known, or approve a pricing deferral.','Custom Orders','Pricing & Deposits'),
       ('CUSTOM_ORDER_UNPRICED_DELIVERY_OVERRIDE','Deliver Custom Order Before Pricing','Approve delivery of an order whose price is still pending.','Custom Orders','Overrides')
ON CONFLICT(permission_key) DO NOTHING;
INSERT INTO public.role_permissions(role_id,permission_id,updated_at)
SELECT r.role_id,p.permission_id,now() FROM public.roles r CROSS JOIN public.permissions p
WHERE UPPER(r.role_name)='ADMIN' AND p.permission_key IN ('CUSTOM_ORDER_DEFER_PRICING','CUSTOM_ORDER_UNPRICED_DELIVERY_OVERRIDE')
ON CONFLICT(role_id,permission_id) DO NOTHING;
