SELECT pg_catalog.setval(pg_get_serial_sequence('public.permissions','permission_id'),GREATEST((SELECT MAX(permission_id) FROM public.permissions),1),TRUE);
INSERT INTO public.permissions(permission_key,permission_name,description,permission_group,permission_subgroup)
VALUES ('RESET_TEMPLATE_QUEUE_CODE','Reset template submission access codes','Reset organization submission codes and invalidate website sessions.','Administration','Studio'),
('PROCESS_TEMPLATE_SUBMISSIONS','Process template submissions','Review and print organization template submissions.','Operations','Studio')
ON CONFLICT(permission_key) DO NOTHING;
INSERT INTO public.role_permissions(role_id,permission_id,updated_at)
SELECT r.role_id,p.permission_id,CURRENT_TIMESTAMP FROM public.roles r CROSS JOIN public.permissions p
WHERE upper(r.role_name)='ADMIN' AND p.permission_key IN ('RESET_TEMPLATE_QUEUE_CODE','PROCESS_TEMPLATE_SUBMISSIONS')
ON CONFLICT(role_id,permission_id) DO NOTHING;
