package data;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public record DatabaseCredentials(Map<String, String> values) {
    public static final Path CREDENTIALS_PATH = Path.of(System.getProperty("user.home"), ".smartstock", "database-credentials.txt");
    public static final String DEFAULT_DB_NAME = "smartstock";

    public static DatabaseCredentials load() {
        Map<String, String> values = new HashMap<>();
        Path credentialsPath = EnvironmentProfile.active().file("database-credentials.txt");
        if (!Files.isRegularFile(credentialsPath)
                && EnvironmentProfile.active() == EnvironmentProfile.DEVELOPMENT) {
            credentialsPath = CREDENTIALS_PATH;
        }
        if (!Files.exists(credentialsPath)) {
            return new DatabaseCredentials(values);
        }
        try {
            for (String line : Files.readAllLines(credentialsPath)) {
                String trimmed = line.trim();
                if (trimmed.isBlank() || trimmed.startsWith("#") || !trimmed.contains("=")) {
                    continue;
                }
                int index = trimmed.indexOf('=');
                String key = trimmed.substring(0, index).trim();
                String value = unquote(trimmed.substring(index + 1).trim());
                if (!key.isBlank()) {
                    values.put(key, value);
                }
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
        return new DatabaseCredentials(values);
    }

    public static Path activeCredentialsPath() {
        return EnvironmentProfile.active().file("database-credentials.txt");
    }

    public String get(String key) {
        return values.get(key);
    }

    public String resolve(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.startsWith("${") && trimmed.endsWith("}")) {
            trimmed = trimmed.substring(2, trimmed.length() - 1);
        }
        return values.getOrDefault(trimmed, value);
    }

    public boolean isUnresolvedCredentialKey(String value) {
        if (value == null) {
            return false;
        }
        String trimmed = value.trim();
        if (trimmed.startsWith("${") && trimmed.endsWith("}")) {
            trimmed = trimmed.substring(2, trimmed.length() - 1);
        }
        return trimmed.matches("SMARTSTOCK_[A-Z0-9_]+") && !values.containsKey(trimmed);
    }

    public boolean hasServerCredentials() {
        return has("SMARTSTOCK_DB_USER") && has("SMARTSTOCK_DB_PASSWORD");
    }

    private boolean has(String key) {
        String value = values.get(key);
        return value != null && !value.isBlank();
    }

    private static String unquote(String value) {
        if (value == null || value.length() < 2) {
            return value;
        }
        if ((value.startsWith("'") && value.endsWith("'")) || (value.startsWith("\"") && value.endsWith("\""))) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }
}
