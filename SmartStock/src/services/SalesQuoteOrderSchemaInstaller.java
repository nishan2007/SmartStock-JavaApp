package services;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

public final class SalesQuoteOrderSchemaInstaller {
    private SalesQuoteOrderSchemaInstaller() {
    }

    public static void ensureSchema(Connection conn) throws SQLException {
        try {
            SqlScriptRunner.runScripts(conn, List.of("database/sales_quotes_orders_setup.sql"));
        } catch (IOException ex) {
            throw new SQLException("Unable to install sales quotes/orders schema.", ex);
        }
    }
}
