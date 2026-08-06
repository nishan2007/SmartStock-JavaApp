\set ON_ERROR_STOP on

SELECT public.smartstock_server_registry('ENSURE_LOCATION',jsonb_build_object(
  'location_id',5,'store_name','Bootstrap Test Store','store_code','0005','timezone','America/New_York'));
DO $test$
BEGIN
    IF NOT EXISTS(SELECT 1 FROM public.locations WHERE location_id=5 AND receipt_store_code='0005') THEN
        RAISE EXCEPTION 'secured store identity bootstrap failed';
    END IF;
END
$test$;

SELECT (public.smartstock_server_registry('REGISTER_PRIMARY',jsonb_build_object(
  'location_id',1,'installation_id','primary-a','hostname','primary-a','certificate_fingerprint','fp-a','endpoint_host','primary-a.local'
))->>'serverInstanceId') AS primary_id \gset
CREATE TEMP TABLE registry_test_ids(name text PRIMARY KEY,id uuid NOT NULL);
INSERT INTO registry_test_ids(name,id) VALUES ('primary',:'primary_id');

DO $test$
BEGIN
    BEGIN
        PERFORM public.smartstock_server_registry('REGISTER_PRIMARY',jsonb_build_object(
          'location_id',1,'installation_id','primary-b','hostname','primary-b','certificate_fingerprint','fp-b','endpoint_host','primary-b.local'));
        RAISE EXCEPTION 'second primary was accepted';
    EXCEPTION WHEN unique_violation THEN NULL;
    END;
    IF has_table_privilege('service_role','public.store_server_instances','SELECT') THEN
        RAISE EXCEPTION 'service_role has forbidden direct registry table access';
    END IF;
    IF NOT has_function_privilege('service_role','public.smartstock_server_registry(text,jsonb)','EXECUTE') THEN
        RAISE EXCEPTION 'service_role cannot execute registry RPC';
    END IF;
    IF has_function_privilege('authenticated','public.smartstock_server_registry(text,jsonb)','EXECUTE') THEN
        RAISE EXCEPTION 'register role can execute server registry RPC';
    END IF;
END
$test$;

SELECT (public.smartstock_server_registry('REGISTER_STANDBY',jsonb_build_object(
  'location_id',1,'installation_id','standby-a','hostname','standby-a','certificate_fingerprint','fp-s','endpoint_host','standby-a.local'
))->>'serverInstanceId') AS standby_id \gset
INSERT INTO registry_test_ids(name,id) VALUES ('standby',:'standby_id');
SELECT public.smartstock_server_registry('PREPARE_STANDBY',jsonb_build_object(
  'location_id',1,'server_instance_id',:'standby_id','actor_user_id',7,'actor_name','Integration Admin'));

SELECT public.smartstock_server_registry('HEARTBEAT',jsonb_build_object(
  'location_id',1,'server_instance_id',:'primary_id','last_materialization_at',now(),'materialized_row_count',42));
SELECT (public.smartstock_server_registry('BEGIN_HANDOFF',jsonb_build_object(
  'location_id',1,'server_instance_id',:'primary_id','target_server_instance_id',:'standby_id','idempotency_key','online-1'
))->>'handoffId') AS handoff_id \gset
SELECT public.smartstock_server_registry('MARK_HANDOFF_READY',jsonb_build_object(
  'location_id',1,'server_instance_id',:'primary_id','handoff_id',:'handoff_id'));
SELECT public.smartstock_server_registry('MARK_HANDOFF_READY',jsonb_build_object(
  'location_id',1,'server_instance_id',:'primary_id','handoff_id',:'handoff_id'));
SELECT public.smartstock_server_registry('COMPLETE_HANDOFF',jsonb_build_object(
  'location_id',1,'server_instance_id',:'standby_id','handoff_id',:'handoff_id'));
SELECT public.smartstock_server_registry('COMPLETE_HANDOFF',jsonb_build_object(
  'location_id',1,'server_instance_id',:'standby_id','handoff_id',:'handoff_id'));

DO $test$
BEGIN
    IF (SELECT count(*) FROM public.store_server_instances WHERE location_id=1 AND role='PRIMARY') <> 1 THEN
        RAISE EXCEPTION 'online handoff did not leave exactly one primary';
    END IF;
    IF (SELECT role FROM public.store_server_instances
        WHERE server_instance_id=(SELECT id FROM registry_test_ids WHERE name='primary')) <> 'RETIRED' THEN
        RAISE EXCEPTION 'online handoff did not retire the source';
    END IF;
    IF (SELECT generation FROM public.store_server_instances
        WHERE server_instance_id=(SELECT id FROM registry_test_ids WHERE name='standby')) <> 2 THEN
        RAISE EXCEPTION 'online handoff generation mismatch';
    END IF;
    IF NOT EXISTS(SELECT 1 FROM public.store_server_events
        WHERE location_id=1 AND event_type='STANDBY_PREPARED' AND actor_name='Integration Admin') THEN
        RAISE EXCEPTION 'standby readiness approval was not audited';
    END IF;
END
$test$;

SELECT (public.smartstock_server_registry('REGISTER_PRIMARY',jsonb_build_object(
  'location_id',2,'installation_id','primary-offline','hostname','primary-offline','certificate_fingerprint','fp-o','endpoint_host','primary-offline.local'
))->>'serverInstanceId') AS offline_primary_id \gset
INSERT INTO registry_test_ids(name,id) VALUES ('offline-primary',:'offline_primary_id');
SELECT (public.smartstock_server_registry('REGISTER_STANDBY',jsonb_build_object(
  'location_id',2,'installation_id','recovery-a','hostname','recovery-a','certificate_fingerprint','fp-r','endpoint_host','recovery-a.local'
))->>'serverInstanceId') AS recovery_id \gset
INSERT INTO registry_test_ids(name,id) VALUES ('recovery',:'recovery_id');
SELECT public.smartstock_server_registry('HEARTBEAT',jsonb_build_object(
  'location_id',2,'server_instance_id',:'offline_primary_id','last_materialization_at',now()-interval '5 minutes','materialized_row_count',25));
