package data;

import services.SchemaContractService;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import ui.helpers.BlockingCallGuard;
import ui.helpers.PerformanceDiagnostics;

public class DB {
    private static final Object SCHEMA_LOCK = new Object();
    private static final Object POOL_LOCK = new Object();
    private static volatile PostgresConnectionPool pool;
    private static volatile DatabaseConfig poolConfig;
    private static final Set<String> ENSURED_SCHEMA_KEYS = ConcurrentHashMap.newKeySet();
    public static Connection getConnection() throws SQLException {
        BlockingCallGuard.check("primary database connection");
        long started = System.nanoTime();
        DatabaseConfig config = DatabaseConfig.load();
        if (config.mode() == DatabaseMode.CLIENT || config.mode() == DatabaseMode.REMOTE_ADMIN) {
            throw new SQLException("Direct database access is disabled on this device. SmartStock must use its authenticated API service.", "28000");
        }
        if (config.mode() == DatabaseMode.SERVER && !isLoopbackJdbcUrl(config.jdbcUrl())
                && !Boolean.getBoolean("smartstock.remote.gateway")) {
            throw new SQLException("The SmartStock Server Service database URL must use localhost. Registers connect through HTTPS port 8443.", "28000");
        }
        if (!config.hasPrimaryConnection()) {
            throw new SQLException(config.missingPrimaryConnectionMessage());
        }
        if (config.hasUnresolvedCredentialPlaceholders()) {
            throw new SQLException(config.missingPrimaryConnectionMessage());
        }
        SQLException last = null;
        for (int attempt = 1; attempt <= 3; attempt++) {
            Connection conn = null;
            try {
                conn = connectionFor(config);
                if (config.mode() == DatabaseMode.SERVER) {
                    ensureSchemaOnce(conn, config);
                }
                PerformanceDiagnostics.record("database", "primary-connect", started, true, -1);
                return conn;
            } catch (SQLException ex) {
                if (conn != null) {
                    try { conn.close(); } catch (SQLException ignored) { }
                }
                last = ex;
                if (attempt == 3 || !isTransientConnectionFailure(ex)) break;
                try {
                    Thread.sleep(500L * attempt);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw ex;
                }
            }
        }
        PerformanceDiagnostics.record("database", "primary-connect", started, false, -1);
        if (last != null) last.printStackTrace();
        throw last == null ? new SQLException("Database connection failed.") : last;
    }

    private static Connection connectionFor(DatabaseConfig config) throws SQLException {
        PostgresConnectionPool ready = pool;
        if (ready != null && config.equals(poolConfig)) {
            return ready.borrow();
        }
        PostgresConnectionPool selected;
        synchronized (POOL_LOCK) {
            if (pool == null || !config.equals(poolConfig)) {
                if (pool != null) pool.close();
                pool = new PostgresConnectionPool(withPrimarySecurity(config), config.dbUser(), config.dbPassword());
                poolConfig = config;
            }
            selected = pool;
        }
        return selected.borrow();
    }

    public static void shutdown() {
        configurationChanged();
    }

    static void configurationChanged() {
        synchronized (POOL_LOCK) {
            if (pool != null) pool.close();
            pool = null;
            poolConfig = null;
            ENSURED_SCHEMA_KEYS.clear();
        }
    }
    private static String withPrimarySecurity(DatabaseConfig config) {
        String url = config.jdbcUrl() == null ? "" : config.jdbcUrl();
        url = withJdbcParam(url, "connectTimeout", "10");
        url = withJdbcParam(url, "socketTimeout", "30");
        url = withJdbcParam(url, "tcpKeepAlive", "true");
        return url;
    }

    private static boolean isLoopbackJdbcUrl(String url) {
        String lower = url == null ? "" : url.toLowerCase();
        return lower.startsWith("jdbc:postgresql://127.0.0.1:")
                || lower.startsWith("jdbc:postgresql://localhost:")
                || lower.startsWith("jdbc:postgresql://[::1]:");
    }

    private static boolean isTransientConnectionFailure(SQLException ex) {
        String state = ex.getSQLState();
        return state == null || state.startsWith("08") || "57P01".equals(state) || "57P02".equals(state);
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
            SchemaContractService.requireLocalReady(conn);
            ENSURED_SCHEMA_KEYS.add(key);
        }
    }
}
