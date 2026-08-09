package architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class DeviceAutoLogoutArchitectureTest {
    private static String source(String path) throws Exception {
        return Files.readString(Path.of(path));
    }

    @Test
    void schemaAndSyncCarryPerDevicePolicy() throws Exception {
        String setup = source("database/v1/local/001_schema.sql");
        String migration = source(
                "database/v1/local/001_schema.sql");
        String sync = source("src/services/ReferenceDataSyncService.java");
        String runner = source("src/services/ServerSupabaseMigrationRunner.java");
        String deviceInstaller = source("src/services/DeviceCredentialSchemaInstaller.java");

        for (String column : new String[]{"auto_logout_enabled", "auto_logout_minutes"}) {
            assertTrue(setup.contains(column));
            assertTrue(migration.contains(column));
        }
        assertTrue(sync.contains("\"devices\""));
        assertTrue(setup.contains("devices_auto_logout_minutes_check"));
        assertTrue(migration.contains("auto_logout_minutes <= 480"));
        assertTrue(runner.contains("SchemaContractService.cloudContractResources()"));
        assertTrue(deviceInstaller.contains(
                "SchemaContractService.requireLocalReady(connection)"));
    }

    @Test
    void apiReturnsAndRefreshesThePolicy() throws Exception {
        String server = source("src/services/LanApiServer.java");
        String client = source("src/services/LanApiClient.java");

        assertTrue(server.contains("server.createContext(\"/v1/sessions/policy\""));
        assertTrue(server.contains("authenticateSession(context.exchange(), device, true)"));
        assertTrue(server.contains("result.put(\"autoLogoutEnabled\""));
        assertTrue(server.contains("result.put(\"autoLogoutMinutes\""));
        assertTrue(client.contains("post(\"/v1/sessions/policy\""));
        assertTrue(client.contains("record SessionPolicy("));
    }

    @Test
    void uiAndSessionLifecycleUseSharedPolicyAndLogoutPaths() throws Exception {
        String devices = source("src/ui/screens/DeviceManagement.java");
        String login = source("src/ui/screens/Login.java");
        String session = source("src/managers/SessionManager.java");
        String autoLogout = source("src/managers/AutoLogoutManager.java");
        String menu = source("src/ui/components/AppMenuBar.java");

        assertTrue(devices.contains("Enable automatic logout after inactivity"));
        assertTrue(devices.contains("new SpinnerNumberModel(15, 1, 480, 1)"));
        assertTrue(devices.contains("overrides automatic logout"));
        assertTrue(login.contains("AutoLogoutManager.start(result)"));
        assertTrue(session.contains("AutoLogoutManager.stop()"));
        assertTrue(autoLogout.contains("POLICY_REFRESH_MILLIS = 60_000"));
        assertTrue(autoLogout.contains("SessionLogoutManager.logout("));
        assertTrue(menu.contains("SessionLogoutManager.logout(parent)"));
    }
}
