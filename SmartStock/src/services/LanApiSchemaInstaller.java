package services;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/** Installs the local-only tables owned by the SmartStock LAN service. */
public final class LanApiSchemaInstaller {
    private LanApiSchemaInstaller() {
    }

    public static void ensureSchema(Connection connection) throws SQLException {
        try {
            SqlScriptRunner.runScripts(connection, List.of("database/lan_api_security_setup.sql"));
        } catch (IOException ex) {
            throw new SQLException("Could not load the SmartStock LAN service security schema.", ex);
        }
    }
}
