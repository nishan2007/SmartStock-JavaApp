package architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiOnlyCloudSyncBoundaryTest {
    private static final Path ROOT = Path.of(System.getProperty("user.dir"));

    @Test
    void productionRuntimePathsDoNotOpenCloudJdbcConnections() throws Exception {
        for (String relative : List.of(
                "src/services/SyncWorker.java",
                "src/services/StoreHydrationService.java",
                "src/app/ProductionReadinessMain.java",
                "src/app/ProductionRecoveryDrillMain.java",
                "src/services/ServerImageAssetMaintenance.java")) {
            String source = Files.readString(ROOT.resolve(relative));
            assertFalse(source.contains("getCloudConnection()"), relative);
            assertFalse(source.contains("cloudJdbcUrl()"), relative);
            assertFalse(source.contains("cloudDbPassword()"), relative);
        }
    }

    @Test
    void setupHidesLegacyCloudPostgresCredentials() throws Exception {
        String source = Files.readString(ROOT.resolve("src/ui/screens/DatabaseSetup.java"));
        assertFalse(source.contains("Supabase Database Address"));
        assertFalse(source.contains("Supabase Database User"));
        assertFalse(source.contains("Supabase Database Password"));
        assertFalse(source.contains("!database.hasCloudConnection()"));
    }

    @Test
    void serverOnlyRpcsAreRestrictedAndMirrorExcludesPasswordVerifiers() throws Exception {
        String migration = Files.readString(ROOT.resolve(
                "database/migrations/20260723143000_api_only_sync_exchange.sql"));
        String mirror = Files.readString(ROOT.resolve(
                "src/services/CloudRowMirrorService.java"));

        assertTrue(migration.contains("ALTER TABLE public.smartstock_store_rows ENABLE ROW LEVEL SECURITY"));
        assertTrue(migration.contains("FROM PUBLIC, anon, authenticated"));
        assertTrue(migration.contains("TO service_role"));
        assertTrue(mirror.contains("clean.contains(\"password\")"));
        assertTrue(mirror.contains("clean.contains(\"_pin_\")"));
        assertTrue(mirror.contains("\"badge_secret_hash\".equals(clean)"));
    }
}
