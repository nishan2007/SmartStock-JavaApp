package services;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class ServerStoreCloudIntegrationTest {
    @Test
    void configuredEnvironmentReturnsAtLeastOneExistingStore() throws Exception {
        assumeTrue(Boolean.getBoolean("smartstock.test.cloudStores"),
                "Enable only for the configured server-only integration environment.");

        assertFalse(ServerStoreSetupService.listCloud().isEmpty(),
                "The configured SmartStock environment should contain an existing store.");
    }
}
