-- wifi_sessions uses a UUID location identity and belongs to the separate
-- captive-portal workflow. It is neither a SmartStock store-recovery table nor
-- eligible for the POS legacy cleanup.

INSERT INTO smartstock_private.cloud_object_manifest(
    object_type, object_name, disposition, rationale
)
VALUES (
    'TABLE', 'wifi_sessions', 'RETAIN',
    'Separate captive-portal workflow with UUID locations; outside SmartStock POS cleanup.'
)
ON CONFLICT (object_type, object_name) DO UPDATE
SET disposition = EXCLUDED.disposition,
    rationale = EXCLUDED.rationale,
    quarantine_started_at = NULL;

