BEGIN;
DO $test$
DECLARE
    v_cursor bigint;
    v_events jsonb := '[]'::jsonb;
    v_table text;
    v_result jsonb;
    v_count integer;
BEGIN
    SELECT COALESCE(max(cloud_sequence),0) INTO v_cursor FROM public.sync_outbox;
    FOREACH v_table IN ARRAY ARRAY['employee_time_clock','payroll_payments','employee_payroll_bonuses'] LOOP
        v_events := v_events || jsonb_build_array(
            jsonb_build_object('event_id',gen_random_uuid(),'event_type','REFERENCE_ROW_CHANGED',
                'payload',jsonb_build_object('table_name',v_table,'operation','UPSERT',
                    'row_data',jsonb_build_object('location_id',1))),
            jsonb_build_object('event_id',gen_random_uuid(),'event_type','REFERENCE_ROW_CHANGED',
                'payload',jsonb_build_object('table_name',v_table,'operation','UPSERT',
                    'row_data',jsonb_build_object('location_id',2))));
    END LOOP;
    v_result := public.smartstock_sync_exchange(2,v_cursor,v_events,100);
    IF jsonb_array_length(v_result->'acknowledged_event_ids') <> 6 THEN
        RAISE EXCEPTION 'Upload acknowledgement failed';
    END IF;
    v_result := public.smartstock_sync_exchange(1,v_cursor,'[]'::jsonb,100);
    SELECT count(*) INTO v_count FROM jsonb_array_elements(v_result->'changes') c
      WHERE c->'payload'->'row_data'->>'location_id'='1';
    IF v_count <> 0 THEN RAISE EXCEPTION 'Foreign-store payroll echo delivered'; END IF;
    IF jsonb_array_length(v_result->'changes') <> 3 THEN
        RAISE EXCEPTION 'Legitimate owning-store updates did not all arrive';
    END IF;
    v_result := public.smartstock_sync_exchange(2,v_cursor,v_events,100);
    SELECT count(*) INTO v_count FROM public.sync_outbox
      WHERE event_id IN (SELECT (e->>'event_id')::uuid FROM jsonb_array_elements(v_events) e);
    IF v_count <> 6 THEN RAISE EXCEPTION 'Retry created duplicate events'; END IF;
END
$test$;
ROLLBACK;
