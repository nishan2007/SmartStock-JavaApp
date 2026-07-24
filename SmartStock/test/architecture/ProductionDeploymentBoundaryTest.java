package architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductionDeploymentBoundaryTest {
    private static final Path ROOT = Path.of(System.getProperty("user.dir"));

    @Test
    void productionCannotSilentlyPackageDevelopmentProject() throws Exception {
        String config = Files.readString(ROOT.resolve("src/services/SupabaseProjectConfig.java"));
        String packageScript = Files.readString(ROOT.resolve("tools/package-update-release.sh"));
        assertTrue(config.contains("Production cannot use the SmartStock development Supabase project"));
        assertTrue(packageScript.contains("Production packaging requires SUPABASE_URL"));
        assertTrue(packageScript.contains("Refusing to package production against the development"));
    }

    @Test
    void identityMigrationHasNoPasswordOrPinInputs() throws Exception {
        String main = Files.readString(ROOT.resolve("src/app/ProductionIdentityMigrationMain.java"));
        String manifest = Files.readString(
                ROOT.resolve("tools/production-identity-manifest.example.json"));
        assertFalse(manifest.toLowerCase().contains("password"));
        assertFalse(manifest.toLowerCase().contains("pin"));
        assertFalse(main.contains("EMPLOYEE_PASSWORD"));
        assertTrue(main.contains("SMARTSTOCK_MIGRATION_SOURCE_DB_PASSWORD"));
    }

    @Test
    void productionReadinessRequiresRecoveryEvidence() throws Exception {
        String readiness = Files.readString(
                ROOT.resolve("src/services/ProductionReadinessService.java"));
        assertTrue(readiness.contains("recovery-drill"));
        assertTrue(readiness.contains("_recovery_drill"));
        assertTrue(readiness.contains("minus(30, ChronoUnit.DAYS)"));
    }
}
