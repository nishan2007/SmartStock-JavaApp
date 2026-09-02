package architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class WalletEnrollmentGatewayArchitectureTest {
    private static final Path SRC = Path.of("src");

    @Test void publicEnrollmentUsesADedicatedLoopbackOnlyListener() throws Exception {
        String gateway = Files.readString(SRC.resolve("services/WalletEnrollmentServer.java"));
        String lan = Files.readString(SRC.resolve("services/LanApiServer.java"));
        assertTrue(gateway.contains("127.0.0.1"));
        assertTrue(gateway.contains("DEFAULT_PORT = 8447"));
        assertTrue(gateway.contains("/wallet/enroll/"));
        assertTrue(gateway.contains("WalletEnrollmentServer::notFound"));
        assertFalse(lan.contains("createContext(\"/wallet/enroll/\""));
    }

    @Test void signingPasswordFallsBackToProtectedCredentialStorage() throws Exception {
        String config = Files.readString(SRC.resolve("services/AppleWalletConfig.java"));
        assertTrue(config.contains("SecureCredentialStore.read(\"apple-wallet-signing-password\")"));
        assertTrue(config.indexOf("SMARTSTOCK_WALLET_SIGNING_PASSWORD")
                < config.indexOf("SecureCredentialStore.read(\"apple-wallet-signing-password\")"));
    }

    @Test void installedServerOwnsAndClosesTheEnrollmentListener() throws Exception {
        String service = Files.readString(SRC.resolve("app/SyncServiceMain.java"));
        assertTrue(service.contains("WalletEnrollmentServer.startIfConfigured()"));
        assertTrue(service.contains("walletEnrollment.close()"));
    }

    @Test void enrollmentFailuresAreLocallyDiagnosableAndAuditFailureDoesNotDiscardThePass() throws Exception {
        String handler = Files.readString(SRC.resolve("services/WalletEnrollmentHandler.java"));
        assertTrue(handler.contains("wallet-enrollment-errors.log"));
        assertTrue(handler.contains("recordFailure(\"issuance\""));
        assertTrue(handler.contains("recordFailure(\"audit\""));
        assertTrue(handler.indexOf("recordFailure(\"audit\"")
                < handler.indexOf("application/vnd.apple.pkpass"));
    }
}
