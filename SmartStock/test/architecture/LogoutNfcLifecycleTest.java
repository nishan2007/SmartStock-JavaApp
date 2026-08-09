package architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class LogoutNfcLifecycleTest {
    private static final Path SOURCE = Path.of("src");

    @Test
    void logoutDisposesOldScreenBeforeStartingNfcLoginOnNextUiTurn() throws Exception {
        String navigation = Files.readString(SOURCE.resolve("managers/NavigationManager.java"));
        int dispose = navigation.indexOf("currentScreen.dispose();", navigation.indexOf("logoutToLogin"));
        int deferredLogin = navigation.indexOf("SwingUtilities.invokeLater", dispose);
        int createLogin = navigation.indexOf("new Login()", deferredLogin);
        assertTrue(dispose >= 0 && deferredLogin > dispose && createLogin > deferredLogin);
    }

    @Test
    void loginRestartsNfcMonitorWhenWindowBecomesActive() throws Exception {
        String login = Files.readString(SOURCE.resolve("ui/screens/Login.java"));
        assertTrue(login.contains("windowActivated(WindowEvent event) { ensureNfcMonitorRunning(); }"));
        assertTrue(login.contains("windowClosed(WindowEvent event) { stopNfcMonitor(); }"));
        assertTrue(login.contains("monitor.interrupt();"));
    }
}
