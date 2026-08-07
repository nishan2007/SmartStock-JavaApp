package services;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Applies SmartStock's packaged Supabase schema using a one-time TLS database
 * connection. The database password is supplied as a char[] and is never saved.
 */
public final class ServerSupabaseMigrationRunner {
    private static final long ADVISORY_LOCK = 0x534D41525453544FL;

    private static final List<String> MIGRATIONS = List.of(
            "database/base_schema_setup.sql",
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
            "database/email_outbox_setup.sql",
            "database/app_updates_setup.sql",
            "database/supabase_rpc_security_setup.sql",
            "database/supabase_rls_hardening_setup.sql",
            "database/migrations/20260613100000_align_quotation_invoice_print_defaults.sql",
            "database/migrations/20260616120000_add_change_basket_target.sql",
            "database/migrations/20260616123000_add_change_basket_updates.sql",
            "database/migrations/20260616185136_add_smartstock_email_outbox.sql",
            "database/migrations/20260616185357_harden_smartstock_email_outbox_policies.sql",
            "database/migrations/20260616211000_add_sales_device_name.sql",
            "database/migrations/20260619204003_add_price_tag_template_preferences.sql",
            "database/migrations/20260619204739_add_price_tag_template_slots.sql",
            "database/migrations/20260619231500_add_employee_weekly_schedule.sql",
            "database/migrations/20260619233000_harden_employee_weekly_schedule.sql",
            "database/migrations/20260620034000_add_schedule_lunch_times.sql",
            "database/migrations/20260621171429_add_workflow_parent_sync_uuids.sql",
            "database/migrations/20260715153000_add_store_schedule_shifts.sql",
            "database/migrations/20260715183000_add_employee_schedule_user_date_index.sql",
            "database/migrations/20260715193000_add_employee_payroll_settings_and_overtime.sql",
            "database/migrations/20260715213000_add_time_clock_auto_close_review.sql",
            "database/migrations/20260716094500_add_company_schedule_holidays.sql",
            "database/migrations/20260716153000_add_rfid_nfc_badge_access.sql",
            "database/migrations/20260716190000_add_balance_sheet_email_recipient.sql",
            "database/migrations/20260716210000_add_custom_item_classification.sql",
            "database/migrations/20260717230000_add_weekly_salary_pay_period.sql",
            "database/migrations/20260717233000_add_employee_hire_date.sql",
            "database/migrations/20260718120000_finalize_supabase_security_boundary.sql",
            "database/migrations/20260718123000_enable_rls_on_internal_sync_tables.sql",
            "database/migrations/20260718124500_remove_anonymous_login_lookup.sql",
            "database/migrations/20260718185135_reconcile_shared_schema_contract.sql",
            "database/migrations/20260718185621_normalize_shared_column_defaults.sql",
            "database/migrations/20260718190019_require_sale_item_product.sql",
            "database/migrations/20260718191243_reharden_public_function_search_paths.sql",
            "database/migrations/20260718213000_add_sale_item_report_indexes.sql",
            "database/migrations/20260720120000_add_badge_pin_login_preference.sql",
            "database/migrations/20260722180000_image_asset_registry.sql",
            "database/migrations/20260723013000_add_employee_nickname.sql",
            "database/migrations/20260723143000_api_only_sync_exchange.sql",
            "database/migrations/20260724062034_harden_remote_admin_internal_tables.sql",
            "database/migrations/20260724151753_add_device_auto_logout_policy.sql",
            "database/migrations/20260806120000_add_other_income_entries.sql",
            "database/migrations/20260806180000_store_server_registry.sql",
            "database/migrations/20260806181000_store_server_registry_indexes.sql",
            "database/migrations/20260806193000_require_whole_gyd_other_income.sql",
            "database/migrations/20260806210000_editable_balance_sheet_revisions.sql",
            "database/migrations/20260806213000_index_balance_sheet_revision_foreign_keys.sql",
            "database/migrations/20260723220000_first_admin_bootstrap.sql"
    );

    private ServerSupabaseMigrationRunner() {
    }

    public static Status inspect(String connectionString, char[] password,
                                 SupabaseProjectConfig project) throws Exception {
        ConnectionSpec spec = ConnectionSpec.parse(connectionString, project);
        try (Connection connection = open(spec, password)) {
            ensureProject(connection, project);
            if (!ledgerExists(connection)) {
                return new Status(false, false, 0, MIGRATIONS.size(),
                        hasManagedTables(connection)
                                ? "An unmanaged SmartStock schema already exists."
                                : "The Supabase project is ready to initialize.");
            }
            int applied = appliedCount(connection);
            return new Status(true, applied == MIGRATIONS.size(), applied,
                    MIGRATIONS.size(), applied == MIGRATIONS.size()
                    ? "The Supabase schema is current."
                    : (MIGRATIONS.size() - applied) + " update(s) are available.");
        }
    }

