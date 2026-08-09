package services;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import data.DB;
import data.EnvironmentProfile;
import utils.SecureCredentialStore;

import java.io.InputStream;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.Date;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.UUID;

/**
 * Resumable first-administrator bootstrap across Supabase Auth, hosted
 * SmartStock tables, and the store server's local database.
 */
public final class ServerFirstAdministratorService {
    private ServerFirstAdministratorService() {
    }

    public static boolean isComplete() {
        try (Connection connection = DB.getConnection()) {
            return isComplete(connection);
        } catch (Exception ex) {
            return false;
        }
    }

    public static boolean requiresFirstOnlineLogin() {
        try (Connection connection = DB.getConnection()) {
            try (PreparedStatement statement = connection.prepareStatement("""
                    SELECT EXISTS (
                        SELECT 1
                        FROM users u
                        JOIN roles r ON r.role_id=u.role_id
                        JOIN user_locations ul ON ul.user_id=u.user_id
                        WHERE COALESCE(u.is_active, TRUE)=TRUE
                          AND UPPER(r.role_name)='ADMIN'
                          AND u.auth_user_id IS NOT NULL
                          AND NOT EXISTS (
                              SELECT 1 FROM local_auth_cache cache
                              WHERE cache.user_id=u.user_id
                                AND cache.password_salt IS NOT NULL
                                AND cache.password_hash IS NOT NULL
                          )
                    )
                    """);
                 ResultSet rows = statement.executeQuery()) {
                return rows.next() && rows.getBoolean(1);
            }
        } catch (Exception ex) {
            return false;
        }
    }

