-- Product aliases are shared by catalog UUID; quantities are consolidated only
-- inside the receiving store's own PostgreSQL transaction, never from a cache.
ALTER TABLE public.sale_items ADD COLUMN IF NOT EXISTS merge_cost_price_snapshot numeric(12,2);
CREATE TABLE IF NOT EXISTS public.product_merges (
  merge_id uuid NOT NULL UNIQUE,
  source_uid uuid PRIMARY KEY,
  target_uid uuid NOT NULL,
  origin_location_id integer NOT NULL,
  merged_by_user_id integer NOT NULL,
  retained_fields jsonb NOT NULL DEFAULT '{}'::jsonb,
  updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CHECK(source_uid<>target_uid)
);
CREATE INDEX IF NOT EXISTS product_merges_target_idx ON public.product_merges(target_uid);
CREATE TABLE IF NOT EXISTS public.product_stock_commands (
 command_id uuid PRIMARY KEY, origin_location_id integer NOT NULL, location_id integer NOT NULL,
 product_uid uuid NOT NULL, expected_quantity integer NOT NULL,
 new_quantity integer NOT NULL CHECK(new_quantity>=0), requested_by_user_id integer NOT NULL,
 device_id uuid NOT NULL, updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS product_stock_commands_owner_idx ON public.product_stock_commands(location_id,updated_at);
CREATE TABLE IF NOT EXISTS public.product_stock_command_results (
 command_id uuid PRIMARY KEY, location_id integer NOT NULL,
 state text NOT NULL CHECK(state IN ('APPLIED','CONFLICT')),
 previous_quantity integer NOT NULL, requested_quantity integer NOT NULL,
 updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS public.product_merge_applications (
  merge_id uuid NOT NULL,
  location_id integer NOT NULL,
  source_product_id integer NOT NULL,
  target_product_id integer NOT NULL,
  quantity_moved integer NOT NULL,
  before_state jsonb NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY(merge_id,location_id)
);
CREATE INDEX IF NOT EXISTS product_merge_applications_owner_idx ON public.product_merge_applications(location_id,updated_at);

CREATE OR REPLACE FUNCTION public.canonical_product_uid(value uuid) RETURNS uuid
LANGUAGE plpgsql STABLE AS $$
DECLARE next_uid uuid; seen uuid[] := ARRAY[]::uuid[]; current_uid uuid := value;
BEGIN
  LOOP
    IF current_uid=ANY(seen) THEN RAISE EXCEPTION 'Product merge cycle.' USING ERRCODE='55000'; END IF;
    seen:=array_append(seen,current_uid);
    SELECT target_uid INTO next_uid FROM public.product_merges WHERE source_uid=current_uid;
    IF NOT FOUND THEN RETURN current_uid; END IF;
    current_uid:=next_uid;
  END LOOP;
END
$$;
CREATE OR REPLACE FUNCTION public.canonical_product_id(value integer) RETURNS integer
LANGUAGE sql STABLE AS $$
  SELECT COALESCE((SELECT dest.product_id FROM public.products src JOIN public.products dest
    ON dest.catalog_uid=public.canonical_product_uid(src.catalog_uid) WHERE src.product_id=value),value)
$$;

-- Exclude raw remote caches (their IDs belong to their source store), retained
-- photo evidence, source tombstones, and tables with product-key collisions.
CREATE OR REPLACE FUNCTION public.product_merge_reference_columns()
RETURNS TABLE(schema_name text,table_name text,column_name text)
LANGUAGE sql STABLE AS $$
 SELECT DISTINCT n.nspname::text,t.relname::text,a.attname::text
 FROM pg_class t JOIN pg_namespace n ON n.oid=t.relnamespace
 JOIN pg_attribute a ON a.attrelid=t.oid AND a.attnum>0 AND NOT a.attisdropped
 WHERE t.relkind IN ('r','p') AND n.nspname IN ('public','storefront')
 AND t.relname NOT LIKE 'sync\_%' ESCAPE '\'
 AND t.relname NOT IN ('products','product_merges','product_merge_applications','inventory',
   'product_barcodes','product_shelf_assignments','product_lifecycle_audit','catalog_studio_reviews',
   'catalog_studio_image_reviews','product_image_coverage','reservations','favorites')
 AND NOT (n.nspname='storefront' AND t.relname='products')
 AND a.atttypid IN ('int4'::regtype,'int8'::regtype)
 AND (a.attname IN ('product_id','inventory_product_id') OR EXISTS(
   SELECT 1 FROM pg_constraint fk WHERE fk.contype='f' AND fk.conrelid=t.oid
   AND fk.confrelid='public.products'::regclass AND array_length(fk.conkey,1)=1 AND fk.conkey[1]=a.attnum))
$$;

CREATE OR REPLACE FUNCTION public.redirect_merged_product_reference() RETURNS trigger
LANGUAGE plpgsql AS $$
DECLARE old_id integer; replacement integer;
BEGIN
 old_id:=(to_jsonb(NEW)->>TG_ARGV[0])::integer;
 IF old_id IS NULL THEN RETURN NEW; END IF;
 replacement:=public.canonical_product_id(old_id);
 IF replacement<>old_id THEN NEW:=jsonb_populate_record(NEW,jsonb_build_object(TG_ARGV[0],replacement)); END IF;
 RETURN NEW;
END
$$;
CREATE OR REPLACE FUNCTION public.guard_merged_product_stock() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
 IF public.canonical_product_id(NEW.product_id)<>NEW.product_id AND NEW.quantity_on_hand<>0 THEN
   RAISE EXCEPTION 'This product was merged. Refresh inventory before changing stock.' USING ERRCODE='40001';
 END IF;
 RETURN NEW;
END
$$;
DROP TRIGGER IF EXISTS product_merge_stock_guard ON public.inventory;
CREATE TRIGGER product_merge_stock_guard BEFORE INSERT OR UPDATE ON public.inventory
FOR EACH ROW EXECUTE FUNCTION public.guard_merged_product_stock();
DROP TRIGGER IF EXISTS product_merge_barcode_guard ON public.product_barcodes;
CREATE TRIGGER product_merge_barcode_guard BEFORE INSERT OR UPDATE OF product_id ON public.product_barcodes
FOR EACH ROW EXECUTE FUNCTION public.redirect_merged_product_reference('product_id');

CREATE OR REPLACE FUNCTION public.keep_merged_product_retired() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
 IF TG_OP='DELETE' THEN
   IF EXISTS(SELECT 1 FROM public.product_merges WHERE source_uid=OLD.catalog_uid OR target_uid=OLD.catalog_uid) THEN
     RAISE EXCEPTION 'Merged product identities cannot be deleted.' USING ERRCODE='55000';
   END IF;
   RETURN OLD;
 END IF;
 IF TG_OP='UPDATE' AND (NEW.product_id IS DISTINCT FROM OLD.product_id OR NEW.catalog_uid IS DISTINCT FROM OLD.catalog_uid)
   AND EXISTS(SELECT 1 FROM public.product_merges WHERE source_uid=OLD.catalog_uid OR target_uid=OLD.catalog_uid) THEN
   RAISE EXCEPTION 'Merged product identities cannot be changed.' USING ERRCODE='55000';
 END IF;
 IF EXISTS(SELECT 1 FROM public.product_merges WHERE source_uid=NEW.catalog_uid) THEN
   NEW.is_active:=FALSE; NEW.barcode:=NULL;
   NEW.archive_reason:='Merged into product #'||public.canonical_product_id(NEW.product_id);
   NEW.archived_at:=COALESCE(NEW.archived_at,CURRENT_TIMESTAMP);
 END IF;
 RETURN NEW;
END
$$;
DROP TRIGGER IF EXISTS product_merge_retired_guard ON public.products;
CREATE TRIGGER product_merge_retired_guard BEFORE INSERT OR UPDATE OR DELETE ON public.products
FOR EACH ROW EXECUTE FUNCTION public.keep_merged_product_retired();

CREATE OR REPLACE FUNCTION public.canonical_product_lines(value jsonb,key_name text) RETURNS jsonb
LANGUAGE plpgsql STABLE AS $$
DECLARE line jsonb; result jsonb := '[]'; raw text;
BEGIN
 IF jsonb_typeof(value->'lines') IS DISTINCT FROM 'array' THEN RETURN value; END IF;
 FOR line IN SELECT * FROM jsonb_array_elements(value->'lines') LOOP
   raw:=line->>key_name;
   IF raw ~ '^[0-9]{1,9}$' THEN
     line:=line||jsonb_build_object(key_name,public.canonical_product_id(raw::integer));
   END IF;
   result:=result||jsonb_build_array(line);
 END LOOP;
 RETURN jsonb_set(value,'{lines}',result);
END
$$;
CREATE OR REPLACE FUNCTION public.redirect_merged_sales_task() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN NEW.checkout_request:=public.canonical_product_lines(NEW.checkout_request,'productId'); RETURN NEW; END
$$;
DROP TRIGGER IF EXISTS product_merge_task_guard ON public.sales_tasks;
CREATE TRIGGER product_merge_task_guard BEFORE INSERT OR UPDATE OF checkout_request ON public.sales_tasks
FOR EACH ROW EXECUTE FUNCTION public.redirect_merged_sales_task();
CREATE OR REPLACE FUNCTION public.redirect_merged_stock_ledger() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
 IF NEW.item_type='PRODUCT' THEN NEW.item_id:=public.canonical_product_id(NEW.item_id); END IF;
 RETURN NEW;
END
$$;
DROP TRIGGER IF EXISTS product_merge_ledger_guard ON public.stock_ledger_entries;
CREATE TRIGGER product_merge_ledger_guard BEFORE INSERT OR UPDATE OF item_id ON public.stock_ledger_entries
FOR EACH ROW EXECUTE FUNCTION public.redirect_merged_stock_ledger();

CREATE OR REPLACE FUNCTION public.install_product_merge_reference_guards() RETURNS void
LANGUAGE plpgsql AS $$
DECLARE ref record;
BEGIN
 FOR ref IN SELECT * FROM public.product_merge_reference_columns() WHERE schema_name='public' LOOP
   EXECUTE format('DROP TRIGGER IF EXISTS %I ON %I.%I','product_merge_'||ref.column_name,ref.schema_name,ref.table_name);
   EXECUTE format('CREATE TRIGGER %I BEFORE INSERT OR UPDATE OF %I ON %I.%I FOR EACH ROW EXECUTE FUNCTION public.redirect_merged_product_reference(%L)',
     'product_merge_'||ref.column_name,ref.column_name,ref.schema_name,ref.table_name,ref.column_name);
 END LOOP;
END
$$;
SELECT public.install_product_merge_reference_guards();

CREATE OR REPLACE FUNCTION public.preserve_product_merge_mapping() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
 IF TG_OP='DELETE' OR NEW IS DISTINCT FROM OLD THEN
   RAISE EXCEPTION 'Product merge mappings are immutable.' USING ERRCODE='55000';
 END IF;
 RETURN NEW;
END
$$;
DROP TRIGGER IF EXISTS product_merge_mapping_immutable ON public.product_merges;
CREATE TRIGGER product_merge_mapping_immutable BEFORE UPDATE OR DELETE ON public.product_merges
FOR EACH ROW EXECUTE FUNCTION public.preserve_product_merge_mapping();

-- Consolidation itself is performed by the server service in the same transaction
-- as INSERT. Replicas use the same service; a received mapping cannot be marked
-- applied until local stock, references, barcodes and the receipt commit together.
