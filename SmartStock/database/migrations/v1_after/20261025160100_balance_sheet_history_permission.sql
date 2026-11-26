INSERT INTO public.permissions(permission_key,permission_name,description,permission_group,permission_subgroup)
VALUES ('VIEW_BALANCE_SHEET_HISTORY','View Balance Sheet History','Inspect captured drafts, snapshots and changes by employees.','Accounting','Balance Sheet')
ON CONFLICT(permission_key) DO NOTHING;
INSERT INTO public.role_permissions(role_id,permission_id,updated_at)
SELECT r.role_id,p.permission_id,now() FROM public.roles r CROSS JOIN public.permissions p
WHERE upper(r.role_name)='ADMIN' AND p.permission_key='VIEW_BALANCE_SHEET_HISTORY'
ON CONFLICT(role_id,permission_id) DO NOTHING;
