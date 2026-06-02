package data;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

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
    public static final Path CONFIG_PATH = Path.of(System.getProperty("user.home"), ".smartstock", "database.properties");

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

        if (isBlank(jdbcUrl)) {
            if (mode == DatabaseMode.CLOUD_DIRECT) {
                jdbcUrl = firstNonBlank(
                        System.getenv("SMARTSTOCK_CLOUD_DB_URL"),
                        props.getProperty("cloud.jdbc.url")
                );
            } else {
                jdbcUrl = "jdbc:postgresql://" + host + ":" + port + "/" + database;
            }
        }

        String user = firstNonBlank(
                System.getProperty("smartstock.db.user"),
                System.getenv("SMARTSTOCK_DB_USER"),
                props.getProperty("db.user")
        );
        String password = firstNonBlank(
                System.getProperty("smartstock.db.password"),
                System.getenv("SMARTSTOCK_DB_PASSWORD"),
                props.getProperty("db.password")
        );
        if (mode == DatabaseMode.CLOUD_DIRECT) {
            user = firstNonBlank(user, System.getenv("SMARTSTOCK_CLOUD_DB_USER"), props.getProperty("cloud.db.user"));
            password = firstNonBlank(password, System.getenv("SMARTSTOCK_CLOUD_DB_PASSWORD"), props.getProperty("cloud.db.password"));
        }

        String cloudUrl = firstNonBlank(System.getenv("SMARTSTOCK_CLOUD_DB_URL"), props.getProperty("cloud.jdbc.url"));
        String cloudUser = firstNonBlank(System.getenv("SMARTSTOCK_CLOUD_DB_USER"), props.getProperty("cloud.db.user"));
        String cloudPassword = firstNonBlank(System.getenv("SMARTSTOCK_CLOUD_DB_PASSWORD"), props.getProperty("cloud.db.password"));
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
        Properties props = new Properties();
        props.setProperty("mode", mode.name());
        props.setProperty("server.host", serverHost);
        props.setProperty("server.port", String.valueOf(serverPort));
        props.setProperty("database.name", databaseNameFromUrl(jdbcUrl));
        props.setProperty("jdbc.url", jdbcUrl == null ? "" : jdbcUrl);
        props.setProperty("db.user", dbUser == null ? "" : dbUser);
        props.setProperty("db.password", dbPassword == null ? "" : dbPassword);
        if (locationId != null) {
            props.setProperty("location.id", String.valueOf(locationId));
        }
        if (!isBlank(cloudJdbcUrl)) {
            props.setProperty("cloud.jdbc.url", cloudJdbcUrl);
        }
        if (!isBlank(cloudDbUser)) {
            props.setProperty("cloud.db.user", cloudDbUser);
        }
        if (!isBlank(cloudDbPassword)) {
            props.setProperty("cloud.db.password", cloudDbPassword);
        }
        props.setProperty("sync.interval.seconds", String.valueOf(syncIntervalSeconds));
        Files.createDirectories(CONFIG_PATH.getParent());
        try (OutputStream output = Files.newOutputStream(CONFIG_PATH)) {
            props.store(output, "SmartStock database mode and sync configuration");
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
        DatabaseMode cleanMode = mode == null ? DatabaseMode.CLOUD_DIRECT : mode;
        String cleanHost = firstNonBlank(serverHost, "127.0.0.1");
        int cleanPort = serverPort <= 0 ? 5432 : serverPort;
        String cleanCloudUrl = firstNonBlank(cloudJdbcUrl, "");
        DatabaseCredentials savedCredentials = DatabaseCredentials.load();
        String cleanJdbcUrl = firstNonBlank(jdbcUrl, "");
        if (isBlank(cleanJdbcUrl) && cleanMode != DatabaseMode.CLOUD_DIRECT) {
            cleanJdbcUrl = "jdbc:postgresql://" + cleanHost + ":" + cleanPort + "/smartstock";
        }
        if (isBlank(cleanJdbcUrl) && cleanMode == DatabaseMode.CLOUD_DIRECT) {
            cleanJdbcUrl = cleanCloudUrl;
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
