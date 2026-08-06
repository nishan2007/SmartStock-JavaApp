-- Secured control-plane registry for SmartStock store servers.
-- Store PostgreSQL remains authoritative for POS traffic; this registry only
-- coordinates server identity, health, and controlled replacement.

CREATE SCHEMA IF NOT EXISTS smartstock_private;
REVOKE ALL ON SCHEMA smartstock_private FROM PUBLIC, anon, authenticated;

CREATE TABLE IF NOT EXISTS public.store_server_instances (
    server_instance_id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    location_id integer NOT NULL REFERENCES public.locations(location_id),
    installation_id text NOT NULL,
    display_name text,
    hostname text NOT NULL,
    app_version text,
    certificate_fingerprint text NOT NULL,
    endpoint_host text NOT NULL,
    endpoint_port integer NOT NULL DEFAULT 8443 CHECK (endpoint_port BETWEEN 1 AND 65535),
    role text NOT NULL CHECK (role IN ('PRIMARY','STANDBY','DRAINING','RETIRED','FENCED')),
    generation bigint NOT NULL DEFAULT 0 CHECK (generation >= 0),
    last_heartbeat_at timestamptz,
    last_sync_at timestamptz,
    last_materialization_at timestamptz,
    materialized_row_count bigint CHECK (materialized_row_count IS NULL OR materialized_row_count >= 0),
    recovery_validated_at timestamptz,
    recovery_materialization_at timestamptz,
    recovery_network_checked_at timestamptz,
    status_message text,
    replaced_by_server_instance_id uuid REFERENCES public.store_server_instances(server_instance_id),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    retired_at timestamptz,
    UNIQUE (location_id, installation_id)
);

CREATE UNIQUE INDEX IF NOT EXISTS store_server_instances_one_primary_idx
    ON public.store_server_instances(location_id) WHERE role = 'PRIMARY';
CREATE UNIQUE INDEX IF NOT EXISTS store_server_instances_generation_idx
    ON public.store_server_instances(location_id, generation) WHERE generation > 0;
CREATE INDEX IF NOT EXISTS store_server_instances_health_idx
    ON public.store_server_instances(location_id, last_heartbeat_at DESC);
CREATE INDEX IF NOT EXISTS store_server_instances_replaced_by_idx
    ON public.store_server_instances(replaced_by_server_instance_id);

CREATE TABLE IF NOT EXISTS public.store_server_handoffs (
    handoff_id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    location_id integer NOT NULL REFERENCES public.locations(location_id),
    source_server_instance_id uuid REFERENCES public.store_server_instances(server_instance_id),
    target_server_instance_id uuid NOT NULL REFERENCES public.store_server_instances(server_instance_id),
    status text NOT NULL CHECK (status IN ('PREPARING','READY','COMPLETED','FAILED')),
    emergency boolean NOT NULL DEFAULT false,
    requested_by_user_id integer,
    requested_by_name text,
    idempotency_key text NOT NULL,
    recovery_materialized_at timestamptz,
    recovery_row_count bigint,
    warning_acknowledged boolean NOT NULL DEFAULT false,
    failure_message text,
    created_at timestamptz NOT NULL DEFAULT now(),
    ready_at timestamptz,
    completed_at timestamptz,
    UNIQUE (location_id, idempotency_key)
);

CREATE UNIQUE INDEX IF NOT EXISTS store_server_handoffs_one_open_idx
    ON public.store_server_handoffs(location_id)
    WHERE status IN ('PREPARING','READY');
CREATE INDEX IF NOT EXISTS store_server_handoffs_source_idx
    ON public.store_server_handoffs(source_server_instance_id);
CREATE INDEX IF NOT EXISTS store_server_handoffs_target_idx
    ON public.store_server_handoffs(target_server_instance_id);

