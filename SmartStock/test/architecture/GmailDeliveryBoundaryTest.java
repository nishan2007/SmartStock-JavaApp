package architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class GmailDeliveryBoundaryTest {
    @Test
    void emailDeliveryIsServerOnlyAndNoLongerUsesSupabaseFunction() throws Exception {
        String sender = Files.readString(Path.of("src/services/ServerEmailOutboxService.java"));
        String gmail = Files.readString(Path.of("src/services/GmailOAuthService.java"));
        String locations = Files.readString(Path.of("src/services/LanGmailService.java"));
        assertTrue(sender.contains("GmailOAuthService.send"));
        assertFalse(sender.contains("SupabaseSessionManager"));
        assertFalse(sender.contains("EMAIL_FUNCTION"));
        assertTrue(gmail.contains("SecureCredentialStore"));
        assertTrue(gmail.contains("code_challenge_method=S256"));
        assertTrue(locations.contains("LOCATION_MANAGEMENT"));
        assertTrue(locations.contains("security_audit_events"));
        assertFalse(Files.exists(Path.of("../supabase/functions/smartstock-gmail-sender/index.ts")));
        assertFalse(Files.exists(Path.of("supabase/functions/smartstock-gmail-sender/index.ts")));
    }
}
