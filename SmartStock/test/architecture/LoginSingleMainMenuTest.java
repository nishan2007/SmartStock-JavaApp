package architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class LoginSingleMainMenuTest {
    @Test
    void loginAllowsOnlyOneAuthenticationAndNavigationWinner() throws Exception {
        String login = Files.readString(Path.of("src/ui/screens/Login.java"));

        assertTrue(login.contains(
                "authenticationInProgress.compareAndSet(false, true)"
        ));
        assertTrue(login.contains(
                "mainMenuOpened.compareAndSet(false, true)"
        ));
        assertTrue(login.contains("authenticationInProgress.set(false)"));
    }

    @Test
    void navigationReusesAnExistingMainMenuAfterLogin() throws Exception {
        String navigation = Files.readString(Path.of("src/managers/NavigationManager.java"));

        int method = navigation.indexOf("public static void showMainMenuAfterLogin");
        int nextMethod = navigation.indexOf("public static void closeApplication", method);
        String body = navigation.substring(method, nextMethod);
        assertTrue(body.contains("activeMainMenu != null && activeMainMenu.isDisplayable()"));
        assertTrue(body.contains("showExistingMainMenu(login)"));
        assertTrue(body.contains("finally"));
    }
}
