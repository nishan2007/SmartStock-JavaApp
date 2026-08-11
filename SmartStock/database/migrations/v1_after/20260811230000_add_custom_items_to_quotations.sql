ALTER TABLE quotations ADD COLUMN IF NOT EXISTS production_due_date date;
ALTER TABLE quotations ADD COLUMN IF NOT EXISTS linked_custom_order_id bigint;
ALTER TABLE quotation_lines ADD COLUMN IF NOT EXISTS custom_item_id bigint;
ALTER TABLE quotation_lines ADD COLUMN IF NOT EXISTS custom_variant_id bigint;
ALTER TABLE quotation_lines ADD COLUMN IF NOT EXISTS custom_configuration jsonb;
ALTER TABLE invoices ADD COLUMN IF NOT EXISTS linked_custom_order_id bigint;
ALTER TABLE invoice_lines ADD COLUMN IF NOT EXISTS custom_item_id bigint;
ALTER TABLE invoice_lines ADD COLUMN IF NOT EXISTS custom_variant_id bigint;
ALTER TABLE invoice_lines ADD COLUMN IF NOT EXISTS custom_configuration jsonb;
ALTER TABLE custom_orders ADD COLUMN IF NOT EXISTS source_quotation_id bigint;
ALTER TABLE custom_orders ADD COLUMN IF NOT EXISTS source_invoice_id bigint;
ALTER TABLE custom_orders ADD COLUMN IF NOT EXISTS invoice_billed boolean NOT NULL DEFAULT false;

CREATE TABLE IF NOT EXISTS quotation_line_print_addons (
  quotation_line_print_addon_id bigserial PRIMARY KEY,
  quotation_line_id bigint NOT NULL REFERENCES quotation_lines(quotation_line_id) ON DELETE CASCADE,
  print_material_id bigint,
  material_name text NOT NULL,
  print_size_preset_id bigint,
  print_size_name text,
  pricing_mode text NOT NULL,
  print_description text,
  print_line_count integer NOT NULL DEFAULT 1,
  print_charge numeric(12,2) NOT NULL DEFAULT 0,
  sort_order integer NOT NULL DEFAULT 0
);
CREATE TABLE IF NOT EXISTS invoice_line_print_addons (
  invoice_line_print_addon_id bigserial PRIMARY KEY,
  invoice_line_id bigint NOT NULL REFERENCES invoice_lines(invoice_line_id) ON DELETE CASCADE,
  quotation_line_print_addon_id bigint REFERENCES quotation_line_print_addons(quotation_line_print_addon_id),
  print_material_id bigint,
  material_name text NOT NULL,
  print_size_preset_id bigint,
  print_size_name text,
  pricing_mode text NOT NULL,
  print_description text,
  print_line_count integer NOT NULL DEFAULT 1,
  print_charge numeric(12,2) NOT NULL DEFAULT 0,
  sort_order integer NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS quotation_lines_custom_item_idx ON quotation_lines(custom_item_id) WHERE custom_item_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS invoice_lines_custom_item_idx ON invoice_lines(custom_item_id) WHERE custom_item_id IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS custom_orders_source_quotation_key ON custom_orders(source_quotation_id) WHERE source_quotation_id IS NOT NULL;

DO $$ BEGIN
  ALTER TABLE quotations ADD CONSTRAINT quotations_linked_custom_order_fk FOREIGN KEY(linked_custom_order_id) REFERENCES custom_orders(custom_order_id);
EXCEPTION WHEN duplicate_object THEN NULL; END $$;
DO $$ BEGIN
  ALTER TABLE invoices ADD CONSTRAINT invoices_linked_custom_order_fk FOREIGN KEY(linked_custom_order_id) REFERENCES custom_orders(custom_order_id);
EXCEPTION WHEN duplicate_object THEN NULL; END $$;
DO $$ BEGIN
  ALTER TABLE custom_orders ADD CONSTRAINT custom_orders_source_quotation_fk FOREIGN KEY(source_quotation_id) REFERENCES quotations(quotation_id);
EXCEPTION WHEN duplicate_object THEN NULL; END $$;
DO $$ BEGIN
  ALTER TABLE custom_orders ADD CONSTRAINT custom_orders_source_invoice_fk FOREIGN KEY(source_invoice_id) REFERENCES invoices(invoice_id);
EXCEPTION WHEN duplicate_object THEN NULL; END $$;
DO $$ BEGIN ALTER TABLE quotation_lines ADD CONSTRAINT quotation_lines_custom_item_fk FOREIGN KEY(custom_item_id) REFERENCES custom_order_items(custom_item_id); EXCEPTION WHEN duplicate_object THEN NULL; END $$;
DO $$ BEGIN ALTER TABLE quotation_lines ADD CONSTRAINT quotation_lines_custom_variant_fk FOREIGN KEY(custom_variant_id) REFERENCES custom_order_item_variants(custom_variant_id); EXCEPTION WHEN duplicate_object THEN NULL; END $$;
DO $$ BEGIN ALTER TABLE invoice_lines ADD CONSTRAINT invoice_lines_custom_item_fk FOREIGN KEY(custom_item_id) REFERENCES custom_order_items(custom_item_id); EXCEPTION WHEN duplicate_object THEN NULL; END $$;
DO $$ BEGIN ALTER TABLE invoice_lines ADD CONSTRAINT invoice_lines_custom_variant_fk FOREIGN KEY(custom_variant_id) REFERENCES custom_order_item_variants(custom_variant_id); EXCEPTION WHEN duplicate_object THEN NULL; END $$;
