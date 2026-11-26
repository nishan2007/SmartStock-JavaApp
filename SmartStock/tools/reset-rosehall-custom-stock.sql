-- Run only through reset-rosehall-custom-stock.ps1 on the Rosehall server.
\set ON_ERROR_STOP on
BEGIN;
SET LOCAL lock_timeout='5s';
SET LOCAL statement_timeout='60s';
SELECT pg_advisory_xact_lock(728493,1613);
LOCK TABLE public.custom_order_items,public.custom_order_item_variants,
 public.custom_item_inventory,public.custom_variant_inventory IN SHARE ROW EXCLUSIVE MODE;
DO $guard$
BEGIN
 IF NOT EXISTS(SELECT 1 FROM public.locations WHERE location_id=2 AND lower(name)='rosehall')
 OR NOT EXISTS(SELECT 1 FROM public.custom_inventory_legacy_migration WHERE location_id=2)
 THEN RAISE EXCEPTION 'This is not the migrated Rosehall database; no changes made.'; END IF;
 IF EXISTS(SELECT 1 FROM public.custom_order_item_movements WHERE note='rosehall-custom-stock-reset-20261124')
 THEN RAISE EXCEPTION 'This reset was already applied; refusing to erase subsequent stock.'; END IF;
END $guard$;
-- Written before any update, while stock is locked. Failure to save aborts the reset.
\copy (SELECT jsonb_build_object('operation','rosehall-custom-stock-reset-20261124','captured_at',now(),'database',current_database(),'items',COALESCE((SELECT jsonb_agg(to_jsonb(s)) FROM public.custom_item_inventory s WHERE location_id=2),'[]'::jsonb),'variants',COALESCE((SELECT jsonb_agg(to_jsonb(s)) FROM public.custom_variant_inventory s WHERE location_id=2),'[]'::jsonb))) TO '__EVIDENCE_FILE__' WITH (FORMAT csv)
INSERT INTO public.custom_order_item_movements(custom_item_id,location_id,change_qty,reason,note,user_name)
 SELECT custom_item_id,2,-quantity_on_hand,'STOCK_COUNT','rosehall-custom-stock-reset-20261124',current_user
 FROM public.custom_item_inventory WHERE location_id=2 AND quantity_on_hand<>0;
INSERT INTO public.custom_order_item_movements(custom_item_id,custom_variant_id,variant_name,location_id,change_qty,reason,note,user_name)
 SELECT v.custom_item_id,s.custom_variant_id,v.variant_name,2,-s.quantity_on_hand,'STOCK_COUNT','rosehall-custom-stock-reset-20261124',current_user
 FROM public.custom_variant_inventory s JOIN public.custom_order_item_variants v USING(custom_variant_id)
 WHERE s.location_id=2 AND s.quantity_on_hand<>0;
UPDATE public.custom_item_inventory SET quantity_on_hand=0,updated_at=now() WHERE location_id=2 AND quantity_on_hand<>0;
UPDATE public.custom_variant_inventory SET quantity_on_hand=0,updated_at=now() WHERE location_id=2 AND quantity_on_hand<>0;
DO $verify$
BEGIN
 IF EXISTS(SELECT 1 FROM public.custom_item_inventory WHERE location_id=2 AND quantity_on_hand<>0)
 OR EXISTS(SELECT 1 FROM public.custom_variant_inventory WHERE location_id=2 AND quantity_on_hand<>0)
 THEN RAISE EXCEPTION 'Rosehall stock verification failed; rolling back.'; END IF;
END $verify$;
COMMIT;
SELECT 'CUSTOM_ITEM' AS stock_type,count(*) AS balances,count(*) FILTER(WHERE quantity_on_hand<>0) AS nonzero
 FROM public.custom_item_inventory WHERE location_id=2
UNION ALL SELECT 'CUSTOM_VARIANT',count(*),count(*) FILTER(WHERE quantity_on_hand<>0)
 FROM public.custom_variant_inventory WHERE location_id=2;
