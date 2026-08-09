package app;

import services.CloudSyncManifest;
import services.ProductionRecoveryDrillService;
import services.ServerSupabaseCredentials;
import services.SupabaseProjectConfig;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Arrays;

/** Runs a destructive restore only against an explicitly empty development drill database. */
public final class DevelopmentRecoveryDrillMain {
    private DevelopmentRecoveryDrillMain() {
    }

    public static void main(String[] args) throws Exception {
        SupabaseProjectConfig project = SupabaseProjectConfig.load();
        if (project.environment() != SupabaseProjectConfig.Environment.DEVELOPMENT) {
            throw new IllegalStateException("This recovery drill is development-only.");
        }
        if (!ServerSupabaseCredentials.isConfigured()) {
            throw new IllegalStateException("The development Supabase Server Key is required.");
        }
        if (args.length != 2 || !"--confirm-empty-target".equals(args[1])) {
            throw new IllegalArgumentException(
                    "Usage: DevelopmentRecoveryDrillMain <location-id> --confirm-empty-target");
        }
        int locationId = parseLocation(args[0]);
        String targetUrl = requireEnv("SMARTSTOCK_RECOVERY_TARGET_DB_URL");
        String targetUser = requireEnv("SMARTSTOCK_RECOVERY_TARGET_DB_USER");
        char[] targetPassword = optionalEnv("SMARTSTOCK_RECOVERY_TARGET_DB_PASSWORD").toCharArray();
        try (Connection target = DriverManager.getConnection(
                targetUrl, targetUser, new String(targetPassword))) {
            var result = ProductionRecoveryDrillService.run(target, locationId,
                    CloudSyncManifest.fetchStoreSnapshot(locationId));
            System.out.printf(
                    "DEVELOPMENT RECOVERY DRILL PASSED location=%d database=%s tables=%d rows=%d%n",
                    locationId, result.targetDatabase(), result.comparisons().size(),
                    result.restoredRows());
        } finally {
            Arrays.fill(targetPassword, '\0');
        }
    }

    private static int parseLocation(String value) {
        try {
            int locationId = Integer.parseInt(value);
            if (locationId <= 0) throw new NumberFormatException();
            return locationId;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("The location ID must be a positive integer.", ex);
        }
    }

    private static String requireEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " is required.");
        }
        return value.trim();
    }

    private static String optionalEnv(String name) {
        String value = System.getenv(name);
        return value == null ? "" : value;
    }
}
