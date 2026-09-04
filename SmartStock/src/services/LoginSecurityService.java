package services;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

public final class LoginSecurityService {
    private static final int MAX_FAILURES = 5;
    private static final Duration FAILURE_WINDOW = Duration.ofMinutes(15);
    private static final Duration LOCK_DURATION = Duration.ofMinutes(15);

    private LoginSecurityService() {
    }

    public static void requireAllowed(Connection conn, String identifier) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT locked_until FROM login_security_state WHERE identifier_hash = ?")) {
            ps.setString(1, hash(identifier));
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next() && rs.getTimestamp(1) != null && rs.getTimestamp(1).toInstant().isAfter(Instant.now())) {
                    long minutes = Math.max(1, Duration.between(Instant.now(), rs.getTimestamp(1).toInstant()).toMinutes() + 1);
                    throw new SQLException("Too many failed sign-in attempts. Try again in about " + minutes + " minute(s), or ask an administrator for help.");
                }
            }
        } catch (SQLException ex) {
            if ("42P01".equals(ex.getSQLState())) return;
            throw ex;
        }
    }

    public static void recordFailure(Connection conn, UUID deviceId, String identifier, String reason) {
        try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO login_security_state(identifier_hash, failed_count, window_started_at, last_failed_at, locked_until, updated_at)
                VALUES (?, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, NULL, CURRENT_TIMESTAMP)
                ON CONFLICT (identifier_hash) DO UPDATE SET
                    failed_count = CASE
                        WHEN login_security_state.window_started_at < CURRENT_TIMESTAMP - INTERVAL '15 minutes' THEN 1
                        ELSE login_security_state.failed_count + 1
                    END,
                    window_started_at = CASE
                        WHEN login_security_state.window_started_at < CURRENT_TIMESTAMP - INTERVAL '15 minutes' THEN CURRENT_TIMESTAMP
                        ELSE login_security_state.window_started_at
                    END,
                    last_failed_at = CURRENT_TIMESTAMP,
                    locked_until = CASE
                        WHEN (CASE WHEN login_security_state.window_started_at < CURRENT_TIMESTAMP - INTERVAL '15 minutes'
                                   THEN 1 ELSE login_security_state.failed_count + 1 END) >= 5
                        THEN CURRENT_TIMESTAMP + INTERVAL '15 minutes'
                        ELSE login_security_state.locked_until
                    END,
                    updated_at = CURRENT_TIMESTAMP
                """)) {
            ps.setString(1, hash(identifier));
            ps.executeUpdate();
            recordAudit(conn, "LOGIN_FAILED", deviceId, reason);
        } catch (Exception ex) {
            System.err.println("Could not record failed login: " + ex.getMessage());
        }
    }

    public static void recordSuccess(Connection conn, String identifier) {
        try (PreparedStatement ps = conn.prepareStatement("DELETE FROM login_security_state WHERE identifier_hash = ?")) {
            ps.setString(1, hash(identifier));
            ps.executeUpdate();
        } catch (Exception ignored) {
        }
    }

    public static int clearFailures(Connection conn, String... identifiers) throws SQLException {
        Set<String> hashes = new LinkedHashSet<>();
        if (identifiers != null) {
            for (String identifier : identifiers) {
                if (identifier != null && !identifier.isBlank()) hashes.add(hash(identifier));
            }
        }
        if (hashes.isEmpty()) return 0;
        int cleared = 0;
        try (PreparedStatement ps = conn.prepareStatement(
                "DELETE FROM login_security_state WHERE identifier_hash = ?")) {
            for (String identifierHash : hashes) {
                ps.setString(1, identifierHash);
                ps.addBatch();
            }
            for (int count : ps.executeBatch()) {
                if (count > 0) cleared += count;
            }
        }
        return cleared;
    }

    private static void recordAudit(Connection conn, String type, UUID deviceId, String details) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO security_audit_events(event_type, device_id, details)
                VALUES (?, ?, ?)
                """)) {
            ps.setString(1, type);
            ps.setObject(2, deviceId);
            ps.setString(3, details == null ? "Authentication rejected" : details);
            ps.executeUpdate();
        }
    }

    private static String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(
                    (value == null ? "" : value.trim().toLowerCase()).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
