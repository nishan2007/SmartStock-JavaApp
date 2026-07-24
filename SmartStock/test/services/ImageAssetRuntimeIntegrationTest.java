package services;

import data.DB;
import data.DatabaseConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Opt-in API-only verification against the configured SmartStock server.
 * Run with -Dsmartstock.runtime.image.test=true; normal unit test runs skip it.
 */
@EnabledIfSystemProperty(named = "smartstock.runtime.image.test", matches = "true")
class ImageAssetRuntimeIntegrationTest {
    @Test
    void verifiesCompleteSyncWorkerCycle() {
        SyncWorker.SyncStatus status = SyncWorker.runOnceNow();
        if (status.cloudReachable()) {
            assertNotNull(status.lastSuccess());
            assertTrue(status.message().startsWith("Cloud reachable"));
            return;
        }
        SyncServiceStatusService.ServiceInfo service = SyncWorker.latestStatus().serviceInfo();
        assertNotNull(service, "Sync failed: " + status.lastError());
        assertEquals("Running", service.status());
        assertNotNull(service.lastSeenAt());
        assertTrue(Duration.between(service.lastSeenAt(), Instant.now()).abs()
                .compareTo(Duration.ofMinutes(6)) < 0,
                "The persistent sync-service heartbeat is stale.");
    }

    @Test
    void verifiesLocalImageRegistryReachedApiMaterializedCloudMirror() throws Exception {
        DatabaseConfig config = DatabaseConfig.load();
        assertNotNull(config.locationId(), "A store assignment is required.");
        CloudSyncManifest hosted = CloudSyncManifest.fetchStoreSnapshot(config.locationId());
        try (Connection local = DB.getConnection()) {
            assertEquals(count(local, "image_assets"), hosted.rowCount("image_assets"));
            assertEquals(count(local, "image_asset_references"),
                    hosted.rowCount("image_asset_references"));
            ServerImageAssetService.Counts counts = ServerImageAssetService.counts(local);
            assertEquals(0, counts.pendingUploads());
            assertEquals(0, counts.missingLocal());
            assertEquals(0, counts.missingCloud());
        }
    }

    private static long count(Connection local, String table) throws Exception {
        try (PreparedStatement ps = local.prepareStatement(
                "SELECT COUNT(*) FROM \"" + table + "\"");
             ResultSet rs = ps.executeQuery()) {
            assertTrue(rs.next());
            return rs.getLong(1);
        }
    }
}
