package services;

import data.DatabaseConfig;
import data.DatabaseMode;
import data.EnvironmentProfile;

import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Arrays;
import java.util.Base64;

/** Creates SmartStock's local server role without exposing technical fields in normal setup. */
public final class LocalDatabaseBootstrapService {
    private static final SecureRandom RANDOM = new SecureRandom();

    private LocalDatabaseBootstrapService() {
    }

    public static DatabaseConfig ensureConfigured(char[] postgresAdministratorPassword)
            throws Exception {
        DatabaseConfig existing = DatabaseConfig.load().withMode(DatabaseMode.SERVER);
        if (existing.hasPrimaryConnection() && !existing.hasUnresolvedCredentialPlaceholders()) {
            existing.save();
            return existing;
        }

        char[] supplied = postgresAdministratorPassword == null
                ? new char[0] : postgresAdministratorPassword.clone();
        String applicationPassword = generatedPassword();
        String database = EnvironmentProfile.active() == EnvironmentProfile.PRODUCTION
                ? "smartstock" : "smartstock_dev";
        String adminUrl = "jdbc:postgresql://127.0.0.1:5432/postgres";
        try {
            try (Connection admin = openAdministrator(adminUrl, supplied)) {
                admin.setAutoCommit(true);
                ensureRole(admin, "smartstock_server", applicationPassword);
                ensureDatabase(admin, database, "smartstock_server");
                // The local database is never a register endpoint.
                try (Statement statement = admin.createStatement()) {
                    statement.execute("ALTER SYSTEM SET listen_addresses = 'localhost'");
                }
            }
            DatabaseConfig configured = new DatabaseConfig(
                    DatabaseMode.SERVER,
                    "jdbc:postgresql://127.0.0.1:5432/" + database,
                    "smartstock_server",
                    applicationPassword,
                    "127.0.0.1",
                    5432,
                    null,
                    300);
            configured.save();
            return configured;
        } finally {
            Arrays.fill(supplied, '\0');
            applicationPassword = null;
        }
    }

    private static Connection openAdministrator(String url, char[] supplied) throws Exception {
        String password = new String(supplied);
        Exception first = null;
        for (String user : new String[]{"postgres", System.getProperty("user.name", "")}) {
            if (user.isBlank()) continue;
            try {
                return DriverManager.getConnection(url, user, password);
            } catch (Exception ex) {
                if (first == null) first = ex;
            }
        }
        throw new IllegalArgumentException(
                "Enter the PostgreSQL administrator password selected during installation.",
                first);
    }

    private static void ensureRole(Connection connection, String role, String password)
            throws Exception {
        boolean exists;
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT EXISTS (SELECT 1 FROM pg_roles WHERE rolname=?)")) {
            statement.setString(1, role);
            try (ResultSet rows = statement.executeQuery()) {
                exists = rows.next() && rows.getBoolean(1);
            }
        }
        String sql = exists
                ? "ALTER ROLE " + quoteIdentifier(role) + " LOGIN PASSWORD " + quoteLiteral(password)
                : "CREATE ROLE " + quoteIdentifier(role) + " LOGIN PASSWORD " + quoteLiteral(password);
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private static void ensureDatabase(Connection connection, String database, String owner)
            throws Exception {
        boolean exists;
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT EXISTS (SELECT 1 FROM pg_database WHERE datname=?)")) {
            statement.setString(1, database);
            try (ResultSet rows = statement.executeQuery()) {
                exists = rows.next() && rows.getBoolean(1);
            }
        }
        if (!exists) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("CREATE DATABASE " + quoteIdentifier(database)
                        + " OWNER " + quoteIdentifier(owner));
            }
        }
    }

    private static String generatedPassword() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        try {
            return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        } finally {
            Arrays.fill(bytes, (byte) 0);
        }
    }

    private static String quoteIdentifier(String value) {
        if (value == null || !value.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            throw new IllegalArgumentException("Invalid PostgreSQL identifier.");
        }
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    private static String quoteLiteral(String value) {
        return "'" + value.replace("'", "''") + "'";
    }
}
