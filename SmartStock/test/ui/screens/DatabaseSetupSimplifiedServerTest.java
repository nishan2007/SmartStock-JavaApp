package ui.screens;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class DatabaseSetupSimplifiedServerTest {
    @Test
    void serverDefaultsToCloudOnlyAndKeepsTechnicalFieldsBehindAdvancedButton()
            throws Exception {
        String source = Files.readString(Path.of("src/ui/screens/DatabaseSetup.java"));
        assertTrue(source.contains("new JButton(\"Show Advanced Settings\")"));
        assertTrue(source.contains("boolean serverAdvanced = server && advancedSettingsVisible"));
        assertTrue(source.contains("setRowVisible(jdbcUrlField, serverAdvanced)"));
        assertTrue(source.contains("setRowVisible(dbUserField, serverAdvanced)"));
        assertTrue(source.contains("setRowVisible(dbPasswordField, serverAdvanced)"));
        assertTrue(source.contains("setRowVisible(lanSubnetField, serverAdvanced)"));
        assertTrue(source.contains("setRowVisible(syncIntervalSpinner, serverAdvanced)"));
        assertTrue(source.contains("setRowVisible(supabaseProjectUrlField, server)"));
        assertTrue(source.contains("setRowVisible(supabasePublishableKeyField, server)"));
        assertTrue(source.contains("setRowVisible(serverCloudCredentialField, server)"));
    }

    @Test
    void registerConnectionUsesServerAssignedStoreInsteadOfManualField() throws Exception {
        String source = Files.readString(Path.of("src/ui/screens/DatabaseSetup.java"));
        assertTrue(source.contains(
                "setRowVisible(serverHostField, serverAdvanced || client || remote)"));
        assertTrue(source.contains(
                "setRowVisible(serverPortSpinner, serverAdvanced || client || remote)"));
        assertTrue(source.contains("setRowVisible(locationIdField, serverAdvanced)"));
        assertTrue(source.contains("selected.locationId()"));
    }
}
