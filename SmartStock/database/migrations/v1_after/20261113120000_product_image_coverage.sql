CREATE TABLE IF NOT EXISTS public.product_image_coverage (
    product_id bigint NOT NULL REFERENCES public.products(product_id) ON DELETE CASCADE,
    image_kind text NOT NULL CHECK (image_kind IN ('ad_square','ad_portrait','ad_story','ad_landscape')),
    image_reference text NOT NULL,
    pixel_width integer NOT NULL CHECK (pixel_width > 0),
    pixel_height integer NOT NULL CHECK (pixel_height > 0),
    approved_by_user_id integer,
    approved_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (product_id, image_kind)
);
ALTER TABLE public.product_image_coverage ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON public.product_image_coverage FROM PUBLIC;
DO $$ BEGIN
 IF EXISTS(SELECT 1 FROM pg_roles WHERE rolname='anon') THEN
  REVOKE ALL ON public.product_image_coverage FROM anon;
 END IF;
 IF EXISTS(SELECT 1 FROM pg_roles WHERE rolname='authenticated') THEN
  REVOKE ALL ON public.product_image_coverage FROM authenticated;
 END IF;
 IF EXISTS(SELECT 1 FROM pg_roles WHERE rolname='service_role') THEN
  GRANT ALL ON public.product_image_coverage TO service_role;
 END IF;
END $$;
