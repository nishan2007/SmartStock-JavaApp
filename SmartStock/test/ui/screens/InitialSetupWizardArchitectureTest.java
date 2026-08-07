package ui.screens;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InitialSetupWizardArchitectureTest {
    @Test
    void asksForComputerModeBeforeShowingConnectionDetails() throws Exception {
        String source = Files.readString(Path.of("src/ui/screens/InitialSetupWizard.java"));

        assertTrue(source.contains("\"Store Server\""));
        assertTrue(source.contains("\"Register\""));
        assertTrue(source.contains("\"Remote Admin\""));
        assertTrue(source.contains("\"Developer / Test\""));
        assertTrue(source.contains("\"Production\""));
        assertTrue(source.contains("EnvironmentProfile.activate(profile)"));
        assertTrue(source.contains("new ServerSetupWizard(owner == null ? this : owner)"));
        assertTrue(source.contains("new DatabaseSetup(owner == null ? this : owner, selectedMode)"));
        assertTrue(source.contains("PostgreSQL and Maven are not installed"));
    }

    @Test
    void serverSetupIsAResumableTaskOrientedWizard() throws Exception {
        String source = Files.readString(Path.of("src/ui/screens/ServerSetupWizard.java"));
        String welcome = Files.readString(Path.of("src/ui/screens/WelcomeFrame.java"));

        assertTrue(source.contains("STEP_COUNT = 6"));
        assertTrue(source.contains("\"Connect Supabase\""));
        assertTrue(source.contains("\"Initialize Cloud\""));
        assertTrue(source.contains("\"Prepare Local Database\""));
        assertTrue(source.contains("\"Create or Select Store\""));
        assertTrue(source.contains("\"Create First Administrator\""));
        assertTrue(source.contains("\"Start and Verify Server\""));
        assertTrue(source.contains("determineResumeStep()"));
        assertTrue(source.contains("SupabaseProjectConnectionVerifier.verify"));
        assertTrue(source.contains("ServerSupabaseMigrationRunner.migrate"));
        assertTrue(source.contains("ServerProvisioningService.provision"));
        assertTrue(source.contains("LocalDatabaseBootstrapService.ensureConfigured"));
        assertTrue(source.contains("ServerStoreSetupService.create"));
        assertTrue(source.contains("ServerStoreSetupService.listCloud"));
        assertTrue(source.contains("ServerStoreSetupService.restoreFromCloud"));
        assertTrue(source.contains("Loading existing stores from this environment"));
        assertTrue(source.contains("FirstAdministratorSetupDialog"));
        assertTrue(source.contains("\"LocalSubnet\""));
        assertTrue(welcome.contains("new ServerSetupWizard(this)"));
    }

    @Test
    void productionRuntimeInstallerDoesNotInstallBuildTools() throws Exception {
        String source = Files.readString(Path.of("src/services/PostgresRuntimeService.java"));
        String installSection = source.substring(
                source.indexOf("public static CommandResult installOrUpdateRuntime()"),
                source.indexOf("public static ServerPrerequisites checkServerPrerequisites()"));

        assertTrue(installSection.contains("brew install postgresql"));
        assertFalse(installSection.contains("brew install maven"));
        assertFalse(installSection.contains("brew install openjdk"));
    }
}
