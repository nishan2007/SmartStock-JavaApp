package services;

import data.DatabaseConfig;
import data.DatabaseMode;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class QuotationInvoiceSchemaInstaller {
    private static final Object INSTALL_LOCK = new Object();
    private static final Set<String> INSTALLED_DATABASES = ConcurrentHashMap.newKeySet();

    private QuotationInvoiceSchemaInstaller() {
    }

    public static void ensureSchema(Connection conn) throws SQLException {
        if (DatabaseConfig.load().mode() != DatabaseMode.SERVER) {
            return;
        }
        String key = databaseKey(conn);
        if (INSTALLED_DATABASES.contains(key)) {
            return;
        }
        synchronized (INSTALL_LOCK) {
            if (INSTALLED_DATABASES.contains(key)) {
                return;
            }
            runInstaller(conn);
            INSTALLED_DATABASES.add(key);
        }
    }

    private static void runInstaller(Connection conn) throws SQLException {
        try {
            SqlScriptRunner.runScripts(conn, List.of("database/quotations_invoices_setup.sql"));
        } catch (IOException ex) {
            throw new SQLException("Unable to install quotations/orders schema.", ex);
        }
    }

    private static String databaseKey(Connection conn) throws SQLException {
        String url = conn.getMetaData().getURL();
        String user = conn.getMetaData().getUserName();
        return (url == null ? "unknown" : url) + "|" + (user == null ? "" : user);
    }
}
