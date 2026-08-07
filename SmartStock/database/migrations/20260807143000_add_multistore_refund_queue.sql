-- Supabase service-role-only coordination for cross-store refunds.
CREATE SEQUENCE IF NOT EXISTS public.smartstock_cross_store_refund_sequence;

CREATE TABLE IF NOT EXISTS public.smartstock_cross_store_refund_requests (
    request_id uuid PRIMARY KEY,
    request_sequence bigint NOT NULL DEFAULT nextval('public.smartstock_cross_store_refund_sequence'),
    source_location_id integer NOT NULL,
    receiving_location_id integer NOT NULL,
    source_sale_id integer NOT NULL,
    refund_method text NOT NULL,
    refund_amount numeric(14,2) NOT NULL CHECK(refund_amount > 0),
    reason text NOT NULL,
    actor jsonb NOT NULL DEFAULT '{}'::jsonb,
    status text NOT NULL DEFAULT 'PAID_PENDING_SOURCE',
    source_error text,
    created_at timestamptz NOT NULL DEFAULT pg_catalog.now(),
    updated_at timestamptz NOT NULL DEFAULT pg_catalog.now(),
    UNIQUE(request_sequence)
);

CREATE TABLE IF NOT EXISTS public.smartstock_cross_store_refund_lines (
    request_id uuid NOT NULL REFERENCES public.smartstock_cross_store_refund_requests(request_id) ON DELETE CASCADE,
    source_sale_item_id integer NOT NULL,
    product_id integer NOT NULL,
    quantity integer NOT NULL CHECK(quantity > 0),
    unit_price numeric(14,2) NOT NULL CHECK(unit_price >= 0),
    disposition text NOT NULL CHECK(disposition IN ('RESTOCK','DISCARD')),
    destination_location_id integer,
    disposition_reason text,
    source_status text NOT NULL DEFAULT 'PENDING',
    destination_status text NOT NULL DEFAULT 'PENDING',
    confirmed_quantity integer NOT NULL DEFAULT 0,
    conflict_quantity integer NOT NULL DEFAULT 0,
    PRIMARY KEY(request_id,source_sale_item_id)
);

CREATE INDEX IF NOT EXISTS smartstock_cross_store_refund_source_idx
ON public.smartstock_cross_store_refund_requests(source_location_id,status,request_sequence);
CREATE INDEX IF NOT EXISTS smartstock_cross_store_refund_destination_idx
ON public.smartstock_cross_store_refund_lines(destination_location_id,destination_status);

ALTER TABLE public.smartstock_cross_store_refund_requests ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.smartstock_cross_store_refund_lines ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON TABLE public.smartstock_cross_store_refund_requests FROM PUBLIC,anon,authenticated;
REVOKE ALL ON TABLE public.smartstock_cross_store_refund_lines FROM PUBLIC,anon,authenticated;
REVOKE ALL ON SEQUENCE public.smartstock_cross_store_refund_sequence FROM PUBLIC,anon,authenticated;
GRANT ALL ON TABLE public.smartstock_cross_store_refund_requests TO service_role;
GRANT ALL ON TABLE public.smartstock_cross_store_refund_lines TO service_role;
GRANT USAGE,SELECT ON SEQUENCE public.smartstock_cross_store_refund_sequence TO service_role;

CREATE OR REPLACE FUNCTION public.smartstock_reserve_cross_store_refund(p_request jsonb)
RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path TO '' AS $$
DECLARE v_id uuid; v_source integer; v_receiving integer; v_sale integer; v_line jsonb;
        v_item integer; v_qty integer; v_sold integer; v_returned integer; v_reserved integer;
        v_sequence bigint;
