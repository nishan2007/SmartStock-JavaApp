INSERT INTO public.permissions(permission_key,permission_name,description,permission_group,permission_subgroup)
VALUES ('REVERSE_CUSTOM_ORDER_PAYMENTS','Reverse Order Payments','Refund a received order payment and restore the order balance.','Custom Orders','Refunds & Returns')
ON CONFLICT(permission_key) DO NOTHING;
INSERT INTO public.role_permissions(role_id,permission_id,updated_at)
SELECT r.role_id,p.permission_id,CURRENT_TIMESTAMP
FROM public.roles r CROSS JOIN public.permissions p
WHERE upper(r.role_name)='ADMIN' AND p.permission_key='REVERSE_CUSTOM_ORDER_PAYMENTS'
ON CONFLICT(role_id,permission_id) DO NOTHING;