CREATE TABLE IF NOT EXISTS public.store_server_events (
    server_event_id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    location_id integer NOT NULL REFERENCES public.locations(location_id),
    server_instance_id uuid REFERENCES public.store_server_instances(server_instance_id),
    handoff_id uuid REFERENCES public.store_server_handoffs(handoff_id),
    event_type text NOT NULL,
    actor_user_id integer,
    actor_name text,
    details text,
    created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS store_server_events_location_created_idx
    ON public.store_server_events(location_id, created_at DESC);
CREATE INDEX IF NOT EXISTS store_server_events_instance_idx
    ON public.store_server_events(server_instance_id);
CREATE INDEX IF NOT EXISTS store_server_events_handoff_idx
    ON public.store_server_events(handoff_id);

ALTER TABLE public.store_server_instances ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.store_server_handoffs ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.store_server_events ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON TABLE public.store_server_instances FROM PUBLIC, anon, authenticated;
REVOKE ALL ON TABLE public.store_server_handoffs FROM PUBLIC, anon, authenticated;
REVOKE ALL ON TABLE public.store_server_events FROM PUBLIC, anon, authenticated;
REVOKE ALL ON SEQUENCE public.store_server_events_server_event_id_seq FROM PUBLIC, anon, authenticated;
REVOKE ALL ON TABLE public.store_server_instances FROM service_role;
REVOKE ALL ON TABLE public.store_server_handoffs FROM service_role;
REVOKE ALL ON TABLE public.store_server_events FROM service_role;
REVOKE ALL ON SEQUENCE public.store_server_events_server_event_id_seq FROM service_role;

CREATE OR REPLACE FUNCTION smartstock_private.smartstock_server_registry(
    p_action text,
    p_payload jsonb DEFAULT '{}'::jsonb
)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO ''
AS $$
DECLARE
    v_action text := upper(coalesce(p_action, ''));
    v_location_id integer := nullif(p_payload->>'location_id','')::integer;
    v_instance_id uuid := nullif(p_payload->>'server_instance_id','')::uuid;
    v_target_id uuid := nullif(p_payload->>'target_server_instance_id','')::uuid;
    v_source public.store_server_instances%ROWTYPE;
    v_target public.store_server_instances%ROWTYPE;
    v_handoff public.store_server_handoffs%ROWTYPE;
    v_generation bigint;
    v_result jsonb;
BEGIN
    IF v_location_id IS NULL OR v_location_id <= 0 THEN
        RAISE EXCEPTION 'A valid store location is required.' USING ERRCODE = '22023';
    END IF;

    IF v_action = 'ENSURE_LOCATION' THEN
        IF coalesce(p_payload->>'store_name','')='' OR coalesce(p_payload->>'store_code','')='' THEN
            RAISE EXCEPTION 'Store name and store code are required.' USING ERRCODE='22023';
        END IF;
        IF EXISTS (SELECT 1 FROM public.locations WHERE location_id=v_location_id
          AND upper(coalesce(receipt_store_code,''))<>upper(p_payload->>'store_code'))
          OR EXISTS (SELECT 1 FROM public.locations WHERE location_id<>v_location_id
          AND upper(coalesce(receipt_store_code,''))=upper(p_payload->>'store_code')) THEN
            RAISE EXCEPTION 'Store identity conflicts with an existing cloud location.' USING ERRCODE='40001';
        END IF;
        INSERT INTO public.locations(location_id,name,receipt_store_code,timezone,address)
        VALUES(v_location_id,p_payload->>'store_name',p_payload->>'store_code',
          coalesce(nullif(p_payload->>'timezone',''),'America/New_York'),nullif(p_payload->>'address',''))
        ON CONFLICT(location_id) DO UPDATE SET name=EXCLUDED.name,
          receipt_store_code=EXCLUDED.receipt_store_code,timezone=EXCLUDED.timezone,
          address=coalesce(EXCLUDED.address,public.locations.address);
        SELECT coalesce(max(location_id),1) INTO v_generation FROM public.locations;
        IF pg_get_serial_sequence('public.locations','location_id') IS NOT NULL THEN
            PERFORM setval(pg_get_serial_sequence('public.locations','location_id'),v_generation,true);
        END IF;
        RETURN jsonb_build_object('locationId',v_location_id,'ensured',true);
    END IF;

    IF v_action = 'LIST' THEN
        SELECT coalesce(jsonb_agg(jsonb_build_object(
            'serverInstanceId', s.server_instance_id,
            'locationId', s.location_id,
            'installationId', s.installation_id,
            'displayName', coalesce(s.display_name, s.hostname),
            'hostname', s.hostname,
            'appVersion', coalesce(s.app_version,''),
            'certificateFingerprint', s.certificate_fingerprint,
            'endpointHost', s.endpoint_host,
            'endpointPort', s.endpoint_port,
            'role', s.role,
            'generation', s.generation,
            'health', CASE
                WHEN s.role = 'FENCED' THEN 'FENCED'
                WHEN s.role = 'RETIRED' THEN 'RETIRED'
                WHEN coalesce(s.status_message,'') <> '' THEN 'DEGRADED'
                WHEN s.last_heartbeat_at >= now() - interval '2 minutes' THEN 'ONLINE'
                WHEN s.last_heartbeat_at >= now() - interval '10 minutes' THEN 'STALE'
                ELSE 'OFFLINE' END,
            'lastHeartbeatAt', s.last_heartbeat_at,
            'lastSyncAt', s.last_sync_at,
            'lastMaterializationAt', s.last_materialization_at,
            'materializedRowCount', s.materialized_row_count,
            'recoveryValidatedAt', s.recovery_validated_at,
            'recoveryMaterializationAt', s.recovery_materialization_at,
            'recoveryNetworkCheckedAt', s.recovery_network_checked_at,
            'statusMessage', coalesce(s.status_message,''),
            'replacedByServerInstanceId', s.replaced_by_server_instance_id,
            'createdAt', s.created_at,
            'retiredAt', s.retired_at
        ) ORDER BY CASE s.role WHEN 'PRIMARY' THEN 0 WHEN 'DRAINING' THEN 1 WHEN 'STANDBY' THEN 2 ELSE 3 END,
          s.last_heartbeat_at DESC NULLS LAST), '[]'::jsonb)
        INTO v_result FROM public.store_server_instances s WHERE s.location_id = v_location_id;
        RETURN jsonb_build_object('servers', v_result);
    END IF;

    IF v_action = 'HANDOFF_STATUS' THEN
        SELECT * INTO v_handoff
        FROM public.store_server_handoffs h
        WHERE h.location_id=v_location_id
          AND (nullif(p_payload->>'handoff_id','') IS NULL
               OR h.handoff_id=nullif(p_payload->>'handoff_id','')::uuid)
          AND (coalesce(p_payload->>'idempotency_key','')=''
               OR h.idempotency_key=p_payload->>'idempotency_key')
          AND (v_instance_id IS NULL OR h.source_server_instance_id=v_instance_id
               OR h.target_server_instance_id=v_instance_id)
        ORDER BY h.created_at DESC LIMIT 1;
        IF NOT FOUND THEN RETURN jsonb_build_object('status','NONE'); END IF;
        RETURN jsonb_build_object('handoffId',v_handoff.handoff_id,'status',v_handoff.status,
          'sourceServerInstanceId',v_handoff.source_server_instance_id,
          'targetServerInstanceId',v_handoff.target_server_instance_id,
          'emergency',v_handoff.emergency,
          'recoveryMaterializedAt',v_handoff.recovery_materialized_at,
          'recoveryRowCount',v_handoff.recovery_row_count,
          'failureMessage',coalesce(v_handoff.failure_message,''));
    END IF;

    IF v_action = 'LIST_EVENTS' THEN
        SELECT coalesce(jsonb_agg(jsonb_build_object(
          'eventType',e.event_type,'serverInstanceId',e.server_instance_id,
          'handoffId',e.handoff_id,'actorName',coalesce(e.actor_name,''),
          'details',coalesce(e.details,''),'createdAt',e.created_at)
          ORDER BY e.created_at DESC),'[]'::jsonb)
        INTO v_result FROM (SELECT * FROM public.store_server_events
          WHERE location_id=v_location_id ORDER BY created_at DESC LIMIT 100) e;
        RETURN jsonb_build_object('events',v_result);
    END IF;

    PERFORM pg_advisory_xact_lock(1398035026, v_location_id);

    IF v_action IN ('BEGIN_HANDOFF','EMERGENCY_TAKEOVER')
       AND coalesce(p_payload->>'idempotency_key','') <> '' THEN
        SELECT * INTO v_handoff FROM public.store_server_handoffs
        WHERE location_id=v_location_id AND idempotency_key=p_payload->>'idempotency_key';
        IF FOUND THEN
            RETURN jsonb_build_object('handoffId',v_handoff.handoff_id,'status',v_handoff.status,
              'recoveryMaterializedAt',v_handoff.recovery_materialized_at,
              'recoveryRowCount',v_handoff.recovery_row_count);
        END IF;
    END IF;

    IF v_action IN ('REGISTER_PRIMARY','REGISTER_STANDBY') THEN
        IF coalesce(p_payload->>'installation_id','') = ''
           OR coalesce(p_payload->>'hostname','') = ''
           OR coalesce(p_payload->>'certificate_fingerprint','') = ''
           OR coalesce(p_payload->>'endpoint_host','') = '' THEN
            RAISE EXCEPTION 'Server identity and endpoint details are required.' USING ERRCODE = '22023';
        END IF;
        IF v_action = 'REGISTER_PRIMARY' AND EXISTS (
            SELECT 1 FROM public.store_server_instances
            WHERE location_id=v_location_id AND role IN ('PRIMARY','DRAINING')
              AND installation_id<>p_payload->>'installation_id') THEN
            RAISE EXCEPTION 'Another primary server is already registered for this store.' USING ERRCODE = '23505';
        END IF;
        SELECT coalesce(max(generation),0) INTO v_generation
          FROM public.store_server_instances WHERE location_id=v_location_id;
        INSERT INTO public.store_server_instances(
            location_id,installation_id,display_name,hostname,app_version,
            certificate_fingerprint,endpoint_host,endpoint_port,role,generation,last_heartbeat_at
        ) VALUES (
            v_location_id,p_payload->>'installation_id',nullif(p_payload->>'display_name',''),
            p_payload->>'hostname',nullif(p_payload->>'app_version',''),
            p_payload->>'certificate_fingerprint',p_payload->>'endpoint_host',
            coalesce(nullif(p_payload->>'endpoint_port','')::integer,8443),
            CASE WHEN v_action='REGISTER_PRIMARY' THEN 'PRIMARY' ELSE 'STANDBY' END,
            CASE WHEN v_action='REGISTER_PRIMARY' THEN v_generation+1 ELSE 0 END,now()
        ) ON CONFLICT(location_id,installation_id) DO UPDATE SET
            display_name=coalesce(EXCLUDED.display_name,public.store_server_instances.display_name),
            hostname=EXCLUDED.hostname,app_version=EXCLUDED.app_version,
            certificate_fingerprint=EXCLUDED.certificate_fingerprint,
            endpoint_host=EXCLUDED.endpoint_host,endpoint_port=EXCLUDED.endpoint_port,
            role=CASE WHEN v_action='REGISTER_PRIMARY' THEN 'PRIMARY' ELSE public.store_server_instances.role END,
            generation=CASE WHEN v_action='REGISTER_PRIMARY' THEN v_generation+1 ELSE public.store_server_instances.generation END,
            last_heartbeat_at=now(),updated_at=now()
        RETURNING server_instance_id INTO v_instance_id;
        INSERT INTO public.store_server_events(location_id,server_instance_id,event_type,details)
        VALUES(v_location_id,v_instance_id,v_action,'Server registered through secured control plane.');
        RETURN jsonb_build_object('serverInstanceId',v_instance_id,'registered',true);
    END IF;

    SELECT * INTO v_source FROM public.store_server_instances
      WHERE server_instance_id=v_instance_id AND location_id=v_location_id FOR UPDATE;
    IF NOT FOUND THEN RAISE EXCEPTION 'Server instance was not found.' USING ERRCODE='P0002'; END IF;

    IF v_action = 'PREPARE_STANDBY' THEN
        IF v_source.role<>'STANDBY' THEN RAISE EXCEPTION 'Server is not a standby.' USING ERRCODE='55000'; END IF;
        INSERT INTO public.store_server_events(location_id,server_instance_id,event_type,actor_user_id,actor_name,details)
        VALUES(v_location_id,v_instance_id,'STANDBY_PREPARED',nullif(p_payload->>'actor_user_id','')::integer,
          nullif(p_payload->>'actor_name',''),'Administrator verified standby readiness.');
        RETURN jsonb_build_object('prepared',true);
    END IF;

    IF v_action = 'HEARTBEAT' THEN
        UPDATE public.store_server_instances SET
            hostname=coalesce(nullif(p_payload->>'hostname',''),hostname),
            app_version=coalesce(nullif(p_payload->>'app_version',''),app_version),
            endpoint_host=coalesce(nullif(p_payload->>'endpoint_host',''),endpoint_host),
            endpoint_port=coalesce(nullif(p_payload->>'endpoint_port','')::integer,endpoint_port),
            last_heartbeat_at=now(),
            last_sync_at=coalesce(nullif(p_payload->>'last_sync_at','')::timestamptz,last_sync_at),
            last_materialization_at=coalesce(nullif(p_payload->>'last_materialization_at','')::timestamptz,last_materialization_at),
            materialized_row_count=coalesce(nullif(p_payload->>'materialized_row_count','')::bigint,materialized_row_count),
            status_message=nullif(p_payload->>'status_message',''),updated_at=now()
        WHERE server_instance_id=v_instance_id;
        RETURN jsonb_build_object('accepted',true,'role',v_source.role,'generation',v_source.generation,
            'fenced',v_source.role='FENCED');
    END IF;

    IF v_action = 'MARK_RECOVERY_READY' THEN
        IF v_source.role <> 'STANDBY' THEN
            RAISE EXCEPTION 'Only a standby can be marked recovery-ready.' USING ERRCODE='55000';
        END IF;
        SELECT * INTO v_target FROM public.store_server_instances
          WHERE location_id=v_location_id AND role='PRIMARY' FOR UPDATE;
        IF NOT FOUND OR v_target.last_materialization_at IS NULL THEN
            RAISE EXCEPTION 'No verified primary recovery point is available.' USING ERRCODE='55000';
        END IF;
        UPDATE public.store_server_instances SET recovery_validated_at=now(),
          recovery_materialization_at=v_target.last_materialization_at,
          recovery_network_checked_at=now(),updated_at=now()
          WHERE server_instance_id=v_instance_id;
        INSERT INTO public.store_server_events(location_id,server_instance_id,event_type,details)
        VALUES(v_location_id,v_instance_id,'STANDBY_RECOVERY_VALIDATED','Standby restored the latest verified cloud materialization.');
        RETURN jsonb_build_object('ready',true,'recoveryMaterializedAt',v_target.last_materialization_at);
    END IF;

    IF v_action = 'RENAME' THEN
        UPDATE public.store_server_instances SET display_name=nullif(p_payload->>'display_name',''),updated_at=now()
          WHERE server_instance_id=v_instance_id;
        INSERT INTO public.store_server_events(location_id,server_instance_id,event_type,actor_user_id,actor_name,details)
        VALUES(v_location_id,v_instance_id,'SERVER_RENAMED',nullif(p_payload->>'actor_user_id','')::integer,
          nullif(p_payload->>'actor_name',''),'Server display name changed.');
        RETURN jsonb_build_object('updated',true);
    END IF;

    IF v_action = 'RETIRE' THEN
        IF v_source.role IN ('PRIMARY','DRAINING') THEN
            RAISE EXCEPTION 'The active primary must be replaced before it can be retired.' USING ERRCODE='55000';
        END IF;
        UPDATE public.store_server_instances SET role='RETIRED',retired_at=now(),updated_at=now()
          WHERE server_instance_id=v_instance_id;
        INSERT INTO public.store_server_events(location_id,server_instance_id,event_type,actor_user_id,actor_name,details)
        VALUES(v_location_id,v_instance_id,'SERVER_RETIRED',nullif(p_payload->>'actor_user_id','')::integer,
          nullif(p_payload->>'actor_name',''),'Server retired by administrator.');
        RETURN jsonb_build_object('retired',true);
    END IF;

    IF v_action = 'BEGIN_HANDOFF' THEN
        IF v_source.role <> 'PRIMARY' THEN RAISE EXCEPTION 'Source server is not the active primary.' USING ERRCODE='55000'; END IF;
        SELECT * INTO v_target FROM public.store_server_instances
          WHERE server_instance_id=v_target_id AND location_id=v_location_id FOR UPDATE;
        IF NOT FOUND OR v_target.role <> 'STANDBY' THEN RAISE EXCEPTION 'Target server is not an available standby.' USING ERRCODE='55000'; END IF;
        INSERT INTO public.store_server_handoffs(location_id,source_server_instance_id,target_server_instance_id,
          status,requested_by_user_id,requested_by_name,idempotency_key)
        VALUES(v_location_id,v_instance_id,v_target_id,'PREPARING',nullif(p_payload->>'actor_user_id','')::integer,
          nullif(p_payload->>'actor_name',''),p_payload->>'idempotency_key')
        ON CONFLICT(location_id,idempotency_key) DO UPDATE SET idempotency_key=EXCLUDED.idempotency_key
        RETURNING * INTO v_handoff;
        UPDATE public.store_server_instances SET role='DRAINING',updated_at=now() WHERE server_instance_id=v_instance_id;
        INSERT INTO public.store_server_events(location_id,server_instance_id,handoff_id,event_type,actor_user_id,actor_name,details)
        VALUES(v_location_id,v_instance_id,v_handoff.handoff_id,'HANDOFF_STARTED',v_handoff.requested_by_user_id,
          v_handoff.requested_by_name,'Primary entered draining state.');
        RETURN jsonb_build_object('handoffId',v_handoff.handoff_id,'status',v_handoff.status);
    END IF;

    IF v_action = 'MARK_HANDOFF_READY' THEN
        SELECT * INTO v_handoff FROM public.store_server_handoffs
          WHERE handoff_id=nullif(p_payload->>'handoff_id','')::uuid AND location_id=v_location_id FOR UPDATE;
        IF FOUND AND v_handoff.source_server_instance_id=v_instance_id
           AND v_handoff.status IN ('READY','COMPLETED') THEN
            RETURN jsonb_build_object('handoffId',v_handoff.handoff_id,'status',v_handoff.status,
              'recoveryMaterializedAt',v_handoff.recovery_materialized_at,
              'recoveryRowCount',v_handoff.recovery_row_count);
        END IF;
        IF NOT FOUND OR v_handoff.status<>'PREPARING' OR v_handoff.source_server_instance_id<>v_instance_id THEN
            RAISE EXCEPTION 'Handoff is not awaiting source readiness.' USING ERRCODE='55000';
        END IF;
        IF v_source.last_materialization_at IS NULL THEN
            RAISE EXCEPTION 'A verified cloud materialization is required.' USING ERRCODE='55000';
        END IF;
        UPDATE public.store_server_handoffs SET status='READY',ready_at=now(),
          recovery_materialized_at=v_source.last_materialization_at,recovery_row_count=v_source.materialized_row_count
          WHERE handoff_id=v_handoff.handoff_id;
        INSERT INTO public.store_server_events(location_id,server_instance_id,handoff_id,event_type,actor_user_id,actor_name,details)
        VALUES(v_location_id,v_instance_id,v_handoff.handoff_id,'HANDOFF_READY',v_handoff.requested_by_user_id,
          v_handoff.requested_by_name,'Final sync and cloud materialization verified.');
        RETURN jsonb_build_object('handoffId',v_handoff.handoff_id,'status','READY',
          'recoveryMaterializedAt',v_source.last_materialization_at,'recoveryRowCount',v_source.materialized_row_count);
    END IF;

    IF v_action = 'COMPLETE_HANDOFF' THEN
        SELECT * INTO v_handoff FROM public.store_server_handoffs
          WHERE handoff_id=nullif(p_payload->>'handoff_id','')::uuid AND location_id=v_location_id FOR UPDATE;
        IF FOUND AND v_handoff.status='COMPLETED' AND v_handoff.target_server_instance_id=v_instance_id THEN
            SELECT generation INTO v_generation FROM public.store_server_instances
              WHERE server_instance_id=v_instance_id;
            RETURN jsonb_build_object('handoffId',v_handoff.handoff_id,'status','COMPLETED','generation',v_generation);
        END IF;
        IF NOT FOUND OR v_handoff.status<>'READY' OR v_handoff.target_server_instance_id<>v_instance_id THEN
            RAISE EXCEPTION 'Handoff is not ready for this replacement server.' USING ERRCODE='55000';
        END IF;
        SELECT coalesce(max(generation),0)+1 INTO v_generation FROM public.store_server_instances WHERE location_id=v_location_id;
        UPDATE public.store_server_instances SET role='RETIRED',retired_at=now(),
          replaced_by_server_instance_id=v_instance_id,updated_at=now()
          WHERE server_instance_id=v_handoff.source_server_instance_id;
        UPDATE public.store_server_instances SET role='PRIMARY',generation=v_generation,last_heartbeat_at=now(),updated_at=now()
          WHERE server_instance_id=v_instance_id;
        UPDATE public.store_server_handoffs SET status='COMPLETED',completed_at=now() WHERE handoff_id=v_handoff.handoff_id;
        INSERT INTO public.store_server_events(location_id,server_instance_id,handoff_id,event_type,actor_user_id,actor_name,details)
        VALUES(v_location_id,v_instance_id,v_handoff.handoff_id,'HANDOFF_COMPLETED',v_handoff.requested_by_user_id,
          v_handoff.requested_by_name,'Replacement server activated.');
        RETURN jsonb_build_object('handoffId',v_handoff.handoff_id,'status','COMPLETED','generation',v_generation);
    END IF;

    IF v_action = 'FAIL_HANDOFF' THEN
        SELECT * INTO v_handoff FROM public.store_server_handoffs
          WHERE handoff_id=nullif(p_payload->>'handoff_id','')::uuid AND location_id=v_location_id FOR UPDATE;
        IF FOUND AND v_handoff.status='FAILED' THEN
            RETURN jsonb_build_object('handoffId',v_handoff.handoff_id,'status','FAILED');
        END IF;
        IF NOT FOUND OR v_handoff.status NOT IN ('PREPARING','READY') THEN
            RAISE EXCEPTION 'Open handoff was not found.' USING ERRCODE='P0002';
        END IF;
        UPDATE public.store_server_handoffs SET status='FAILED',failure_message=left(coalesce(p_payload->>'failure_message','Handoff cancelled.'),2000)
          WHERE handoff_id=v_handoff.handoff_id;
        UPDATE public.store_server_instances SET role='PRIMARY',updated_at=now()
          WHERE server_instance_id=v_handoff.source_server_instance_id AND role='DRAINING';
        INSERT INTO public.store_server_events(location_id,server_instance_id,handoff_id,event_type,actor_user_id,actor_name,details)
        VALUES(v_location_id,v_handoff.source_server_instance_id,v_handoff.handoff_id,'HANDOFF_FAILED',
          v_handoff.requested_by_user_id,v_handoff.requested_by_name,
          left(coalesce(p_payload->>'failure_message','Handoff cancelled.'),2000));
        RETURN jsonb_build_object('handoffId',v_handoff.handoff_id,'status','FAILED');
    END IF;

    IF v_action = 'EMERGENCY_TAKEOVER' THEN
        IF coalesce((p_payload->>'warning_acknowledged')::boolean,false) IS NOT TRUE THEN
            RAISE EXCEPTION 'Emergency recovery warning must be acknowledged.' USING ERRCODE='22023';
        END IF;
        IF v_source.role <> 'STANDBY' THEN RAISE EXCEPTION 'Replacement server is not a standby.' USING ERRCODE='55000'; END IF;
        SELECT * INTO v_target FROM public.store_server_instances
          WHERE location_id=v_location_id AND role='PRIMARY' FOR UPDATE;
        IF FOUND AND v_target.last_heartbeat_at >= now()-interval '2 minutes' THEN
            RAISE EXCEPTION 'The current primary is still online; use verified handoff.' USING ERRCODE='55000';
        END IF;
        IF NOT FOUND OR v_target.last_materialization_at IS NULL THEN
            RAISE EXCEPTION 'No verified recovery materialization is available.' USING ERRCODE='55000';
        END IF;
        IF v_source.recovery_validated_at IS NULL
           OR v_source.recovery_materialization_at IS DISTINCT FROM v_target.last_materialization_at
           OR v_source.recovery_network_checked_at < now()-interval '15 minutes' THEN
            RAISE EXCEPTION 'The standby has not restored and validated the latest recovery point.' USING ERRCODE='55000';
        END IF;
        SELECT coalesce(max(generation),0)+1 INTO v_generation FROM public.store_server_instances WHERE location_id=v_location_id;
        INSERT INTO public.store_server_handoffs(location_id,source_server_instance_id,target_server_instance_id,status,
          emergency,requested_by_user_id,requested_by_name,idempotency_key,recovery_materialized_at,recovery_row_count,
          warning_acknowledged,completed_at)
        VALUES(v_location_id,v_target.server_instance_id,v_instance_id,'COMPLETED',true,
          nullif(p_payload->>'actor_user_id','')::integer,nullif(p_payload->>'actor_name',''),p_payload->>'idempotency_key',
          v_target.last_materialization_at,v_target.materialized_row_count,true,now()) RETURNING * INTO v_handoff;
        UPDATE public.store_server_instances SET role='FENCED',replaced_by_server_instance_id=v_instance_id,updated_at=now()
          WHERE server_instance_id=v_target.server_instance_id;
        UPDATE public.store_server_instances SET role='PRIMARY',generation=v_generation,last_heartbeat_at=now(),updated_at=now()
          WHERE server_instance_id=v_instance_id;
        INSERT INTO public.store_server_events(location_id,server_instance_id,handoff_id,event_type,actor_user_id,actor_name,details)
        VALUES(v_location_id,v_instance_id,v_handoff.handoff_id,'EMERGENCY_TAKEOVER',v_handoff.requested_by_user_id,
          v_handoff.requested_by_name,'Offline primary fenced; administrator acknowledged possible unsynced data.');
        RETURN jsonb_build_object('handoffId',v_handoff.handoff_id,'status','COMPLETED','generation',v_generation,
          'recoveryMaterializedAt',v_target.last_materialization_at,'recoveryRowCount',v_target.materialized_row_count);
    END IF;

    RAISE EXCEPTION 'Unsupported server registry action.' USING ERRCODE='22023';
END
$$;

REVOKE ALL ON FUNCTION smartstock_private.smartstock_server_registry(text,jsonb)
    FROM PUBLIC, anon, authenticated;
GRANT USAGE ON SCHEMA smartstock_private TO service_role;
GRANT EXECUTE ON FUNCTION smartstock_private.smartstock_server_registry(text,jsonb) TO service_role;

CREATE OR REPLACE FUNCTION public.smartstock_server_registry(
    p_action text,
    p_payload jsonb DEFAULT '{}'::jsonb
)
RETURNS jsonb
LANGUAGE sql
SECURITY INVOKER
SET search_path TO ''
AS $$
    SELECT smartstock_private.smartstock_server_registry(p_action,p_payload)
$$;

REVOKE ALL ON FUNCTION public.smartstock_server_registry(text,jsonb) FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.smartstock_server_registry(text,jsonb) TO service_role;
