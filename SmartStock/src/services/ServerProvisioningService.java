package services;

import data.DatabaseConfig;
import data.DatabaseCredentials;
import data.DatabaseMode;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public final class ServerProvisioningService {
    private ServerProvisioningService() {
    }

    public static ProvisionResult provision(DatabaseConfig config) throws Exception {
        if (config.mode() != DatabaseMode.SERVER) {
            throw new SQLException("Switch Mode to SERVER before provisioning this machine.");
        }
        if (isBlank(config.dbUser())) {
            throw new SQLException("Enter the local PostgreSQL admin/app user before provisioning.");
        }
        if (isBlank(config.jdbcUrl())) {
            throw new SQLException("Enter the local JDBC URL before provisioning.");
        }
        if (config.hasUnresolvedCredentialPlaceholders()) {
            throw new SQLException(config.missingPrimaryConnectionMessage());
        }
        if (looksLikeCredentialKey(config.dbUser()) || looksLikeCredentialKey(config.dbPassword())) {
            throw new SQLException("The database fields still contain credential labels like SMARTSTOCK_DB_USER. Click Load Saved Credentials, or paste the actual saved values from "
                    + DatabaseCredentials.CREDENTIALS_PATH + ".");
        }

        JdbcParts localParts = JdbcParts.parse(config.jdbcUrl());
        List<String> steps = new ArrayList<>();
        createDatabaseIfMissing(localParts, config.dbUser(), config.dbPassword(), steps);
        ensureClientRole(localParts, config.dbUser(), config.dbPassword(), steps);

        try (Connection local = DriverManager.getConnection(config.jdbcUrl(), config.dbUser(), config.dbPassword())) {
            BaseSchemaInstaller.ensureSchema(local);
            int customOrderStatements = installLocalWorkflowSchemas(local);
            SyncSchemaInstaller.ensureSchema(local);
            SyncSchemaInstaller.ensureSecurityHardening(local);
            LocalAuthCacheService.ensureSchema(local);
            ensureClientGrants(local, steps);
            steps.add("Installed local base schema, custom order workflow schema, and sync/employee credential tables ("
                    + customOrderStatements + " custom-order statements).");
        }

        if (config.hasCloudConnection()) {
            try (Connection local = DriverManager.getConnection(config.jdbcUrl(), config.dbUser(), config.dbPassword());
                 Connection cloud = DriverManager.getConnection(config.cloudJdbcUrl(), config.cloudDbUser(), config.cloudDbPassword())) {
                BaseSchemaInstaller.ensureSchema(local);
                installLocalWorkflowSchemas(local);
                SyncSchemaInstaller.ensureSchema(cloud);
                SyncSchemaInstaller.ensureSecurityHardening(cloud);
                int copiedRows = ReferenceDataSyncService.refreshFromCloud(local, cloud);
                int historyRows = ReferenceDataSyncService.pullExistingLocationHistory(local, cloud, config.locationId());
                int cachedImages = ImageCacheWarmupService.warmLocalCache(local);
                ReceiptCounterSyncService.SeedResult receiptSeed =
                        ReceiptCounterSyncService.seedFromExistingReceipts(local, cloud, config.locationId());
                steps.add("Verified cloud connection and installed cloud sync tables.");
                steps.add("Pulled " + copiedRows + " reference rows from cloud into local database.");
                steps.add("Pulled " + historyRows + " existing transaction/history rows from cloud into local database.");
                steps.add("Cached " + cachedImages + " image/logo file(s) locally for offline use.");
                steps.add("Seeded receipt counter from existing receipts for " + receiptSeed.locationsUpdated()
                        + " location(s); highest next receipt counter is " + receiptSeed.highestNextCounter() + ".");
            }
        } else {
            steps.add("Cloud credentials not configured yet; local server can run, but sync will stay offline.");
        }

        config.save();
        PostgresRuntimeService.CommandResult lanResult = PostgresRuntimeService.ensureLanServerAccess(config.serverPort());
        PostgresRuntimeService.CommandResult syncServiceResult = PostgresRuntimeService.ensureSyncServiceInstalled();
        SyncWorker.startIfServerMode();
        steps.add("Saved server-mode configuration.");
        steps.add(lanResult.success()
                ? "PostgreSQL is configured to accept SmartStock client connections from the local network."
                : "PostgreSQL LAN setup needs manual attention: " + lanResult.output());
        steps.add(syncServiceResult.success()
                ? "Background sync service is installed and ready."
                : "Background sync service install failed: " + syncServiceResult.output());
        steps.add("Started the in-app sync worker.");
        return new ProvisionResult(String.join("\n", steps));
    }

    private static int installLocalWorkflowSchemas(Connection local) throws Exception {
        return SqlScriptRunner.runScripts(local, List.of(
                "database/permission_descriptions_setup.sql",
                "database/permission_descriptions_and_sections_backfill.sql",
                "database/employee_schedule_setup.sql",
                "database/location_management_setup.sql",
                "database/store_timezone_setup.sql",
                "database/department_setup.sql",
                "database/vendor_setup.sql",
                "database/held_cart_setup.sql",
                "database/product_type_setup.sql",
                "database/product_size_setup.sql",
                "database/product_sku_setup.sql",
                "database/customer_type_setup.sql",
                "database/device_management_setup.sql",
                "database/hardware_setup_permission.sql",
                "database/company_customization_setup.sql",
                "database/company_customization_permission.sql",
                "database/company_preferences_permission.sql",
                "database/returns_setup.sql",
                "database/sale_discount_setup.sql",
                "database/normal_sales_audit_setup.sql",
                "database/sale_override_controls_setup.sql",
                "database/sales_transaction_source_setup.sql",
                "database/cash_drawer_management_setup.sql",
                "database/balance_sheet_expenses_setup.sql",
                "database/time_clock_setup.sql",
                "database/store_transfer_setup.sql",
                "database/end_of_day_setup.sql",
                "database/maintenance_management_setup.sql",
                "database/inventory_sensitive_permissions.sql",
                "database/custom_orders_setup.sql",
                "database/custom_order_sku_setup.sql",
                "database/custom_order_controls_setup.sql",
                "database/custom_order_line_discount_setup.sql",
                "database/custom_order_safety_controls_setup.sql",
                "database/quotations_invoices_setup.sql",
                "database/notification_permissions_setup.sql",
                "database/workflow_sync_identity_setup.sql",
                "database/app_updates_setup.sql"
        ));
    }

    private static void createDatabaseIfMissing(JdbcParts localParts, String user, String password, List<String> steps) throws SQLException {
        String adminUrl = localParts.withDatabase("postgres");
        try (Connection admin = DriverManager.getConnection(adminUrl, user, password)) {
            admin.setAutoCommit(true);
            if (databaseExists(admin, localParts.database())) {
                steps.add("Local database already exists: " + localParts.database());
                return;
            }
            try (Statement stmt = admin.createStatement()) {
                stmt.executeUpdate("CREATE DATABASE " + quoteIdentifier(localParts.database()));
                steps.add("Created local database: " + localParts.database());
            }
        }
    }

    private static void ensureClientRole(JdbcParts localParts, String user, String password, List<String> steps) throws SQLException {
        String adminUrl = localParts.withDatabase("postgres");
        String clientUser = DatabaseCredentials.DEFAULT_CLIENT_DB_USER;
        String clientPassword = DatabaseCredentials.DEFAULT_CLIENT_DB_PASSWORD;
        try (Connection admin = DriverManager.getConnection(adminUrl, user, password);
             Statement stmt = admin.createStatement()) {
            admin.setAutoCommit(true);
            try {
                if (roleExists(admin, clientUser)) {
                    stmt.executeUpdate("ALTER ROLE " + quoteIdentifier(clientUser)
                            + " WITH LOGIN PASSWORD " + quoteLiteral(clientPassword));
                    steps.add("Updated SmartStock client database role: " + clientUser);
                } else {
                    stmt.executeUpdate("CREATE ROLE " + quoteIdentifier(clientUser)
                            + " LOGIN PASSWORD " + quoteLiteral(clientPassword));
                    steps.add("Created SmartStock client database role: " + clientUser);
                }
            } catch (SQLException ex) {
                steps.add("Skipped SmartStock client role password repair because this PostgreSQL user cannot manage roles. "
                        + "Run the macOS installer or repair command on the server if client login credentials need repair: "
                        + rootCauseMessage(ex));
            }
            stmt.executeUpdate("GRANT CONNECT ON DATABASE " + quoteIdentifier(localParts.database())
                    + " TO " + quoteIdentifier(clientUser));
        }
    }

    private static void ensureClientGrants(Connection local, List<String> steps) throws SQLException {
        String clientUser = DatabaseCredentials.DEFAULT_CLIENT_DB_USER;
        try (Statement stmt = local.createStatement()) {
            stmt.executeUpdate("GRANT USAGE ON SCHEMA public TO " + quoteIdentifier(clientUser));
            stmt.executeUpdate("GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO " + quoteIdentifier(clientUser));
            stmt.executeUpdate("GRANT USAGE, SELECT, UPDATE ON ALL SEQUENCES IN SCHEMA public TO " + quoteIdentifier(clientUser));
            stmt.executeUpdate("ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO " + quoteIdentifier(clientUser));
            stmt.executeUpdate("ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT USAGE, SELECT, UPDATE ON SEQUENCES TO " + quoteIdentifier(clientUser));
        }
        steps.add("Granted SmartStock client role access to local app tables and sequences.");
    }

    private static boolean databaseExists(Connection conn, String databaseName) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT 1 FROM pg_database WHERE datname = ?")) {
            ps.setString(1, databaseName);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static boolean roleExists(Connection conn, String roleName) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT 1 FROM pg_roles WHERE rolname = ?")) {
            ps.setString(1, roleName);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static String quoteIdentifier(String identifier) throws SQLException {
        if (identifier == null || identifier.isBlank()) {
            throw new SQLException("Database name cannot be blank.");
        }
        if (!identifier.matches("[A-Za-z0-9_]+")) {
            throw new SQLException("Database name must contain only letters, numbers, and underscores.");
        }
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }

    private static String quoteLiteral(String value) {
        return "'" + (value == null ? "" : value).replace("'", "''") + "'";
    }

    private static String rootCauseMessage(SQLException ex) {
        Throwable cause = ex;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        String message = cause.getMessage();
        return message == null || message.isBlank() ? cause.getClass().getSimpleName() : message;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static boolean looksLikeCredentialKey(String value) {
        return value != null && value.trim().matches("SMARTSTOCK_[A-Z0-9_]+");
    }

    public record ProvisionResult(String message) {
    }

    private record JdbcParts(String prefix, String hostPort, String database, String query) {
        static JdbcParts parse(String jdbcUrl) throws SQLException {
            String prefix = "jdbc:postgresql://";
            if (jdbcUrl == null || !jdbcUrl.startsWith(prefix)) {
                throw new SQLException("Local JDBC URL must start with jdbc:postgresql://");
            }
            String rest = jdbcUrl.substring(prefix.length());
            int slash = rest.indexOf('/');
            if (slash < 0 || slash == rest.length() - 1) {
                throw new SQLException("Local JDBC URL must include a database name, for example jdbc:postgresql://127.0.0.1:5432/smartstock");
            }
            String hostPort = rest.substring(0, slash);
            String databaseAndQuery = rest.substring(slash + 1);
            int queryIndex = databaseAndQuery.indexOf('?');
            String database = queryIndex >= 0 ? databaseAndQuery.substring(0, queryIndex) : databaseAndQuery;
            String query = queryIndex >= 0 ? databaseAndQuery.substring(queryIndex) : "";
            if (database.isBlank()) {
                throw new SQLException("Local JDBC URL database name cannot be blank.");
            }
            return new JdbcParts(prefix, hostPort, database, query);
        }

        String withDatabase(String databaseName) {
            return prefix + hostPort + "/" + databaseName + query;
        }
    }
}
