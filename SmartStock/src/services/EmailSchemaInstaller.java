package services;

import java.sql.Connection;
import java.sql.SQLException;

/** Validates the canonical v1 schema for callers retained under the installer API. */
public final class EmailSchemaInstaller {
    private EmailSchemaInstaller() {
    }

    public static void ensureSchema(Connection connection) throws SQLException {
        SchemaContractService.requireLocalReady(connection);
    }
}
