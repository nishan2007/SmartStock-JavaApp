package data;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import services.SupabaseProjectConfig;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EnvironmentProfileTest {
    @AfterEach
    void clearOverride() {
        System.clearProperty("smartstock.environment");
    }

    @Test
    void developmentAndProductionUseDifferentConfigurationPathsAndSecretNames() {
        System.setProperty("smartstock.environment", "development");
        var developmentDatabase = DatabaseConfig.configPath();
        var developmentSupabase = SupabaseProjectConfig.configPath();
        var developmentSecret = EnvironmentProfile.active().secretKey("primary-db-user");

        System.setProperty("smartstock.environment", "production");
        var productionDatabase = DatabaseConfig.configPath();
        var productionSupabase = SupabaseProjectConfig.configPath();
        var productionSecret = EnvironmentProfile.active().secretKey("primary-db-user");

        assertNotEquals(developmentDatabase, productionDatabase);
        assertNotEquals(developmentSupabase, productionSupabase);
        assertNotEquals(developmentSecret, productionSecret);
        assertTrue(productionDatabase.endsWith(
                java.nio.file.Path.of("profiles", "production", "database.properties")));
    }
}
