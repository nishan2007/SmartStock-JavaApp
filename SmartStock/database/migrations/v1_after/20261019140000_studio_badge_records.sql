-- Private store-local badge history; excluded from reference synchronization.
CREATE TABLE IF NOT EXISTS studio_badge_print_records (
 record_id UUID PRIMARY KEY, location_id INTEGER NOT NULL, template_id UUID NOT NULL,
 created_by INTEGER NOT NULL, created_at TIMESTAMPTZ NOT NULL DEFAULT now(), payload JSONB NOT NULL
);
CREATE INDEX IF NOT EXISTS studio_badge_records_template_time_idx
 ON studio_badge_print_records(location_id,template_id,created_at DESC,record_id);
REVOKE ALL ON studio_badge_print_records FROM PUBLIC;
