package services;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/** Stores byte counts only; cloud payloads and credentials are never logged. */
final class CloudTransferMetrics {
    private CloudTransferMetrics() {
    }

    static void record(Connection local, String operation, String requestBody,
                       String responseBody) throws SQLException {
        try (PreparedStatement cleanup = local.prepareStatement("""
                DELETE FROM sync_transfer_metrics
                WHERE created_at < CURRENT_TIMESTAMP - INTERVAL '90 days'
                """)) {
            cleanup.executeUpdate();
        }
        try (PreparedStatement ps = local.prepareStatement("""
                INSERT INTO sync_transfer_metrics(operation,request_bytes,response_bytes)
                VALUES (?,?,?)
                """)) {
            ps.setString(1, operation);
            ps.setLong(2, bytes(requestBody));
            ps.setLong(3, bytes(responseBody));
            ps.executeUpdate();
        }
    }

    private static int bytes(String value) {
        return value == null ? 0 : value.getBytes(StandardCharsets.UTF_8).length;
    }
}
