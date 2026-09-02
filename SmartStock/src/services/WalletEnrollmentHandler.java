package services;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import data.DB;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.Instant;
import java.util.UUID;

/** Public handler deliberately limited to one-time Apple Wallet enrollment. */
final class WalletEnrollmentHandler implements HttpHandler {
    private static final String PREFIX = "/wallet/enroll/";
    private static final Path ERROR_LOG = Path.of(System.getProperty("user.home"),
            ".smartstock", "wallet-enrollment-errors.log");

    @Override public void handle(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.getResponseHeaders().set("Referrer-Policy", "no-referrer");
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        exchange.getResponseHeaders().set("Content-Security-Policy",
                "default-src 'none'; form-action 'self'; frame-ancestors 'none'; base-uri 'none'");
        try {
            String method = exchange.getRequestMethod();
            if (!"GET".equals(method) && !"POST".equals(method)) {
                exchange.getResponseHeaders().set("Allow", "GET, POST");
                exchange.sendResponseHeaders(405, -1);
                return;
            }
            String path = exchange.getRequestURI().getPath();
            String token = path.startsWith(PREFIX) ? path.substring(PREFIX.length()) : "";
            if (!token.matches("[A-Za-z0-9_-]{43}")) {
                exchange.sendResponseHeaders(404, -1);
                return;
            }
            if ("GET".equals(method)) {
                byte[] page = WalletEnrollmentPage.html().getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
                exchange.sendResponseHeaders(200, page.length);
                try (var out = exchange.getResponseBody()) { out.write(page); }
                return;
            }
            byte[] pass;
            try (Connection connection = DB.getConnection()) {
                pass = AppleWalletBadgeService.consumeEnrollment(connection, token);
                try {
                    try (PreparedStatement audit = connection.prepareStatement("""
                            INSERT INTO security_audit_events(event_type, details)
                            VALUES ('APPLE_WALLET_PASS_ISSUED', 'One-time enrollment consumed')
                            """)) { audit.executeUpdate(); }
                } catch (Exception auditFailure) {
                    recordFailure("audit", auditFailure);
                }
            }
            exchange.getResponseHeaders().set("Content-Type", "application/vnd.apple.pkpass");
            exchange.getResponseHeaders().set("Content-Disposition",
                    "attachment; filename=SmartStock-Employee-Badge.pkpass");
            exchange.sendResponseHeaders(200, pass.length);
            try (var out = exchange.getResponseBody()) { out.write(pass); }
        } catch (Exception exception) {
            recordFailure("issuance", exception);
            byte[] body = ("Unable to issue this badge. The link may be expired or already used. "
                    + "Ask your administrator for help.").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
            exchange.sendResponseHeaders(400, body.length);
            try (var out = exchange.getResponseBody()) { out.write(body); }
        } finally {
            exchange.close();
        }
    }

    private static void recordFailure(String stage, Exception exception) {
        String failureId = UUID.randomUUID().toString();
        String type = exception == null ? "unknown" : exception.getClass().getName();
        String message = exception == null || exception.getMessage() == null
                ? "" : exception.getMessage().replaceAll("[\\r\\n]+", " ");
        if (message.length() > 500) message = message.substring(0, 500);
        String line = Instant.now() + " failureId=" + failureId + " stage=" + stage
                + " type=" + type + " message=" + message + System.lineSeparator();
        System.err.print(line);
        try {
            Files.createDirectories(ERROR_LOG.getParent());
            Files.writeString(ERROR_LOG, line, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ignored) {
            // The public response remains generic even when local diagnostics are unavailable.
        }
    }
}
