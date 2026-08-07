package ui.screens;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SyncStatusTimingTest {
    @Test
    void calculatesNextSyncFromLastSuccessAndConfiguredInterval() {
        assertEquals(1_300_000L, SyncStatus.nextSyncEpochMillis(1_000_000L, 300_000L));
    }

    @Test
    void leavesNextSyncUnknownUntilAFirstSuccessExists() {
        assertEquals(0L, SyncStatus.nextSyncEpochMillis(0L, 300_000L));
    }
}
