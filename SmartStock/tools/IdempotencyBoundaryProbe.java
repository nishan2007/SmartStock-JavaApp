package services;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Map;
import java.util.UUID;

/** Rollback-only proof that a retried mutation returns its first result and does not run twice. */
public final class IdempotencyBoundaryProbe {
    private IdempotencyBoundaryProbe() { }

    public static void main(String[] args) throws Exception {
        try (Connection c = DriverManager.getConnection(required("SMARTSTOCK_TEST_JDBC_URL"),
                required("SMARTSTOCK_TEST_DB_USER"), required("SMARTSTOCK_TEST_DB_PASSWORD"))) {
            c.setAutoCommit(false);
            try {
                UUID deviceId = device(c);
                String key = "rollback-probe-" + UUID.randomUUID();
                String operation = "probe.financial-mutation.v1";
                String hash = LanSecurity.sha256("same-request");
                try (Statement s = c.createStatement()) {
                    s.execute("CREATE TEMP TABLE idempotency_probe_counter(value integer NOT NULL) ON COMMIT DROP");
                    s.execute("INSERT INTO idempotency_probe_counter(value) VALUES (0)");
                }

                Map<String,Object> first = LanApiServer.loadIdempotentResult(c, deviceId, key, operation, hash);
                if (first != null) throw new AssertionError("A new idempotency key unexpectedly had a result.");
                try (Statement s = c.createStatement()) {
                    s.executeUpdate("UPDATE idempotency_probe_counter SET value=value+1");
                }
                Map<String,Object> result = Map.of("transactionId", 4242, "status", "completed");
                LanApiServer.completeIdempotency(c, deviceId, key, result);

                Map<String,Object> retry = LanApiServer.loadIdempotentResult(c, deviceId, key, operation, hash);
                if (retry == null || ((Number) retry.get("transactionId")).intValue() != 4242)
                    throw new AssertionError("Retry did not return the original mutation result.");
                if (counter(c) != 1) throw new AssertionError("Retry executed the mutation more than once.");

                try {
                    LanApiServer.loadIdempotentResult(c, deviceId, key, operation, LanSecurity.sha256("different-request"));
                    throw new AssertionError("Reusing an idempotency key for a different request was accepted.");
                } catch (Exception expected) {
                    if (expected.getMessage() == null || !expected.getMessage().contains("different request")) throw expected;
                }
                System.out.println("Idempotent retry, original-result replay, and key-conflict checks passed.");
            } finally {
                c.rollback();
            }
        }
    }

    private static UUID device(Connection c) throws Exception {
        try (Statement s = c.createStatement(); ResultSet rs = s.executeQuery(
                "SELECT device_id FROM devices WHERE COALESCE(is_approved,false) AND NOT COALESCE(is_blocked,false) ORDER BY device_id LIMIT 1")) {
            if (!rs.next()) throw new IllegalStateException("Probe needs one approved device.");
            return (UUID) rs.getObject(1);
        }
    }

    private static int counter(Connection c) throws Exception {
        try (Statement s = c.createStatement(); ResultSet rs = s.executeQuery("SELECT value FROM idempotency_probe_counter")) {
            rs.next(); return rs.getInt(1);
        }
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalStateException(name + " is required.");
        return value;
    }
}
