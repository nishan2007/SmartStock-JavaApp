ALTER TABLE public.custom_order_print_size_presets ADD COLUMN IF NOT EXISTS guide_width_in numeric(8,3);
ALTER TABLE public.custom_order_print_size_presets ADD COLUMN IF NOT EXISTS guide_height_in numeric(8,3);
UPDATE public.custom_order_print_size_presets p SET guide_width_in=(m.parts)[2]::numeric,guide_height_in=(m.parts)[1]::numeric
FROM (SELECT print_size_preset_id,regexp_match(preset_name,'([0-9]+(?:[.][0-9]+)?)\s*(?:in(?:ches)?|["”])?\s*[x×]\s*([0-9]+(?:[.][0-9]+)?)','i') parts FROM public.custom_order_print_size_presets) m
WHERE p.print_size_preset_id=m.print_size_preset_id AND p.guide_width_in IS NULL AND p.guide_height_in IS NULL AND m.parts IS NOT NULL AND (m.parts)[1]::numeric BETWEEN .001 AND 60 AND (m.parts)[2]::numeric BETWEEN .001 AND 60;
UPDATE public.custom_order_print_size_presets SET guide_width_in=4,guide_height_in=4 WHERE guide_width_in IS NULL AND guide_height_in IS NULL AND preset_name ~* '\mlogo\M';
DO $$ BEGIN
 IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid='public.custom_order_print_size_presets'::regclass AND conname='custom_order_print_preset_guides_chk') THEN
 ALTER TABLE public.custom_order_print_size_presets ADD CONSTRAINT custom_order_print_preset_guides_chk CHECK ((guide_width_in IS NULL AND guide_height_in IS NULL) OR (guide_width_in IS NOT NULL AND guide_height_in IS NOT NULL AND guide_width_in > 0 AND guide_width_in <= 60 AND guide_height_in > 0 AND guide_height_in <= 60));
 END IF;
END $$;
