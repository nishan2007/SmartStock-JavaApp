-- Additive protocol; the existing full recovery snapshot API stays compatible.
ALTER TABLE public.smartstock_store_snapshot_generations
    ADD COLUMN IF NOT EXISTS table_fingerprints jsonb NOT NULL DEFAULT '{}'::jsonb;

CREATE OR REPLACE FUNCTION smartstock_private.snapshot_table_fingerprints(p_generation uuid, p_counts jsonb)
RETURNS jsonb LANGUAGE sql STABLE SECURITY INVOKER SET search_path = '' AS $$
    SELECT COALESCE(pg_catalog.jsonb_object_agg(t.key,
        pg_catalog.encode(pg_catalog.sha256(pg_catalog.convert_to(
            t.value || ':' || COALESCE((SELECT pg_catalog.string_agg(
                pg_catalog.jsonb_build_array(r.row_key,r.row_hash)::text, E'\n' ORDER BY r.row_key)
                FROM public.smartstock_store_snapshot_rows r
                WHERE r.generation_id=p_generation AND r.table_name=t.key), ''), 'UTF8')), 'hex')), '{}'::jsonb)
    FROM pg_catalog.jsonb_each_text(p_counts) t
$$;
REVOKE ALL ON FUNCTION smartstock_private.snapshot_table_fingerprints(uuid,jsonb) FROM PUBLIC, anon, authenticated;

CREATE OR REPLACE FUNCTION smartstock_private.complete_snapshot_fingerprints()
RETURNS trigger LANGUAGE plpgsql SECURITY INVOKER SET search_path = '' AS $$
BEGIN
    IF NEW.status='COMPLETE' AND OLD.status='BUILDING' THEN
        NEW.table_fingerprints := smartstock_private.snapshot_table_fingerprints(NEW.generation_id,NEW.table_counts);
    END IF;
    RETURN NEW;
END
$$;
REVOKE ALL ON FUNCTION smartstock_private.complete_snapshot_fingerprints() FROM PUBLIC, anon, authenticated;
DROP TRIGGER IF EXISTS complete_snapshot_fingerprints ON public.smartstock_store_snapshot_generations;
CREATE TRIGGER complete_snapshot_fingerprints BEFORE UPDATE ON public.smartstock_store_snapshot_generations
FOR EACH ROW EXECUTE FUNCTION smartstock_private.complete_snapshot_fingerprints();

UPDATE public.smartstock_store_snapshot_generations
SET table_fingerprints=smartstock_private.snapshot_table_fingerprints(generation_id,table_counts)
WHERE status='COMPLETE' AND table_fingerprints='{}'::jsonb;

CREATE OR REPLACE FUNCTION public.smartstock_store_snapshot_manifest(p_location_id integer)
RETURNS jsonb LANGUAGE sql STABLE SECURITY DEFINER SET search_path = '' AS $$
    SELECT pg_catalog.jsonb_build_object(
        'generation_id',s.current_generation_id,'completed_at',g.completed_at,
        'cross_store_protocol',1,
        'schema_version',m.baseline_version,
        'schema_ready',COALESCE(m.baseline_version=1
            AND m.resource_fingerprint_sha256 ~ '^[0-9a-f]{64}$'
            AND m.catalog_fingerprint_sha256 ~ '^[0-9a-f]{64}$'
            AND m.resource_fingerprint_sha256 <> pg_catalog.repeat('0',64)
            AND m.catalog_fingerprint_sha256 <> pg_catalog.repeat('0',64),false),
        'active_row_count',g.active_row_count,
        'tables',COALESCE((SELECT pg_catalog.jsonb_agg(pg_catalog.jsonb_build_object(
            'name',t.key,'row_count',t.value::bigint,'fingerprint',g.table_fingerprints->>t.key) ORDER BY t.key)
            FROM pg_catalog.jsonb_each_text(g.table_counts) t),'[]'::jsonb))
    FROM public.smartstock_store_mirror_status s
    JOIN public.smartstock_store_snapshot_generations g ON g.generation_id=s.current_generation_id AND g.status='COMPLETE'
    LEFT JOIN smartstock_private.smartstock_schema_metadata m ON m.schema_scope='CLOUD'
    WHERE s.location_id=p_location_id
$$;
REVOKE ALL ON FUNCTION public.smartstock_store_snapshot_manifest(integer) FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.smartstock_store_snapshot_manifest(integer) TO service_role;

