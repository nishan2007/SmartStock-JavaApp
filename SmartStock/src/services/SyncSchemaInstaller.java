package services;

import java.sql.Connection;
import java.sql.SQLException;

/** Validates sync tables supplied by the canonical local v1 baseline. */
public final class SyncSchemaInstaller {
    private SyncSchemaInstaller() {
    }

    public static void ensureSchema(Connection connection) throws SQLException {
        SchemaContractService.requireLocalReady(connection);
    }

    public static void ensureSecurityHardening(Connection connection) throws SQLException {
        SchemaContractService.requireLocalReady(connection);
    }
}
