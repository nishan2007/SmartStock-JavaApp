package ui.screens;

import org.junit.jupiter.api.Test;

import java.time.YearMonth;

import static org.junit.jupiter.api.Assertions.*;

class TimeClockPrivacyTest {
    @Test void cacheIsPartitionedByEmployeeStoreAndMonth() {
        String first = TimeClock.timeClockCacheKey(10, 1, YearMonth.of(2026, 8));
        assertNotEquals(first, TimeClock.timeClockCacheKey(11, 1, YearMonth.of(2026, 8)));
        assertNotEquals(first, TimeClock.timeClockCacheKey(10, 2, YearMonth.of(2026, 8)));
        assertNotEquals(first, TimeClock.timeClockCacheKey(10, 1, YearMonth.of(2026, 9)));
    }

    @Test void sessionRowsMustBelongToTheSignedInEmployee() {
        assertTrue(TimeClock.belongsToUser(42, 42));
        assertFalse(TimeClock.belongsToUser(41, 42));
        assertFalse(TimeClock.belongsToUser(42, null));
    }

    @Test void serverTimeClockDashboardNeverRequestsOtherEmployees() throws Exception {
        String server = java.nio.file.Files.readString(
                java.nio.file.Path.of("src/services/LanApiServer.java"));
        String endpoint = server.substring(server.indexOf("private ApiResult timeClockDashboard"),
                server.indexOf("private ApiResult timeClockPunchState"));
        assertTrue(endpoint.contains("ServerTimeClockManager.loadDashboard(c,false)"));
        assertFalse(endpoint.contains("TIME_CLOCK_MANAGEMENT"));
        assertFalse(endpoint.contains("EMPLOYEE_MANAGEMENT"));
        assertFalse(endpoint.contains("ROLE_MANAGEMENT"));
    }
}
