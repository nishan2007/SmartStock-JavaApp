CREATE OR REPLACE FUNCTION public.smartstock_sync_exchange(
    p_location_id integer,
    p_cursor bigint DEFAULT 0,
    p_events jsonb DEFAULT '[]'::jsonb,
    p_limit integer DEFAULT 100
) RETURNS jsonb
LANGUAGE plpgsql SECURITY DEFINER
SET search_path TO ''
AS $_$
DECLARE
    v_limit integer := LEAST(GREATEST(COALESCE(p_limit, 100), 1), 500);
    v_cursor bigint := GREATEST(COALESCE(p_cursor, 0::bigint), 0::bigint);
    v_event jsonb;
    v_event_id uuid;
    v_acknowledged jsonb := '[]'::jsonb;
    v_changes jsonb := '[]'::jsonb;
    v_next_cursor bigint := v_cursor;
BEGIN
    IF p_location_id IS NULL OR p_location_id <= 0 THEN
        RAISE EXCEPTION 'A valid store location is required.';
    END IF;
    IF pg_catalog.jsonb_typeof(COALESCE(p_events, '[]'::jsonb)) <> 'array' THEN
        RAISE EXCEPTION 'p_events must be a JSON array.';
    END IF;
    IF pg_catalog.jsonb_array_length(COALESCE(p_events, '[]'::jsonb)) > 100 THEN
        RAISE EXCEPTION 'A sync request cannot contain more than 100 events.';
    END IF;

    FOR v_event IN SELECT value FROM pg_catalog.jsonb_array_elements(COALESCE(p_events, '[]'::jsonb))
    LOOP
        IF pg_catalog.jsonb_typeof(v_event) <> 'object'
           OR COALESCE(v_event->>'event_id', '') = ''
           OR COALESCE(v_event->>'event_type', '') = '' THEN
            RAISE EXCEPTION 'Each sync event requires event_id and event_type.';
        END IF;
        v_event_id := (v_event->>'event_id')::uuid;
        INSERT INTO public.sync_applied_events(origin_event_id,event_type,origin_location_id,origin_device_id,cloud_reference)
        VALUES(v_event_id,pg_catalog.left(v_event->>'event_type',200),p_location_id,
               NULLIF(v_event->>'device_id',''),'smartstock_sync_exchange')
        ON CONFLICT(origin_event_id) DO NOTHING;
        INSERT INTO public.sync_outbox(event_id,event_type,location_id,device_id,user_id,payload,status,attempts,
            created_at,synced_at,origin_event_id,origin_location_id,origin_device_id,origin_created_at)
        VALUES(v_event_id,pg_catalog.left(v_event->>'event_type',200),p_location_id,
            NULLIF(v_event->>'device_id',''),
            CASE WHEN COALESCE(v_event->>'user_id','') ~ '^[0-9]+$' THEN (v_event->>'user_id')::integer END,
            COALESCE(v_event->'payload','{}'::jsonb),'RECEIVED_FROM_STORE',0,pg_catalog.now(),pg_catalog.now(),
            v_event_id,p_location_id,NULLIF(v_event->>'device_id',''),
            CASE WHEN COALESCE(v_event->>'created_at','')='' THEN pg_catalog.now()
                 ELSE (v_event->>'created_at')::timestamptz END)
        ON CONFLICT(event_id) DO NOTHING;
        v_acknowledged := v_acknowledged || pg_catalog.jsonb_build_array(v_event_id::text);
    END LOOP;

    WITH delta AS (
        SELECT o.cloud_sequence,o.event_id,o.event_type,o.location_id,o.device_id,o.user_id,o.payload,
               o.origin_location_id,o.origin_device_id,o.origin_created_at
        FROM public.sync_outbox o
        WHERE o.cloud_sequence > v_cursor
          AND o.origin_location_id IS DISTINCT FROM p_location_id
          AND (o.location_id IS NULL
            OR o.event_type IN ('PRODUCT_CREATED','PRODUCT_UPDATED','ROLE_CREATED','ROLE_PERMISSIONS_UPDATED',
                'TIME_CLOCK_AUTO_CLOSE_SETTINGS_UPDATED','DEVICE_ACCESS_UPDATED','REFERENCE_ROW_CHANGED')
            OR (o.event_type='STORE_TRANSFER_CREATED'
                AND COALESCE(o.payload->>'destination_location_id','') ~ '^[0-9]+$'
                AND (o.payload->>'destination_location_id')::integer=p_location_id)
            OR (o.event_type='STORE_TRANSFER_RECEIVED'
                AND COALESCE(o.payload->>'source_location_id','') ~ '^[0-9]+$'
                AND (o.payload->>'source_location_id')::integer=p_location_id))
        ORDER BY o.cloud_sequence LIMIT v_limit
    )
    SELECT COALESCE(pg_catalog.jsonb_agg(pg_catalog.jsonb_build_object(
        'sequence',cloud_sequence,'event_id',event_id,'event_type',event_type,'location_id',location_id,
        'device_id',device_id,'user_id',user_id,'payload',payload,'origin_location_id',origin_location_id,
        'origin_device_id',origin_device_id,'created_at',origin_created_at) ORDER BY cloud_sequence),'[]'::jsonb),
        COALESCE(pg_catalog.max(cloud_sequence),v_cursor)
    INTO v_changes,v_next_cursor FROM delta;

    INSERT INTO public.store_sync_status(location_id,status,message,last_success_at,last_seen_at,updated_at)
    VALUES(p_location_id,'Online','Store synchronized through the HTTPS API',pg_catalog.now(),pg_catalog.now(),pg_catalog.now())
    ON CONFLICT(location_id) DO UPDATE SET status=EXCLUDED.status,message=EXCLUDED.message,
        last_success_at=EXCLUDED.last_success_at,last_seen_at=EXCLUDED.last_seen_at,updated_at=EXCLUDED.updated_at;
    RETURN pg_catalog.jsonb_build_object('acknowledged_event_ids',v_acknowledged,'changes',v_changes,
        'next_cursor',v_next_cursor,'has_more',pg_catalog.jsonb_array_length(v_changes)=v_limit);
END
$_$;

REVOKE ALL ON FUNCTION public.smartstock_sync_exchange(integer,bigint,jsonb,integer) FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.smartstock_sync_exchange(integer,bigint,jsonb,integer) TO service_role;
