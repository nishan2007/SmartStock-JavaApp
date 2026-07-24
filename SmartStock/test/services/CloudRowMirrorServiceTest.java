package services;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CloudRowMirrorServiceTest {
    @Test
    void excludesOnlyDerivedDeviceActivityFromDurableMirror() {
        assertTrue(CloudRowMirrorService.excludedOperationalColumn("devices", "last_seen"));
        assertTrue(CloudRowMirrorService.excludedOperationalColumn("devices", "updated_at"));
        assertTrue(CloudRowMirrorService.excludedOperationalColumn("devices", "session_count"));

        assertFalse(CloudRowMirrorService.excludedOperationalColumn("devices", "is_approved"));
        assertFalse(CloudRowMirrorService.excludedOperationalColumn("devices", "last_store_id"));
        assertFalse(CloudRowMirrorService.excludedOperationalColumn(
                "customer_account_transactions", "updated_at"));
    }
}
