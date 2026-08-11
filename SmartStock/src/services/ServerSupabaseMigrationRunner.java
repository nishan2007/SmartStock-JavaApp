package services;

import java.net.URI;
import java.net.URISyntaxException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Installs the immutable Supabase v1 baseline and ordered post-v1 migrations. */
public final class ServerSupabaseMigrationRunner {
    private static final long ADVISORY_LOCK = 0x534D41525453544FL;

    private ServerSupabaseMigrationRunner() {
    }

    public static Status inspect(String connectionString, char[] password,
                                 SupabaseProjectConfig project) throws Exception {
        ConnectionSpec spec = ConnectionSpec.parse(connectionString, project);
        try (Connection connection = open(spec, password)) {
            ensureProject(connection, project);
            if (!metadataExists(connection)) {
                return new Status(false, false, 0, availableCount(),
                        hasManagedTables(connection)
                                ? "An unmanaged SmartStock schema already exists."
                                : "The empty Supabase project is ready for the v1 baseline.");
            }
            int applied = appliedCount(connection);
            SchemaContractService.Readiness readiness =
                    SchemaContractService.validateCloudApplied(connection,
                            appliedContractResources(connection));
            boolean current = readiness.ready() && applied == availableCount();
            return new Status(true, current, applied, availableCount(),
                    !readiness.ready() ? readiness.message()
                            : current ? "The Supabase v1 schema is current."
                            : "The Supabase schema is valid and has pending immutable post-v1 migrations.");
        }
    }

    public static Result migrate(String connectionString, char[] password,
                                 SupabaseProjectConfig project) throws Exception {
        Objects.requireNonNull(project, "Supabase project is required.");
        if (password == null || password.length == 0) {
            throw new IllegalArgumentException("Enter the Supabase database password.");
        }
        ConnectionSpec spec = ConnectionSpec.parse(connectionString, project);
        try (Connection connection = open(spec, password)) {
            ensureProject(connection, project);
            acquireLock(connection);
            int applied = 0;
            try {
                if (!metadataExists(connection)) {
                    if (hasManagedTables(connection)) {
                        throw new SQLException("This project already contains SmartStock tables but has no v1 contract. Automatic adoption is blocked.");
                    }
                    SchemaContractService.installCloudBaseline(connection);
                    applied++;
                } else {
                    SchemaContractService.Readiness readiness =
                            SchemaContractService.validateCloudApplied(connection,
                                    appliedContractResources(connection));
                    if (!readiness.ready()) throw new SQLException(readiness.message());
                }

                for (String resource : SchemaContractService.cloudPostV1MigrationResources()) {
                    String sql = SqlScriptRunner.readResource(resource);
                    String checksum = sha256(sql);
                    String existing = appliedChecksum(connection, resource);
                    if (existing != null) {
                        if (!checksum.equals(existing)) {
                            throw new SQLException("Applied migration is immutable but its packaged checksum changed: " + resource);
                        }
                        continue;
                    }
                    applyOne(connection, resource, sql, checksum);
                    applied++;
                }
                SchemaContractService.refreshCloudContract(connection);
                verify(connection);
                return new Result(applied, availableCount(), applied == 0
                        ? "Supabase schema v1 is already current."
                        : "Applied the Supabase v1 baseline and "
                        + Math.max(0, applied - 1) + " post-v1 migration(s).");
            } finally {
                releaseLock(connection);
            }
        }
    }

    static List<String> migrationResources() {
        return SchemaContractService.cloudContractResources();
    }

