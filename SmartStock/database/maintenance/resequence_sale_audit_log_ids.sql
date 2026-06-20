-- Compact sale_audit_log ids back into chronological order and reset the sequence.
-- Run on every synced database that contains the same sale_audit_log sync_uuid set.

BEGIN;

LOCK TABLE sale_audit_log IN ACCESS EXCLUSIVE MODE;

CREATE TEMP TABLE sale_audit_log_resequence_map ON COMMIT DROP AS
SELECT sale_audit_id AS old_id,
       ROW_NUMBER() OVER (
           ORDER BY created_at,
                    action_scope,
                    sale_id NULLS LAST,
                    sale_item_id NULLS LAST,
                    return_id NULLS LAST,
                    return_item_id NULLS LAST,
                    sale_audit_id
       )::BIGINT AS new_id
FROM sale_audit_log;

UPDATE sale_audit_log sal
SET sale_audit_id = map.old_id + (SELECT COALESCE(MAX(old_id), 0) + COUNT(*) + 1000 FROM sale_audit_log_resequence_map)
FROM sale_audit_log_resequence_map map
WHERE sal.sale_audit_id = map.old_id;

UPDATE sale_audit_log sal
SET sale_audit_id = map.new_id
FROM sale_audit_log_resequence_map map
WHERE sal.sale_audit_id = map.old_id + (SELECT COALESCE(MAX(old_id), 0) + COUNT(*) + 1000 FROM sale_audit_log_resequence_map);

DO $$
BEGIN
    IF to_regclass('sync_id_map') IS NOT NULL THEN
        DELETE FROM sync_id_map WHERE table_name = 'sale_audit_log';
    END IF;
END
$$;

SELECT setval(
    pg_get_serial_sequence('sale_audit_log', 'sale_audit_id'),
    GREATEST((SELECT COALESCE(MAX(sale_audit_id), 0) FROM sale_audit_log), 1),
    (SELECT COUNT(*) > 0 FROM sale_audit_log)
);

COMMIT;
