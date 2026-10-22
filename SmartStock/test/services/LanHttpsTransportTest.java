package services;

import com.sun.net.httpserver.HttpsExchange;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.net.ssl.*;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.security.KeyStore;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

class LanHttpsTransportTest {
    @TempDir Path root;

    @Test void preservesTheProviderForOtherWebServers() throws Exception {
        LanHttpsTransport.preserveDefaultProvider();
        var server = com.sun.net.httpserver.HttpsServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        try { assertFalse(server.getClass().getName().startsWith("org.eclipse.jetty.")); }
        finally { server.stop(0); }
    }

    @Test void servesConcurrentPinnedTlsWithoutTheJdkReverseLookupPath() throws Exception {
        Path file = root.resolve("tls.p12");
        String password = UUID.randomUUID().toString();
        var generate = LanTlsIdentity.class.getDeclaredMethod("generateKeyStore", Path.class, String.class, String.class);
        generate.setAccessible(true);
        generate.invoke(null, file, password, "localhost");
        KeyStore keys = KeyStore.getInstance("PKCS12");
        try (var input = Files.newInputStream(file)) { keys.load(input, password.toCharArray()); }
        var km = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        km.init(keys, password.toCharArray());
        SSLContext tls = SSLContext.getInstance("TLS");
        tls.init(km.getKeyManagers(), null, null);
        var tm = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tm.init(keys);
        SSLContext trusted = SSLContext.getInstance("TLS");
        trusted.init(null, tm.getTrustManagers(), null);
        var server = LanHttpsTransport.create(new InetSocketAddress("127.0.0.1", 0), tls);
        server.createContext("/v1/health", exchange -> {
            assertInstanceOf(HttpsExchange.class, exchange);
            assertTrue(exchange.getRemoteAddress().getAddress().isLoopbackAddress());
            byte[] reply = exchange.getRequestURI().getPath().getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, reply.length);
            try (var output = exchange.getResponseBody()) { output.write(reply); }
        });
        server.createContext("/v1/mutation", exchange -> {
            byte[] body = exchange.getRequestBody().readAllBytes();
            if (!"POST".equals(exchange.getRequestMethod()) || !"Bearer fixture".equals(exchange.getRequestHeaders().getFirst("Authorization"))) {
                exchange.sendResponseHeaders(401, -1);
                exchange.close();
                return;
            }
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (var output = exchange.getResponseBody()) { output.write(body); }
        });
        server.start();
        int port = server.getAddress().getPort();
        var stalled = new java.util.ArrayList<java.net.Socket>();
        try {
            assertEquals("org.eclipse.jetty.http.spi.JettyHttpServer", server.getClass().getName());
            var client = HttpClient.newBuilder().sslContext(trusted).connectTimeout(Duration.ofSeconds(3)).build();
            URI endpoint = URI.create("https://localhost:" + port + "/v1/health");
            // More idle TLS peers than the old listener's entire 16-worker pool.
            // Accepting TCP must not allow these peers to starve valid register handshakes.
            for (int i = 0; i < 24; i++) stalled.add(new java.net.Socket("127.0.0.1", port));
            var untrusted = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
            assertThrows(javax.net.ssl.SSLHandshakeException.class, () -> untrusted.send(
                    HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(5)).build(), HttpResponse.BodyHandlers.discarding()));
            var pending = new java.util.ArrayList<CompletableFuture<HttpResponse<String>>>();
            for (int i = 0; i < 32; i++) {
                pending.add(client.sendAsync(HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(5)).build(),
                        HttpResponse.BodyHandlers.ofString()));
            }
            for (var response : pending) {
                assertEquals(200, response.get(8, TimeUnit.SECONDS).statusCode());
                assertEquals("/v1/health", response.get().body());
            }
            URI mutation = endpoint.resolve("/v1/mutation");
            assertEquals(401, client.send(HttpRequest.newBuilder(mutation).timeout(Duration.ofSeconds(5))
                    .POST(HttpRequest.BodyPublishers.ofString("{}")).build(), HttpResponse.BodyHandlers.discarding()).statusCode());
            String json = "{\"item\":\"fixture\"}";
            var saved = client.send(HttpRequest.newBuilder(mutation).timeout(Duration.ofSeconds(5))
                    .header("Authorization", "Bearer fixture").header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json)).build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(200, saved.statusCode());
            assertEquals(json, saved.body());
        } finally {
            for (var socket : stalled) socket.close();
            server.stop(0);
        }
        // Listener and pool must be released, so a restart can reuse the same transport.
        try (var socket = new java.net.ServerSocket()) {
            socket.bind(new InetSocketAddress("127.0.0.1", port));
        }
    }
}