-- Both RPCs validate the generation before returning even an empty page.
CREATE OR REPLACE FUNCTION public.smartstock_cross_store_row_hashes(
    p_location_id integer,p_generation_id uuid,p_table_name text,p_after_sequence bigint DEFAULT 0,p_limit integer DEFAULT 1000)
RETURNS jsonb LANGUAGE plpgsql STABLE SECURITY INVOKER SET search_path = '' AS $$
DECLARE result jsonb;
BEGIN
    IF p_table_name IS NULL OR p_table_name NOT IN ('products','product_barcodes','inventory','sales','sale_items',
        'sale_returns','sale_return_items','customer_account_transactions','custom_orders','quotations','invoices') THEN
        RAISE EXCEPTION 'Unsupported cross-store table';
    END IF;
    IF p_limit IS NULL OR p_limit<1 OR p_limit>1000 OR p_after_sequence IS NULL OR p_after_sequence<0 THEN
        RAISE EXCEPTION 'Invalid page bounds';
    END IF;
    PERFORM 1 FROM public.smartstock_store_snapshot_generations
    WHERE generation_id=p_generation_id AND location_id=p_location_id AND status='COMPLETE';
    IF NOT FOUND THEN RAISE EXCEPTION 'Completed snapshot is unavailable'; END IF;
    WITH page AS (
        SELECT row_key,row_hash,row_sequence FROM public.smartstock_store_snapshot_rows
        WHERE generation_id=p_generation_id AND table_name=p_table_name AND row_sequence>p_after_sequence
        ORDER BY row_sequence LIMIT p_limit
    ) SELECT pg_catalog.jsonb_build_object('generation_id',p_generation_id,
        'rows',COALESCE(pg_catalog.jsonb_agg(pg_catalog.jsonb_build_object('row_key',row_key,
            'row_hash',row_hash,'sequence',row_sequence) ORDER BY row_sequence),'[]'::jsonb),
        'next_cursor',COALESCE(pg_catalog.max(row_sequence),p_after_sequence)) INTO result FROM page;
    RETURN result;
END
$$;

CREATE OR REPLACE FUNCTION public.smartstock_cross_store_rows(
    p_location_id integer,p_generation_id uuid,p_table_name text,p_row_keys jsonb)
RETURNS jsonb LANGUAGE plpgsql STABLE SECURITY INVOKER SET search_path = '' AS $$
DECLARE result jsonb;
BEGIN
    IF p_table_name IS NULL OR p_table_name NOT IN ('products','product_barcodes','inventory','sales','sale_items',
        'sale_returns','sale_return_items','customer_account_transactions','custom_orders','quotations','invoices') THEN
        RAISE EXCEPTION 'Unsupported cross-store table';
    END IF;
    IF p_row_keys IS NULL OR pg_catalog.jsonb_typeof(p_row_keys)<>'array' THEN
        RAISE EXCEPTION 'Row keys must be an array';
    END IF;
    IF pg_catalog.jsonb_array_length(p_row_keys)>1000 THEN RAISE EXCEPTION 'Too many row keys'; END IF;
    PERFORM 1 FROM public.smartstock_store_snapshot_generations
    WHERE generation_id=p_generation_id AND location_id=p_location_id AND status='COMPLETE';
    IF NOT FOUND THEN RAISE EXCEPTION 'Completed snapshot is unavailable'; END IF;
    SELECT pg_catalog.jsonb_build_object('generation_id',p_generation_id,
        'rows',COALESCE(pg_catalog.jsonb_agg(pg_catalog.jsonb_build_object('row_key',r.row_key,
            'row_hash',r.row_hash,'row_data',r.row_data) ORDER BY r.row_key),'[]'::jsonb)) INTO result
    FROM public.smartstock_store_snapshot_rows r
    WHERE r.generation_id=p_generation_id AND r.table_name=p_table_name
      AND r.row_key IN (SELECT value FROM pg_catalog.jsonb_array_elements(p_row_keys));
    RETURN result;
END
$$;
REVOKE ALL ON FUNCTION public.smartstock_cross_store_row_hashes(integer,uuid,text,bigint,integer) FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION public.smartstock_cross_store_rows(integer,uuid,text,jsonb) FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.smartstock_cross_store_row_hashes(integer,uuid,text,bigint,integer) TO service_role;
GRANT EXECUTE ON FUNCTION public.smartstock_cross_store_rows(integer,uuid,text,jsonb) TO service_role;
-- Existing snapshot PK and page index cover both prepared-query access paths.
