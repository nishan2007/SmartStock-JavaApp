package architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NfcAuthenticationCoverageTest {
    @Test
    void everyEmployeeAuthenticationSurfaceUsesNfc() throws Exception {
        String login = Files.readString(Path.of("src/ui/screens/Login.java"));
        String welcome = Files.readString(Path.of("src/ui/screens/WelcomeFrame.java"));
        String approval = Files.readString(Path.of("src/services/ManagerApprovalService.java"));
        String server = Files.readString(Path.of("src/services/LanApiServer.java"));

        assertTrue(login.contains("PcscNfcService.read"));
        assertTrue(login.contains("if (!PcscNfcService.hasReader())"),
                "Login must retry when the NFC reader is briefly unavailable during logout");
        assertTrue(welcome.contains("PcscNfcService.read"));
        assertTrue(approval.contains("NfcBadgePromptController"));
        assertTrue(server.contains("LocalAuthCacheService.verifyEmployeePin"));
    }

    @Test
    void firstBadgeTapOffersSecurePinCreation() throws Exception {
        String login = Files.readString(Path.of("src/ui/screens/Login.java"));
        String client = Files.readString(Path.of("src/services/LanApiClient.java"));
        String server = Files.readString(Path.of("src/services/LanApiServer.java"));

        assertTrue(login.contains("showFirstBadgePinSetup"));
        assertTrue(login.contains("Account Password:"));
        assertTrue(client.contains("/v1/sessions/badge-pin-setup"));
        assertTrue(server.contains("signInSupabasePassword(user.email(), accountPassword)"));
        assertTrue(server.contains("LocalAuthCacheService.saveEmployeePin"));
        assertTrue(server.contains("PIN_ALREADY_CONFIGURED"));
    }

    @Test
    void companyPreferenceCanAllowAuditedBadgeOnlyAuthentication() throws Exception {
        String login = Files.readString(Path.of("src/ui/screens/Login.java"));
        String preferences = Files.readString(Path.of("src/ui/screens/CompanyCustomization.java"));
        String server = Files.readString(Path.of("src/services/LanApiServer.java"));
        String approvalPrompt = Files.readString(Path.of("src/ui/helpers/NfcBadgePromptController.java"));
        String schema = Files.readString(Path.of("database/v1/local/001_schema.sql"));
        String lanSchema = Files.readString(Path.of("database/v1/local/001_schema.sql"));

        assertTrue(preferences.contains("requireBadgePinLoginBox"));
        assertTrue(preferences.contains("Badge-only") || preferences.contains("possession of an active badge"));
        assertTrue(login.contains("status.pinRequired()"));
        assertTrue(server.contains("ServerCompanyCustomizationRepository.isBadgePinRequired"));
        assertTrue(server.contains("BADGE_ONLY_LOGIN"));
        assertTrue(server.contains("BADGE_ONLY_MANAGER_APPROVAL"));
        assertTrue(approvalPrompt.contains("LanApiClient.badgeStatus"),
                "Manager badge prompts must load the same company PIN policy used by login");
        assertTrue(approvalPrompt.contains("No PIN is required"),
                "Badge-only manager approval must not continue prompting for a PIN");
        assertTrue(schema.contains("require_badge_pin_login boolean DEFAULT true NOT NULL"));
        assertTrue(lanSchema.contains("'BADGE_ONLY'"),
                "Existing LAN session constraints must accept badge-only sessions");
        assertTrue(lanSchema.contains("'BADGE_PIN_SETUP'"),
                "First-time PIN setup must be accepted as a session source");
    }

    @Test
    void passwordFieldsAreLimitedToCoveredAuthOrSecretSetup() throws Exception {
        Path source = Path.of("src");
        List<String> files;
        try (var paths = Files.walk(source)) {
            files = paths.filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> {
                        try { return Files.readString(path).contains("JPasswordField"); }
                        catch (Exception ex) { throw new RuntimeException(ex); }
                    })
                    .map(path -> source.relativize(path).toString().replace('\\', '/'))
                    .sorted()
                    .toList();
        }
        assertEquals(List.of(
                "services/EmployeePinService.java",
                "services/ManagerApprovalService.java",
                "ui/screens/DatabaseSetup.java",
                "ui/screens/FirstAdministratorSetupDialog.java",
                "ui/screens/Login.java",
                "ui/screens/ServerSetupWizard.java",
                "ui/screens/SupabaseProjectInitializerDialog.java",
                "ui/helpers/NfcBadgePromptController.java"
        ).stream().sorted().toList(), files);
    }
}
