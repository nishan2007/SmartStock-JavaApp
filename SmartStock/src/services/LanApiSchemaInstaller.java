package services;

import java.sql.Connection;
import java.sql.SQLException;

/** Validates the canonical v1 schema for callers retained under the installer API. */
public final class LanApiSchemaInstaller {
    private LanApiSchemaInstaller() {
    }

    public static void ensureSchema(Connection connection) throws SQLException {
        SchemaContractService.ensureMobileItemWebUpgrade(connection);
        SchemaContractService.requireLocalReady(connection);
    }
}
