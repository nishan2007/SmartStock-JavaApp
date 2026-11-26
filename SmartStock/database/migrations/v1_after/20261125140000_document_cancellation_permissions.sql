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
