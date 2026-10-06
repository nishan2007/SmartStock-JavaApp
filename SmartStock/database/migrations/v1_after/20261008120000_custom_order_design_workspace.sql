ALTER TABLE public.custom_order_items
 ADD COLUMN IF NOT EXISTS template_width numeric(12,3),
 ADD COLUMN IF NOT EXISTS template_height numeric(12,3),
 ADD COLUMN IF NOT EXISTS template_unit text NOT NULL DEFAULT 'IN',
 ADD COLUMN IF NOT EXISTS template_uses_order_area boolean NOT NULL DEFAULT false;
DO $$ BEGIN
 IF NOT EXISTS(SELECT 1 FROM pg_constraint WHERE conname='custom_order_items_template_dimensions_chk'
   AND conrelid='public.custom_order_items'::regclass) THEN
  ALTER TABLE public.custom_order_items ADD CONSTRAINT custom_order_items_template_dimensions_chk
   CHECK ((template_width IS NULL AND template_height IS NULL) OR
          (template_width > 0 AND template_height > 0 AND template_width <= 10000 AND template_height <= 10000));
 END IF;
 IF NOT EXISTS(SELECT 1 FROM pg_constraint WHERE conname='custom_order_items_template_unit_chk'
   AND conrelid='public.custom_order_items'::regclass) THEN
  ALTER TABLE public.custom_order_items ADD CONSTRAINT custom_order_items_template_unit_chk
   CHECK (template_unit IN ('IN','FT','YD','CM','M'));
 END IF;
END $$;

ALTER TABLE public.custom_order_item_variants
 ADD COLUMN IF NOT EXISTS template_width numeric(12,3),
 ADD COLUMN IF NOT EXISTS template_height numeric(12,3),
 ADD COLUMN IF NOT EXISTS template_unit text,
 ADD COLUMN IF NOT EXISTS template_uses_order_area boolean;
DO $$ BEGIN
 IF NOT EXISTS(SELECT 1 FROM pg_constraint WHERE conname='custom_order_item_variants_template_dimensions_chk'
   AND conrelid='public.custom_order_item_variants'::regclass) THEN
  ALTER TABLE public.custom_order_item_variants ADD CONSTRAINT custom_order_item_variants_template_dimensions_chk
   CHECK ((template_width IS NULL AND template_height IS NULL AND template_unit IS NULL) OR
          (template_width > 0 AND template_height > 0 AND template_width <= 10000 AND template_height <= 10000
           AND template_unit IN ('IN','FT','YD','CM','M')));
 END IF;
END $$;

ALTER TABLE public.custom_order_lines
 ADD COLUMN IF NOT EXISTS template_width numeric(12,3),
 ADD COLUMN IF NOT EXISTS template_height numeric(12,3),
 ADD COLUMN IF NOT EXISTS template_unit text;
ALTER TABLE public.custom_order_design_proofs
 ADD COLUMN IF NOT EXISTS document_revision integer;

CREATE TABLE IF NOT EXISTS public.custom_order_design_documents (
 document_id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
 custom_order_id bigint NOT NULL REFERENCES public.custom_orders(custom_order_id),
 custom_order_line_id bigint REFERENCES public.custom_order_lines(custom_order_line_id),
 width numeric(12,3) NOT NULL CHECK(width > 0 AND width <= 10000),
 height numeric(12,3) NOT NULL CHECK(height > 0 AND height <= 10000),
 unit text NOT NULL CHECK(unit IN ('IN','FT','YD','CM','M')),
 revision integer NOT NULL DEFAULT 0 CHECK(revision >= 0),
 document_json jsonb NOT NULL DEFAULT '{"version":1,"background":"#ffffff","objects":[]}'::jsonb,
 updated_by_user_id integer,
 updated_at timestamptz NOT NULL DEFAULT now(),
 created_at timestamptz NOT NULL DEFAULT now(),
 UNIQUE(custom_order_line_id)
);
CREATE UNIQUE INDEX IF NOT EXISTS custom_order_design_documents_overview
 ON public.custom_order_design_documents(custom_order_id) WHERE custom_order_line_id IS NULL;
CREATE TABLE IF NOT EXISTS public.custom_order_design_document_revisions (
 document_id uuid NOT NULL REFERENCES public.custom_order_design_documents(document_id),
 revision integer NOT NULL,
 document_json jsonb NOT NULL,
 saved_by_user_id integer,
 saved_at timestamptz NOT NULL DEFAULT now(),
 PRIMARY KEY(document_id,revision)
);
ALTER TABLE public.custom_order_design_documents ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.custom_order_design_document_revisions ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON public.custom_order_design_documents,public.custom_order_design_document_revisions FROM PUBLIC;
DO $$ BEGIN
 IF EXISTS(SELECT 1 FROM pg_roles WHERE rolname='anon') THEN
  REVOKE ALL ON public.custom_order_design_documents,public.custom_order_design_document_revisions FROM anon;
 END IF;
 IF EXISTS(SELECT 1 FROM pg_roles WHERE rolname='authenticated') THEN
  REVOKE ALL ON public.custom_order_design_documents,public.custom_order_design_document_revisions FROM authenticated;
 END IF;
END $$;
INSERT INTO public.custom_order_design_documents(custom_order_id,width,height,unit)
 SELECT custom_order_id,16,9,'IN' FROM public.custom_orders ON CONFLICT DO NOTHING;
UPDATE public.custom_order_lines SET template_width=COALESCE(template_width,8.5),
 template_height=COALESCE(template_height,11),template_unit=COALESCE(template_unit,'IN')
 WHERE template_width IS NULL;
INSERT INTO public.custom_order_design_documents(custom_order_id,custom_order_line_id,width,height,unit)
 SELECT custom_order_id,custom_order_line_id,template_width,template_height,template_unit
 FROM public.custom_order_lines ON CONFLICT DO NOTHING;
