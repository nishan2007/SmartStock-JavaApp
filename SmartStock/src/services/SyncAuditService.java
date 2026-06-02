package services;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Map;

public final class SyncAuditService {
    private SyncAuditService() {
    }

    public static void record(Connection conn,
                              String actionType,
                              String tableName,
                              Object localIdBefore,
                              Object localIdAfter,
                              Object cloudId,
                              String matchKey,
                              String status,
                              Map<String, ?> details) throws SQLException {
        if (conn == null || actionType == null || actionType.isBlank()) {
            return;
        }
        SyncSchemaInstaller.ensureSchema(conn);
        String sql = """
                INSERT INTO sync_audit_log (
                    action_type, table_name, local_id_before, local_id_after,
                    cloud_id, match_key, status, details
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb)
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, actionType);
            ps.setString(2, blankToNull(tableName));
            ps.setString(3, stringOrNull(localIdBefore));
            ps.setString(4, stringOrNull(localIdAfter));
            ps.setString(5, stringOrNull(cloudId));
            ps.setString(6, blankToNull(matchKey));
            ps.setString(7, status == null || status.isBlank() ? "INFO" : status);
            ps.setString(8, SyncJson.object(details == null ? Map.of() : details));
            ps.executeUpdate();
        }
    }

    private static String stringOrNull(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
