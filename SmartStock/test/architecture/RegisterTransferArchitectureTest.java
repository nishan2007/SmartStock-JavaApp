package architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

class RegisterTransferArchitectureTest {
    private static String source(String path) throws Exception {
        return Files.readString(Path.of(path));
    }

    @Test
    void transferSchemaAndSyncCoverBothStores() throws Exception {
        String migration=source("database/migrations/v1_after/20260811190000_add_register_transfers.sql");
        String mirror=source("src/services/CloudRowMirrorService.java");
        String manifest=source("src/services/ReferenceDataSyncService.java");
        assertTrue(migration.contains("register_transfers_one_prepared_device_idx"));
        assertTrue(migration.contains("'PREPARED', 'COMPLETED', 'CANCELLED', 'EXPIRED'"));
        assertTrue(mirror.contains("register_transfers\", \"(t.source_location_id=? OR t.destination_location_id=?)"));
        assertTrue(manifest.contains("\"register_transfers\""));
    }

    @Test
    void serverEnforcesPermissionDrawerClosureAndFreshDestinationTrust() throws Exception {
        String service=source("src/services/RegisterTransferService.java");
        String server=source("src/services/LanApiServer.java");
        String client=source("src/services/LanApiClient.java");
        assertTrue(service.contains("OPEN_DRAWER_SESSION"));
        assertTrue(service.contains("DEVICE_MANAGEMENT"));
        assertTrue(service.contains("DeviceCredentialService.revokeCredential"));
        assertTrue(service.contains("cash_drawer_device_assignments SET is_active=FALSE"));
        assertTrue(server.contains("requirePreparedForDestination"));
        assertTrue(client.contains("clearServerTrust"));
        assertTrue(client.contains("LAN_API_FINGERPRINT_SECRET"));
        assertTrue(client.contains("TRANSFER_STATE_SECRET"));
        assertFalse(service.contains("COALESCE(is_active,TRUE)"));
    }

    @Test
    void uiKeepsMoveAndEmergencyRecoveryDistinctFromBlocking() throws Exception {
        String devices=source("src/ui/screens/DeviceManagement.java");
        String welcome=source("src/ui/screens/WelcomeFrame.java");
        assertTrue(devices.contains("Move This Register"));
        assertTrue(devices.contains("Close and balance" ) || devices.contains("open cash drawer"));
        assertTrue(welcome.contains("Recover at Another Store"));
        assertTrue(welcome.contains("Emergency Register Recovery"));
    }
}
