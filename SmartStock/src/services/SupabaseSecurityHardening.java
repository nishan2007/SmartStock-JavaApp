package services;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Retained API for callers that previously repaired RLS at runtime.
 * Security policy is now authored only by the canonical v1 baseline.
 */
public final class SupabaseSecurityHardening {
    private SupabaseSecurityHardening() {
    }

    public static void protectInternalTable(Connection connection, String tableName)
            throws SQLException {
        SchemaContractService.requireLocalReady(connection);
    }
}
