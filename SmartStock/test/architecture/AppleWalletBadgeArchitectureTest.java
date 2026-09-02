package architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class AppleWalletBadgeArchitectureTest {
    @Test
    void walletTemplatesHaveOrderedSchemaStoreBindingAndServerValidation() throws Exception {
        String resource="database/migrations/v1_after/20260831150000_wallet_template.sql";
        assertTrue(services.SchemaContractService.localContractResources().contains(resource));
        String base=Files.readString(Path.of("database/v1/local/001_schema.sql"));
        assertTrue(base.contains("wallet_template_json text DEFAULT '' NOT NULL"));
        assertTrue(base.contains("employee_wallet_enrollment_location_idx"));
        String server=Files.readString(Path.of("src/services/LanApiServer.java"));
        assertTrue(server.contains("WalletTemplateRepository.save(connection, locationId"));
        assertTrue(server.contains("APPLE_WALLET_TEMPLATE_UPDATED"));
        String service=Files.readString(Path.of("src/services/AppleWalletBadgeService.java"));
        assertTrue(service.contains("ps.setInt(5,locationId)"));
        assertTrue(service.contains("WalletTemplateRepository.load(c,locationId)"));
        assertTrue(service.contains("BadgePrintService.loadEmployeeBadgeData(c,userId,locationId)"));
        String mirror=Files.readString(Path.of("src/services/CloudRowMirrorService.java"));
        assertTrue(mirror.contains("SELECT t.* FROM"),"Mirror transports added customization columns");
    }
    @Test
    void walletCredentialsAreHashedRevocableAndLocationBound() throws Exception {
        String migration=Files.readString(Path.of("database/migrations/v1_after/20260831120000_apple_wallet_badges.sql"));
        String service=Files.readString(Path.of("src/services/AppleWalletBadgeService.java"));
        assertTrue(migration.contains("credential_hash text NOT NULL UNIQUE"));
        assertTrue(migration.contains("status IN ('ACTIVE','REVOKED')"));
        assertTrue(migration.contains("token_hash text NOT NULL UNIQUE"));
        assertFalse(migration.contains("wallet_credential text"));
        assertTrue(service.contains("JOIN user_locations ul"));
        assertTrue(service.contains("LocalAuthCacheService.verifyEmployeePin"));
        String sync=Files.readString(Path.of("src/services/CrossStoreReferenceSyncService.java"));
        assertTrue(sync.contains("new TableSnapshot(\"employee_wallet_credentials\""));
        assertTrue(sync.contains("upsertWalletCredential"));
    }

    @Test
    void publicEnrollmentIsOneTimeAndNfcDefaultsOff() throws Exception {
        String service=Files.readString(Path.of("src/services/AppleWalletBadgeService.java"));
        String config=Files.readString(Path.of("src/services/AppleWalletConfig.java"));
        String server=Files.readString(Path.of("src/services/LanApiServer.java"));
        String enrollment=Files.readString(Path.of("src/services/WalletEnrollmentHandler.java"));
        String gateway=Files.readString(Path.of("src/services/WalletEnrollmentServer.java"));
        assertTrue(service.contains("consumed_at IS NULL AND e.expires_at>CURRENT_TIMESTAMP"));
        assertTrue(service.contains("UPDATE employee_wallet_enrollments SET consumed_at=CURRENT_TIMESTAMP"));
        assertTrue(config.contains("smartstock.wallet.nfcEnabled\", \"false"));
        assertTrue(server.contains("WALLET_NFC_DISABLED"));
        assertTrue(enrollment.contains("application/vnd.apple.pkpass"));
        assertTrue(gateway.contains("127.0.0.1"));
        assertFalse(server.contains("createContext(\"/wallet/enroll/\""));
    }

    @Test
    void registerLoginKeepsPhysicalBadgeFlowAndAddsWalletFlow() throws Exception {
        String login=Files.readString(Path.of("src/ui/screens/Login.java"));
        assertTrue(login.contains("loginWithWallet"));
        assertTrue(login.contains("loginWithCredentials"));
        assertTrue(login.contains("normalizeScannedCredential"));
    }
}
