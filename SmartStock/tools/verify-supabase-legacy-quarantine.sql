-- Read-only verification for the quarantine observation window.

SELECT smartstock_private.assert_legacy_cleanup_ready(interval '24 hours');
SELECT smartstock_private.assert_legacy_quarantine_intact(85, interval '0 seconds');

-- At the final deletion review, separately run this after the full 14 days:
-- SELECT smartstock_private.assert_legacy_quarantine_intact(85, interval '14 days');

SELECT inventory.object_name, inventory.object_schema,
       inventory.public_exists, inventory.legacy_exists,
       object_exists, estimated_rows, total_bytes,
       manifest.quarantine_expected_present,
       inventory.quarantine_started_at, inventory.last_verified_at
FROM smartstock_private.cloud_object_inventory inventory
JOIN smartstock_private.cloud_object_manifest manifest
  ON manifest.object_type = inventory.object_type
 AND manifest.object_name = inventory.object_name
WHERE inventory.disposition = 'LEGACY_CANDIDATE'
ORDER BY inventory.object_name;

SELECT disposition, object_schema, count(*) AS object_count
FROM smartstock_private.cloud_object_inventory
GROUP BY disposition, object_schema
ORDER BY disposition, object_schema;

SELECT generation.location_id,
       generation.generation_id,
       generation.active_row_count,
       generation.completed_at,
       status.verified_at,
       status.credentials_verified_at,
       pg_catalog.now() - generation.completed_at AS recovery_age
FROM public.smartstock_store_mirror_status status
JOIN public.smartstock_store_snapshot_generations generation
  ON generation.generation_id = status.current_generation_id
 AND generation.status = 'COMPLETE'
ORDER BY generation.location_id;
