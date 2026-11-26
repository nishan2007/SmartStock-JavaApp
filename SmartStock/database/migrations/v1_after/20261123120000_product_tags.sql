-- Store-owned reusable tags. Public visibility is projected only by StorefrontService.
CREATE TABLE IF NOT EXISTS public.product_tag_definitions (
    tag_id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    name text NOT NULL CHECK (length(btrim(name)) BETWEEN 1 AND 80),
    is_public boolean NOT NULL DEFAULT false,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX IF NOT EXISTS product_tag_definitions_name_key
    ON public.product_tag_definitions (lower(btrim(name)));
CREATE TABLE IF NOT EXISTS public.product_tags (
    product_id integer NOT NULL REFERENCES public.products(product_id) ON DELETE CASCADE,
    tag_id uuid NOT NULL REFERENCES public.product_tag_definitions(tag_id) ON DELETE CASCADE,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (product_id, tag_id)
);
CREATE INDEX IF NOT EXISTS product_tags_tag_product_idx ON public.product_tags(tag_id,product_id);
ALTER TABLE public.product_tag_definitions ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.product_tags ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON public.product_tag_definitions,public.product_tags FROM PUBLIC;
DO $$ BEGIN
 IF EXISTS(SELECT 1 FROM pg_roles WHERE rolname='anon') THEN
  REVOKE ALL ON public.product_tag_definitions,public.product_tags FROM anon;
 END IF;
 IF EXISTS(SELECT 1 FROM pg_roles WHERE rolname='authenticated') THEN
  REVOKE ALL ON public.product_tag_definitions,public.product_tags FROM authenticated;
 END IF;
 IF EXISTS(SELECT 1 FROM pg_roles WHERE rolname='service_role') THEN
  GRANT ALL ON public.product_tag_definitions,public.product_tags TO service_role;
 END IF;
END $$;
