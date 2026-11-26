
INSERT INTO public.permissions(permission_key,permission_name,description,permission_group,permission_subgroup)
VALUES ('REVERSE_PHONE_MINUTES','Reverse Phone Minutes','Reverse minutes with a reason and release paid credit or refund cash.','Point of Sale','Phone Minutes')
ON CONFLICT(permission_key) DO NOTHING;
INSERT INTO public.role_permissions(role_id,permission_id,updated_at)
SELECT r.role_id,p.permission_id,now() FROM public.roles r CROSS JOIN public.permissions p
WHERE upper(r.role_name)='ADMIN' AND p.permission_key='REVERSE_PHONE_MINUTES'
ON CONFLICT(role_id,permission_id) DO NOTHING;

CREATE OR REPLACE FUNCTION public.smartstock_cross_store_row_hashes(
    p_location_id integer,p_generation_id uuid,p_table_name text,p_after_sequence bigint DEFAULT 0,p_limit integer DEFAULT 1000)
RETURNS jsonb LANGUAGE plpgsql STABLE SECURITY INVOKER SET search_path = '' AS $$
DECLARE result jsonb;
BEGIN
    IF p_table_name IS NULL OR p_table_name NOT IN ('products','product_barcodes','inventory','sales','sale_items',
        'sale_returns','sale_return_items','customer_account_transactions','phone_minutes_sales','custom_orders','quotations','invoices') THEN
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
        'sale_returns','sale_return_items','customer_account_transactions','phone_minutes_sales','custom_orders','quotations','invoices') THEN
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
