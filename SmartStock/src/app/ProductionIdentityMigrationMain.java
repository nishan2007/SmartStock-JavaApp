package app;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import services.ProductionIdentityMigrationService;

import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Arrays;

/**
 * Offline operator tool. It never accepts or handles employee passwords.
 */
public final class ProductionIdentityMigrationMain {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private ProductionIdentityMigrationMain() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 1 || args.length > 2
                || (args.length == 2 && !"--confirm".equals(args[1]))) {
            System.err.println("Usage: ProductionIdentityMigrationMain <manifest.json> [--confirm]");
            System.exit(2);
        }

        Path manifestPath = Path.of(args[0]).toAbsolutePath().normalize();
        ProductionIdentityMigrationService.MigrationManifest manifest;
        try (Reader reader = Files.newBufferedReader(manifestPath)) {
            manifest = GSON.fromJson(reader,
                    ProductionIdentityMigrationService.MigrationManifest.class);
        }

        String sourceUrl = requireEnv("SMARTSTOCK_MIGRATION_SOURCE_DB_URL");
        String sourceUser = requireEnv("SMARTSTOCK_MIGRATION_SOURCE_DB_USER");
        char[] sourcePassword = requireSecret("SMARTSTOCK_MIGRATION_SOURCE_DB_PASSWORD");
        String targetUrl = requireEnv("SMARTSTOCK_MIGRATION_TARGET_DB_URL");
        String targetUser = requireEnv("SMARTSTOCK_MIGRATION_TARGET_DB_USER");
        char[] targetPassword = requireSecret("SMARTSTOCK_MIGRATION_TARGET_DB_PASSWORD");

        try (Connection source = DriverManager.getConnection(
                sourceUrl, sourceUser, new String(sourcePassword));
             Connection target = DriverManager.getConnection(
                     targetUrl, targetUser, new String(targetPassword))) {
            var preview = ProductionIdentityMigrationService.preview(source, target, manifest);
            System.out.println("Validated identities:");
            for (var identity : preview.identities()) {
                System.out.println("- " + identity.source().username()
                        + " -> production Auth " + identity.productionAuthUserId()
                        + "; badge preserved=" + maskBadge(identity.source().badgeId()));
            }
            if (args.length != 2) {
                System.out.println("Dry run complete. Re-run with --confirm to perform one transaction.");
                return;
            }
            var result = ProductionIdentityMigrationService.migrate(source, target, manifest);
            System.out.println("Migration committed:");
            for (var identity : result.identities()) {
                System.out.println("- source user " + identity.sourceUserId()
                        + " -> production user " + identity.productionUserId()
                        + " (" + identity.username() + ")");
            }
        } finally {
            Arrays.fill(sourcePassword, '\0');
            Arrays.fill(targetPassword, '\0');
        }
    }

    private static String requireEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " is required.");
        }
        return value.trim();
    }

    private static char[] requireSecret(String name) {
        return requireEnv(name).toCharArray();
    }

    private static String maskBadge(String badge) {
        if (badge == null || badge.length() < 8) return "[configured]";
        return badge.substring(0, 4) + "..." + badge.substring(badge.length() - 4);
    }
}
