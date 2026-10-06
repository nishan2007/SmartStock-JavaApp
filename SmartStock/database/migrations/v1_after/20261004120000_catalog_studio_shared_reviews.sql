CREATE TABLE IF NOT EXISTS public.catalog_studio_reviews (
    product_id bigint PRIMARY KEY,
    name text NOT NULL,
    source_image_url text,
    source_sha256 varchar(64),
    decision varchar(20) NOT NULL DEFAULT 'review',
    reference text,
    error text,
    generator text,
    generated_for_review boolean NOT NULL DEFAULT false,
    original_bytes bytea,
    preview_jpeg bytea,
    revision bigint NOT NULL DEFAULT 1,
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT catalog_studio_reviews_decision_check
        CHECK (decision IN ('review','approved','rejected','imported','failed','import_failed'))
);
ALTER TABLE public.catalog_studio_reviews ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON public.catalog_studio_reviews FROM PUBLIC;
DO $$ BEGIN
 IF EXISTS(SELECT 1 FROM pg_roles WHERE rolname='anon') THEN
  REVOKE ALL ON public.catalog_studio_reviews FROM anon;
 END IF;
 IF EXISTS(SELECT 1 FROM pg_roles WHERE rolname='authenticated') THEN
  REVOKE ALL ON public.catalog_studio_reviews FROM authenticated;
 END IF;
 IF EXISTS(SELECT 1 FROM pg_roles WHERE rolname='service_role') THEN
  GRANT ALL ON public.catalog_studio_reviews TO service_role;
 END IF;
END $$;
