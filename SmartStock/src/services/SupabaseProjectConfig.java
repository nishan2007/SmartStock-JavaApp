package services;

import java.net.URI;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import data.EnvironmentProfile;
import utils.SecureFilePermissions;

/**
 * Resolves the hosted Supabase project without allowing a production runtime
 * to fall back to SmartStock's development project.
 */
public record SupabaseProjectConfig(
        Environment environment,
        String url,
        String publishableKey,
        String projectRef
) {
    private static final String DEVELOPMENT_CONFIG_RESOURCE =
            "config/development-supabase.properties";
    private static final Properties DEVELOPMENT = loadDevelopmentConfig();
    public static final String DEVELOPMENT_PROJECT_REF =
            requiredDevelopmentProperty("project.ref");
    public static final String DEVELOPMENT_URL = requiredDevelopmentProperty("url");
    public static final String DEVELOPMENT_PUBLISHABLE_KEY =
            requiredDevelopmentProperty("publishable.key");
    private static final Path LEGACY_CONFIG_PATH = Path.of(System.getProperty("user.home"),
            ".smartstock", "supabase.properties");

    public static Path configPath() {
        return EnvironmentProfile.active().file("supabase.properties");
    }
    private static final Pattern HOSTED_PROJECT =
            Pattern.compile("^([a-z0-9]{20})\\.supabase\\.co$", Pattern.CASE_INSENSITIVE);

    public static SupabaseProjectConfig load() {
        Properties saved = loadSaved();
        return resolve(
                firstNonBlank(configured("SMARTSTOCK_ENVIRONMENT"),
                        saved.getProperty("environment"), EnvironmentProfile.active().id()),
                firstNonBlank(configured("SUPABASE_URL"), saved.getProperty("url")),
                firstNonBlank(configured("SUPABASE_PUBLISHABLE_KEY"),
                        saved.getProperty("publishable.key"))
        );
    }

    public static void savePublicConfig(EnvironmentProfile profile, String url,
                                        String publishableKey, String lanSubnet) throws IOException {
        EnvironmentProfile selected = profile == null ? EnvironmentProfile.active() : profile;
        SupabaseProjectConfig validated = resolve(selected.id(), url, publishableKey);
        Properties properties = new Properties();
        properties.setProperty("environment", selected.id());
        properties.setProperty("url", validated.url());
        properties.setProperty("publishable.key", validated.publishableKey());
        properties.setProperty("lan.subnet", clean(lanSubnet) == null
                ? "LocalSubnet" : lanSubnet.trim());
        Path configPath = configPath();
        Files.createDirectories(configPath.getParent());
        SecureFilePermissions.restrictDirectoryToOwner(configPath.getParent());
        try (OutputStream output = Files.newOutputStream(configPath)) {
            properties.store(output, "SmartStock public Supabase project configuration");
        }
        SecureFilePermissions.restrictFileToOwner(configPath);
    }

    public static void saveProductionPublicConfig(String url, String publishableKey,
                                                  String lanSubnet) throws IOException {
        savePublicConfig(EnvironmentProfile.PRODUCTION, url, publishableKey, lanSubnet);
    }

    public static String loadLanSubnet() {
        return firstNonBlank(configured("SMARTSTOCK_LAN_SUBNET"),
                loadSaved().getProperty("lan.subnet"), "LocalSubnet");
    }

    static SupabaseProjectConfig resolve(String environmentValue, String configuredUrl,
                                         String configuredPublishableKey) {
        Environment environment = Environment.from(environmentValue);
        String url = clean(configuredUrl);
        String key = clean(configuredPublishableKey);

        if (environment != Environment.PRODUCTION) {
            if (url == null) url = DEVELOPMENT_URL;
            if (key == null) key = DEVELOPMENT_PUBLISHABLE_KEY;
        }

        if (url == null || key == null) {
            throw new IllegalStateException(
                    "Production requires explicit SUPABASE_URL and SUPABASE_PUBLISHABLE_KEY configuration.");
        }

        String projectRef = projectRef(url);
        if (environment == Environment.PRODUCTION) {
            if (!url.startsWith("https://")) {
                throw new IllegalStateException("Production SUPABASE_URL must use HTTPS.");
            }
            if (DEVELOPMENT_PROJECT_REF.equalsIgnoreCase(projectRef)) {
                throw new IllegalStateException(
                        "Production cannot use the SmartStock development Supabase project.");
            }
        }
        return new SupabaseProjectConfig(environment, stripTrailingSlash(url), key, projectRef);
    }

    public static SupabaseProjectConfig resolveProduction(String url, String publishableKey) {
        return resolve("production", url, publishableKey);
    }

    public static SupabaseProjectConfig resolveForProfile(EnvironmentProfile profile,
                                                          String url, String publishableKey) {
        EnvironmentProfile selected = profile == null ? EnvironmentProfile.active() : profile;
        return resolve(selected.id(), url, publishableKey);
    }

    public boolean isProduction() {
        return environment == Environment.PRODUCTION;
    }

    public void requireMatchingCredentialProject(String credentialProjectRef) {
        String actual = clean(credentialProjectRef);
        if (projectRef == null || actual == null || !projectRef.equalsIgnoreCase(actual)) {
            throw new IllegalArgumentException(
                    "The server cloud credential does not belong to the configured Supabase project.");
        }
    }

    static String projectRef(String url) {
        try {
            String host = URI.create(url).getHost();
            if (host == null) return null;
            Matcher matcher = HOSTED_PROJECT.matcher(host);
            return matcher.matches() ? matcher.group(1).toLowerCase(Locale.ROOT) : null;
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("SUPABASE_URL is invalid.", ex);
        }
    }

    private static String configured(String key) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) value = System.getProperty(key);
        return clean(value);
    }

    private static Properties loadSaved() {
        Properties properties = new Properties();
        Path configPath = Files.isRegularFile(configPath()) ? configPath()
                : EnvironmentProfile.active() == EnvironmentProfile.DEVELOPMENT
                ? LEGACY_CONFIG_PATH : configPath();
        if (!Files.isRegularFile(configPath)) return properties;
        try (InputStream input = Files.newInputStream(configPath)) {
            properties.load(input);
        } catch (IOException ignored) {
        }
        return properties;
    }

    private static Properties loadDevelopmentConfig() {
        Properties properties = new Properties();
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        if (loader == null) loader = SupabaseProjectConfig.class.getClassLoader();
        try (InputStream input = loader.getResourceAsStream(DEVELOPMENT_CONFIG_RESOURCE)) {
            if (input == null) {
                throw new IllegalStateException("Packaged development Supabase configuration was not found: "
                        + DEVELOPMENT_CONFIG_RESOURCE);
            }
            properties.load(input);
            return properties;
        } catch (IOException ex) {
            throw new IllegalStateException("Development Supabase configuration could not be read.", ex);
        }
    }

    private static String requiredDevelopmentProperty(String key) {
        String value = clean(DEVELOPMENT.getProperty(key));
        if (value == null) {
            throw new IllegalStateException("Development Supabase configuration is missing " + key + ".");
        }
        return value;
    }

    private static String firstNonBlank(String... values) {
        if (values == null) return null;
        for (String value : values) {
            if (value != null && !value.isBlank()) return value.trim();
        }
        return null;
    }

    private static String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String stripTrailingSlash(String value) {
        String result = value;
        while (result.endsWith("/")) result = result.substring(0, result.length() - 1);
        return result;
    }

    public enum Environment {
        DEVELOPMENT,
        TEST,
        PRODUCTION;

        static Environment from(String value) {
            if (value == null || value.isBlank()) return DEVELOPMENT;
            return switch (value.trim().toLowerCase(Locale.ROOT)) {
                case "dev", "development" -> DEVELOPMENT;
                case "test", "testing", "staging" -> TEST;
                case "prod", "production" -> PRODUCTION;
                default -> throw new IllegalStateException(
                        "SMARTSTOCK_ENVIRONMENT must be development, test, or production.");
            };
        }
    }
}
