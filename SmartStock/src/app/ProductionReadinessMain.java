package app;

import data.DB;
import data.DatabaseConfig;
import services.ProductionReadinessService;
import services.CloudSyncManifest;
import services.ServerSupabaseCredentials;
import services.SupabaseProjectConfig;

import java.nio.file.Path;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;

public final class ProductionReadinessMain {
    private ProductionReadinessMain() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.err.println("Usage: ProductionReadinessMain <recovery-evidence.json>");
            System.exit(2);
        }
        DatabaseConfig database = DatabaseConfig.load();
        SupabaseProjectConfig project = SupabaseProjectConfig.load();
        List<ProductionReadinessService.Check> checks = new ArrayList<>(
                ProductionReadinessService.configurationChecks(
                        database, project, ServerSupabaseCredentials.isConfigured()));
        if (ProductionReadinessService.allPassed(checks)) {
            try (Connection local = DB.getConnection()) {
                Integer locationId = database.locationId();
                checks.addAll(ProductionReadinessService.databaseChecks(
                        local, CloudSyncManifest.fetch(),
                        CloudSyncManifest.fetchStoreSnapshot(locationId)));
            }
        }
        checks.add(ProductionReadinessService.recoveryEvidenceCheck(Path.of(args[0])));
        for (var check : checks) {
            System.out.printf("[%s] %s - %s%n",
                    check.passed() ? "PASS" : "FAIL", check.name(), check.message());
        }
        if (!ProductionReadinessService.allPassed(checks)) {
            System.err.println("PRODUCTION GO-LIVE BLOCKED");
            System.exit(1);
        }
        System.out.println("PRODUCTION READINESS GATE PASSED");
    }
}
