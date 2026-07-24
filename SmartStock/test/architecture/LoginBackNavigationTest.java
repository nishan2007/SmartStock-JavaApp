package architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoginBackNavigationTest {
    @Test
    void loginProvidesBackButtonThatReturnsToWelcome() throws Exception {
        String login = Files.readString(Path.of("src/ui/screens/Login.java"));

        assertTrue(login.contains("backButton = new JButton(\"Back\")"));
        assertTrue(login.contains("backButton.addActionListener(event -> returnToWelcome())"));
        assertTrue(login.contains("NavigationManager.returnToWelcomeFromLogin(this)"));
        assertTrue(login.contains("backButton.setEnabled(enabled)"));
    }

    @Test
    void returningToWelcomeDoesNotClearSavedLoginState() throws Exception {
        String navigation = Files.readString(Path.of("src/managers/NavigationManager.java"));

        int method = navigation.indexOf("public static void returnToWelcomeFromLogin");
        int nextMethod = navigation.indexOf("public static void returnToWelcomeForConnectionLoss", method);
        String body = navigation.substring(method, nextMethod);

        assertTrue(body.contains("new WelcomeFrame()"));
        assertTrue(body.contains("login.dispose()"));
        assertFalse(body.contains("clearSession"));
        assertFalse(body.contains("clearEmployeeSession"));
    }
}
