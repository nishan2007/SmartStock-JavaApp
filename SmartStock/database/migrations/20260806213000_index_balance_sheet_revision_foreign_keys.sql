CREATE INDEX IF NOT EXISTS balance_sheet_revision_location_idx
    ON balance_sheet_submission_revisions(location_id);

CREATE INDEX IF NOT EXISTS balance_sheet_revision_changed_by_idx
    ON balance_sheet_submission_revisions(changed_by_user_id);

CREATE INDEX IF NOT EXISTS balance_sheet_submission_last_editor_idx
    ON balance_sheet_submissions(last_edited_by_user_id);
