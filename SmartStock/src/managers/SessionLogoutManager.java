package managers;

import services.LanApiClient;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import java.awt.Window;
import java.util.concurrent.atomic.AtomicBoolean;

/** One authoritative employee logout path for menu, button, and inactivity logout. */
public final class SessionLogoutManager {
    private static final AtomicBoolean LOGOUT_IN_PROGRESS = new AtomicBoolean();

    private SessionLogoutManager() {
    }

    public static void logout(JFrame currentScreen) {
        Runnable action = () -> {
            if (!LOGOUT_IN_PROGRESS.compareAndSet(false, true)) return;
            try {
                LanApiClient.logoutWithoutWaiting();
                SupabaseSessionManager.clearSession();
                SupabaseSessionManager.clearPersistedSession();
                SessionManager.clearSessionState();
                NavigationManager.logoutToLogin(resolveScreen(currentScreen));
            } finally {
                LOGOUT_IN_PROGRESS.set(false);
            }
        };
        if (SwingUtilities.isEventDispatchThread()) action.run();
        else SwingUtilities.invokeLater(action);
    }

    private static JFrame resolveScreen(JFrame preferred) {
        if (preferred != null && preferred.isDisplayable()) return preferred;
        for (Window window : Window.getWindows()) {
            if (window instanceof JFrame frame && frame.isActive() && frame.isDisplayable()) {
                return frame;
            }
        }
        for (Window window : Window.getWindows()) {
            if (window instanceof JFrame frame && frame.isVisible() && frame.isDisplayable()) {
                return frame;
            }
        }
        return null;
    }
}