    public static Result migrate(String connectionString, char[] password,
                                 SupabaseProjectConfig project) throws Exception {
        Objects.requireNonNull(project, "Supabase project is required.");
        ConnectionSpec spec = ConnectionSpec.parse(connectionString, project);
        if (password == null || password.length == 0) {
            throw new IllegalArgumentException("Enter the Supabase database password.");
        }
        try (Connection connection = open(spec, password)) {
            ensureProject(connection, project);
            boolean ledgerPresent = ledgerExists(connection);
            if (!ledgerPresent && hasManagedTables(connection)) {
                throw new SQLException("This project already contains SmartStock tables but has no "
                        + "SmartStock migration ledger. Automatic adoption is blocked to prevent data loss.");
            }
            createLedger(connection);
            acquireLock(connection);
            int applied = 0;
            try {
                for (String resource : MIGRATIONS) {
                    String sql = SqlScriptRunner.readResource(resource);
                    String checksum = sha256(sql);
                    Applied existing = applied(connection, resource);
                    if (existing != null) {
                        if (!checksum.equals(existing.checksum())) {
                            throw new SQLException("Packaged migration checksum changed after deployment: "
                                    + resource);
                        }
                        continue;
                    }
                    applyOne(connection, resource, sql, checksum);
                    applied++;
                }
                verify(connection);
                return new Result(applied, MIGRATIONS.size(),
                        applied == 0 ? "Supabase schema is already current."
                                : "Applied " + applied + " Supabase migration(s).");
            } finally {
                releaseLock(connection);
            }
        }
    }

    static List<String> migrationResources() {
        return MIGRATIONS;
    }

    private static Connection open(ConnectionSpec spec, char[] password) throws SQLException {
        String jdbc = "jdbc:postgresql://" + spec.hostForJdbc() + ":" + spec.port()
                + "/" + spec.database()
                + "?sslmode=verify-full&connectTimeout=15&socketTimeout=120"
                + "&ApplicationName=SmartStock-Supabase-Initializer";
        return DriverManager.getConnection(jdbc, spec.username(), new String(password));
    }

