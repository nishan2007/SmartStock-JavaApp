package services;

import java.sql.Connection;
import java.sql.SQLException;

public final class AppUpdateSchemaInstaller {
    private AppUpdateSchemaInstaller() {
    }

    public static void ensureSchema(Connection conn) throws SQLException {
        try {
            SqlScriptRunner.runScripts(conn, java.util.List.of("database/app_updates_setup.sql"));
        } catch (SQLException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new SQLException("Unable to install app update schema.", ex);
        }
    }
}
