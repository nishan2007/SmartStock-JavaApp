-- Groups are catalog metadata. Only products own inventory and transactions.
CREATE TABLE IF NOT EXISTS public.product_groups (
    group_id uuid PRIMARY KEY,
    name text NOT NULL CHECK (length(btrim(name)) BETWEEN 1 AND 200),
    option_names jsonb NOT NULL CHECK (jsonb_typeof(option_names) = 'array'),
    revision bigint NOT NULL DEFAULT 1,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP
);
ALTER TABLE public.products ADD COLUMN IF NOT EXISTS group_id uuid REFERENCES public.product_groups(group_id);
ALTER TABLE public.products ADD COLUMN IF NOT EXISTS variant_options jsonb NOT NULL DEFAULT '{}'::jsonb;
CREATE INDEX IF NOT EXISTS products_group_idx ON public.products(group_id, product_id);
CREATE UNIQUE INDEX IF NOT EXISTS products_group_options_unique ON public.products(group_id, lower(variant_options::text)) WHERE group_id IS NOT NULL;
