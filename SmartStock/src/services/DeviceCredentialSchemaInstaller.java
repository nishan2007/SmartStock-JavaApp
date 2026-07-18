package services;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

public final class DeviceCredentialSchemaInstaller {
    private DeviceCredentialSchemaInstaller() {
    }

    public static void ensureSchema(Connection conn) throws SQLException {
        try {
            SqlScriptRunner.runScripts(conn, List.of("database/device_management_setup.sql"));
        } catch (IOException ex) {
            throw new SQLException("Could not load the device security schema.", ex);
        }
    }
}
