package services;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LanSyncAdminServiceTest {
    @Test
    void recognizesFreshWindowsServiceLoopAsRunningSyncEngine() {
        Instant now = Instant.parse("2026-08-07T14:00:00Z");
        var service = new SyncServiceStatusService.ServiceInfo(
                "Running", "Cloud reachable", now.minusSeconds(280), now.minusSeconds(280));

        assertTrue(LanSyncAdminService.isFreshServiceLoop(service, now, 300));
    }

    @Test
    void rejectsStoppedOrStaleServiceLoop() {
        Instant now = Instant.parse("2026-08-07T14:00:00Z");
        assertFalse(LanSyncAdminService.isFreshServiceLoop(
                new SyncServiceStatusService.ServiceInfo(
                        "Stopped", "Stopped", now, now.minusSeconds(10)), now, 60));
        assertFalse(LanSyncAdminService.isFreshServiceLoop(
                new SyncServiceStatusService.ServiceInfo(
                        "Running", "Old heartbeat", now, now.minusSeconds(1_000)), now, 60));
    }
}
