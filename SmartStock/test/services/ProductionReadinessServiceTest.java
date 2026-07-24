package services;

import data.DatabaseConfig;
import data.DatabaseMode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductionReadinessServiceTest {
    @Test
    void acceptsOnlyLoopbackStoreDatabaseUrls() {
        assertTrue(ProductionReadinessService.isLoopbackJdbc(
                "jdbc:postgresql://127.0.0.1:5432/smartstock"));
        assertTrue(ProductionReadinessService.isLoopbackJdbc(
                "jdbc:postgresql://localhost:5432/smartstock"));
        assertFalse(ProductionReadinessService.isLoopbackJdbc(
                "jdbc:postgresql://192.168.1.10:5432/smartstock"));
    }

    @Test
    void productionConfigurationFailsClosed() {
        SupabaseProjectConfig project = SupabaseProjectConfig.resolve("production",
                "https://abcdefghijklmnopqrst.supabase.co", "production-key");
        DatabaseConfig database = new DatabaseConfig(
                DatabaseMode.SERVER,
                "jdbc:postgresql://127.0.0.1:5432/smartstock",
                "smartstock_server", "secret", "127.0.0.1", 5432, 1,
                60);
        List<ProductionReadinessService.Check> checks =
                ProductionReadinessService.configurationChecks(database, project, false);
        assertFalse(ProductionReadinessService.allPassed(checks));
        assertTrue(checks.stream().anyMatch(check -> check.name().equals("server-cloud-credential")
                && !check.passed()));
        assertFalse(checks.stream().anyMatch(check -> check.name().equals("cloud-database")));
    }
}
