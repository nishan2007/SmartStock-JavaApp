package services;

import data.DatabaseConfig;
import data.DatabaseMode;
import data.EnvironmentProfile;

import java.security.SecureRandom;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Arrays;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

/** Creates SmartStock's local server role without exposing technical fields in normal setup. */
public final class LocalDatabaseBootstrapService {
    private static final SecureRandom RANDOM = new SecureRandom();

    private LocalDatabaseBootstrapService() {
    }

    static Path windowsBootstrapCredentialPath() {
        return EnvironmentProfile.active().file("postgres-bootstrap-admin.dpapi");
    }

    public static boolean hasGeneratedAdministratorCredential() {
        return Files.isRegularFile(windowsBootstrapCredentialPath());
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
        if (supplied.length == 0 && hasGeneratedAdministratorCredential()) {
            supplied = readGeneratedAdministratorCredential();
        }
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
            Files.deleteIfExists(windowsBootstrapCredentialPath());
            return configured;
        } finally {
            Arrays.fill(supplied, '\0');
            applicationPassword = null;
        }
    }

    private static char[] readGeneratedAdministratorCredential() throws IOException {
        String encrypted = Files.readString(
                windowsBootstrapCredentialPath(), StandardCharsets.US_ASCII).trim();
        String script = "Add-Type -AssemblyName System.Security;"
                + "$ErrorActionPreference='Stop';"
                + "$encoded=[Console]::In.ReadToEnd();"
                + "$protected=[Convert]::FromBase64String($encoded);"
                + "$data=[Security.Cryptography.ProtectedData]::Unprotect($protected,$null,[Security.Cryptography.DataProtectionScope]::CurrentUser);"
                + "if($null -eq $data){throw 'Windows DPAPI returned no PostgreSQL bootstrap credential.'};"
                + "[Console]::Out.Write([Text.Encoding]::UTF8.GetString($data))";
        Process process = new ProcessBuilder("powershell.exe", "-NoLogo", "-NoProfile",
                "-NonInteractive", "-ExecutionPolicy", "Bypass", "-Command", script)
                .redirectErrorStream(true).start();
        try {
            process.getOutputStream().write(encrypted.getBytes(StandardCharsets.US_ASCII));
            process.getOutputStream().close();
            if (!process.waitFor(10, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new IOException("Reading the generated PostgreSQL credential timed out.");
            }
            String output = new String(
                    process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            if (process.exitValue() != 0 || output.isBlank()) {
                throw new IOException(output.isBlank()
                        ? "Windows could not read the generated PostgreSQL credential." : output);
            }
            return output.toCharArray();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IOException("Reading the generated PostgreSQL credential was interrupted.", ex);
        } finally {
            process.destroy();
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
