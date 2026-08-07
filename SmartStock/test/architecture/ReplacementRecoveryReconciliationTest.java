package architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ReplacementRecoveryReconciliationTest {
    private static final Path ROOT = Path.of(System.getProperty("user.dir"));

    @Test
    void verifiedReplacementReconcilesSeededRolePermissions() throws Exception {
        String recovery = Files.readString(ROOT.resolve(
                "src/services/CloudRecoveryService.java"));
        String hydration = Files.readString(ROOT.resolve(
                "src/services/StoreHydrationService.java"));
        String welcome = Files.readString(ROOT.resolve(
                "src/ui/screens/WelcomeFrame.java"));
        String databaseSetup = Files.readString(ROOT.resolve(
                "src/ui/screens/DatabaseSetup.java"));

        assertTrue(recovery.contains(
                "reconcilePermissionAssignments && \"role_permissions\".equals(table)"));
        assertTrue(recovery.contains("DELETE FROM role_permissions"));
        assertTrue(hydration.contains(
                "CloudRecoveryService.restoreReplacementStoreMirror(local,locationId,mirror)"));
        assertTrue(welcome.contains("LanApiClient.ensureLocalServerCredential()"));
        assertTrue(welcome.contains("Online - server UI credential unavailable:"));
        assertTrue(databaseSetup.contains(
                "SupabaseProjectConfig.savePublicConfig(EnvironmentProfile.active()"));
    }
}
