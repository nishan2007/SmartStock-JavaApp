-- Store-owned custom stock. Catalog quantities are retained only as legacy migration evidence.
CREATE TABLE IF NOT EXISTS public.custom_item_inventory (
 custom_item_id bigint NOT NULL REFERENCES public.custom_order_items(custom_item_id) ON DELETE CASCADE,
 location_id integer NOT NULL REFERENCES public.locations(location_id),
 quantity_on_hand numeric NOT NULL DEFAULT 0,
 reorder_level numeric NOT NULL DEFAULT 0 CHECK(reorder_level>=0),
 sold_quantity numeric NOT NULL DEFAULT 0,
 updated_at timestamptz NOT NULL DEFAULT now(),
 PRIMARY KEY(custom_item_id,location_id)
);
CREATE TABLE IF NOT EXISTS public.custom_variant_inventory (
 custom_variant_id bigint NOT NULL REFERENCES public.custom_order_item_variants(custom_variant_id) ON DELETE CASCADE,
 location_id integer NOT NULL REFERENCES public.locations(location_id),
 quantity_on_hand numeric NOT NULL DEFAULT 0,
 reorder_level numeric NOT NULL DEFAULT 0 CHECK(reorder_level>=0),
 sold_quantity numeric NOT NULL DEFAULT 0,
 updated_at timestamptz NOT NULL DEFAULT now(),
 PRIMARY KEY(custom_variant_id,location_id)
);
CREATE INDEX IF NOT EXISTS custom_item_inventory_location_idx ON public.custom_item_inventory(location_id,quantity_on_hand,reorder_level);
CREATE INDEX IF NOT EXISTS custom_variant_inventory_location_idx ON public.custom_variant_inventory(location_id,quantity_on_hand,reorder_level);
CREATE TABLE IF NOT EXISTS public.custom_inventory_legacy_migration (
 singleton boolean PRIMARY KEY DEFAULT true CHECK(singleton),
 location_id integer NOT NULL REFERENCES public.locations(location_id),
 migrated_at timestamptz NOT NULL DEFAULT now()
);
REVOKE ALL ON public.custom_item_inventory,public.custom_variant_inventory,public.custom_inventory_legacy_migration FROM PUBLIC;

-- Indexed store-scoped reads, including active variant totals for parent rows.
CREATE OR REPLACE FUNCTION public.custom_stock_value(item bigint,variant bigint,store integer,field text)
RETURNS numeric LANGUAGE sql STABLE SET search_path=public AS $stock$
 SELECT CASE WHEN variant IS NOT NULL THEN
  COALESCE((SELECT CASE field WHEN 'quantity_on_hand' THEN s.quantity_on_hand WHEN 'reorder_level' THEN s.reorder_level WHEN 'sold_quantity' THEN s.sold_quantity END FROM custom_variant_inventory s WHERE s.custom_variant_id=variant AND s.location_id=store),0)
 WHEN COALESCE((SELECT has_variants FROM custom_order_items WHERE custom_item_id=item),false) THEN
  COALESCE((SELECT SUM(CASE field WHEN 'quantity_on_hand' THEN s.quantity_on_hand WHEN 'reorder_level' THEN s.reorder_level WHEN 'sold_quantity' THEN s.sold_quantity END) FROM custom_order_item_variants v JOIN custom_variant_inventory s ON s.custom_variant_id=v.custom_variant_id AND s.location_id=store WHERE v.custom_item_id=item AND v.is_active),0)
 ELSE COALESCE((SELECT CASE field WHEN 'quantity_on_hand' THEN s.quantity_on_hand WHEN 'reorder_level' THEN s.reorder_level WHEN 'sold_quantity' THEN s.sold_quantity END FROM custom_item_inventory s WHERE s.custom_item_id=item AND s.location_id=store),0) END
$stock$;
