-- Historical receiving corrections only: never apply another inventory deduction.
ALTER TABLE public.stock_ledger_entries ADD COLUMN IF NOT EXISTS source_movement_type text;
ALTER TABLE public.stock_ledger_entries ADD COLUMN IF NOT EXISTS source_movement_id bigint;
ALTER TABLE public.stock_ledger_entries ADD COLUMN IF NOT EXISTS valuation_source text NOT NULL DEFAULT 'POSTED_SNAPSHOT';
ALTER TABLE public.stock_ledger_entries ALTER COLUMN user_id DROP NOT NULL;
ALTER TABLE public.stock_ledger_entries ALTER COLUMN device_id DROP NOT NULL;
ALTER TABLE public.stock_ledger_entries ALTER COLUMN system_quantity DROP NOT NULL;
ALTER TABLE public.stock_ledger_entries ALTER COLUMN counted_quantity DROP NOT NULL;
ALTER TABLE public.stock_ledger_entries ALTER COLUMN selling_price DROP NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS stock_ledger_source_movement_idx
    ON public.stock_ledger_entries(source_movement_type,source_movement_id);
WITH movements AS (
    SELECT 'PRODUCT_MOVEMENT' source_type, m.movement_id, 'PRODUCT' item_type,
           m.product_id item_id,m.location_id,m.change_qty::numeric change_qty,m.note,m.user_id,m.user_name,
           m.device_id,m.receive_id,m.created_at,p.name item_name,p.price selling_price,p.cost_price unit_cost
    FROM public.inventory_movements m LEFT JOIN public.products p ON p.product_id=m.product_id
    WHERE m.reason='RECEIVING_STOCK_OVERRIDE' AND m.change_qty<0
    UNION ALL
    SELECT 'CUSTOM_MOVEMENT',m.movement_id,
           CASE WHEN m.custom_variant_id IS NULL THEN 'CUSTOM_ITEM' ELSE 'CUSTOM_VARIANT' END,
           COALESCE(m.custom_variant_id,m.custom_item_id)::integer,m.location_id,m.change_qty,m.note,m.user_id,m.user_name,
           m.device_id,m.receive_id,m.created_at,
           CONCAT_WS(' - ',p.item_name,COALESCE(m.variant_name,v.variant_name)),
           CASE WHEN p.custom_item_id IS NULL THEN NULL ELSE COALESCE(v.fixed_price,p.fixed_price,p.area_price,0) END,NULL::numeric
    FROM public.custom_order_item_movements m
    LEFT JOIN public.custom_order_items p ON p.custom_item_id=m.custom_item_id
    LEFT JOIN public.custom_order_item_variants v ON v.custom_variant_id=m.custom_variant_id
    WHERE m.reason='RECEIVING_STOCK_OVERRIDE' AND m.change_qty<0
), candidates AS (
    SELECT m.*,
        substring(m.note from 'system_stock=(-?[0-9]+)(?:;|$)')::integer old_system,
        substring(m.note from 'counted_stock=(-?[0-9]+)(?:;|$)')::integer old_counted
    FROM movements m
    WHERE m.location_id IS NOT NULL AND m.change_qty=trunc(m.change_qty)
      AND -m.change_qty<=2147483647
      AND NOT EXISTS (
          SELECT 1 FROM public.stock_ledger_entries e
          WHERE e.ledger_type='LOSS' AND e.location_id=m.location_id
            AND e.item_type=m.item_type AND e.item_id=m.item_id AND e.reversal_of IS NULL
            AND e.source_movement_id IS NULL
            AND e.receive_id IS NOT NULL AND e.receive_id=m.receive_id
      )
)
INSERT INTO public.stock_ledger_entries(entry_id,location_id,ledger_type,item_type,item_id,item_name,
    quantity,system_quantity,counted_quantity,selling_price,unit_cost,category,description,reason,
    user_id,user_name,device_id,receive_id,created_at,source_movement_type,source_movement_id,valuation_source)
SELECT gen_random_uuid(),m.location_id,'LOSS',m.item_type,m.item_id,
    COALESCE(NULLIF(m.item_name,''),'Historical item #'||m.item_id),
    (-m.change_qty)::integer,m.old_system,m.old_counted,
    CASE WHEN m.selling_price>=0 THEN m.selling_price ELSE NULL END,
    CASE WHEN m.unit_cost>=0 THEN m.unit_cost ELSE NULL END,
    'Historical Shortage','Imported receiving correction; original prices unavailable. Source: '||m.source_type||' #'||m.movement_id,
    COALESCE(NULLIF(trim(m.note),''),'Historical receiving stock correction'),
    u.user_id,COALESCE(NULLIF(m.user_name,''),NULLIF(u.full_name,''),u.username,'Unknown historical employee'),
    CASE WHEN m.device_id ~* '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$' THEN m.device_id::uuid ELSE NULL END,
    m.receive_id,m.created_at,m.source_type,m.movement_id,
    CASE WHEN m.selling_price>=0 THEN 'CURRENT_PRICE_ESTIMATE' ELSE 'UNAVAILABLE' END
FROM candidates m JOIN public.locations l ON l.location_id=m.location_id
LEFT JOIN public.users u ON u.user_id=m.user_id
ON CONFLICT (source_movement_type,source_movement_id) DO NOTHING;
