package data;

import services.SyncSchemaInstaller;
import services.BaseSchemaInstaller;
import services.QuotationInvoiceSchemaInstaller;
import services.AppUpdateSchemaInstaller;
import services.EmailSchemaInstaller;
import services.EmployeePayrollSettingsService;
import services.TimeClockAutoCloseService;
import services.BadgeAccessSchemaInstaller;
import services.DeviceCredentialSchemaInstaller;
import services.LanApiSchemaInstaller;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import ui.helpers.BlockingCallGuard;
import ui.helpers.PerformanceDiagnostics;

public class DB {
    private static final Object SCHEMA_LOCK = new Object();
    private static final Set<String> ENSURED_SCHEMA_KEYS = ConcurrentHashMap.newKeySet();
    private static final Executor CLOUD_NETWORK_TIMEOUT_EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "smartstock-cloud-db-timeout");
        thread.setDaemon(true);
        return thread;
    });

    public static Connection getConnection() throws SQLException {
        BlockingCallGuard.check("primary database connection");
        long started = System.nanoTime();
        DatabaseConfig config = DatabaseConfig.load();
        if (config.mode() == DatabaseMode.CLIENT) {
            throw new SQLException("Direct database access is disabled on this register. SmartStock must use the authenticated LAN service.", "28000");
        }
        if (config.mode() == DatabaseMode.SERVER && !isLoopbackJdbcUrl(config.jdbcUrl())) {
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
            try {
                Connection conn = DriverManager.getConnection(withPrimarySecurity(config), config.dbUser(), config.dbPassword());
                if (config.mode() == DatabaseMode.SERVER) {
                    ensureSchemaOnce(conn, config);
                }
                PerformanceDiagnostics.record("database", "primary-connect", started, true, -1);
                return conn;
            } catch (SQLException ex) {
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

    public static Connection getCloudConnection() throws SQLException {
        BlockingCallGuard.check("cloud database connection");
        long started = System.nanoTime();
        DatabaseConfig config = DatabaseConfig.load();
        if (!config.hasCloudConnection()) {
            throw new SQLException("Cloud database connection is not configured on this machine.");
        }
        Connection conn = DriverManager.getConnection(withCloudTimeouts(config.cloudJdbcUrl()), config.cloudDbUser(), config.cloudDbPassword());
        configureCloudSession(conn);
        PerformanceDiagnostics.record("database", "cloud-connect", started, true, -1);
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
        if (!isLoopbackJdbcUrl(url)) {
            url = withJdbcParam(url, "sslmode", "require");
        }
        return url;
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
            BaseSchemaInstaller.ensureSchema(conn);
            DeviceCredentialSchemaInstaller.ensureSchema(conn);
            LanApiSchemaInstaller.ensureSchema(conn);
            BadgeAccessSchemaInstaller.ensureSchema(conn);
            EmployeePayrollSettingsService.ensureSchema(conn);
            TimeClockAutoCloseService.ensureSchema(conn);
            SyncSchemaInstaller.ensureSchema(conn);
            QuotationInvoiceSchemaInstaller.ensureSchema(conn);
            AppUpdateSchemaInstaller.ensureSchema(conn);
            EmailSchemaInstaller.ensureSchema(conn);
            ENSURED_SCHEMA_KEYS.add(key);
        }
    }
}
