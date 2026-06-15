-- Align SmartStock quote/order print defaults with the live Supabase schema.
-- Existing customized rows are left untouched; this migration updates defaults
-- and only normalizes rows that still carry the old repo defaults.

ALTER TABLE company_customization
ADD COLUMN IF NOT EXISTS quotation_print_title TEXT NOT NULL DEFAULT 'QUOTE / NOT FINAL SALE';

ALTER TABLE company_customization
ADD COLUMN IF NOT EXISTS quotation_print_validity_note TEXT NOT NULL DEFAULT 'This is a quote only and is not a final sale. Prices are valid until the valid-until date shown above unless superseded or cancelled.';

ALTER TABLE company_customization
ADD COLUMN IF NOT EXISTS invoice_print_title TEXT NOT NULL DEFAULT 'SALES ORDER CONFIRMATION';

ALTER TABLE company_customization
ALTER COLUMN quotation_print_title SET DEFAULT 'QUOTE / NOT FINAL SALE';

ALTER TABLE company_customization
ALTER COLUMN quotation_print_validity_note SET DEFAULT 'This is a quote only and is not a final sale. Prices are valid until the valid-until date shown above unless superseded or cancelled.';

ALTER TABLE company_customization
ALTER COLUMN invoice_print_title SET DEFAULT 'SALES ORDER CONFIRMATION';

UPDATE company_customization
SET quotation_print_title = 'QUOTE / NOT FINAL SALE',
    updated_at = NOW()
WHERE quotation_print_title = 'QUOTATION / NOT FINAL SALE';

UPDATE company_customization
SET quotation_print_validity_note = 'This is a quote only and is not a final sale. Prices are valid until the valid-until date shown above unless superseded or cancelled.',
    updated_at = NOW()
WHERE quotation_print_validity_note = 'This is a quotation only and is not a final sale. Prices are valid until the valid-until date shown above unless superseded or cancelled.';

UPDATE company_customization
SET invoice_print_title = 'SALES ORDER CONFIRMATION',
    updated_at = NOW()
WHERE invoice_print_title = 'INVOICE';
