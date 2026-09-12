ALTER TABLE public.products ADD COLUMN IF NOT EXISTS color text NOT NULL DEFAULT '' CHECK (length(color) <= 200);
-- Preserve the reviewed Color option on existing variants.
UPDATE public.products p SET color=opt.value,updated_at=CURRENT_TIMESTAMP
FROM public.products source,
LATERAL jsonb_each_text(source.variant_options) opt
WHERE p.product_id=source.product_id AND source.group_id IS NOT NULL
  AND lower(opt.key)='color' AND p.color='';
