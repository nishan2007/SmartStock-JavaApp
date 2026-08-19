package ui.helpers;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/** One-use localhost callback receiver for desktop OAuth. */
public final class LocalOAuthCallbackServer implements AutoCloseable {
    private final HttpServer server;
    private final CompletableFuture<Result> result = new CompletableFuture<>();

    private LocalOAuthCallbackServer(HttpServer server) { this.server = server; }

    public static LocalOAuthCallbackServer start() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        LocalOAuthCallbackServer receiver = new LocalOAuthCallbackServer(server);
        server.createContext("/smartstock-gmail-oauth", receiver::handle);
        server.setExecutor(null);
        server.start();
        return receiver;
    }

    public String redirectUri() { return "http://127.0.0.1:" + server.getAddress().getPort() + "/smartstock-gmail-oauth"; }

    public Result await(Duration timeout) throws Exception {
        return result.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
    }

    private void handle(HttpExchange exchange) throws IOException {
        Map<String, String> query = parse(exchange.getRequestURI());
        String error = query.getOrDefault("error", "");
        String code = query.getOrDefault("code", "");
        String state = query.getOrDefault("state", "");
        boolean ok = error.isBlank() && !code.isBlank() && !state.isBlank();
        String html = ok
                ? "<html><body><h2>Gmail connected</h2><p>You can close this window and return to SmartStock.</p></body></html>"
                : "<html><body><h2>Gmail connection failed</h2><p>Return to SmartStock for details.</p></body></html>";
        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(ok ? 200 : 400, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
        result.complete(new Result(state, code, error));
    }

    private static Map<String, String> parse(URI uri) {
        Map<String, String> values = new HashMap<>();
        String query = uri.getRawQuery();
        if (query == null) return values;
        for (String pair : query.split("&")) {
            String[] parts = pair.split("=", 2);
            values.put(decode(parts[0]), parts.length == 2 ? decode(parts[1]) : "");
        }
        return values;
    }

    private static String decode(String value) { return URLDecoder.decode(value, StandardCharsets.UTF_8); }

    @Override public void close() { server.stop(0); }

    public record Result(String state, String code, String error) { }
}
