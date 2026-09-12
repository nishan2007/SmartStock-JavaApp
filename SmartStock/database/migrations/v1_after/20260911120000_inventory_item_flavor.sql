ALTER TABLE public.products ADD COLUMN IF NOT EXISTS flavor text NOT NULL DEFAULT '' CHECK (length(flavor) <= 200);
-- Preserve the reviewed Flavor option on existing variants without replacing an existing value.
UPDATE public.products p SET flavor=opt.value,updated_at=CURRENT_TIMESTAMP
FROM public.products source,
LATERAL jsonb_each_text(source.variant_options) opt
WHERE p.product_id=source.product_id AND source.group_id IS NOT NULL
  AND lower(opt.key)='flavor' AND p.flavor='';
