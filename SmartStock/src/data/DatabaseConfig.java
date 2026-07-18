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
        String cloudJdbcUrl,
        String cloudDbUser,
        String cloudDbPassword,
        int syncIntervalSeconds
) {
    public static final String PRIMARY_DB_USER_SECRET = "primary-db-user";
    public static final String PRIMARY_DB_PASSWORD_SECRET = "primary-db-password";
    public static final String CLOUD_DB_USER_SECRET = "cloud-db-user";
    public static final String CLOUD_DB_PASSWORD_SECRET = "cloud-db-password";
    public static final Path CONFIG_PATH = Path.of(System.getProperty("user.home"), ".smartstock", "database.properties");

    public static boolean hasConfigFile() {
        return Files.isRegularFile(CONFIG_PATH);
    }

    public static DatabaseConfig load() {
        Properties props = new Properties();
        if (Files.exists(CONFIG_PATH)) {
            try (InputStream input = Files.newInputStream(CONFIG_PATH)) {
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
        String database = firstNonBlank(props.getProperty("database.name"), "smartstock");
        String jdbcUrl = firstNonBlank(
                System.getProperty("smartstock.db.url"),
                System.getenv("SMARTSTOCK_DB_URL"),
                props.getProperty("jdbc.url")
        );

        if (mode == DatabaseMode.CLIENT) {
            // Registers connect only to the SmartStock HTTPS service. Keeping a
            // JDBC URL here would make accidental database fallback possible.
            jdbcUrl = "";
        } else if (isBlank(jdbcUrl)) {
            jdbcUrl = "jdbc:postgresql://" + host + ":" + port + "/" + database;
        }

        String user = firstNonBlank(
                System.getProperty("smartstock.db.user"),
                System.getenv("SMARTSTOCK_DB_USER"),
                SecureCredentialStore.read(PRIMARY_DB_USER_SECRET),
                props.getProperty("db.user")
        );
        String password = firstNonBlank(
                System.getProperty("smartstock.db.password"),
                System.getenv("SMARTSTOCK_DB_PASSWORD"),
                SecureCredentialStore.read(PRIMARY_DB_PASSWORD_SECRET),
                props.getProperty("db.password")
        );
        if (mode == DatabaseMode.CLIENT) {
            user = "";
            password = "";
        }

        String cloudUrl = firstNonBlank(System.getenv("SMARTSTOCK_CLOUD_DB_URL"), props.getProperty("cloud.jdbc.url"));
        String cloudUser = firstNonBlank(System.getenv("SMARTSTOCK_CLOUD_DB_USER"),
                SecureCredentialStore.read(CLOUD_DB_USER_SECRET), props.getProperty("cloud.db.user"));
        String cloudPassword = firstNonBlank(System.getenv("SMARTSTOCK_CLOUD_DB_PASSWORD"),
                SecureCredentialStore.read(CLOUD_DB_PASSWORD_SECRET), props.getProperty("cloud.db.password"));
        DatabaseCredentials savedCredentials = DatabaseCredentials.load();

        return new DatabaseConfig(
                mode,
                firstNonBlank(jdbcUrl, ""),
                firstNonBlank(savedCredentials.resolve(user), ""),
                firstNonBlank(savedCredentials.resolve(password), ""),
                host,
                port,
                parseNullableInt(props.getProperty("location.id")),
                savedCredentials.resolve(cloudUrl),
                firstNonBlank(savedCredentials.resolve(cloudUser), ""),
                firstNonBlank(savedCredentials.resolve(cloudPassword), ""),
                parseInt(firstNonBlank(props.getProperty("sync.interval.seconds"), "60"), 60)
        );
    }

    public boolean hasCloudConnection() {
        return !isBlank(cloudJdbcUrl) && !isBlank(cloudDbUser) && !isBlank(cloudDbPassword);
    }

    public boolean hasPrimaryConnection() {
        return !isBlank(jdbcUrl) && !isBlank(dbUser);
    }

    public boolean hasUnresolvedCredentialPlaceholders() {
        DatabaseCredentials savedCredentials = DatabaseCredentials.load();
        return savedCredentials.isUnresolvedCredentialKey(dbUser)
                || savedCredentials.isUnresolvedCredentialKey(dbPassword)
                || savedCredentials.isUnresolvedCredentialKey(cloudDbUser)
                || savedCredentials.isUnresolvedCredentialKey(cloudDbPassword);
    }

    public String missingPrimaryConnectionMessage() {
        if (hasUnresolvedCredentialPlaceholders()) {
            return "Database setup still contains credential labels like SMARTSTOCK_DB_USER instead of real values. "
                    + "Click Load Saved Credentials in Database Setup, or run the SmartStock installer to create "
                    + DatabaseCredentials.CREDENTIALS_PATH + ".";
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
        if (mode != DatabaseMode.CLIENT) {
            storeSecret(PRIMARY_DB_USER_SECRET, dbUser);
            storeSecret(PRIMARY_DB_PASSWORD_SECRET, dbPassword);
            storeSecret(CLOUD_DB_USER_SECRET, cloudDbUser);
            storeSecret(CLOUD_DB_PASSWORD_SECRET, cloudDbPassword);
        }
        Properties props = new Properties();
        props.setProperty("mode", mode.name());
        props.setProperty("server.host", serverHost);
        props.setProperty("server.port", String.valueOf(serverPort));
        if (mode != DatabaseMode.CLIENT) {
            props.setProperty("database.name", databaseNameFromUrl(jdbcUrl));
            props.setProperty("jdbc.url", jdbcUrl == null ? "" : jdbcUrl);
            props.setProperty("db.user", isBlank(dbUser) ? "" : "${SMARTSTOCK_SECURE_DB_USER}");
            props.setProperty("db.password", isBlank(dbPassword) ? "" : "${SMARTSTOCK_SECURE_DB_PASSWORD}");
        }
        if (locationId != null) {
            props.setProperty("location.id", String.valueOf(locationId));
        }
        if (mode != DatabaseMode.CLIENT && !isBlank(cloudJdbcUrl)) {
            props.setProperty("cloud.jdbc.url", cloudJdbcUrl);
        }
        if (mode != DatabaseMode.CLIENT && !isBlank(cloudDbUser)) {
            props.setProperty("cloud.db.user", "${SMARTSTOCK_SECURE_CLOUD_DB_USER}");
        }
        if (mode != DatabaseMode.CLIENT && !isBlank(cloudDbPassword)) {
            props.setProperty("cloud.db.password", "${SMARTSTOCK_SECURE_CLOUD_DB_PASSWORD}");
        }
        props.setProperty("sync.interval.seconds", String.valueOf(syncIntervalSeconds));
        Files.createDirectories(CONFIG_PATH.getParent());
        SecureFilePermissions.restrictDirectoryToOwner(CONFIG_PATH.getParent());
        try (OutputStream output = Files.newOutputStream(CONFIG_PATH)) {
            props.store(output, "SmartStock database mode and sync configuration");
        }
        SecureFilePermissions.restrictFileToOwner(CONFIG_PATH);
    }

    private static void storeSecret(String key, String value) throws IOException {
        if (!isBlank(value) && !value.trim().startsWith("${SMARTSTOCK_SECURE_")) {
            SecureCredentialStore.write(key, value.trim());
        }
    }

    public DatabaseConfig withMode(DatabaseMode newMode) {
        return new DatabaseConfig(newMode, jdbcUrl, dbUser, dbPassword, serverHost, serverPort, locationId,
                cloudJdbcUrl, cloudDbUser, cloudDbPassword, syncIntervalSeconds);
    }

    public static DatabaseConfig fromForm(DatabaseMode mode, String jdbcUrl, String dbUser, String dbPassword,
                                          String serverHost, int serverPort, Integer locationId,
                                          String cloudJdbcUrl, String cloudDbUser, String cloudDbPassword,
                                          int syncIntervalSeconds) {
        DatabaseMode cleanMode = mode == null ? DatabaseMode.CLIENT : mode;
        String cleanHost = firstNonBlank(serverHost, "127.0.0.1");
        int cleanPort = serverPort <= 0 ? 5432 : serverPort;
        String cleanCloudUrl = firstNonBlank(cloudJdbcUrl, "");
        DatabaseCredentials savedCredentials = DatabaseCredentials.load();
        String cleanJdbcUrl = firstNonBlank(jdbcUrl, "");
        if (cleanMode == DatabaseMode.CLIENT) {
            return new DatabaseConfig(DatabaseMode.CLIENT, "", "", "", cleanHost, cleanPort, locationId,
                    "", "", "", syncIntervalSeconds <= 0 ? 60 : syncIntervalSeconds);
        }
        if (isBlank(cleanJdbcUrl)) {
            cleanJdbcUrl = "jdbc:postgresql://" + cleanHost + ":" + cleanPort + "/smartstock";
        }
        return new DatabaseConfig(cleanMode, firstNonBlank(cleanJdbcUrl, ""), firstNonBlank(savedCredentials.resolve(dbUser), ""),
                firstNonBlank(savedCredentials.resolve(dbPassword), ""), cleanHost, cleanPort, locationId,
                savedCredentials.resolve(cleanCloudUrl), firstNonBlank(savedCredentials.resolve(cloudDbUser), ""),
                firstNonBlank(savedCredentials.resolve(cloudDbPassword), ""),
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
