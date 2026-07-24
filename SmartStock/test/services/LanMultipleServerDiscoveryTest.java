package services;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LanMultipleServerDiscoveryTest {
    @AfterEach
    void clearEnvironment() {
        System.clearProperty("smartstock.environment");
    }

    @Test
    void serverChoiceShowsUsefulDetailsWithoutPairingMaterial() {
        var server = server("development", "Main Street", "0001",
                "store-server-1", "A1B2C3D4");

        String label = server.toString();
        assertTrue(label.contains("Main Street (0001)"));
        assertTrue(label.contains("store-server-1"));
        assertTrue(label.contains("Developer/Test"));
        assertTrue(label.contains("A1B2C3D4"));
        assertFalse(label.contains("pairing-proof"));
        assertFalse(label.contains("fingerprint-value"));
    }

    @Test
    void discoveryFiltersServersByTheRegistersActiveEnvironment() {
        System.setProperty("smartstock.environment", "production");

        assertTrue(LanApiClient.matchesActiveEnvironment(
                server("production", "Live Store", "0001", "live-server", "LIVE0001")));
        assertFalse(LanApiClient.matchesActiveEnvironment(
                server("development", "Test Store", "9001", "dev-server", "DEV00001")));
    }

    private static LanApiClient.DiscoveredServer server(
            String environment, String storeName, String storeCode,
            String computerName, String serverId) {
        return new LanApiClient.DiscoveredServer(
                "SmartStock LAN Service", computerName + ".local", 8443,
                environment, storeName, storeCode, computerName, serverId,
                "fingerprint-value", "pairing-proof", "previous-proof");
    }
}
