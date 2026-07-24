package app;

import com.google.gson.GsonBuilder;
import services.CloudSyncManifest;
import data.DatabaseConfig;
import services.ProductionReadinessService;
import services.ProductionRecoveryDrillService;
import services.SupabaseProjectConfig;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.Instant;
import java.util.Arrays;

public final class ProductionRecoveryDrillMain {
    private ProductionRecoveryDrillMain() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 3 || !"--confirm-empty-target".equals(args[2])) {
            System.err.println("Usage: ProductionRecoveryDrillMain "
                    + "<evidence.json> <operator-name> --confirm-empty-target");
            System.exit(2);
        }
        SupabaseProjectConfig project = SupabaseProjectConfig.load();
        if (!project.isProduction()) {
            throw new IllegalStateException("Recovery evidence can only be generated in production mode.");
        }
        String targetUrl = requireEnv("SMARTSTOCK_RECOVERY_TARGET_DB_URL");
        String targetUser = requireEnv("SMARTSTOCK_RECOVERY_TARGET_DB_USER");
        char[] targetPassword = requireEnv("SMARTSTOCK_RECOVERY_TARGET_DB_PASSWORD").toCharArray();
        Path evidencePath = Path.of(args[0]).toAbsolutePath().normalize();
        Integer locationId = DatabaseConfig.load().locationId();
        if (locationId == null) {
            throw new IllegalStateException("The production server Store Location ID is required.");
        }
        try (Connection target = DriverManager.getConnection(
                targetUrl, targetUser, new String(targetPassword));
             ) {
            var result = ProductionRecoveryDrillService.run(target, locationId,
                    CloudSyncManifest.fetchStoreSnapshot(locationId));
            var evidence = new ProductionReadinessService.RecoveryEvidence(
                    "PASS", Instant.now().toString(), args[1], result.targetDatabase(),
                    result.comparisons().size());
            Files.writeString(evidencePath,
                    new GsonBuilder().setPrettyPrinting().create().toJson(evidence));
            System.out.println("Recovery drill passed across "
                    + result.comparisons().size() + " tables; evidence=" + evidencePath);
        } finally {
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
}
