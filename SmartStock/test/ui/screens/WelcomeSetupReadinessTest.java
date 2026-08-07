package ui.screens;

import data.DatabaseConfig;
import data.DatabaseMode;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WelcomeSetupReadinessTest {
    @Test
    void offersServerProcessRecoveryFromTheWelcomeScreen() throws Exception {
        String source = Files.readString(Path.of("src/ui/screens/WelcomeFrame.java"));
        assertTrue(source.contains("Start Server Processes"));
        assertTrue(source.contains("PostgresRuntimeService.startServerProcesses()"));
        assertTrue(source.contains("config.mode() == DatabaseMode.SERVER"));
    }
    @Test
    void serverSetupRemainsAvailableUntilAllConnectionFieldsAndStoreArePresent() {
        DatabaseConfig complete = serverConfig(1);

        assertFalse(WelcomeFrame.isSetupRequired(complete, true, true));
        assertTrue(WelcomeFrame.isSetupRequired(
                serverConfig(null),
                true, true));
        assertFalse(WelcomeFrame.isSetupRequired(serverConfig(1), true, true));
        assertTrue(WelcomeFrame.isSetupRequired(serverConfig(1), true, false));
    }

    @Test
    void setupIsRequiredWithoutAConfigFileOrRegisterStoreAssignment() {
        DatabaseConfig register = new DatabaseConfig(
                DatabaseMode.CLIENT, "", "", "", "store-server", 5432, null,
                60);

        assertTrue(WelcomeFrame.isSetupRequired(register, false));
        assertTrue(WelcomeFrame.isSetupRequired(register, true));
    }

    private static DatabaseConfig serverConfig(Integer locationId) {
        return new DatabaseConfig(
                DatabaseMode.SERVER,
                "jdbc:postgresql://127.0.0.1:5432/smartstock",
                "smartstock", "password", "127.0.0.1", 5432, locationId,
                60);
    }
}
