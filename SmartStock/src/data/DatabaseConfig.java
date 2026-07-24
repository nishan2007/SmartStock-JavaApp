package data;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import utils.SecureFilePermissions;
import utils.SecureCredentialStore;

public record DatabaseConfig(
        DatabaseMode mode,
        String jdbcUrl,
        String dbUser,
        String dbPassword,
        String serverHost,
        int serverPort,
        Integer locationId,
        int syncIntervalSeconds
) {
    public static final String PRIMARY_DB_USER_SECRET = "primary-db-user";
    public static final String PRIMARY_DB_PASSWORD_SECRET = "primary-db-password";
    private static final Path LEGACY_CONFIG_PATH =
            Path.of(System.getProperty("user.home"), ".smartstock", "database.properties");

    public static Path configPath() {
        return EnvironmentProfile.active().file("database.properties");
    }

    public static boolean hasConfigFile() {
        return Files.isRegularFile(configPath())
                || (EnvironmentProfile.active() == EnvironmentProfile.DEVELOPMENT
                && Files.isRegularFile(LEGACY_CONFIG_PATH));
    }

    public static DatabaseConfig load() {
        Properties props = new Properties();
        Path configPath = Files.isRegularFile(configPath()) ? configPath()
                : EnvironmentProfile.active() == EnvironmentProfile.DEVELOPMENT
                ? LEGACY_CONFIG_PATH : configPath();
        if (Files.exists(configPath)) {
            try (InputStream input = Files.newInputStream(configPath)) {
                props.load(input);
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }

        DatabaseMode mode = DatabaseMode.from(firstNonBlank(
                System.getProperty("smartstock.db.mode"),
                System.getenv("SMARTSTOCK_DB_MODE"),
                props.getProperty("mode")
        ));
        String host = firstNonBlank(props.getProperty("server.host"), "127.0.0.1");
        int port = parseInt(firstNonBlank(props.getProperty("server.port"), "5432"), 5432);
        String defaultDatabase = EnvironmentProfile.active() == EnvironmentProfile.PRODUCTION
                ? "smartstock" : "smartstock_dev";
        String database = firstNonBlank(props.getProperty("database.name"), defaultDatabase);
        String jdbcUrl = firstNonBlank(
                System.getProperty("smartstock.db.url"),
                System.getenv("SMARTSTOCK_DB_URL"),
                props.getProperty("jdbc.url")
        );

        if (mode == DatabaseMode.CLIENT || mode == DatabaseMode.REMOTE_ADMIN) {
            // Registers connect only to the SmartStock HTTPS service. Keeping a
            // JDBC URL here would make accidental database fallback possible.
            jdbcUrl = "";
        } else if (isBlank(jdbcUrl)) {
            jdbcUrl = "jdbc:postgresql://" + host + ":" + port + "/" + database;
        }

        String user = firstNonBlank(
                System.getProperty("smartstock.db.user"),
                System.getenv("SMARTSTOCK_DB_USER"),
                readProfileSecret(PRIMARY_DB_USER_SECRET),
                props.getProperty("db.user")
        );
        String password = firstNonBlank(
                System.getProperty("smartstock.db.password"),
                System.getenv("SMARTSTOCK_DB_PASSWORD"),
                readProfileSecret(PRIMARY_DB_PASSWORD_SECRET),
                props.getProperty("db.password")
        );
        if (mode == DatabaseMode.CLIENT || mode == DatabaseMode.REMOTE_ADMIN) {
            user = "";
            password = "";
        }

        DatabaseCredentials savedCredentials = DatabaseCredentials.load();

        return new DatabaseConfig(
                mode,
                firstNonBlank(jdbcUrl, ""),
                firstNonBlank(savedCredentials.resolve(user), ""),
                firstNonBlank(savedCredentials.resolve(password), ""),
                host,
                port,
                parseNullableInt(props.getProperty("location.id")),
                parseInt(firstNonBlank(props.getProperty("sync.interval.seconds"), "60"), 60)
        );
    }

    public boolean hasPrimaryConnection() {
        return !isBlank(jdbcUrl) && !isBlank(dbUser);
    }

    public boolean hasUnresolvedCredentialPlaceholders() {
        DatabaseCredentials savedCredentials = DatabaseCredentials.load();
        return savedCredentials.isUnresolvedCredentialKey(dbUser)
                || savedCredentials.isUnresolvedCredentialKey(dbPassword);
    }

    public String missingPrimaryConnectionMessage() {
        if (hasUnresolvedCredentialPlaceholders()) {
            return "Database setup still contains credential labels like SMARTSTOCK_DB_USER instead of real values. "
                    + "Click Load Saved Credentials in Database Setup, or run the SmartStock installer to create "
                    + DatabaseCredentials.activeCredentialsPath() + ".";
        }
        if (isBlank(jdbcUrl)) {
            return "Database JDBC URL is not configured. Open Database Setup and enter the local/server JDBC URL.";
        }
        if (isBlank(dbUser)) {
            return "Database user is not configured. Open Database Setup and enter the database user.";
        }
        return null;
    }

    public void save() throws IOException {
        if (mode == DatabaseMode.SERVER) {
            storeSecret(PRIMARY_DB_USER_SECRET, dbUser);
            storeSecret(PRIMARY_DB_PASSWORD_SECRET, dbPassword);
        } else if (mode == DatabaseMode.REMOTE_ADMIN) {
            SecureCredentialStore.delete(profileSecret(PRIMARY_DB_USER_SECRET));
            SecureCredentialStore.delete(profileSecret(PRIMARY_DB_PASSWORD_SECRET));
        }
        Properties props = new Properties();
        props.setProperty("mode", mode.name());
        props.setProperty("server.host", serverHost);
        props.setProperty("server.port", String.valueOf(serverPort));
        if (mode == DatabaseMode.SERVER) {
            props.setProperty("database.name", databaseNameFromUrl(jdbcUrl));
            props.setProperty("jdbc.url", jdbcUrl == null ? "" : jdbcUrl);
            props.setProperty("db.user", isBlank(dbUser) ? "" : "${SMARTSTOCK_SECURE_DB_USER}");
            props.setProperty("db.password", isBlank(dbPassword) ? "" : "${SMARTSTOCK_SECURE_DB_PASSWORD}");
        }
        if (locationId != null) {
            props.setProperty("location.id", String.valueOf(locationId));
        }
        props.setProperty("sync.interval.seconds", String.valueOf(syncIntervalSeconds));
        Path configPath = configPath();
        Files.createDirectories(configPath.getParent());
        SecureFilePermissions.restrictDirectoryToOwner(configPath.getParent());
        try (OutputStream output = Files.newOutputStream(configPath)) {
            props.store(output, "SmartStock database mode and sync configuration");
        }
        SecureFilePermissions.restrictFileToOwner(configPath);
    }

    private static void storeSecret(String key, String value) throws IOException {
        if (!isBlank(value) && !value.trim().startsWith("${SMARTSTOCK_SECURE_")) {
            SecureCredentialStore.write(profileSecret(key), value.trim());
        }
    }

    private static String profileSecret(String key) {
        return EnvironmentProfile.active().secretKey(key);
    }

    private static String readProfileSecret(String key) {
        String value = SecureCredentialStore.read(profileSecret(key));
        if ((value == null || value.isBlank())
                && EnvironmentProfile.active() == EnvironmentProfile.DEVELOPMENT) {
            value = SecureCredentialStore.read(key);
        }
        return value;
    }

    public DatabaseConfig withMode(DatabaseMode newMode) {
        return new DatabaseConfig(newMode, jdbcUrl, dbUser, dbPassword, serverHost, serverPort, locationId,
                syncIntervalSeconds);
    }

    public static DatabaseConfig fromForm(DatabaseMode mode, String jdbcUrl, String dbUser, String dbPassword,
                                          String serverHost, int serverPort, Integer locationId,
                                          int syncIntervalSeconds) {
        DatabaseMode cleanMode = mode == null ? DatabaseMode.CLIENT : mode;
        String cleanHost = firstNonBlank(serverHost, "127.0.0.1");
        int cleanPort = serverPort <= 0 ? 5432 : serverPort;
        DatabaseCredentials savedCredentials = DatabaseCredentials.load();
        String cleanJdbcUrl = firstNonBlank(jdbcUrl, "");
        if (cleanMode == DatabaseMode.CLIENT || cleanMode == DatabaseMode.REMOTE_ADMIN) {
            return new DatabaseConfig(cleanMode, "", "", "", cleanHost, cleanPort, locationId,
                    syncIntervalSeconds <= 0 ? 60 : syncIntervalSeconds);
        }
        if (isBlank(cleanJdbcUrl)) {
            cleanJdbcUrl = "jdbc:postgresql://" + cleanHost + ":" + cleanPort + "/smartstock";
        }
        return new DatabaseConfig(cleanMode, firstNonBlank(cleanJdbcUrl, ""), firstNonBlank(savedCredentials.resolve(dbUser), ""),
                firstNonBlank(savedCredentials.resolve(dbPassword), ""), cleanHost, cleanPort, locationId,
                syncIntervalSeconds <= 0 ? 60 : syncIntervalSeconds);
    }

    private static String databaseNameFromUrl(String url) {
        if (isBlank(url)) {
            return "smartstock";
        }
        int slash = url.lastIndexOf('/');
        if (slash < 0 || slash == url.length() - 1) {
            return "smartstock";
        }
        String name = url.substring(slash + 1);
        int query = name.indexOf('?');
        return query >= 0 ? name.substring(0, query) : name;
    }

    private static Integer parseNullableInt(String value) {
        if (isBlank(value)) {
            return null;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value.trim());
        } catch (Exception ex) {
            return fallback;
        }
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (!isBlank(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
