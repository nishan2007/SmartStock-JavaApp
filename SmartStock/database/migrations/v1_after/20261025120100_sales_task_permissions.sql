INSERT INTO public.permissions(permission_key,permission_name,description,permission_group,permission_subgroup)
VALUES ('MANAGE_SALES_TASKS','Manage Sales Tasks','Create, assign, complete, price, reopen and cancel service work. Financial operations also require their existing permissions.','Point of Sale','Sales Tasks')
ON CONFLICT(permission_key) DO NOTHING;
INSERT INTO public.role_permissions(role_id,permission_id,updated_at)
SELECT r.role_id,p.permission_id,now() FROM public.roles r CROSS JOIN public.permissions p
WHERE upper(r.role_name)='ADMIN' AND p.permission_key='MANAGE_SALES_TASKS'
ON CONFLICT(role_id,permission_id) DO NOTHING;
