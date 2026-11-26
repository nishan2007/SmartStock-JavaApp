-- Invoked only for production server location 2, inside its startup transaction.
-- Exact legacy migration identifies the existing, owner-authorized Rosehall stock.
DO $repair$
DECLARE marker public.custom_inventory_legacy_migration%ROWTYPE;
BEGIN
 PERFORM pg_advisory_xact_lock(728493,1613);
 SELECT * INTO marker FROM public.custom_inventory_legacy_migration WHERE singleton FOR UPDATE;
 IF NOT FOUND OR marker.location_id<>2 OR marker.rosehall_stock_corrected_at IS NOT NULL
 OR marker.migrated_at<>TIMESTAMPTZ '2026-11-17 10:04:46.069939-04' THEN RETURN; END IF;
 IF NOT EXISTS(SELECT 1 FROM public.locations WHERE location_id=2 AND lower(btrim(name))='rosehall')
 THEN RAISE EXCEPTION 'Rosehall custom stock correction: store identity mismatch'; END IF;
 LOCK TABLE public.custom_order_items,public.custom_order_item_variants,
 public.custom_item_inventory,public.custom_variant_inventory IN SHARE ROW EXCLUSIVE MODE;
 UPDATE public.custom_inventory_legacy_migration SET rosehall_stock_correction_before=
 jsonb_build_object('operation','rosehall-custom-stock-reset-20261124','captured_at',now(),
 'items',COALESCE((SELECT jsonb_agg(to_jsonb(s)) FROM public.custom_item_inventory s WHERE location_id=2),'[]'::jsonb),
 'variants',COALESCE((SELECT jsonb_agg(to_jsonb(s)) FROM public.custom_variant_inventory s WHERE location_id=2),'[]'::jsonb))
 WHERE singleton;
 -- Respect the previously supplied manual repair if it has already run.
 IF NOT EXISTS(SELECT 1 FROM public.custom_order_item_movements WHERE location_id=2 AND note='rosehall-custom-stock-reset-20261124') THEN
  INSERT INTO public.custom_order_item_movements(custom_item_id,location_id,change_qty,reason,note,user_name)
  SELECT custom_item_id,2,-quantity_on_hand,'STOCK_COUNT','rosehall-custom-stock-reset-20261124','Authorized server update'
  FROM public.custom_item_inventory WHERE location_id=2 AND quantity_on_hand<>0;
  INSERT INTO public.custom_order_item_movements(custom_item_id,custom_variant_id,variant_name,location_id,change_qty,reason,note,user_name)
  SELECT v.custom_item_id,s.custom_variant_id,v.variant_name,2,-s.quantity_on_hand,'STOCK_COUNT','rosehall-custom-stock-reset-20261124','Authorized server update'
  FROM public.custom_variant_inventory s JOIN public.custom_order_item_variants v USING(custom_variant_id)
  WHERE s.location_id=2 AND s.quantity_on_hand<>0;
  UPDATE public.custom_item_inventory SET quantity_on_hand=0,updated_at=now() WHERE location_id=2 AND quantity_on_hand<>0;
  UPDATE public.custom_variant_inventory SET quantity_on_hand=0,updated_at=now() WHERE location_id=2 AND quantity_on_hand<>0;
 END IF;
 UPDATE public.custom_inventory_legacy_migration SET rosehall_stock_corrected_at=now() WHERE singleton;
END $repair$;
