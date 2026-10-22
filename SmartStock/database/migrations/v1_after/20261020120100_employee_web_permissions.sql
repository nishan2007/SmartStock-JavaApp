INSERT INTO public.mobile_permissions(permission_key,permission_name,description,permission_group,permission_subgroup,created_at)
VALUES ('ACCESS_EMPLOYEE_WEB','Access Employee Web App','Sign in to an activated browser and view your own time-clock history and calculated earnings.','People','Employee Web',CURRENT_TIMESTAMP),
('VIEW_ALL_EMPLOYEE_WEB','View All Employees in Web App','Browse active and deactivated employees across all stores and view their time-clock history and calculated earnings. Requires Access Employee Web App.','People','Employee Web',CURRENT_TIMESTAMP)
ON CONFLICT(permission_key) DO NOTHING;
INSERT INTO public.role_mobile_permissions(role_id,permission_key,updated_at)
SELECT r.role_id,p.permission_key,CURRENT_TIMESTAMP FROM public.roles r CROSS JOIN public.mobile_permissions p
WHERE UPPER(r.role_name)='ADMIN' AND p.permission_key IN ('ACCESS_EMPLOYEE_WEB','VIEW_ALL_EMPLOYEE_WEB')
ON CONFLICT(role_id,permission_key) DO NOTHING;
