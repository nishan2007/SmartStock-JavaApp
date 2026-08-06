ALTER TABLE balance_sheet_submissions ADD COLUMN IF NOT EXISTS revision_no INTEGER NOT NULL DEFAULT 0;
ALTER TABLE balance_sheet_submissions ADD COLUMN IF NOT EXISTS last_edited_at TIMESTAMPTZ;
ALTER TABLE balance_sheet_submissions ADD COLUMN IF NOT EXISTS last_edited_by_user_id INTEGER REFERENCES users(user_id);
ALTER TABLE balance_sheet_submissions ADD COLUMN IF NOT EXISTS last_edited_by_name TEXT;

CREATE TABLE IF NOT EXISTS balance_sheet_submission_revisions (
    balance_sheet_revision_id BIGSERIAL PRIMARY KEY,
    balance_sheet_submission_id BIGINT NOT NULL REFERENCES balance_sheet_submissions(balance_sheet_submission_id),
    location_id INTEGER NOT NULL REFERENCES locations(location_id), revision_no INTEGER NOT NULL,
    action_type TEXT NOT NULL DEFAULT 'EDIT', reason TEXT NOT NULL, change_summary TEXT NOT NULL,
    before_snapshot JSONB NOT NULL, after_snapshot JSONB NOT NULL,
    changed_by_user_id INTEGER REFERENCES users(user_id), changed_by_name TEXT,
    device_id TEXT, device_name TEXT, changed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT balance_sheet_revision_reason_chk CHECK (LENGTH(TRIM(reason)) > 0),
    CONSTRAINT balance_sheet_revision_unique UNIQUE (balance_sheet_submission_id, revision_no)
);
CREATE INDEX IF NOT EXISTS balance_sheet_revision_submission_idx ON balance_sheet_submission_revisions(balance_sheet_submission_id, revision_no DESC);
ALTER TABLE balance_sheet_submission_revisions ADD COLUMN IF NOT EXISTS change_summary TEXT NOT NULL DEFAULT 'Balance Sheet revised';
CREATE OR REPLACE FUNCTION prevent_balance_sheet_revision_changes() RETURNS TRIGGER LANGUAGE plpgsql
SET search_path=pg_catalog,public AS $$
BEGIN RAISE EXCEPTION 'Balance sheet revision history is immutable'; END; $$;
REVOKE ALL ON FUNCTION prevent_balance_sheet_revision_changes() FROM PUBLIC;
DO $$ BEGIN
  IF EXISTS(SELECT 1 FROM pg_roles WHERE rolname='anon') THEN REVOKE ALL ON FUNCTION prevent_balance_sheet_revision_changes() FROM anon; END IF;
  IF EXISTS(SELECT 1 FROM pg_roles WHERE rolname='authenticated') THEN REVOKE ALL ON FUNCTION prevent_balance_sheet_revision_changes() FROM authenticated; END IF;
END $$;
DROP TRIGGER IF EXISTS balance_sheet_revisions_immutable ON balance_sheet_submission_revisions;
CREATE TRIGGER balance_sheet_revisions_immutable BEFORE UPDATE OR DELETE ON balance_sheet_submission_revisions
FOR EACH ROW EXECUTE FUNCTION prevent_balance_sheet_revision_changes();

INSERT INTO permissions(permission_key,permission_name,description,permission_group,permission_subgroup)
VALUES('EDIT_BALANCE_SHEET','Edit Submitted Balance Sheet','Allows revising the latest submitted Balance Sheet during its 48-hour edit window.','Operations','Cash Drawer')
ON CONFLICT(permission_key) DO UPDATE SET permission_name=EXCLUDED.permission_name,description=EXCLUDED.description,
permission_group=EXCLUDED.permission_group,permission_subgroup=EXCLUDED.permission_subgroup;
INSERT INTO role_permissions(role_id,permission_id)
SELECT r.role_id,p.permission_id FROM roles r JOIN permissions p ON p.permission_key='EDIT_BALANCE_SHEET'
WHERE UPPER(r.role_name) IN ('ADMIN','OWNER','CEO') OR UPPER(r.role_name) LIKE '%MANAGER%'
ON CONFLICT(role_id,permission_id) DO NOTHING;

ALTER TABLE balance_sheet_submission_revisions ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON balance_sheet_submission_revisions FROM PUBLIC;
DO $$ DECLARE revision_sequence TEXT; BEGIN
  IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname='anon') THEN REVOKE ALL ON balance_sheet_submission_revisions FROM anon; END IF;
  IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname='authenticated') THEN REVOKE ALL ON balance_sheet_submission_revisions FROM authenticated; END IF;
  IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname='service_role') THEN
    GRANT ALL ON balance_sheet_submission_revisions TO service_role;
    revision_sequence := pg_get_serial_sequence('balance_sheet_submission_revisions','balance_sheet_revision_id');
    IF revision_sequence IS NOT NULL THEN EXECUTE format('GRANT ALL ON SEQUENCE %s TO service_role',revision_sequence); END IF;
    DROP POLICY IF EXISTS balance_sheet_submission_revisions_service_role_all ON balance_sheet_submission_revisions;
    CREATE POLICY balance_sheet_submission_revisions_service_role_all ON balance_sheet_submission_revisions
      FOR ALL TO service_role USING (true) WITH CHECK (true);
  END IF;
END $$;
