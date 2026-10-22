ALTER TABLE public.custom_order_print_materials ADD COLUMN IF NOT EXISTS available_colors jsonb NOT NULL DEFAULT '{}'::jsonb CHECK (jsonb_typeof(available_colors) = 'object');
