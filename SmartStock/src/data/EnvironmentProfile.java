package data;

import utils.SecureFilePermissions;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** Selects the isolated SmartStock runtime profile used by this process. */
public enum EnvironmentProfile {
    DEVELOPMENT("development", "Developer / Test"),
    PRODUCTION("production", "Production");

    private static final Path ROOT =
            Path.of(System.getProperty("user.home"), ".smartstock");
    private static final Path ACTIVE_PATH = ROOT.resolve("active-environment.properties");
    private final String id;
    private final String displayName;

    EnvironmentProfile(String id, String displayName) {
        this.id = id;
        this.displayName = displayName;
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public Path directory() {
        return ROOT.resolve("profiles").resolve(id);
    }

    public Path file(String name) {
        return directory().resolve(name);
    }

    public String secretKey(String baseKey) {
        return "smartstock-" + id + "-" + baseKey;
    }

    public static EnvironmentProfile active() {
        String override = System.getProperty("smartstock.environment");
        if (override == null || override.isBlank()) override = System.getenv("SMARTSTOCK_ENVIRONMENT");
        if (override != null && !override.isBlank()) return from(override);
        Properties properties = new Properties();
        if (Files.isRegularFile(ACTIVE_PATH)) {
            try (InputStream input = Files.newInputStream(ACTIVE_PATH)) {
                properties.load(input);
            } catch (Exception ignored) {
            }
        }
        return from(properties.getProperty("environment"));
    }

    public static void activate(EnvironmentProfile profile) throws Exception {
        EnvironmentProfile selected = profile == null ? DEVELOPMENT : profile;
        Files.createDirectories(ROOT);
        SecureFilePermissions.restrictDirectoryToOwner(ROOT);
        Properties properties = new Properties();
        properties.setProperty("environment", selected.id);
        try (OutputStream output = Files.newOutputStream(ACTIVE_PATH)) {
            properties.store(output, "Active SmartStock environment");
        }
        SecureFilePermissions.restrictFileToOwner(ACTIVE_PATH);
        Files.createDirectories(selected.directory());
        SecureFilePermissions.restrictDirectoryToOwner(selected.directory());
    }

    public static EnvironmentProfile from(String value) {
        if (value == null || value.isBlank()) return DEVELOPMENT;
        return switch (value.trim().toLowerCase()) {
            case "prod", "production" -> PRODUCTION;
            case "dev", "development", "test", "testing", "staging" -> DEVELOPMENT;
            default -> throw new IllegalArgumentException(
                    "Environment must be development or production.");
        };
    }

    @Override
    public String toString() {
        return displayName;
    }
}
