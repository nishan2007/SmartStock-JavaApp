package ui.helpers;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionDataCacheTest {
    @AfterEach
    void clear() {
        SessionDataCache.setEndpoint("test-endpoint");
        SessionDataCache.setScope("test-default");
        SessionDataCache.clear();
    }

    @Test
    void endpointChangesIsolateCachedData() {
        SessionDataCache.setEndpoint("https://server-a:8443");
        SessionDataCache.put("inventory", "server-a-data");
        SessionDataCache.setEndpoint("https://server-b:8443");
        assertTrue(SessionDataCache.get("inventory", String.class, Duration.ofMinutes(1)).isEmpty());
    }

    @Test
    void cachedValueTracksFreshness() {
        SessionDataCache.setScope("employee-1|store-1");
        SessionDataCache.put("inventory", "snapshot");

        var fresh = SessionDataCache.get("inventory", String.class, Duration.ofMinutes(1)).orElseThrow();
        assertEquals("snapshot", fresh.value());
        assertTrue(fresh.fresh());

        var stale = SessionDataCache.get("inventory", String.class, Duration.ZERO).orElseThrow();
        assertFalse(stale.fresh());
    }

    @Test
    void scopeChangesClearPriorEmployeeData() {
        SessionDataCache.setScope("employee-1|store-1");
        SessionDataCache.put("customers", "private-data");
        SessionDataCache.setScope("employee-2|store-1");

        assertTrue(SessionDataCache.get("customers", String.class, Duration.ofMinutes(1)).isEmpty());
        assertEquals(0, SessionDataCache.size());
    }

    @Test
    void prefixInvalidationRemovesOnlyAffectedSnapshots() {
        SessionDataCache.setScope("employee-1|store-1");
        SessionDataCache.put("inventory:list", "inventory");
        SessionDataCache.put("inventory:lookups", "lookups");
        SessionDataCache.put("vendors:list", "vendors");

        SessionDataCache.invalidate("inventory:");

        assertTrue(SessionDataCache.get("inventory:list", String.class, Duration.ofMinutes(1)).isEmpty());
        assertTrue(SessionDataCache.get("inventory:lookups", String.class, Duration.ofMinutes(1)).isEmpty());
        assertTrue(SessionDataCache.get("vendors:list", String.class, Duration.ofMinutes(1)).isPresent());
    }

    @Test
    void cacheRemainsBoundedDuringManyFilteredSearches() {
        for (int index = 0; index < 600; index++) {
            SessionDataCache.put("search:" + index, "result-" + index);
        }
        assertTrue(SessionDataCache.size() <= 512);
    }
}
