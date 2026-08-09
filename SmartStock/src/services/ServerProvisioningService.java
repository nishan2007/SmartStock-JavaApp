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
    private static final List<String> LOCAL_WORKFLOW_SCHEMAS = List.of(
            "database/permission_descriptions_setup.sql",
            "database/permission_descriptions_and_sections_backfill.sql",
            "database/employee_schedule_setup.sql",
            "database/location_management_setup.sql",
            "database/store_timezone_setup.sql",
            "database/department_setup.sql",
            "database/item_details_setup.sql",
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
            "database/custom_orders_setup.sql",
            "database/cash_drawer_management_setup.sql",
            "database/balance_sheet_expenses_setup.sql",
            "database/time_clock_setup.sql",
            "database/migrations/20260808120000_add_employee_unpaid_break.sql",
            "database/migrations/20260809153000_remove_local_wifi_sessions.sql",
            "database/store_transfer_setup.sql",
            "database/end_of_day_setup.sql",
            "database/maintenance_management_setup.sql",
            "database/inventory_sensitive_permissions.sql",
            "database/custom_order_sku_setup.sql",
            "database/custom_order_controls_setup.sql",
            "database/custom_order_line_discount_setup.sql",
            "database/custom_order_safety_controls_setup.sql",
            "database/quotations_invoices_setup.sql",
            "database/notification_permissions_setup.sql",
            "database/workflow_sync_identity_setup.sql",
            "database/app_updates_setup.sql"
    );

    public static void testLocalConnection() throws Exception { try (java.sql.Connection ignored=data.DB.getConnection()) { } }
    public static void testCloudConnection() throws Exception {
        if (!ServerSupabaseCredentials.isConfigured()) {
            throw new IllegalStateException("Save the Supabase Server Key first.");
        }
        CloudSyncManifest.fetch();
    }
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
                    + DatabaseCredentials.activeCredentialsPath() + ".");
        }

        JdbcParts localParts = JdbcParts.parse(config.jdbcUrl());
        if (!localParts.isLoopback()) {
            throw new SQLException("The local PostgreSQL URL must use 127.0.0.1, localhost, or ::1. Registers use the HTTPS service instead of PostgreSQL.");
        }
        List<String> steps = new ArrayList<>();
        createDatabaseIfMissing(localParts, config.dbUser(), config.dbPassword(), steps);
        try (Connection local = DriverManager.getConnection(config.jdbcUrl(), config.dbUser(), config.dbPassword())) {
            BaseSchemaInstaller.ensureSchema(local);
            ServerImageAssetService.ensureSchema(local);
            int customOrderStatements = installLocalWorkflowSchemas(local);
            SyncSchemaInstaller.ensureSchema(local);
            SyncSchemaInstaller.ensureSecurityHardening(local);
            LocalAuthCacheService.ensureSchema(local);
            disableLegacyRegisterRoles(local, steps);
            steps.add("Installed local base schema, custom order workflow schema, and sync/employee credential tables ("
                    + customOrderStatements + " custom-order statements).");
        }

        if (ServerSupabaseCredentials.isConfigured()) {
            CloudSyncManifest manifest = CloudSyncManifest.fetch();
            steps.add("Verified the server-only Supabase HTTPS API ("
                    + manifest.tables().size() + " materialized cloud tables available).");
            if (config.locationId() != null) {
                CloudSyncManifest mirror =
                        CloudSyncManifest.fetchStoreSnapshot(config.locationId());
                if (!mirror.tables().isEmpty()) {
                    try (Connection local = DriverManager.getConnection(
                            config.jdbcUrl(), config.dbUser(), config.dbPassword())) {
                        int restored = CloudRecoveryService.restoreStoreMirror(
                                local, config.locationId(), mirror);
                        steps.add("Hydrated " + restored
                                + " missing row(s) from this store's cloud mirror.");
                    }
                }
            }
        } else {
            throw new IllegalStateException(
                    "Save the Supabase Server Key before initializing this server.");
        }

        config.save();
        PostgresRuntimeService.CommandResult lanResult = PostgresRuntimeService.ensureServiceOnlyDatabaseAccess(config);
        steps.add("Saved server-mode configuration.");
        steps.add(lanResult.success()
                ? "PostgreSQL is restricted to the server; registers use HTTPS port 8443."
                : "PostgreSQL isolation needs manual attention: " + lanResult.output());
        steps.add("Background LAN and sync services remain stopped until the store server role is verified.");
        return new ProvisionResult(String.join("\n", steps));
    }

    private static int installLocalWorkflowSchemas(Connection local) throws Exception {
        return SqlScriptRunner.runScripts(local, LOCAL_WORKFLOW_SCHEMAS);
    }

    static List<String> localWorkflowSchemaResources() {
        return LOCAL_WORKFLOW_SCHEMAS;
    }

    private static void installWorkflowSyncIdentitySchema(Connection cloud) throws Exception {
        SqlScriptRunner.runScripts(cloud, List.of(
                "database/workflow_sync_identity_setup.sql",
                "database/supabase_rpc_security_setup.sql",
                "database/supabase_rls_hardening_setup.sql",
                "database/migrations/20260723143000_api_only_sync_exchange.sql"
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

    private static void disableLegacyRegisterRoles(Connection local, List<String> steps) {
        try (Statement stmt = local.createStatement()) {
            stmt.executeUpdate("""
                    DO $$
                    DECLARE role_name TEXT;
                    BEGIN
                      IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'smartstock_client') THEN
                        ALTER ROLE smartstock_client NOLOGIN;
                      END IF;
                      FOR role_name IN SELECT rolname FROM pg_roles WHERE rolname LIKE 'smartstock_device_%' LOOP
                        EXECUTE format('ALTER ROLE %I NOLOGIN', role_name);
                      END LOOP;
                    END $$
                    """);
            steps.add("Disabled all legacy register database login roles.");
        } catch (SQLException ex) {
            steps.add("Legacy register roles could not be disabled with this database user; run the server cutover command with a PostgreSQL administrator.");
        }
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

        boolean isLoopback() {
            String host = hostPort;
            int colon = host.lastIndexOf(':');
            if (host.startsWith("[") && host.contains("]")) {
                host = host.substring(1, host.indexOf(']'));
            } else if (colon > 0) {
                host = host.substring(0, colon);
            }
            return "127.0.0.1".equals(host) || "localhost".equalsIgnoreCase(host) || "::1".equals(host);
        }
    }
}
