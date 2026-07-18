package services;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import ui.helpers.SessionDataCache;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertTrue;

class LanApiMutationInvalidationTest {
    @AfterEach
    void clear() {
        SessionDataCache.clear();
    }

    @Test
    void readsPreserveCacheButSuccessfulMutationsInvalidateIt() {
        SessionDataCache.setEndpoint("https://test:8443");
        SessionDataCache.setScope("user=1|location=1");
        SessionDataCache.put("inventory:list", "cached");
        LanApiClient.invalidateCachesAfterMutation("/v1/inventory/list");
        assertTrue(SessionDataCache.get("inventory:list", String.class, Duration.ofMinutes(1)).isPresent());

        LanApiClient.invalidateCachesAfterMutation("/v1/products/update");
        assertTrue(SessionDataCache.get("inventory:list", String.class, Duration.ofMinutes(1)).isEmpty());
    }

    @Test
    void businessMutationsInvalidateSessionSnapshots() {
        for (String route : java.util.List.of(
                "/v1/sales/complete", "/v1/returns/return", "/v1/transfers/receive",
                "/v1/payroll/bonus", "/v1/custom-orders/workflow/mutation")) {
            SessionDataCache.put("screen:snapshot", route);
            LanApiClient.invalidateCachesAfterMutation(route);
            assertTrue(SessionDataCache.get("screen:snapshot", String.class, Duration.ofMinutes(1)).isEmpty(), route);
        }
    }
}