    private static int availableCount() {
        return 1 + SchemaContractService.cloudPostV1MigrationResources().size();
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
                """); ResultSet rows = statement.executeQuery()) {
            if (!rows.next() || !"postgres".equals(rows.getString(1))) {
                throw new SQLException("The Supabase connection must target postgres.");
            }
            if (!"on".equalsIgnoreCase(rows.getString(2))) {
                throw new SQLException("The Supabase migration connection is not protected by TLS.");
            }
            if (rows.getInt(3) < 150000) {
                throw new SQLException("SmartStock requires Supabase PostgreSQL 15 or newer.");
            }
        }
        if (project.projectRef() == null || project.projectRef().isBlank()) {
            throw new SQLException("The configured Supabase project reference is missing.");
        }
    }

    private static boolean metadataExists(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT to_regclass('smartstock_private.smartstock_schema_metadata') IS NOT NULL");
             ResultSet rows = statement.executeQuery()) {
            return rows.next() && rows.getBoolean(1);
        }
    }

    private static boolean hasManagedTables(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT EXISTS (
                    SELECT 1 FROM pg_tables WHERE schemaname='public'
                ) OR to_regnamespace('smartstock_private') IS NOT NULL
                """); ResultSet rows = statement.executeQuery()) {
            return rows.next() && rows.getBoolean(1);
        }
    }

    private static int appliedCount(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT count(*) FROM smartstock_private.schema_migrations");
             ResultSet rows = statement.executeQuery()) {
            return rows.next() ? rows.getInt(1) : 0;
        }
    }

    private static List<String> appliedContractResources(Connection connection)
            throws SQLException {
        List<String> resources = new ArrayList<>(
                SchemaContractService.cloudBaselineResources());
        for (String resource : SchemaContractService.cloudPostV1MigrationResources()) {
            if (appliedChecksum(connection, resource) != null) resources.add(resource);
        }
        return List.copyOf(resources);
    }

    private static String appliedChecksum(Connection connection, String resource)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT checksum_sha256 FROM smartstock_private.schema_migrations
                WHERE migration_name=?
                """)) {
            statement.setString(1, resource);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? rows.getString(1) : null;
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
                        (migration_name,checksum_sha256,app_version)
                    VALUES (?,?,?)
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
        List<String> expected = List.of(
                "app_releases", "locations", "roles", "permissions",
                "role_permissions", "mobile_permissions", "role_mobile_permissions",
                "users", "user_locations", "devices", "device_sessions",
                "email_outbox", "email_outbox_events", "image_assets",
                "image_asset_references", "sync_outbox", "sync_applied_events",
                "store_sync_status", "remote_admin_commands", "smartstock_store_rows",
                "smartstock_store_mirror_status", "smartstock_store_snapshot_generations",
                "smartstock_store_snapshot_rows", "store_server_instances",
                "store_server_handoffs", "store_server_events",
                "smartstock_cross_store_refund_requests",
                "smartstock_cross_store_refund_lines", "register_transfers");
        List<String> actual = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT tablename FROM pg_tables WHERE schemaname='public' ORDER BY tablename");
             ResultSet rows = statement.executeQuery()) {
            while (rows.next()) actual.add(rows.getString(1));
        }
        if (!actual.equals(expected.stream().sorted().toList())) {
            throw new SQLException("Supabase v1 public table inventory does not match the approved 29-table contract.");
        }
        SchemaContractService.Readiness readiness =
                SchemaContractService.validateCloud(connection);
        if (!readiness.ready()) throw new SQLException(readiness.message());
    }

    private static void acquireLock(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT pg_advisory_lock(?)")) {
            statement.setLong(1, ADVISORY_LOCK);
            statement.execute();
        }
    }

    private static void releaseLock(Connection connection) {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT pg_advisory_unlock(?)")) {
            statement.setLong(1, ADVISORY_LOCK);
            statement.execute();
        } catch (SQLException ignored) {
        }
    }

    private static String sha256(String value) {
        try {
            return java.util.HexFormat.of().formatHex(
                    java.security.MessageDigest.getInstance("SHA-256")
                            .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
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

    public record ConnectionSpec(String host, int port, String database, String username,
                                 String projectRef) {
        public static ConnectionSpec parse(String value, SupabaseProjectConfig project) {
            String clean = value == null ? "" : value.trim();
            if (clean.startsWith("jdbc:")) clean = clean.substring(5);
            if (!clean.startsWith("postgresql://") && !clean.startsWith("postgres://")) {
                throw new IllegalArgumentException("Paste a Supabase Direct or Session Pooler connection string.");
            }
            URI uri;
            try {
                uri = new URI(clean.replace("[YOUR-PASSWORD]", "PASSWORD_PLACEHOLDER"));
            } catch (URISyntaxException ex) {
                throw new IllegalArgumentException("The Supabase connection string is invalid.", ex);
            }
            int port = uri.getPort() < 0 ? 5432 : uri.getPort();
            if (port == 6543) throw new IllegalArgumentException(
                    "Transaction-pooler port 6543 cannot run migrations. Use port 5432.");
            if (port != 5432) throw new IllegalArgumentException(
                    "Supabase migrations require port 5432.");
            if (uri.getHost() == null || uri.getHost().isBlank()) {
                throw new IllegalArgumentException("The Supabase database host is missing.");
            }
            String userInfo = uri.getUserInfo();
            if (userInfo == null || userInfo.isBlank()) {
                throw new IllegalArgumentException("The Supabase database username is missing.");
            }
            String[] userParts = userInfo.split(":", 2);
            if (userParts.length == 2 && !"PASSWORD_PLACEHOLDER".equals(userParts[1])) {
                throw new IllegalArgumentException("Do not embed the database password in the connection string.");
            }
            String database = uri.getPath() == null ? ""
                    : uri.getPath().replaceFirst("^/+", "");
            if (!"postgres".equals(database)) throw new IllegalArgumentException(
                    "The Supabase connection string must target postgres.");
            String expectedRef = project == null ? null : project.projectRef();
            String actualRef = projectRef(uri.getHost(), userParts[0]);
            if (expectedRef == null || actualRef == null
                    || !expectedRef.equalsIgnoreCase(actualRef)) {
                throw new IllegalArgumentException("The database connection does not belong to the configured Supabase project.");
            }
            return new ConnectionSpec(uri.getHost().toLowerCase(Locale.ROOT), port,
                    database, userParts[0], actualRef);
        }

        private static String projectRef(String host, String username) {
            String lowerHost = host.toLowerCase(Locale.ROOT);
            if (lowerHost.startsWith("db.") && lowerHost.endsWith(".supabase.co")) {
                return lowerHost.substring(3,
                        lowerHost.length() - ".supabase.co".length());
            }
            if (lowerHost.endsWith(".pooler.supabase.com")) {
                int dot = username.lastIndexOf('.');
                return dot < 0 ? null
                        : username.substring(dot + 1).toLowerCase(Locale.ROOT);
            }
            return null;
        }

        String hostForJdbc() {
            return host.contains(":") ? "[" + host + "]" : host;
        }
    }
}
