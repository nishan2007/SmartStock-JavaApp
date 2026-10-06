package services;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class StoreRoleRecoveryArchitectureTest {
    @Test
    void repairsBeforeMirroringOrPublishingAndUpdaterReplacesServicePayload() throws Exception {
        String worker = Files.readString(Path.of("src/services/SyncWorker.java"));
        int repair = worker.indexOf("StoreRoleRecoveryService.repairIfNeeded");
        assertTrue(repair > 0);
        assertTrue(repair < worker.indexOf("CloudRowMirrorService.synchronize"));
        assertTrue(repair < worker.indexOf("CrossStoreReferenceSyncService.announceChanges"));
        String updater = Files.readString(Path.of("src/app/SmartStockUpdater.java"));
        assertTrue(updater.contains("updateSyncServiceCopy(appDir, props)"));
    }
}
