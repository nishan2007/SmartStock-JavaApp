package data;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public record DatabaseCredentials(Map<String, String> values) {
    public static final Path CREDENTIALS_PATH = Path.of(System.getProperty("user.home"), ".smartstock", "database-credentials.txt");
    public static final String DEFAULT_DB_NAME = "smartstock";
    public static final String DEFAULT_CLIENT_DB_USER = "smartstock_client";
    public static final String DEFAULT_CLIENT_DB_PASSWORD = "SmartStockClientLan2026!";

    public static DatabaseCredentials load() {
        Map<String, String> values = new HashMap<>();
        if (!Files.exists(CREDENTIALS_PATH)) {
            return new DatabaseCredentials(values);
        }
        try {
            for (String line : Files.readAllLines(CREDENTIALS_PATH)) {
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

    public boolean hasClientCredentials() {
        return has("SMARTSTOCK_CLIENT_DB_USER") && has("SMARTSTOCK_CLIENT_DB_PASSWORD");
    }

    public String clientDbUserOrDefault() {
        String value = get("SMARTSTOCK_CLIENT_DB_USER");
        return value == null || value.isBlank() ? DEFAULT_CLIENT_DB_USER : value;
    }

    public String clientDbPasswordOrDefault() {
        String value = get("SMARTSTOCK_CLIENT_DB_PASSWORD");
        return value == null || value.isBlank() ? DEFAULT_CLIENT_DB_PASSWORD : value;
    }

    public String clientJdbcUrlOrDefault(String host, int port) {
        String savedUrl = get("SMARTSTOCK_CLIENT_JDBC_URL");
        if (savedUrl != null && !savedUrl.isBlank() && !savedUrl.contains("<SERVER-LAN-IP>")) {
            return savedUrl;
        }
        String cleanHost = host == null || host.isBlank() ? "127.0.0.1" : host.trim();
        int cleanPort = port <= 0 ? 5432 : port;
        String database = get("SMARTSTOCK_DB_NAME");
        if (database == null || database.isBlank()) {
            database = DEFAULT_DB_NAME;
        }
        return "jdbc:postgresql://" + cleanHost + ":" + cleanPort + "/" + database.trim();
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
