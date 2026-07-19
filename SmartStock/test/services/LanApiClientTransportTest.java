package services;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

class LanApiClientTransportTest {
    @Test
    void discoveryUsesReachablePacketSourceInsteadOfAdvertisedInterface() {
        LanApiClient.DiscoveredServer advertised = new LanApiClient.DiscoveredServer(
                "SmartStock LAN Service", "10.191.61.176", 8443,
                "fingerprint", "proof", "previous");
        LanApiClient.DiscoveredServer resolved = LanApiClient.discoveredServerAtSource(
                advertised, "192.168.10.47");
        assertEquals("192.168.10.47", resolved.host());
        assertEquals(8443, resolved.port());
    }
    @AfterEach
    void reset() {
        LanApiClient.resetTransportForTests();
    }

    @Test
    void reusesClientUntilTransportStateIsReset() throws Exception {
        LanApiClient.resetTransportForTests();
        HttpClient first = LanApiClient.bootstrapClientForTests();
        assertSame(first, LanApiClient.bootstrapClientForTests());

        LanApiClient.resetTransportForTests();
        assertNotSame(first, LanApiClient.bootstrapClientForTests());
    }
}
