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
    void transientHealthFailureRecoversWithoutDeclaringAnOutage() {
        var attempts = new java.util.concurrent.atomic.AtomicInteger();
        var result = LanApiClient.confirmServerReachable(attempt -> {
            attempts.incrementAndGet();
            if (attempt == 0) throw new java.net.http.HttpConnectTimeoutException("temporary");
        });
        assertTrue(result.reachable());
        assertEquals(2, attempts.get());
    }

    @Test
    void persistentOutageIsBoundedAndCertificateFailureIsNotRetried() {
        var attempts = new java.util.concurrent.atomic.AtomicInteger();
        assertFalse(LanApiClient.confirmServerReachable(attempt -> {
            attempts.incrementAndGet();
            throw new ConnectException("offline");
        }).reachable());
        assertEquals(2, attempts.get());
        attempts.set(0);
        assertFalse(LanApiClient.confirmServerReachable(attempt -> {
            attempts.incrementAndGet();
            throw new javax.net.ssl.SSLHandshakeException("certificate rejected");
        }).reachable());
        assertEquals(1, attempts.get());
    }

    @Test
    void unresolvedMulticastNameFallsBackOnlyToResolvableCertificateAlias() {
        assertEquals("Nishan-2", LanApiClient.resolvableServerHost("Nishan-2.local",
                name -> name.equals("Nishan-2")));
        assertEquals("Nishan-2.local", LanApiClient.resolvableServerHost("Nishan-2.local", name -> true));
        assertEquals("Nishan-2.local", LanApiClient.resolvableServerHost("Nishan-2.local", name -> false));
        assertEquals("server.example.com", LanApiClient.resolvableServerHost("server.example.com", name -> false));
    }
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
        assertTrue(LanApiClient.isConnectionFailure(new java.nio.channels.UnresolvedAddressException()));
        assertTrue(LanApiClient.isConnectionFailure(new RuntimeException(new SocketTimeoutException("timed out"))));
        assertTrue(LanApiClient.isConnectionFailure(new HttpTimeoutException("timed out")));
        assertTrue(LanApiClient.isConnectionFailure(new IOException("connection closed")));
        assertFalse(LanApiClient.isConnectionFailure(
                new LanApiClient.LanApiException("PERMISSION_DENIED", "Not allowed", false)));
        assertFalse(LanApiClient.isConnectionFailure(new IllegalArgumentException("bad input")));
    }
}
