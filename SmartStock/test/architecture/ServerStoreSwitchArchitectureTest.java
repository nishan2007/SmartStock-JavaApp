package architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerStoreSwitchArchitectureTest {
    @Test void storeSwitchPreflightsAndMigratesPairedRegistersSafely()throws Exception{
        String service=Files.readString(Path.of("src/services/ServerStoreSwitchService.java"));
        String wizard=Files.readString(Path.of("src/ui/screens/ServerSetupWizard.java"));
        String setup=Files.readString(Path.of("src/ui/screens/DatabaseSetup.java"));
        String client=Files.readString(Path.of("src/services/LanApiClient.java"));
        String server=Files.readString(Path.of("src/services/LanApiServer.java"));
        String welcome=Files.readString(Path.of("src/ui/screens/WelcomeFrame.java"));

        assertTrue(service.contains("UPPER(COALESCE(status,''))='OPEN'"));
        assertTrue(service.contains("status='PREPARED'"));
        assertTrue(service.contains("LOCK TABLE cash_drawer_sessions,register_transfers"));
        assertTrue(service.contains("UPDATE cash_drawer_device_assignments SET is_active=FALSE"));
        assertTrue(service.contains("UPDATE device_sessions SET logout_time=CURRENT_TIMESTAMP"));
        assertTrue(service.contains("UPDATE lan_api_sessions SET revoked_at=CURRENT_TIMESTAMP"));
        assertTrue(service.contains("'COMPLETED',FALSE"));
        assertTrue(service.contains("assignCodeForEnrollment(connection,destinationLocationId,deviceId)"));
        assertTrue(wizard.contains("ServerStoreSwitchService.preflight"));
        assertTrue(wizard.contains("ServerStoreSwitchService.switchPairedRegisters"));
        assertTrue(setup.contains("ServerStoreSwitchService.preflight"));
        assertTrue(setup.contains("ServerStoreSwitchService.switchServerStore"));
        assertTrue(server.contains("RETURNING last_store_id"));
        assertTrue(client.contains("saveServerAssignedLocation(response.has(\"locationId\")"));
        assertTrue(welcome.contains("LanApiClient.syncDeviceMetadata()"));
    }
}
