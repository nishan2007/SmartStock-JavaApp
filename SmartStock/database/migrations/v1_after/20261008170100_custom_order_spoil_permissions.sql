-- Spoil evidence is mirrored as private store rows, not live cloud POS tables.
INSERT INTO public.permissions(permission_key,permission_name,description,permission_group,permission_subgroup)
VALUES ('RECORD_CUSTOM_ORDER_SPOILS','Record Custom Order Spoils','Record internal spoil evidence and consume replacement inventory.','Custom Orders','Orders'),
 ('REVERSE_CUSTOM_ORDER_SPOILS','Reverse Custom Order Spoils','Reverse a spoil with a reason and restore replacement stock.','Custom Orders','Orders')
ON CONFLICT(permission_key) DO NOTHING;
INSERT INTO public.role_permissions(role_id,permission_id,updated_at)
SELECT r.role_id,p.permission_id,now() FROM public.roles r CROSS JOIN public.permissions p
WHERE UPPER(r.role_name)='ADMIN' AND p.permission_key IN ('RECORD_CUSTOM_ORDER_SPOILS','REVERSE_CUSTOM_ORDER_SPOILS')
ON CONFLICT(role_id,permission_id) DO NOTHING;
