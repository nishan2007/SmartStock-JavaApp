package services;

import data.DB;

import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;

public final class SyncServiceStatusService {
    public static final String SERVICE_ID = "smartstock-background-sync";

    private SyncServiceStatusService() {
    }

    public static void mark(String status, String message) {
        try (Connection conn = DB.getConnection()) {
            mark(conn, status, message);
        } catch (Exception ex) {
            System.err.println("Could not update sync service status: " + ex.getMessage());
        }
    }

    public static void mark(Connection conn, String status, String message) throws SQLException {
        SyncSchemaInstaller.ensureSchema(conn);
        try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO sync_service_status (service_id, status, message, started_at, last_seen_at, updated_at)
                VALUES (?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                ON CONFLICT (service_id)
                DO UPDATE SET status = EXCLUDED.status,
                              message = EXCLUDED.message,
                              started_at = COALESCE(sync_service_status.started_at, EXCLUDED.started_at),
                              last_seen_at = CURRENT_TIMESTAMP,
                              updated_at = CURRENT_TIMESTAMP
                """)) {
            ps.setString(1, SERVICE_ID);
            ps.setString(2, clean(status));
            ps.setString(3, message);
            ps.executeUpdate();
        }
    }

    public static ServiceInfo current() {
        try (Connection conn = DB.getConnection()) {
            return current(conn);
        } catch (Exception ex) {
            return new ServiceInfo("Unknown", "Could not read service status: " + ex.getMessage(), null, null);
        }
    }

    public static ServiceInfo current(Connection conn) throws SQLException {
        SyncSchemaInstaller.ensureSchema(conn);
        try (PreparedStatement ps = conn.prepareStatement("""
                SELECT status, message, started_at, last_seen_at
                FROM sync_service_status
                WHERE service_id = ?
                """)) {
            ps.setString(1, SERVICE_ID);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return new ServiceInfo("Not Installed", "No background sync heartbeat has been recorded.", null, null);
                }
                return new ServiceInfo(
                        rs.getString("status"),
                        rs.getString("message"),
                        toInstant(rs.getTimestamp("started_at")),
                        toInstant(rs.getTimestamp("last_seen_at"))
                );
            }
        }
    }

    public static String processLabel(String mode) {
        String host;
        try {
            host = InetAddress.getLocalHost().getHostName();
        } catch (Exception ex) {
            host = "unknown-host";
        }
        return "SmartStock " + mode + " on " + host + " (" + ManagementFactory.getRuntimeMXBean().getName() + ")";
    }

    private static String clean(String status) {
        return status == null || status.isBlank() ? "Unknown" : status;
    }

    private static Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    public record ServiceInfo(String status, String message, Instant startedAt, Instant lastSeenAt) {
    }
}
