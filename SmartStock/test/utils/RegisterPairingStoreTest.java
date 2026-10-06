package utils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.Base64;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

class RegisterPairingStoreTest {
    @TempDir Path home;

    @Test void rejectsServerSecretsAndEmployeeSessions() {
        for (String key : new String[]{"smartstock-production-primary-db-password",
                "smartstock-production-lan-api-employee-session", "../credentials"}) {
            assertThrows(IllegalArgumentException.class, () -> SecureCredentialStore.readRegisterPairing(home, key));
        }
    }

    @Test void readsOnlyRequestedPairingFromCompanionHome() throws Exception {
        String previous = System.getProperty("os.name");
        try {
            System.setProperty("os.name", "Linux");
            Path file = home.resolve(".smartstock/secure-credentials.properties");
            Files.createDirectories(file.getParent());
            Files.writeString(file, "smartstock-production-lan-api-device-token="
                    + Base64.getEncoder().encodeToString("test-register-token".getBytes(StandardCharsets.UTF_8)));
            assertEquals("test-register-token", SecureCredentialStore.readRegisterPairing(home,
                    "smartstock-production-lan-api-device-token"));
            assertNull(SecureCredentialStore.readRegisterPairing(home, "smartstock-development-lan-api-device-token"));
            assertNull(SecureCredentialStore.readRegisterPairing(home.resolve("missing"),
                    "smartstock-production-lan-api-device-token"));
        } finally { System.setProperty("os.name", previous); }
    }
}
