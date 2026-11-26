CREATE TABLE smartstock_private.schema_migrations (
    migration_name text PRIMARY KEY,
    checksum_sha256 text NOT NULL CHECK (checksum_sha256 ~ '^[0-9a-f]{64}$'),
    app_version text,
    applied_at timestamptz NOT NULL DEFAULT current_timestamp
);

ALTER TABLE smartstock_private.schema_migrations ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON TABLE smartstock_private.schema_migrations
    FROM PUBLIC, anon, authenticated;
GRANT ALL ON TABLE smartstock_private.schema_migrations TO service_role;

INSERT INTO smartstock_private.smartstock_schema_metadata(
    schema_scope, baseline_version, resource_fingerprint_sha256,
    catalog_fingerprint_sha256
) VALUES (
    'CLOUD', 1, '__SMARTSTOCK_RESOURCE_FINGERPRINT__',
    '0000000000000000000000000000000000000000000000000000000000000000'
);

INSERT INTO smartstock_private.schema_migrations(
    migration_name, checksum_sha256, app_version
) VALUES (
    'v1_baseline', '__SMARTSTOCK_RESOURCE_FINGERPRINT__', 'v1'
);

SELECT pg_catalog.setval(pg_get_serial_sequence('public.permissions','permission_id'),GREATEST((SELECT MAX(permission_id) FROM public.permissions),1),TRUE);
INSERT INTO public.permissions(permission_key,permission_name,description,permission_group,permission_subgroup)
VALUES ('RESET_TEMPLATE_QUEUE_CODE','Reset template submission access codes','Reset organization submission codes and invalidate website sessions.','Administration','Studio'),
('PROCESS_TEMPLATE_SUBMISSIONS','Process template submissions','Review and print organization template submissions.','Operations','Studio'),
('MANAGE_SMARTDRIVE','Manage SmartDrive','Manage workstation access, scanners, storage and recovery in SmartDrive.','Administration','SmartDrive')
ON CONFLICT(permission_key) DO NOTHING;
INSERT INTO public.role_permissions(role_id,permission_id,updated_at)
SELECT r.role_id,p.permission_id,CURRENT_TIMESTAMP FROM public.roles r CROSS JOIN public.permissions p
WHERE upper(r.role_name)='ADMIN' AND p.permission_key IN ('RESET_TEMPLATE_QUEUE_CODE','PROCESS_TEMPLATE_SUBMISSIONS','MANAGE_SMARTDRIVE')
ON CONFLICT(role_id,permission_id) DO NOTHING;

INSERT INTO public.mobile_permissions(permission_key,permission_name,description,permission_group,permission_subgroup,created_at)
VALUES ('ACCESS_EMPLOYEE_WEB','Access Employee Web App','Sign in to an activated browser and view your own time-clock history and calculated earnings.','People','Employee Web',CURRENT_TIMESTAMP),
('VIEW_ALL_EMPLOYEE_WEB','View All Employees in Web App','Browse active and deactivated employees across all stores and view their time-clock history and calculated earnings. Requires Access Employee Web App.','People','Employee Web',CURRENT_TIMESTAMP)
ON CONFLICT(permission_key) DO NOTHING;
INSERT INTO public.role_mobile_permissions(role_id,permission_key,updated_at)
SELECT r.role_id,p.permission_key,CURRENT_TIMESTAMP FROM public.roles r CROSS JOIN public.mobile_permissions p
WHERE UPPER(r.role_name)='ADMIN' AND p.permission_key IN ('ACCESS_EMPLOYEE_WEB','VIEW_ALL_EMPLOYEE_WEB')
ON CONFLICT(role_id,permission_key) DO NOTHING;

INSERT INTO public.permissions(permission_key,permission_name,description,permission_group,permission_subgroup)
VALUES ('REVERSE_CUSTOM_ORDER_PAYMENTS','Reverse Order Payments','Refund a received order payment and restore the order balance.','Custom Orders','Refunds & Returns')
ON CONFLICT(permission_key) DO NOTHING;
INSERT INTO public.role_permissions(role_id,permission_id,updated_at)
SELECT r.role_id,p.permission_id,CURRENT_TIMESTAMP
FROM public.roles r CROSS JOIN public.permissions p
WHERE upper(r.role_name)='ADMIN' AND p.permission_key='REVERSE_CUSTOM_ORDER_PAYMENTS'
ON CONFLICT(role_id,permission_id) DO NOTHING;

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

INSERT INTO public.permissions(permission_key,permission_name,description,permission_group,permission_subgroup)
VALUES
 ('CANCEL_QUOTATIONS','Cancel Quotations','Cancel draft or issued quotations.','Quotations & Invoices','Cancellations'),
 ('CANCEL_INVOICES','Cancel Invoices','Cancel invoices after payments and deliveries have been reversed.','Quotations & Invoices','Cancellations'),
 ('CANCEL_INVOICE_DELIVERIES','Cancel Invoice Deliveries','Reverse an invoice delivery and return stock to inventory.','Quotations & Invoices','Cancellations'),
 ('CANCEL_INVOICE_PAYMENTS','Cancel Invoice Payments','Reverse an invoice payment and restore its outstanding balance.','Quotations & Invoices','Cancellations')
ON CONFLICT(permission_key) DO NOTHING;
INSERT INTO public.role_permissions(role_id,permission_id,updated_at)
SELECT r.role_id,p.permission_id,CURRENT_TIMESTAMP
FROM public.roles r CROSS JOIN public.permissions p
WHERE upper(r.role_name)='ADMIN' AND p.permission_key IN
 ('CANCEL_QUOTATIONS','CANCEL_INVOICES','CANCEL_INVOICE_DELIVERIES','CANCEL_INVOICE_PAYMENTS')
ON CONFLICT(role_id,permission_id) DO NOTHING;