BEGIN
  v_id=(p_request->>'request_id')::uuid; v_source=(p_request->>'source_location_id')::integer;
  v_receiving=(p_request->>'receiving_location_id')::integer; v_sale=(p_request->>'source_sale_id')::integer;
  IF v_source<=0 OR v_receiving<=0 OR v_source=v_receiving OR v_sale<=0
     OR pg_catalog.jsonb_typeof(p_request->'lines')<>'array'
     OR pg_catalog.jsonb_array_length(p_request->'lines') NOT BETWEEN 1 AND 200 THEN
    RAISE EXCEPTION 'Invalid cross-store refund request.' USING ERRCODE='22023';
  END IF;
  SELECT request_sequence INTO v_sequence FROM public.smartstock_cross_store_refund_requests WHERE request_id=v_id;
  IF FOUND THEN RETURN pg_catalog.jsonb_build_object('requestId',v_id,'requestSequence',v_sequence,'duplicate',true); END IF;
  FOR v_line IN SELECT value FROM pg_catalog.jsonb_array_elements(p_request->'lines') LOOP
    v_item=(v_line->>'source_sale_item_id')::integer; v_qty=(v_line->>'quantity')::integer;
    SELECT COALESCE((r.row_data->>'quantity')::integer,0) INTO v_sold
      FROM public.smartstock_store_rows r WHERE r.location_id=v_source AND r.table_name='sale_items'
       AND NOT r.is_deleted AND (r.row_data->>'sale_id')::integer=v_sale
       AND (r.row_data->>'sale_item_id')::integer=v_item;
    IF NOT FOUND THEN RAISE EXCEPTION 'Sale item is not available in the latest source mirror.' USING ERRCODE='P0002'; END IF;
    SELECT COALESCE(SUM((r.row_data->>'quantity')::integer),0) INTO v_returned
      FROM public.smartstock_store_rows r WHERE r.location_id=v_source AND r.table_name='sale_return_items'
       AND NOT r.is_deleted AND (r.row_data->>'sale_item_id')::integer=v_item;
    SELECT COALESCE(SUM(l.quantity),0) INTO v_reserved
      FROM public.smartstock_cross_store_refund_lines l JOIN public.smartstock_cross_store_refund_requests q USING(request_id)
      WHERE q.source_location_id=v_source AND q.status NOT IN ('REJECTED','CANCELLED') AND l.source_sale_item_id=v_item;
    IF v_qty<=0 OR v_qty>v_sold-v_returned-v_reserved THEN
      RAISE EXCEPTION 'Requested return quantity is no longer available.' USING ERRCODE='23514';
    END IF;
  END LOOP;
  INSERT INTO public.smartstock_cross_store_refund_requests(request_id,source_location_id,receiving_location_id,
    source_sale_id,refund_method,refund_amount,reason,actor)
  VALUES(v_id,v_source,v_receiving,v_sale,p_request->>'refund_method',(p_request->>'refund_amount')::numeric,
    p_request->>'reason',COALESCE(p_request->'actor','{}'::jsonb)) RETURNING request_sequence INTO v_sequence;
  FOR v_line IN SELECT value FROM pg_catalog.jsonb_array_elements(p_request->'lines') LOOP
    INSERT INTO public.smartstock_cross_store_refund_lines(request_id,source_sale_item_id,product_id,quantity,unit_price,
      disposition,destination_location_id,disposition_reason)
    VALUES(v_id,(v_line->>'source_sale_item_id')::integer,(v_line->>'product_id')::integer,
      (v_line->>'quantity')::integer,(v_line->>'unit_price')::numeric,v_line->>'disposition',
      NULLIF(v_line->>'destination_location_id','')::integer,v_line->>'disposition_reason');
  END LOOP;
  RETURN pg_catalog.jsonb_build_object('requestId',v_id,'requestSequence',v_sequence,'duplicate',false);
END $$;

