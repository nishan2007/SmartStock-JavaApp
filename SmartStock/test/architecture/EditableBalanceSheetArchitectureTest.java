package architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class EditableBalanceSheetArchitectureTest {
    @Test
    void revisionsArePermissionedTimeLimitedAuditedAndEmailed() throws Exception {
        String setup=source("database/balance_sheet_expenses_setup.sql");
        String migration=source("database/migrations/20260806210000_editable_balance_sheet_revisions.sql");
        String server=source("src/services/ServerBalanceSheetService.java");
        String api=source("src/services/LanApiServer.java");
        String client=source("src/services/BalanceSheetService.java");
        String screen=source("src/ui/screens/BalanceSheet.java");
        String email=source("src/services/ServerEmailOutboxService.java");
        String sync=source("src/services/ReferenceDataSyncService.java");
        String runner=source("src/services/ServerSupabaseMigrationRunner.java");

        assertTrue(setup.contains("EDIT_BALANCE_SHEET"));
        assertTrue(setup.contains("balance_sheet_submission_revisions"));
        assertTrue(migration.contains("prevent_balance_sheet_revision_changes"));
        assertTrue(server.contains("submitted_at + INTERVAL '48 hours'"));
        assertTrue(server.contains("A newer Balance Sheet has already been submitted"));
        assertTrue(server.contains("expectedRevision() != locked.revisionNo()"));
        assertTrue(server.contains("before_snapshot,after_snapshot"));
        assertTrue(server.contains("BALANCE_SHEET_REVISED"));
        assertTrue(server.contains("balance_sheet_revision_changed_by_idx"));
        assertTrue(api.contains("requireAnyPermission(connection,session.userId(),\"EDIT_BALANCE_SHEET\")"));
        assertTrue(client.contains("mutate(\"REVISE\",body)"));
        assertTrue(screen.contains("Edit Latest Submission"));
        assertTrue(screen.contains("Reason for changes (required)"));
        assertTrue(email.contains("this replaces the previously emailed copy"));
        assertTrue(email.contains("after_snapshot->'sheet'"));
        assertTrue(email.contains("Edited by:"));
        assertTrue(email.contains("Reason:"));
        assertTrue(sync.contains("\"balance_sheet_submission_revisions\""));
        assertTrue(runner.contains("20260806210000_editable_balance_sheet_revisions.sql"));
        assertTrue(runner.contains("20260806213000_index_balance_sheet_revision_foreign_keys.sql"));
    }

    private static String source(String path) throws Exception { return Files.readString(Path.of(path)); }
}