UPDATE public.store_server_instances SET last_heartbeat_at=now()-interval '20 minutes'
 WHERE server_instance_id=:'offline_primary_id';
DO $test$
BEGIN
    BEGIN
        PERFORM public.smartstock_server_registry('EMERGENCY_TAKEOVER',jsonb_build_object(
          'location_id',2,'server_instance_id',(SELECT id FROM registry_test_ids WHERE name='recovery'),
          'idempotency_key','missing-recovery','warning_acknowledged',true));
        RAISE EXCEPTION 'takeover without restored recovery point was accepted';
    EXCEPTION WHEN SQLSTATE '55000' THEN NULL;
    END;
    BEGIN
        PERFORM public.smartstock_server_registry('EMERGENCY_TAKEOVER',jsonb_build_object(
          'location_id',2,'server_instance_id',(SELECT id FROM registry_test_ids WHERE name='recovery'),
          'idempotency_key','missing-ack','warning_acknowledged',false));
        RAISE EXCEPTION 'takeover without warning acknowledgement was accepted';
    EXCEPTION WHEN invalid_parameter_value THEN NULL;
    END;
END
$test$;
SELECT public.smartstock_server_registry('MARK_RECOVERY_READY',jsonb_build_object(
  'location_id',2,'server_instance_id',:'recovery_id'));
SELECT public.smartstock_server_registry('EMERGENCY_TAKEOVER',jsonb_build_object(
  'location_id',2,'server_instance_id',:'recovery_id','idempotency_key','emergency-1','warning_acknowledged',true));
SELECT public.smartstock_server_registry('EMERGENCY_TAKEOVER',jsonb_build_object(
  'location_id',2,'server_instance_id',:'recovery_id','idempotency_key','emergency-1','warning_acknowledged',true));

DO $test$
BEGIN
    IF (SELECT role FROM public.store_server_instances
        WHERE server_instance_id=(SELECT id FROM registry_test_ids WHERE name='offline-primary')) <> 'FENCED' THEN
        RAISE EXCEPTION 'emergency takeover did not fence the old primary';
    END IF;
    IF (SELECT role FROM public.store_server_instances
        WHERE server_instance_id=(SELECT id FROM registry_test_ids WHERE name='recovery')) <> 'PRIMARY' THEN
        RAISE EXCEPTION 'emergency takeover did not activate the standby';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM public.store_server_events WHERE location_id=2 AND event_type='EMERGENCY_TAKEOVER') THEN
        RAISE EXCEPTION 'emergency takeover audit event is missing';
    END IF;
END
$test$;

SELECT (public.smartstock_server_registry('REGISTER_PRIMARY',jsonb_build_object(
  'location_id',4,'installation_id','rollback-primary','hostname','rollback-primary','certificate_fingerprint','fp-rp','endpoint_host','rollback-primary.local'
))->>'serverInstanceId') AS rollback_primary_id \gset
INSERT INTO registry_test_ids(name,id) VALUES ('rollback-primary',:'rollback_primary_id');
SELECT (public.smartstock_server_registry('REGISTER_STANDBY',jsonb_build_object(
  'location_id',4,'installation_id','rollback-standby','hostname','rollback-standby','certificate_fingerprint','fp-rs','endpoint_host','rollback-standby.local'
))->>'serverInstanceId') AS rollback_standby_id \gset
INSERT INTO registry_test_ids(name,id) VALUES ('rollback-standby',:'rollback_standby_id');
SELECT (public.smartstock_server_registry('BEGIN_HANDOFF',jsonb_build_object(
  'location_id',4,'server_instance_id',:'rollback_primary_id','target_server_instance_id',:'rollback_standby_id','idempotency_key','rollback-1'
))->>'handoffId') AS rollback_handoff_id \gset
CREATE TEMP TABLE registry_test_handoffs(name text PRIMARY KEY,id uuid NOT NULL);
INSERT INTO registry_test_handoffs(name,id) VALUES ('rollback',:'rollback_handoff_id');
SELECT public.smartstock_server_registry('FAIL_HANDOFF',jsonb_build_object(
  'location_id',4,'server_instance_id',:'rollback_primary_id','handoff_id',:'rollback_handoff_id','failure_message','injected final sync failure'));
SELECT public.smartstock_server_registry('FAIL_HANDOFF',jsonb_build_object(
  'location_id',4,'server_instance_id',:'rollback_primary_id','handoff_id',:'rollback_handoff_id','failure_message','retry'));

DO $test$
BEGIN
    IF (SELECT role FROM public.store_server_instances
        WHERE server_instance_id=(SELECT id FROM registry_test_ids WHERE name='rollback-primary')) <> 'PRIMARY' THEN
        RAISE EXCEPTION 'failed handoff did not restore source primary';
    END IF;
    IF (SELECT status FROM public.store_server_handoffs
        WHERE handoff_id=(SELECT id FROM registry_test_handoffs WHERE name='rollback')) <> 'FAILED' THEN
        RAISE EXCEPTION 'failed handoff status was not retained';
    END IF;
END
$test$;

SELECT 'store server registry integration checks passed' AS result;
