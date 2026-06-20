package data;

import services.SyncSchemaInstaller;
import services.BaseSchemaInstaller;
import services.QuotationInvoiceSchemaInstaller;
import services.WorkflowSyncIdentitySchemaInstaller;
import services.AppUpdateSchemaInstaller;
import services.EmailSchemaInstaller;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class DB {
    private static final Object SCHEMA_LOCK = new Object();
    private static final Set<String> ENSURED_SCHEMA_KEYS = ConcurrentHashMap.newKeySet();
    private static final Executor CLOUD_NETWORK_TIMEOUT_EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "smartstock-cloud-db-timeout");
        thread.setDaemon(true);
        return thread;
    });

    public static Connection getConnection() throws SQLException {
        DatabaseConfig config = DatabaseConfig.load();
        if (!config.hasPrimaryConnection()) {
            throw new SQLException(config.missingPrimaryConnectionMessage());
        }
        if (config.hasUnresolvedCredentialPlaceholders()) {
            throw new SQLException(config.missingPrimaryConnectionMessage());
        }
        try {
            Connection conn = DriverManager.getConnection(config.jdbcUrl(), config.dbUser(), config.dbPassword());
            if (config.mode() == DatabaseMode.SERVER) {
                ensureSchemaOnce(conn, config);
            }
            return conn;
        } catch (SQLException e) {
            e.printStackTrace();
            throw e;
        }
    }

    public static Connection getCloudConnection() throws SQLException {
        DatabaseConfig config = DatabaseConfig.load();
        if (!config.hasCloudConnection()) {
            throw new SQLException("Cloud database connection is not configured on this machine.");
        }
        Connection conn = DriverManager.getConnection(withCloudTimeouts(config.cloudJdbcUrl()), config.cloudDbUser(), config.cloudDbPassword());
        configureCloudSession(conn);
        return conn;
    }

    private static void configureCloudSession(Connection conn) throws SQLException {
        conn.setNetworkTimeout(CLOUD_NETWORK_TIMEOUT_EXECUTOR, 30_000);
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("SET statement_timeout = '30s'");
            stmt.execute("SET lock_timeout = '10s'");
            stmt.execute("SET idle_in_transaction_session_timeout = '60s'");
        }
    }

    private static String withCloudTimeouts(String jdbcUrl) {
        String url = jdbcUrl == null ? "" : jdbcUrl;
        url = withJdbcParam(url, "connectTimeout", "10");
        url = withJdbcParam(url, "socketTimeout", "30");
        url = withJdbcParam(url, "tcpKeepAlive", "true");
        return url;
    }

    private static String withJdbcParam(String jdbcUrl, String key, String value) {
        if (jdbcUrl.contains(key + "=")) {
            return jdbcUrl;
        }
        String separator = jdbcUrl.contains("?") ? "&" : "?";
        return jdbcUrl + separator + key + "=" + value;
    }

    private static void ensureSchemaOnce(Connection conn, DatabaseConfig config) throws SQLException {
        String key = config.mode() + "|" + config.jdbcUrl();
        if (ENSURED_SCHEMA_KEYS.contains(key)) {
            return;
        }
        synchronized (SCHEMA_LOCK) {
            if (ENSURED_SCHEMA_KEYS.contains(key)) {
                return;
            }
            BaseSchemaInstaller.ensureSchema(conn);
            SyncSchemaInstaller.ensureSchema(conn);
            QuotationInvoiceSchemaInstaller.ensureSchema(conn);
            WorkflowSyncIdentitySchemaInstaller.ensureSchema(conn);
            AppUpdateSchemaInstaller.ensureSchema(conn);
            EmailSchemaInstaller.ensureSchema(conn);
            ENSURED_SCHEMA_KEYS.add(key);
        }
    }
}
