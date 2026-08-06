-- Cover registry foreign keys used by history, replacement, and handoff lookups.
CREATE INDEX IF NOT EXISTS store_server_instances_replaced_by_idx
    ON public.store_server_instances(replaced_by_server_instance_id);
CREATE INDEX IF NOT EXISTS store_server_handoffs_source_idx
    ON public.store_server_handoffs(source_server_instance_id);
CREATE INDEX IF NOT EXISTS store_server_handoffs_target_idx
    ON public.store_server_handoffs(target_server_instance_id);
CREATE INDEX IF NOT EXISTS store_server_events_location_created_idx
    ON public.store_server_events(location_id, created_at DESC);
CREATE INDEX IF NOT EXISTS store_server_events_instance_idx
    ON public.store_server_events(server_instance_id);
CREATE INDEX IF NOT EXISTS store_server_events_handoff_idx
    ON public.store_server_events(handoff_id);
