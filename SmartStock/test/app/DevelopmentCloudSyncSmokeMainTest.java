package app;

import data.DatabaseConfig;
import data.DatabaseMode;
import org.junit.jupiter.api.Test;
import services.SupabaseProjectConfig;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DevelopmentCloudSyncSmokeMainTest {
    @Test
    void acceptsOnlyCompleteDevelopmentServerConfiguration() {
        assertDoesNotThrow(() -> DevelopmentCloudSyncSmokeMain.requireDevelopmentServer(
                database(DatabaseMode.SERVER, 1),
                developmentProject()));

        assertThrows(IllegalStateException.class,
                () -> DevelopmentCloudSyncSmokeMain.requireDevelopmentServer(
                        database(DatabaseMode.CLIENT, 1), developmentProject()));
        assertThrows(IllegalStateException.class,
                () -> DevelopmentCloudSyncSmokeMain.requireDevelopmentServer(
                        database(DatabaseMode.SERVER, null), developmentProject()));
    }

    @Test
    void refusesAnyProductionProject() {
        SupabaseProjectConfig production = new SupabaseProjectConfig(
                SupabaseProjectConfig.Environment.PRODUCTION,
                "https://abcdefghijklmnopqrst.supabase.co",
                "production-publishable-key",
                "abcdefghijklmnopqrst");

        assertThrows(IllegalStateException.class,
                () -> DevelopmentCloudSyncSmokeMain.requireDevelopmentServer(
                        database(DatabaseMode.SERVER, 1), production));
    }

    private static DatabaseConfig database(DatabaseMode mode, Integer locationId) {
        return new DatabaseConfig(
                mode,
                "jdbc:postgresql://127.0.0.1:5432/smartstock",
                "smartstock_test",
                "test-password",
                "127.0.0.1",
                5432,
                locationId,
                60);
    }

    private static SupabaseProjectConfig developmentProject() {
        return new SupabaseProjectConfig(
                SupabaseProjectConfig.Environment.DEVELOPMENT,
                SupabaseProjectConfig.DEVELOPMENT_URL,
                SupabaseProjectConfig.DEVELOPMENT_PUBLISHABLE_KEY,
                SupabaseProjectConfig.DEVELOPMENT_PROJECT_REF);
    }
}
