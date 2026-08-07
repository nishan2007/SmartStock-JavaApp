package ui.screens;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatabaseSetupServiceStatusTest {
    @Test
    void recognizesMacAndWindowsServiceStates() {
        assertTrue(DatabaseSetup.serviceLooksRunning("postgresql@17 started nishan", "postgres"));
        assertTrue(DatabaseSetup.serviceLooksRunning("postgresql-x64-17   Running", "postgres"));
        assertFalse(DatabaseSetup.serviceLooksRunning("postgresql@17 stopped", "postgres"));
        assertTrue(DatabaseSetup.serviceLooksRunning("state = running\npid = 42", "smartstock"));
        assertTrue(DatabaseSetup.serviceLooksRunning("Status: Running", "smartstock"));
        assertFalse(DatabaseSetup.serviceLooksRunning("Could not find service", "smartstock"));
    }

    @Test
    void connectionSaveAlsoPersistsAnEnteredServerCredential() throws Exception {
        String source = Files.readString(Path.of("src/ui/screens/DatabaseSetup.java"));
        assertTrue(source.contains("ServerSupabaseCredentials.install(serverCredential)"));
        assertTrue(source.contains("Connection settings and Supabase server credential saved securely."));
        assertFalse(source.contains("badge-only cloud operations"));
    }

    @Test
    void windowsSetupUsesTheActiveEnvironmentInsideDatabaseSetup() throws Exception {
        String source = Files.readString(Path.of("src/ui/screens/DatabaseSetup.java"));
        assertTrue(source.contains("Complete Windows Server Setup"));
        assertTrue(source.contains("installWindowsProductionServer"));
        assertTrue(source.contains("Windows will ask for administrator approval"));
        assertTrue(source.contains("savePublicConfig(EnvironmentProfile.active()"));
        assertFalse(source.contains("saveProductionPublicConfig("));
    }
}
