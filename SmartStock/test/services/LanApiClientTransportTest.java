package services;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LanApiClientTransportTest {
    @Test
    void discoveryKeepsCertificateHostnameInsteadOfReplacingItWithPacketIp() {
        LanApiClient.DiscoveredServer advertised = new LanApiClient.DiscoveredServer(
                "SmartStock LAN Service", "Nishan-2.local", 8443,
                "fingerprint", "proof", "previous");
        LanApiClient.DiscoveredServer resolved = LanApiClient.discoveredServerAtSource(
                advertised, "192.168.10.47");
        assertEquals("Nishan-2.local", resolved.host());
        assertEquals(8443, resolved.port());
    }

    @Test
    void discoveryFallsBackToPacketSourceWhenOlderServerOmitsHost() {
        LanApiClient.DiscoveredServer advertised = new LanApiClient.DiscoveredServer(
                "SmartStock LAN Service", "", 8443,
                "fingerprint", "proof", "previous");
        assertEquals("192.168.10.47",
                LanApiClient.discoveredServerAtSource(advertised, "192.168.10.47").host());
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

    @Test
    void recognizesLanTransportFailuresButNotServerBusinessErrors() {
        assertTrue(LanApiClient.isConnectionFailure(new ConnectException("refused")));
        assertTrue(LanApiClient.isConnectionFailure(new RuntimeException(new SocketTimeoutException("timed out"))));
        assertTrue(LanApiClient.isConnectionFailure(new HttpTimeoutException("timed out")));
        assertTrue(LanApiClient.isConnectionFailure(new IOException("connection closed")));
        assertFalse(LanApiClient.isConnectionFailure(
                new LanApiClient.LanApiException("PERMISSION_DENIED", "Not allowed", false)));
        assertFalse(LanApiClient.isConnectionFailure(new IllegalArgumentException("bad input")));
    }
}
