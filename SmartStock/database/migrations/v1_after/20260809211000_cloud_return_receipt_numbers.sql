ALTER TABLE public.smartstock_cross_store_refund_requests
    ADD COLUMN IF NOT EXISTS return_receipt_number text,
    ADD COLUMN IF NOT EXISTS receipt_device_id text,
    ADD COLUMN IF NOT EXISTS receipt_sequence integer;

CREATE UNIQUE INDEX IF NOT EXISTS smartstock_cross_store_refund_receipt_number_uidx
    ON public.smartstock_cross_store_refund_requests(return_receipt_number)
    WHERE COALESCE(return_receipt_number, '') <> '';

CREATE OR REPLACE FUNCTION public.smartstock_reserve_cross_store_refund(p_request jsonb) RETURNS jsonb
    LANGUAGE plpgsql SECURITY DEFINER SET search_path TO '' AS $$
DECLARE v_id uuid; v_source integer; v_receiving integer; v_sale integer; v_line jsonb;
        v_item integer; v_qty integer; v_sold integer; v_returned integer; v_reserved integer; v_sequence bigint;
BEGIN
  v_id=(p_request->>'request_id')::uuid; v_source=(p_request->>'source_location_id')::integer;
  v_receiving=(p_request->>'receiving_location_id')::integer; v_sale=(p_request->>'source_sale_id')::integer;
  IF v_source<=0 OR v_receiving<=0 OR v_source=v_receiving OR v_sale<=0
     OR COALESCE(p_request->>'return_receipt_number','')='' OR COALESCE(p_request->>'receipt_device_id','')=''
     OR (p_request->>'receipt_sequence')::integer<=0 OR pg_catalog.jsonb_typeof(p_request->'lines')<>'array'
     OR pg_catalog.jsonb_array_length(p_request->'lines') NOT BETWEEN 1 AND 200 THEN
    RAISE EXCEPTION 'Invalid cross-store refund request.' USING ERRCODE='22023';
  END IF;
  SELECT request_sequence INTO v_sequence FROM public.smartstock_cross_store_refund_requests WHERE request_id=v_id;
  IF FOUND THEN RETURN pg_catalog.jsonb_build_object('requestId',v_id,'requestSequence',v_sequence,'duplicate',true); END IF;
  FOR v_line IN SELECT value FROM pg_catalog.jsonb_array_elements(p_request->'lines') LOOP
    v_item=(v_line->>'source_sale_item_id')::integer; v_qty=(v_line->>'quantity')::integer;
    SELECT COALESCE((r.row_data->>'quantity')::integer,0) INTO v_sold FROM public.smartstock_store_rows r
      WHERE r.location_id=v_source AND r.table_name='sale_items' AND NOT r.is_deleted
       AND (r.row_data->>'sale_id')::integer=v_sale AND (r.row_data->>'sale_item_id')::integer=v_item;
    IF NOT FOUND THEN RAISE EXCEPTION 'Sale item is not available in the latest source mirror.' USING ERRCODE='P0002'; END IF;
    SELECT COALESCE(SUM((r.row_data->>'quantity')::integer),0) INTO v_returned FROM public.smartstock_store_rows r
      WHERE r.location_id=v_source AND r.table_name='sale_return_items' AND NOT r.is_deleted
       AND (r.row_data->>'sale_item_id')::integer=v_item;
    SELECT COALESCE(SUM(l.quantity),0) INTO v_reserved FROM public.smartstock_cross_store_refund_lines l
      JOIN public.smartstock_cross_store_refund_requests q USING(request_id)
      WHERE q.source_location_id=v_source AND q.status NOT IN ('REJECTED','CANCELLED') AND l.source_sale_item_id=v_item;
    IF v_qty<=0 OR v_qty>v_sold-v_returned-v_reserved THEN
      RAISE EXCEPTION 'Requested return quantity is no longer available.' USING ERRCODE='23514';
    END IF;
  END LOOP;
  INSERT INTO public.smartstock_cross_store_refund_requests(request_id,source_location_id,receiving_location_id,
    source_sale_id,refund_method,refund_amount,reason,actor,return_receipt_number,receipt_device_id,receipt_sequence)
  VALUES(v_id,v_source,v_receiving,v_sale,p_request->>'refund_method',(p_request->>'refund_amount')::numeric,
    p_request->>'reason',COALESCE(p_request->'actor','{}'::jsonb),p_request->>'return_receipt_number',
    p_request->>'receipt_device_id',(p_request->>'receipt_sequence')::integer) RETURNING request_sequence INTO v_sequence;
  FOR v_line IN SELECT value FROM pg_catalog.jsonb_array_elements(p_request->'lines') LOOP
    INSERT INTO public.smartstock_cross_store_refund_lines(request_id,source_sale_item_id,product_id,quantity,unit_price,
      disposition,destination_location_id,disposition_reason)
    VALUES(v_id,(v_line->>'source_sale_item_id')::integer,(v_line->>'product_id')::integer,
      (v_line->>'quantity')::integer,(v_line->>'unit_price')::numeric,v_line->>'disposition',
      NULLIF(v_line->>'destination_location_id','')::integer,v_line->>'disposition_reason');
  END LOOP;
  RETURN pg_catalog.jsonb_build_object('requestId',v_id,'requestSequence',v_sequence,'duplicate',false);
END $$;

REVOKE ALL ON FUNCTION public.smartstock_reserve_cross_store_refund(jsonb) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.smartstock_reserve_cross_store_refund(jsonb) TO service_role;
