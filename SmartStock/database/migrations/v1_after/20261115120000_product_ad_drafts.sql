-- Store-local review evidence. Never included in register reference snapshots.
CREATE TABLE IF NOT EXISTS public.product_ad_drafts (
 product_id bigint NOT NULL REFERENCES public.products(product_id) ON DELETE CASCADE,
 image_kind text NOT NULL CHECK (image_kind IN ('ad_square','ad_portrait','ad_story','ad_landscape')),
 preview_png bytea NOT NULL CHECK (octet_length(preview_png) BETWEEN 1 AND 12582912),
 decision text NOT NULL DEFAULT 'pending' CHECK (decision IN ('pending','approved','rejected')),
 revision bigint NOT NULL DEFAULT 1 CHECK (revision > 0),
 uploaded_by_user_id integer NOT NULL,
 reviewed_by_user_id integer,
 updated_at timestamptz NOT NULL DEFAULT now(),
 PRIMARY KEY(product_id,image_kind)
);
ALTER TABLE public.product_ad_drafts ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON public.product_ad_drafts FROM PUBLIC;
DO $$ BEGIN
 IF EXISTS(SELECT 1 FROM pg_roles WHERE rolname='anon') THEN
  REVOKE ALL ON public.product_ad_drafts FROM anon;
 END IF;
 IF EXISTS(SELECT 1 FROM pg_roles WHERE rolname='authenticated') THEN
  REVOKE ALL ON public.product_ad_drafts FROM authenticated;
 END IF;
 IF EXISTS(SELECT 1 FROM pg_roles WHERE rolname='service_role') THEN
  GRANT ALL ON public.product_ad_drafts TO service_role;
 END IF;
END $$;

-- Keep review evidence attached to its original product during a catalog merge.
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
   'catalog_studio_image_reviews','product_image_coverage','product_ad_drafts','reservations','favorites')
 AND NOT (n.nspname='storefront' AND t.relname='products')
 AND a.atttypid IN ('int4'::regtype,'int8'::regtype)
 AND (a.attname IN ('product_id','inventory_product_id') OR EXISTS(
   SELECT 1 FROM pg_constraint fk WHERE fk.contype='f' AND fk.conrelid=t.oid
   AND fk.confrelid='public.products'::regclass AND array_length(fk.conkey,1)=1 AND fk.conkey[1]=a.attnum))
$$;
