package architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class LoginNfcPrivacyTest {
    @Test
    void tappedBadgeIdIsKeptOutOfVisibleUsernameField() throws Exception {
        String source = Files.readString(Path.of("src/ui/screens/Login.java"));
        assertTrue(source.contains("usernameField.setText(\"NFC badge detected\")"));
        assertTrue(source.contains("String identifier = lastNfcBadgeIdentifier != null"));
    }
}
