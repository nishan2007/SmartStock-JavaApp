
INSERT INTO public.permissions(permission_key,permission_name,description,permission_group,permission_subgroup)
VALUES ('MANUAL_CUSTOM_ORDER_ENTRY','Manual Custom Order Entry','Enter order-local custom items and print materials/add-ons with manual prices.','Custom Orders','Orders')
ON CONFLICT(permission_key) DO NOTHING;
INSERT INTO public.role_permissions(role_id,permission_id,updated_at)
SELECT r.role_id,p.permission_id,now() FROM public.roles r CROSS JOIN public.permissions p
WHERE UPPER(r.role_name)='ADMIN' AND p.permission_key='MANUAL_CUSTOM_ORDER_ENTRY'
ON CONFLICT(role_id,permission_id) DO NOTHING;
