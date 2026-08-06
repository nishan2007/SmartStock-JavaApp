DO $$
DECLARE
    test_location INTEGER;
    expired_id BIGINT;
    previous_id BIGINT;
    latest_id BIGINT;
    is_editable BOOLEAN;
BEGIN
    SELECT MIN(location_id) INTO test_location FROM locations;
    IF test_location IS NULL THEN RAISE EXCEPTION 'A location fixture is required'; END IF;
    IF NOT (SELECT relrowsecurity FROM pg_class WHERE oid='balance_sheet_submission_revisions'::regclass) THEN
        RAISE EXCEPTION 'Revision audit table does not have RLS enabled';
    END IF;
    IF has_function_privilege('public','prevent_balance_sheet_revision_changes()','EXECUTE') THEN
        RAISE EXCEPTION 'Immutable audit trigger function is executable by PUBLIC';
    END IF;
    IF EXISTS(SELECT 1 FROM pg_roles WHERE rolname='authenticated')
       AND has_table_privilege('authenticated','balance_sheet_submission_revisions','SELECT') THEN
        RAISE EXCEPTION 'Authenticated clients can read the internal revision audit table';
    END IF;
    IF NOT EXISTS(SELECT 1 FROM permissions WHERE permission_key='EDIT_BALANCE_SHEET') THEN
        RAISE EXCEPTION 'EDIT_BALANCE_SHEET permission was not installed';
    END IF;

    INSERT INTO balance_sheet_submissions(location_id,period_start,period_end,submitted_at,notes)
    VALUES(test_location,CURRENT_DATE-3,CURRENT_DATE-3,CURRENT_TIMESTAMP-INTERVAL '49 hours','editable-sheet-test-expired')
    RETURNING balance_sheet_submission_id INTO expired_id;
    SELECT CURRENT_TIMESTAMP < submitted_at+INTERVAL '48 hours' INTO is_editable
    FROM balance_sheet_submissions WHERE balance_sheet_submission_id=expired_id;
    IF is_editable THEN RAISE EXCEPTION '49-hour submission was incorrectly editable'; END IF;

    INSERT INTO balance_sheet_submissions(location_id,period_start,period_end,submitted_at,notes)
    VALUES(test_location,CURRENT_DATE-1,CURRENT_DATE-1,CURRENT_TIMESTAMP-INTERVAL '1 hour','editable-sheet-test-previous')
    RETURNING balance_sheet_submission_id INTO previous_id;
    INSERT INTO balance_sheet_submissions(location_id,period_start,period_end,submitted_at,notes)
    VALUES(test_location,CURRENT_DATE,CURRENT_DATE,CURRENT_TIMESTAMP,'editable-sheet-test-latest')
    RETURNING balance_sheet_submission_id INTO latest_id;

    SELECT CURRENT_TIMESTAMP < current_sheet.submitted_at+INTERVAL '48 hours'
           AND NOT EXISTS(SELECT 1 FROM balance_sheet_submissions newer
                          WHERE newer.location_id=current_sheet.location_id
                            AND (newer.submitted_at>current_sheet.submitted_at OR
                                 (newer.submitted_at=current_sheet.submitted_at AND newer.balance_sheet_submission_id>current_sheet.balance_sheet_submission_id)))
    INTO is_editable FROM balance_sheet_submissions current_sheet WHERE balance_sheet_submission_id=previous_id;
    IF is_editable THEN RAISE EXCEPTION 'Older submission remained editable after a newer submission'; END IF;

    SELECT CURRENT_TIMESTAMP < current_sheet.submitted_at+INTERVAL '48 hours'
           AND NOT EXISTS(SELECT 1 FROM balance_sheet_submissions newer
                          WHERE newer.location_id=current_sheet.location_id
                            AND (newer.submitted_at>current_sheet.submitted_at OR
                                 (newer.submitted_at=current_sheet.submitted_at AND newer.balance_sheet_submission_id>current_sheet.balance_sheet_submission_id)))
    INTO is_editable FROM balance_sheet_submissions current_sheet WHERE balance_sheet_submission_id=latest_id;
    IF NOT is_editable THEN RAISE EXCEPTION 'Latest fresh submission was not editable'; END IF;

    UPDATE balance_sheet_submissions SET revision_no=revision_no+1
    WHERE balance_sheet_submission_id=latest_id AND revision_no=999;
    IF FOUND THEN RAISE EXCEPTION 'Stale revision unexpectedly updated the submission'; END IF;

    INSERT INTO balance_sheet_submission_revisions(balance_sheet_submission_id,location_id,revision_no,reason,change_summary,before_snapshot,after_snapshot)
    VALUES(latest_id,test_location,1,'Integration test','Notes changed','{}','{}');

    BEGIN
        UPDATE balance_sheet_submission_revisions SET reason='tampered' WHERE balance_sheet_submission_id=latest_id;
        RAISE EXCEPTION 'Revision audit row was mutable';
    EXCEPTION WHEN raise_exception THEN
        IF SQLERRM='Revision audit row was mutable' THEN RAISE; END IF;
    END;

    BEGIN
        INSERT INTO balance_sheet_submission_revisions(balance_sheet_submission_id,location_id,revision_no,reason,change_summary,before_snapshot,after_snapshot)
        VALUES(latest_id,test_location,2,'   ','Invalid reason','{}','{}');
        RAISE EXCEPTION 'Blank audit reason was accepted';
    EXCEPTION WHEN check_violation THEN NULL;
    END;
END $$;