    static boolean isComplete(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT EXISTS (
                    SELECT 1
                    FROM users u
                    JOIN roles r ON r.role_id=u.role_id
                    JOIN user_locations ul ON ul.user_id=u.user_id
                    WHERE COALESCE(u.is_active, TRUE)=TRUE
                      AND UPPER(r.role_name)='ADMIN'
                      AND u.auth_user_id IS NOT NULL
                )
                """);
             ResultSet rows = statement.executeQuery()) {
            return rows.next() && rows.getBoolean(1);
        }
    }

    public static Connection openDevelopmentSource() throws Exception {
        EnvironmentProfile profile = EnvironmentProfile.DEVELOPMENT;
        Properties properties = new Properties();
        if (Files.isRegularFile(profile.file("database.properties"))) {
            try (InputStream input = Files.newInputStream(profile.file("database.properties"))) {
                properties.load(input);
            }
        }
        String jdbc = firstNonBlank(properties.getProperty("jdbc.url"),
                "jdbc:postgresql://127.0.0.1:5432/smartstock_dev");
        String user = firstNonBlank(
                SecureCredentialStore.read(profile.secretKey("primary-db-user")),
                readCredentialFile(profile, "SMARTSTOCK_DB_USER"));
        String password = firstNonBlank(
                SecureCredentialStore.read(profile.secretKey("primary-db-password")),
                readCredentialFile(profile, "SMARTSTOCK_DB_PASSWORD"));
        if (user == null || password == null) {
            throw new SQLException("The Development profile does not have saved local database credentials.");
        }
        return DriverManager.getConnection(jdbc, user, password);
    }

    public static Connection openTemporarySource(String jdbcUrl, String user, char[] password)
            throws SQLException {
        if (jdbcUrl == null || !jdbcUrl.startsWith("jdbc:postgresql://")) {
            throw new IllegalArgumentException("Enter a PostgreSQL JDBC URL.");
        }
        if (user == null || user.isBlank()) {
            throw new IllegalArgumentException("Enter the source database user.");
        }
        char[] copy = password == null ? new char[0] : password.clone();
        try {
            return DriverManager.getConnection(jdbcUrl.trim(), user.trim(), new String(copy));
        } finally {
            java.util.Arrays.fill(copy, '\0');
            if (password != null) java.util.Arrays.fill(password, '\0');
        }
    }

    public static List<ProductionIdentityMigrationService.SourceIdentity>
    listAdministrators(Connection source) throws SQLException {
        return ProductionIdentityMigrationService.listActiveAdministrators(source);
    }

    public static List<Identity> listDevelopmentAdministrators() throws Exception {
        try (Connection source = openDevelopmentSource()) {
            return listAdministrators(source).stream()
                    .map(ServerFirstAdministratorService::transferred).toList();
        }
    }

    public static List<Identity> listTemporaryAdministrators(
            String jdbcUrl, String user, char[] password) throws Exception {
        try (Connection source = openTemporarySource(jdbcUrl, user, password)) {
            return listAdministrators(source).stream()
                    .map(ServerFirstAdministratorService::transferred).toList();
        }
    }

    public static Identity transferred(
            ProductionIdentityMigrationService.SourceIdentity source) {
        if (source == null || !source.active()
                || !"ADMIN".equalsIgnoreCase(source.roleName())) {
            throw new IllegalArgumentException("Select an active administrator.");
        }
        return new Identity(source.username(), source.email(), source.fullName(),
                source.nickname(), source.dateOfBirth(), source.badgeId(),
                source.badgeSecretSalt(), source.badgeSecretHash(),
                source.badgeGeneratedAt(), true);
    }

    public static Identity newAdministrator(String username, String email,
                                             String fullName) {
        return new Identity(required(username, "Username"), required(email, "Email"),
                required(fullName, "Display name"), null, null, null,
                null, null, null, false);
    }

    public static BootstrapResult bootstrap(Identity identity, char[] password)
            throws Exception {
        if (identity == null) throw new IllegalArgumentException("Administrator identity is required.");
        char[] copy = password == null ? new char[0] : password.clone();
        try (Connection local = DB.getConnection()) {
            ensureSetupState(local);
            Store store = requireStore(local);
            PendingAuth pending = pendingAuth(local);
            UUID authId;
            if (pending != null) {
                if (!pending.email().equalsIgnoreCase(identity.email())) {
                    throw new SQLException("A different first administrator is already pending. "
                            + "Finish or repair that bootstrap before creating another.");
                }
                authId = pending.authUserId();
            } else {
                authId = SupabaseAuthAdminClient.createConfirmedUser(
                        identity.email(), copy.clone(), identity.fullName());
                savePending(local, identity.email(), authId);
            }

            int cloudUserId = bootstrapCloud(identity, authId, store);
            installLocal(local, cloudUserId, authId, identity, store.locationId());
            markComplete(local, identity.email(), authId, cloudUserId);
            return new BootstrapResult(cloudUserId, authId, identity.transferred(),
                    "First administrator is linked locally and in Supabase. "
                            + "Sign in online once to enable offline login.");
        } finally {
            java.util.Arrays.fill(copy, '\0');
            if (password != null) java.util.Arrays.fill(password, '\0');
        }
    }

    private static int bootstrapCloud(Identity identity, UUID authId, Store store)
            throws Exception {
        JsonObject payload = new JsonObject();
        payload.addProperty("auth_user_id", authId.toString());
        payload.addProperty("username", identity.username());
        payload.addProperty("email", identity.email().toLowerCase(Locale.ROOT));
        payload.addProperty("full_name", identity.fullName());
        add(payload, "nickname", identity.nickname());
        add(payload, "date_of_birth", identity.dateOfBirth() == null
                ? null : identity.dateOfBirth().toString());
        add(payload, "badge_id", identity.badgeId());
        add(payload, "badge_secret_salt", identity.badgeSecretSalt());
        add(payload, "badge_secret_hash", identity.badgeSecretHash());
        add(payload, "badge_generated_at", identity.badgeGeneratedAt() == null
                ? null : identity.badgeGeneratedAt().toInstant().toString());
        payload.addProperty("location_id", store.locationId());
        payload.addProperty("store_name", store.name());
        payload.addProperty("store_code", store.code());
        payload.addProperty("timezone", store.timezone());
        add(payload, "address", store.address());
        JsonObject request = new JsonObject();
        request.add("payload", payload);
        SupabaseServerApi.Response response =
                SupabaseServerApi.postRpc("smartstock_bootstrap_first_admin", request);
        if (!response.successful()) {
            throw new SQLException("Hosted first-administrator bootstrap failed (HTTP "
                    + response.statusCode() + ").");
        }
        JsonObject result = JsonParser.parseString(response.body()).getAsJsonObject();
        if (!result.has("user_id")) {
            throw new SQLException("Hosted first-administrator bootstrap returned no user ID.");
        }
        return result.get("user_id").getAsInt();
    }

    private static void installLocal(Connection connection, int userId, UUID authId,
                                     Identity identity, int locationId) throws SQLException {
        boolean autoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            Integer existing = findLocalUser(connection, authId, identity);
            if (existing != null && existing != userId) {
                throw new SQLException("The local administrator conflicts with the hosted user ID.");
            }
            if (existing == null) {
                int roleId = adminRole(connection);
                try (PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO users (
                            user_id,username,password_hash,full_name,nickname,email,date_of_birth,
                            hire_date,badge_id,badge_secret_salt,badge_secret_hash,
                            badge_generated_at,badge_print_count,compensation_type,salary,
                            role_id,auth_user_id,is_active,password_cache_invalidated_at,
                            employee_pin_salt,employee_pin_hash,employee_pin_updated_at
                        )
                        VALUES (?,?,NULL,?,?,?,?,CURRENT_DATE,?,?,?,?,0,'HOURLY',0,
                                ?,?::uuid,TRUE,CURRENT_TIMESTAMP,NULL,NULL,NULL)
                        """)) {
                    statement.setInt(1, userId);
                    statement.setString(2, identity.username());
                    statement.setString(3, identity.fullName());
                    nullable(statement, 4, identity.nickname(), Types.VARCHAR);
                    statement.setString(5, identity.email().toLowerCase(Locale.ROOT));
                    nullable(statement, 6, identity.dateOfBirth(), Types.DATE);
                    nullable(statement, 7, identity.badgeId(), Types.VARCHAR);
                    nullable(statement, 8, identity.badgeSecretSalt(), Types.VARCHAR);
                    nullable(statement, 9, identity.badgeSecretHash(), Types.VARCHAR);
                    nullable(statement, 10, identity.badgeGeneratedAt(), Types.TIMESTAMP_WITH_TIMEZONE);
                    statement.setInt(11, roleId);
                    statement.setObject(12, authId);
                    statement.executeUpdate();
                }
                try (Statement statement = connection.createStatement()) {
                    statement.execute("""
                            SELECT setval(pg_get_serial_sequence('users','user_id'),
                                GREATEST((SELECT COALESCE(MAX(user_id),1) FROM users),1), true)
                            """);
                }
            }
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO user_locations(user_id,location_id)
                    VALUES (?,?)
                    ON CONFLICT (user_id,location_id) DO NOTHING
                    """)) {
                statement.setInt(1, userId);
                statement.setInt(2, locationId);
                statement.executeUpdate();
            }
            connection.commit();
        } catch (Exception ex) {
            connection.rollback();
            if (ex instanceof SQLException sql) throw sql;
            throw new SQLException("Local administrator bootstrap failed.", ex);
        } finally {
            connection.setAutoCommit(autoCommit);
        }
    }

    private static Integer findLocalUser(Connection connection, UUID authId, Identity identity)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT user_id,auth_user_id::text,username,email
                FROM users
                WHERE auth_user_id=?::uuid OR LOWER(email)=LOWER(?) OR LOWER(username)=LOWER(?)
                LIMIT 2
                """)) {
            statement.setObject(1, authId);
            statement.setString(2, identity.email());
            statement.setString(3, identity.username());
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) return null;
                int id = rows.getInt("user_id");
                if (!authId.toString().equalsIgnoreCase(rows.getString("auth_user_id"))
                        || !identity.username().equalsIgnoreCase(rows.getString("username"))
                        || !identity.email().equalsIgnoreCase(rows.getString("email"))) {
                    throw new SQLException("A conflicting local username, email, or Auth UUID already exists.");
                }
                if (rows.next()) throw new SQLException("Multiple conflicting local identities exist.");
                return id;
            }
        }
    }

    private static int adminRole(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(
                     "SELECT role_id FROM roles WHERE UPPER(role_name)='ADMIN'")) {
            if (!rows.next()) throw new SQLException("The local ADMIN role is missing.");
            return rows.getInt(1);
        }
    }

    private static Store requireStore(Connection connection) throws SQLException {
        Integer configured = data.DatabaseConfig.load().locationId();
        if (configured == null) throw new SQLException("Assign this server to a store first.");
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT location_id,name,receipt_store_code,timezone,address
                FROM locations WHERE location_id=?
                """)) {
            statement.setInt(1, configured);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) throw new SQLException("The configured local store does not exist.");
                return new Store(rows.getInt(1), rows.getString(2), rows.getString(3),
                        rows.getString(4), rows.getString(5));
            }
        }
    }

    private static void ensureSetupState(Connection connection) throws SQLException {
        SchemaContractService.requireLocalReady(connection);
    }

    private static PendingAuth pendingAuth(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("""
                     SELECT email,auth_user_id
                     FROM smartstock_first_admin_setup
                     WHERE setup_key='primary'
                     """)) {
            return rows.next() ? new PendingAuth(rows.getString(1),
                    rows.getObject(2, UUID.class)) : null;
        }
    }

    private static void savePending(Connection connection, String email, UUID authId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO smartstock_first_admin_setup(setup_key,email,auth_user_id)
                VALUES ('primary',?,?)
                ON CONFLICT (setup_key) DO NOTHING
                """)) {
            statement.setString(1, email.toLowerCase(Locale.ROOT));
            statement.setObject(2, authId);
            statement.executeUpdate();
        }
    }

    private static void markComplete(Connection connection, String email, UUID authId,
                                     int userId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE smartstock_first_admin_setup
                SET email=?,auth_user_id=?,production_user_id=?,completed_at=CURRENT_TIMESTAMP
                WHERE setup_key='primary'
                """)) {
            statement.setString(1, email.toLowerCase(Locale.ROOT));
            statement.setObject(2, authId);
            statement.setInt(3, userId);
            statement.executeUpdate();
        }
    }

    private static String readCredentialFile(EnvironmentProfile profile, String key) {
        for (var path : List.of(profile.file("database-credentials.txt"),
                data.DatabaseCredentials.CREDENTIALS_PATH)) {
            if (!Files.isRegularFile(path)) continue;
            try {
                for (String line : Files.readAllLines(path)) {
                    int equals = line.indexOf('=');
                    if (equals > 0 && key.equals(line.substring(0, equals).trim())) {
                        String value = line.substring(equals + 1).trim();
                        if ((value.startsWith("\"") && value.endsWith("\""))
                                || (value.startsWith("'") && value.endsWith("'"))) {
                            value = value.substring(1, value.length() - 1);
                        }
                        if (!value.isBlank()) return value;
                    }
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private static void nullable(PreparedStatement statement, int index, Object value,
                                 int type) throws SQLException {
        if (value == null) statement.setNull(index, type);
        else statement.setObject(index, value);
    }

    private static void add(JsonObject object, String name, String value) {
        if (value == null) object.add(name, com.google.gson.JsonNull.INSTANCE);
        else object.addProperty(name, value);
    }

    private static String required(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " is required.");
        }
        return value.trim();
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) if (value != null && !value.isBlank()) return value.trim();
        return null;
    }

    public record Identity(String username, String email, String fullName, String nickname,
                           Date dateOfBirth, String badgeId, String badgeSecretSalt,
                           String badgeSecretHash, Timestamp badgeGeneratedAt,
                           boolean transferred) {
        public Identity {
            username = required(username, "Username");
            email = required(email, "Email");
            fullName = required(fullName, "Display name");
            if (!email.contains("@")) throw new IllegalArgumentException("Enter a valid email address.");
            if ((badgeSecretSalt == null) != (badgeSecretHash == null)) {
                throw new IllegalArgumentException("Badge verifier metadata is incomplete.");
            }
        }

        @Override
        public String toString() {
            return fullName + " — " + username + " (" + email + ")"
                    + (badgeId == null ? "" : " — Badge " + badgeId);
        }
    }

    public record BootstrapResult(int userId, UUID authUserId, boolean transferred,
                                  String message) {
    }

    private record Store(int locationId, String name, String code, String timezone,
                         String address) {
    }

    private record PendingAuth(String email, UUID authUserId) {
    }
}