    private static void ensureProject(Connection connection, SupabaseProjectConfig project)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT current_database(), current_setting('ssl', true),
                       current_setting('server_version_num')::integer
                """);
             ResultSet rows = statement.executeQuery()) {
            if (!rows.next() || !"postgres".equals(rows.getString(1))) {
                throw new SQLException("The Supabase connection must target the postgres database.");
            }
            if (!"on".equalsIgnoreCase(rows.getString(2))) {
                throw new SQLException("The Supabase migration connection is not protected by TLS.");
            }
            if (rows.getInt(3) < 150000) {
                throw new SQLException("SmartStock requires Supabase PostgreSQL 15 or newer.");
            }
        }
        String expected = project.projectRef();
        if (expected == null || expected.isBlank()) {
            throw new SQLException("The configured Supabase URL does not contain a hosted project reference.");
        }
    }

    private static boolean ledgerExists(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT to_regclass('smartstock_private.schema_migrations') IS NOT NULL");
             ResultSet rows = statement.executeQuery()) {
            return rows.next() && rows.getBoolean(1);
        }
    }

    private static boolean hasManagedTables(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT to_regclass('public.users') IS NOT NULL
                    OR to_regclass('public.locations') IS NOT NULL
                    OR to_regclass('public.roles') IS NOT NULL
                """);
             ResultSet rows = statement.executeQuery()) {
            return rows.next() && rows.getBoolean(1);
        }
    }

    private static void createLedger(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA IF NOT EXISTS smartstock_private");
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS smartstock_private.schema_migrations (
                        migration_name text PRIMARY KEY,
                        checksum_sha256 text NOT NULL,
                        app_version text,
                        applied_at timestamptz NOT NULL DEFAULT current_timestamp
                    )
                    """);
            statement.execute("REVOKE ALL ON SCHEMA smartstock_private FROM PUBLIC, anon, authenticated");
            statement.execute("REVOKE ALL ON ALL TABLES IN SCHEMA smartstock_private FROM PUBLIC, anon, authenticated");
        }
    }

    private static int appliedCount(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(
                     "SELECT count(*) FROM smartstock_private.schema_migrations")) {
            return rows.next() ? rows.getInt(1) : 0;
        }
    }

    private static Applied applied(Connection connection, String name) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT checksum_sha256
                FROM smartstock_private.schema_migrations
                WHERE migration_name=?
                """)) {
            statement.setString(1, name);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? new Applied(rows.getString(1)) : null;
            }
        }
    }

    private static void applyOne(Connection connection, String resource, String sql,
                                 String checksum) throws Exception {
        boolean originalAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            SqlScriptRunner.runSql(connection, sql);
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO smartstock_private.schema_migrations
                        (migration_name, checksum_sha256, app_version)
                    VALUES (?, ?, ?)
                    """)) {
                statement.setString(1, resource);
                statement.setString(2, checksum);
                statement.setString(3, appVersion());
                statement.executeUpdate();
            }
            connection.commit();
        } catch (Exception ex) {
            connection.rollback();
            throw ex;
        } finally {
            connection.setAutoCommit(originalAutoCommit);
        }
    }

    private static void verify(Connection connection) throws SQLException {
        List<String> missing = new ArrayList<>();
        for (String object : List.of("users", "locations", "roles", "user_locations",
                "smartstock_sync_events", "smartstock_store_rows", "store_server_instances",
                "store_server_handoffs", "store_server_events")) {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT to_regclass(?) IS NOT NULL")) {
                statement.setString(1, "public." + object);
                try (ResultSet rows = statement.executeQuery()) {
                    if (!rows.next() || !rows.getBoolean(1)) missing.add(object);
                }
            }
        }
        if (!missing.isEmpty()) {
            throw new SQLException("Supabase initialization did not create required objects: "
                    + String.join(", ", missing));
        }
    }

    private static void acquireLock(Connection connection) throws SQLException {
        try (PreparedStatement statement =
                     connection.prepareStatement("SELECT pg_advisory_lock(?)")) {
            statement.setLong(1, ADVISORY_LOCK);
            statement.execute();
        }
    }

    private static void releaseLock(Connection connection) {
        try (PreparedStatement statement =
                     connection.prepareStatement("SELECT pg_advisory_unlock(?)")) {
            statement.setLong(1, ADVISORY_LOCK);
            statement.execute();
        } catch (SQLException ignored) {
        }
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(
                    value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("SHA-256 is unavailable.", ex);
        }
    }

    private static String appVersion() {
        String value = ServerSupabaseMigrationRunner.class.getPackage().getImplementationVersion();
        return value == null || value.isBlank() ? "development" : value;
    }

    public record Status(boolean initialized, boolean current, int applied, int available,
                         String message) {
    }

    public record Result(int applied, int available, String message) {
    }

    private record Applied(String checksum) {
    }

    public record ConnectionSpec(String host, int port, String database, String username,
                                 String projectRef) {
        public static ConnectionSpec parse(String value, SupabaseProjectConfig project) {
            String clean = value == null ? "" : value.trim();
            if (clean.startsWith("jdbc:")) clean = clean.substring(5);
            if (!clean.startsWith("postgresql://") && !clean.startsWith("postgres://")) {
                throw new IllegalArgumentException(
                        "Paste a Supabase Direct or Session Pooler connection string.");
            }
            URI uri;
            try {
                uri = new URI(clean.replace("[YOUR-PASSWORD]", "PASSWORD_PLACEHOLDER"));
            } catch (URISyntaxException ex) {
                throw new IllegalArgumentException("The Supabase connection string is invalid.", ex);
            }
            int port = uri.getPort() < 0 ? 5432 : uri.getPort();
            if (port == 6543) {
                throw new IllegalArgumentException(
                        "Transaction-pooler port 6543 cannot run migrations. Use Direct or Session mode on port 5432.");
            }
            if (port != 5432) {
                throw new IllegalArgumentException("Supabase migrations require port 5432.");
            }
            if (uri.getHost() == null || uri.getHost().isBlank()) {
                throw new IllegalArgumentException("The Supabase database host is missing.");
            }
            String userInfo = uri.getUserInfo();
            if (userInfo == null || userInfo.isBlank()) {
                throw new IllegalArgumentException("The Supabase database username is missing.");
            }
            String[] userParts = userInfo.split(":", 2);
            if (userParts.length == 2 && !"PASSWORD_PLACEHOLDER".equals(userParts[1])) {
                throw new IllegalArgumentException(
                        "Do not embed the database password in the connection string. Enter it in the password field.");
            }
            String path = uri.getPath();
            String database = path == null ? "" : path.replaceFirst("^/+", "");
            if (!"postgres".equals(database)) {
                throw new IllegalArgumentException(
                        "The Supabase connection string must target the postgres database.");
            }
            String expectedRef = project == null ? null : project.projectRef();
            String actualRef = projectRef(uri.getHost(), userParts[0]);
            if (expectedRef == null || actualRef == null
                    || !expectedRef.equalsIgnoreCase(actualRef)) {
                throw new IllegalArgumentException(
                        "The database connection does not belong to the configured Supabase project.");
            }
            return new ConnectionSpec(uri.getHost().toLowerCase(Locale.ROOT), port,
                    database, userParts[0], actualRef);
        }

        private static String projectRef(String host, String username) {
            String lowerHost = host.toLowerCase(Locale.ROOT);
            if (lowerHost.startsWith("db.") && lowerHost.endsWith(".supabase.co")) {
                return lowerHost.substring(3, lowerHost.length() - ".supabase.co".length());
            }
            if (lowerHost.endsWith(".pooler.supabase.com")) {
                int dot = username.lastIndexOf('.');
                return dot < 0 ? null : username.substring(dot + 1).toLowerCase(Locale.ROOT);
            }
            return null;
        }

        String hostForJdbc() {
            return host.contains(":") ? "[" + host + "]" : host;
        }
    }
}
