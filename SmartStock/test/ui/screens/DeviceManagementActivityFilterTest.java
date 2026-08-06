package ui.screens;

import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeviceManagementActivityFilterTest {
    private static final Instant NOW = Instant.parse("2026-07-24T16:00:00Z");

    @Test
    void showAllIncludesDevicesWithoutActivity() {
        assertTrue(DeviceManagement.wasActiveWithinDays(null, 0, NOW));
    }

    @Test
    void activeWindowIncludesBoundaryAndRecentDevices() {
        assertTrue(DeviceManagement.wasActiveWithinDays(
                Timestamp.from(NOW.minusSeconds(5 * 24 * 60 * 60)), 5, NOW));
        assertTrue(DeviceManagement.wasActiveWithinDays(
                Timestamp.from(NOW.minusSeconds(60)), 5, NOW));
    }

    @Test
    void activeWindowHidesOlderAndNeverActiveDevices() {
        assertFalse(DeviceManagement.wasActiveWithinDays(
                Timestamp.from(NOW.minusSeconds(5 * 24 * 60 * 60 + 1)), 5, NOW));
        assertFalse(DeviceManagement.wasActiveWithinDays(null, 5, NOW));
    }
}
