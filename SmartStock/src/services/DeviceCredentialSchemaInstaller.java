package services;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
public final class DeviceCredentialSchemaInstaller {
    private DeviceCredentialSchemaInstaller() {
    }

    public static void ensureSchema(Connection conn) throws SQLException {
        try {
            SqlScriptRunner.runResource(conn, "database/device_management_setup.sql");
        } catch (IOException ex) {
            throw new SQLException("Could not load the device security schema.", ex);
        }
    }
}
