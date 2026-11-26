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
    @Test void responseParsingPreservesHttpStatusForCompatibilityDecisions()throws Exception {
        var parser=LanApiClient.class.getDeclaredMethod("responseData",java.net.http.HttpResponse.class);parser.setAccessible(true);
        for(int status:new int[]{404,503}) {
            var response=java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{java.net.http.HttpResponse.class},
                    (proxy,method,args)->switch(method.getName()){case "statusCode"->status;case "body"->"<html>Unavailable</html>";default->throw new UnsupportedOperationException();});
            var thrown=org.junit.jupiter.api.Assertions.assertThrows(java.lang.reflect.InvocationTargetException.class,()->parser.invoke(null,response));
            var failure=(LanApiClient.LanApiException)thrown.getCause();
            assertEquals(status,failure.statusCode());assertEquals(status==404,LanApiClient.missingStudioDriveEndpoint(failure));
        }
    }
    @Test void oldServerFallbackRequiresAMissingEndpointAndNeverMasksAuthorizationOrOutages(){
        assertTrue(LanApiClient.missingStudioDriveEndpoint(new LanApiClient.LanApiException("INVALID_SERVER_RESPONSE","Old HTML 404",false,404)));
        assertTrue(LanApiClient.missingStudioDriveEndpoint(new LanApiClient.LanApiException("NOT_FOUND","Missing route",false,404)));
        for(var failure:new LanApiClient.LanApiException[]{
                new LanApiClient.LanApiException("INVALID_SERVER_RESPONSE","Malformed success",true,200),
                new LanApiClient.LanApiException("INVALID_SERVER_RESPONSE","Proxy outage",true,503),
                new LanApiClient.LanApiException("DEVICE_NOT_FOUND","Revoked device",false,404),
                new LanApiClient.LanApiException("PERMISSION_DENIED","Forbidden",false,403),
                new LanApiClient.LanApiException("LOGIN_REQUIRED","Expired session",false,401),
                new LanApiClient.LanApiException("NOT_FOUND","Unknown HTTP status",false)}){
            assertFalse(LanApiClient.missingStudioDriveEndpoint(failure));
        }
    }
    @Test
    void reconciliationWaitsForServerBatchWithoutSlowingOrdinaryRequests() {
        assertTrue(LanApiClient.postTimeout("/v1/images/reconcile", false)
                .compareTo(java.time.Duration.ofSeconds(120)) > 0);
        assertEquals(java.time.Duration.ofSeconds(20),
                LanApiClient.postTimeout("/v1/products/update", false));
        assertEquals(java.time.Duration.ofSeconds(90),
                LanApiClient.postTimeout("/v1/cloud/storage/upload", true));
        assertEquals(java.time.Duration.ofMinutes(30),
                LanApiClient.postTimeout("/v1/studio/remove-background", true));
        assertEquals(java.time.Duration.ofSeconds(75),
                LanApiClient.postTimeout("/v1/studio/models/refresh", false));
        assertEquals(java.time.Duration.ofMinutes(4),
                LanApiClient.postTimeout("/v1/studio/pdf-ocr", false));
        assertTrue(RemoteAdminPolicy.isPhysicalOperation("/v1/studio/pdf-ocr"));
    }

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
