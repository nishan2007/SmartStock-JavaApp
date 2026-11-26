INSERT INTO public.permissions(permission_key,permission_name,description,permission_group,permission_subgroup) VALUES
 ('STORE_KEYS_SELF','Use Store Keys','Sign store keys out and return your own keys.','Employee','Store Keys'),
 ('STORE_KEYS_ASSIST','Record Staff Key Transactions','Record key checkout and return on behalf of employees.','Employee','Store Keys'),
 ('STORE_KEYS_CONFIGURE','Configure Store Keys','Configure numbered keys and pair NFC cards.','Administration','Store Keys'),
 ('STORE_KEYS_FORCE_RETURN','Recover Store Keys','Record physical recovery of a key without the holder present.','Administration','Store Keys'),
 ('STORE_KEYS_HISTORY','View Store Key History','View all key holders and key custody history for the store.','Employee','Store Keys')
 ON CONFLICT(permission_key) DO NOTHING;
INSERT INTO public.role_permissions(role_id,permission_id,updated_at)
 SELECT r.role_id,p.permission_id,CURRENT_TIMESTAMP FROM public.roles r CROSS JOIN public.permissions p
 WHERE upper(r.role_name)='ADMIN' AND p.permission_key IN
 ('STORE_KEYS_SELF','STORE_KEYS_ASSIST','STORE_KEYS_CONFIGURE','STORE_KEYS_FORCE_RETURN','STORE_KEYS_HISTORY')
 ON CONFLICT(role_id,permission_id) DO NOTHING;
