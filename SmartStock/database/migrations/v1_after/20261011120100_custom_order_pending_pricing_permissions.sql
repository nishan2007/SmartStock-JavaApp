-- Shared permission catalogue only; orders remain local store authority.
INSERT INTO public.permissions(permission_key,permission_name,description,permission_group,permission_subgroup)
VALUES ('CUSTOM_ORDER_DEFER_PRICING','Defer Custom Order Pricing','Save an order before its final price is known, or approve a pricing deferral.','Custom Orders','Pricing & Deposits'),
       ('CUSTOM_ORDER_UNPRICED_DELIVERY_OVERRIDE','Deliver Custom Order Before Pricing','Approve delivery of an order whose price is still pending.','Custom Orders','Overrides')
ON CONFLICT(permission_key) DO NOTHING;
INSERT INTO public.role_permissions(role_id,permission_id,updated_at)
SELECT r.role_id,p.permission_id,now() FROM public.roles r CROSS JOIN public.permissions p
WHERE UPPER(r.role_name)='ADMIN' AND p.permission_key IN ('CUSTOM_ORDER_DEFER_PRICING','CUSTOM_ORDER_UNPRICED_DELIVERY_OVERRIDE')
ON CONFLICT(role_id,permission_id) DO NOTHING;