CREATE OR REPLACE FUNCTION public.smartstock_cross_store_refund_queue(p_location_id integer)
RETURNS jsonb LANGUAGE sql STABLE SECURITY DEFINER SET search_path TO '' AS $$
 SELECT pg_catalog.jsonb_build_object('requests',COALESCE(pg_catalog.jsonb_agg(row_data ORDER BY request_sequence),'[]'::jsonb))
 FROM (SELECT q.request_sequence,pg_catalog.jsonb_build_object('request',pg_catalog.to_jsonb(q),
   'lines',COALESCE((SELECT pg_catalog.jsonb_agg(pg_catalog.to_jsonb(l) ORDER BY l.source_sale_item_id)
     FROM public.smartstock_cross_store_refund_lines l WHERE l.request_id=q.request_id),'[]'::jsonb)) row_data
   FROM public.smartstock_cross_store_refund_requests q
   WHERE q.status NOT IN ('COMPLETED','REJECTED','CANCELLED') AND
    (q.source_location_id=p_location_id OR q.receiving_location_id=p_location_id
      OR EXISTS(SELECT 1 FROM public.smartstock_cross_store_refund_lines l WHERE l.request_id=q.request_id AND l.destination_location_id=p_location_id))
 ) queued $$;

CREATE OR REPLACE FUNCTION public.smartstock_update_cross_store_refund(p_request_id uuid,p_status text,p_lines jsonb DEFAULT '[]'::jsonb,p_error text DEFAULT NULL)
RETURNS jsonb LANGUAGE plpgsql SECURITY DEFINER SET search_path TO '' AS $$
DECLARE v_line jsonb;
BEGIN
 UPDATE public.smartstock_cross_store_refund_requests SET status=p_status,source_error=p_error,updated_at=pg_catalog.now()
 WHERE request_id=p_request_id;
 IF NOT FOUND THEN RAISE EXCEPTION 'Cross-store refund request was not found.' USING ERRCODE='P0002'; END IF;
 FOR v_line IN SELECT value FROM pg_catalog.jsonb_array_elements(COALESCE(p_lines,'[]'::jsonb)) LOOP
   UPDATE public.smartstock_cross_store_refund_lines SET
    source_status=COALESCE(v_line->>'source_status',source_status),
    destination_status=COALESCE(v_line->>'destination_status',destination_status),
    confirmed_quantity=COALESCE((v_line->>'confirmed_quantity')::integer,confirmed_quantity),
    conflict_quantity=COALESCE((v_line->>'conflict_quantity')::integer,conflict_quantity)
   WHERE request_id=p_request_id AND source_sale_item_id=(v_line->>'source_sale_item_id')::integer;
 END LOOP;
 IF NOT EXISTS(SELECT 1 FROM public.smartstock_cross_store_refund_lines
   WHERE request_id=p_request_id AND (source_status='PENDING' OR (disposition='RESTOCK' AND confirmed_quantity>0 AND destination_status<>'APPLIED'))) THEN
   UPDATE public.smartstock_cross_store_refund_requests
   SET status=CASE WHEN EXISTS(SELECT 1 FROM public.smartstock_cross_store_refund_lines WHERE request_id=p_request_id AND conflict_quantity>0)
     THEN 'CONFLICT_REVIEW' ELSE 'COMPLETED' END,updated_at=pg_catalog.now()
   WHERE request_id=p_request_id;
 END IF;
 RETURN pg_catalog.jsonb_build_object('updated',true);
END $$;

REVOKE ALL ON FUNCTION public.smartstock_reserve_cross_store_refund(jsonb) FROM PUBLIC,anon,authenticated;
REVOKE ALL ON FUNCTION public.smartstock_cross_store_refund_queue(integer) FROM PUBLIC,anon,authenticated;
REVOKE ALL ON FUNCTION public.smartstock_update_cross_store_refund(uuid,text,jsonb,text) FROM PUBLIC,anon,authenticated;
GRANT EXECUTE ON FUNCTION public.smartstock_reserve_cross_store_refund(jsonb) TO service_role;
GRANT EXECUTE ON FUNCTION public.smartstock_cross_store_refund_queue(integer) TO service_role;
GRANT EXECUTE ON FUNCTION public.smartstock_update_cross_store_refund(uuid,text,jsonb,text) TO service_role;
