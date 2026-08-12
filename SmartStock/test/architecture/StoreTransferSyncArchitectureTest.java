package architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StoreTransferSyncArchitectureTest {
    private static String source(String path) throws Exception {
        return Files.readString(Path.of(path));
    }

    @Test
    void transferIdentityIsGlobalWhileLocalIdsRemainStoreOwned() throws Exception {
        String localMigration=source("database/migrations/v1_after/20260811233000_add_store_transfer_uuid.sql");
        String service=source("src/services/CrossStoreTransferSyncService.java");
        assertTrue(localMigration.contains("transfer_uuid uuid"));
        assertTrue(localMigration.contains("UNIQUE INDEX"));
        assertTrue(service.contains("RETURNING transfer_id"));
        assertTrue(service.contains("WHERE transfer_uuid=?"));
        assertFalse(service.contains("source_transfer_id) VALUES"));
    }

    @Test
    void completeTransfersAndReceiptsAreRoutedAndApplied() throws Exception {
        String service=source("src/services/CrossStoreTransferSyncService.java");
        String cloudMigration=source("database/migrations/v1_after/20260811233100_route_store_transfer_receipts.sql");
        String worker=source("src/services/SyncWorker.java");
        assertTrue(service.contains("payload.add(\"items\", items)"));
        assertTrue(service.contains("STORE_TRANSFER_RECEIVED"));
        assertTrue(service.contains("status IN ('RECEIVED','FAILED')"));
        assertTrue(service.contains("IGNORED_LEGACY"));
        assertFalse(service.contains("received_by_name=?,receive_id=?"),
                "The source store must not reference the destination receiving batch.");
        assertTrue(cloudMigration.contains("destination_location_id"));
        assertTrue(cloudMigration.contains("source_location_id"));
        assertTrue(cloudMigration.contains("REVOKE ALL ON FUNCTION"));
        assertTrue(cloudMigration.contains("TO service_role"));
        assertTrue(worker.contains("CrossStoreTransferSyncService.announcePending"));
        assertTrue(worker.contains("CrossStoreTransferSyncService.applyInbox"));
    }

    @Test
    void localTransferMutationsEmitFullEvents() throws Exception {
        String transfer=source("src/services/LanTransferService.java");
        assertTrue(transfer.contains("CrossStoreTransferSyncService.announceTransfer"));
        assertTrue(transfer.contains("CrossStoreTransferSyncService.recordReceived"));
        assertFalse(transfer.contains("SyncOutboxService.recordEvent(c,\"STORE_TRANSFER_CREATED\""));
    }

    @Test
    void sharedLocationsAndSchedulesUseLiveRowReplication() throws Exception {
        String service=source("src/services/CrossStoreReferenceSyncService.java");
        String worker=source("src/services/SyncWorker.java");
        String cloud=source("database/migrations/v1_after/20260811233100_route_store_transfer_receipts.sql");
        assertTrue(service.contains("\"locations\""));
        assertTrue(service.contains("\"employee_schedule_shifts\""));
        assertTrue(service.contains("\"employee_schedule_holidays\""));
        assertTrue(service.contains("\"employee_schedule_assignments\""));
        assertTrue(service.contains("WHERE locations.updated_at<EXCLUDED.updated_at"));
        assertTrue(service.contains("sync_tombstones"));
        assertTrue(service.contains("REFERENCE_ROW_CHANGED"));
        assertTrue(worker.contains("CrossStoreReferenceSyncService.announceChanges"));
        assertTrue(worker.contains("CrossStoreReferenceSyncService.applyInbox"));
        assertTrue(cloud.contains("'REFERENCE_ROW_CHANGED'"));
    }

    @Test
    void timeClockAndPayrollUseCollisionSafeCrossStoreRows() throws Exception {
        String migration=source("database/migrations/v1_after/20260811233200_add_cross_store_time_clock_identity.sql");
        String service=source("src/services/CrossStoreReferenceSyncService.java");
        String manager=source("src/managers/ServerTimeClockManager.java");
        assertTrue(migration.contains("clock_uuid uuid"));
        assertTrue(migration.contains("payroll_payments_sync_uuid_uidx"));
        assertTrue(service.contains("new TableSnapshot(\"employee_time_clock\""));
        assertTrue(service.contains("new TableSnapshot(\"employee_time_clock_adjustments\""));
        assertTrue(service.contains("new TableSnapshot(\"users\""));
        assertTrue(service.contains("new TableSnapshot(\"user_locations\""));
        assertTrue(service.contains("new TableSnapshot(\"employee_payroll_settings\""));
        assertTrue(service.contains("new TableSnapshot(\"employee_payroll_bonuses\""));
        assertTrue(service.contains("new TableSnapshot(\"payroll_payments\""));
        assertTrue(service.contains("ON CONFLICT(clock_uuid) DO UPDATE"));
        assertTrue(service.contains("-'clock_id'") || service.contains("- 'clock_id'"));
        assertTrue(service.contains("'password_hash','password_cache_invalidated_at','employee_pin_salt'"));
        assertTrue(service.contains("'badge_secret_hash']::text[]"));
        assertTrue(service.contains("payload.add(\"protected_credentials\""));
        assertTrue(service.contains("applyProtectedCredentials"));
        assertFalse(service.contains("'password_hash',password_hash"));
        assertTrue(service.contains("ON CONFLICT(user_id,pay_period_start,pay_period_end,payment_number) DO NOTHING"),
                "Existing paycheck business identities must make cloud replays idempotent.");
        assertFalse(manager.contains("WHERE tc.location_id = ?"));
        assertFalse(manager.contains("FROM employee_payroll_bonuses\n                WHERE location_id = ?"));
        assertTrue(manager.contains("Integer payrollLocationId = request().locationId()"));
    }

    @Test
    void failedInboxRowsDegradeTheVisibleSyncHealth() throws Exception {
        String worker=source("src/services/SyncWorker.java");
        assertTrue(worker.contains("FROM sync_inbox WHERE status='FAILED'"));
        assertTrue(worker.contains("\"Degraded\""));
        assertTrue(worker.contains("operational event(s) failed to apply"));
    }
}
