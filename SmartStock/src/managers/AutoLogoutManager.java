package managers;

import services.LanApiClient;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.WindowConstants;
import javax.swing.border.EmptyBorder;
import java.awt.AWTEvent;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.AWTEventListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/** Enforces the current device's inactivity policy across all SmartStock windows. */
public final class AutoLogoutManager {
    private static final int POLICY_REFRESH_MILLIS = 60_000;
    private static final Timer TICK_TIMER = new Timer(1_000, ignored -> tick());
    private static final Timer POLICY_TIMER = new Timer(POLICY_REFRESH_MILLIS, ignored -> refreshPolicy());
    private static final AtomicBoolean POLICY_REFRESH_RUNNING = new AtomicBoolean();
    private static final AWTEventListener ACTIVITY_LISTENER = AutoLogoutManager::handleActivity;

    private static volatile Policy policy = Policy.disabled();
    private static volatile long lastActivityNanos;
    private static volatile boolean running;
    private static JDialog warningDialog;
    private static JLabel warningLabel;

    static {
        TICK_TIMER.setRepeats(true);
        POLICY_TIMER.setRepeats(true);
    }

    private AutoLogoutManager() {
    }

    public static void start(LanApiClient.LoginResult result) {
        if (result == null) return;
        start(new LanApiClient.SessionPolicy(result.persistentLoginAllowed(),
                result.autoLogoutEnabled(), result.autoLogoutMinutes()));
    }

    public static void start(LanApiClient.SessionPolicy newPolicy) {
        Runnable action = () -> {
            if (!running) {
                Toolkit.getDefaultToolkit().addAWTEventListener(ACTIVITY_LISTENER,
                        AWTEvent.KEY_EVENT_MASK | AWTEvent.MOUSE_EVENT_MASK
                                | AWTEvent.MOUSE_MOTION_EVENT_MASK | AWTEvent.MOUSE_WHEEL_EVENT_MASK);
                running = true;
            }
            applyPolicyInternal(Policy.from(newPolicy), true);
            if (!TICK_TIMER.isRunning()) TICK_TIMER.start();
            if (!POLICY_TIMER.isRunning()) POLICY_TIMER.start();
        };
        runOnEdt(action);
    }

    public static void applyPolicy(LanApiClient.SessionPolicy newPolicy) {
        if (newPolicy == null) return;
        runOnEdt(() -> applyPolicyInternal(Policy.from(newPolicy), false));
    }

    public static void stop() {
        runOnEdt(() -> {
            if (running) {
                Toolkit.getDefaultToolkit().removeAWTEventListener(ACTIVITY_LISTENER);
            }
            running = false;
            TICK_TIMER.stop();
            POLICY_TIMER.stop();
            POLICY_REFRESH_RUNNING.set(false);
            closeWarning();
            policy = Policy.disabled();
            lastActivityNanos = 0;
        });
    }

    private static void applyPolicyInternal(Policy replacement, boolean newSession) {
        Policy previous = policy;
        policy = replacement;
        if (shouldResetDeadline(previous, replacement, newSession)) {
            resetActivity();
        }
    }

    static boolean shouldResetDeadline(Policy previous, Policy replacement, boolean newSession) {
        boolean becameEffective = !previous.effective() && replacement.effective();
        boolean shortened = previous.effective() && replacement.effective()
                && replacement.timeout().compareTo(previous.timeout()) < 0;
        return newSession || becameEffective || shortened || !replacement.effective();
    }

    private static void handleActivity(AWTEvent event) {
        if (!running || SessionManager.getCurrentUserId() == null) return;
        if (event.getSource() instanceof Component component
                && warningDialog != null
                && SwingUtilities.getWindowAncestor(component) == warningDialog) {
            if (!(component instanceof JButton)) resetActivity();
            return;
        }
        if (!countsAsActivity(event)) return;
        resetActivity();
    }

    static boolean countsAsActivity(AWTEvent event) {
        if (event instanceof KeyEvent key) return key.getID() == KeyEvent.KEY_PRESSED;
        if (event instanceof MouseEvent mouse) {
            return mouse.getID() == MouseEvent.MOUSE_PRESSED
                    || mouse.getID() == MouseEvent.MOUSE_MOVED
                    || mouse.getID() == MouseEvent.MOUSE_DRAGGED
                    || mouse.getID() == MouseEvent.MOUSE_WHEEL;
        }
        return false;
    }

    private static void resetActivity() {
        lastActivityNanos = System.nanoTime();
        closeWarning();
    }

    private static void tick() {
        if (!running || SessionManager.getCurrentUserId() == null || !policy.effective()) {
            closeWarning();
            return;
        }
        long idleMillis = Math.max(0, (System.nanoTime() - lastActivityNanos) / 1_000_000L);
        long timeoutMillis = policy.timeout().toMillis();
        if (idleMillis >= timeoutMillis) {
            closeWarning();
            SessionLogoutManager.logout(activeFrame());
            return;
        }
        long remainingMillis = timeoutMillis - idleMillis;
        if (remainingMillis <= warningLeadMillis(timeoutMillis)) {
            showOrUpdateWarning(remainingMillis);
        }
    }

    static long warningLeadMillis(long timeoutMillis) {
        return timeoutMillis <= 60_000L ? 30_000L : 60_000L;
    }

    private static void showOrUpdateWarning(long remainingMillis) {
        long seconds = Math.max(1, (remainingMillis + 999) / 1_000);
        if (warningDialog == null) {
            Window owner = activeFrame();
            warningDialog = new JDialog(owner instanceof Frame frame ? frame : null,
                    "Automatic Logout", JDialog.ModalityType.APPLICATION_MODAL);
            warningDialog.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
            warningLabel = new JLabel("", SwingConstants.CENTER);
            warningLabel.setFont(new Font("SansSerif", Font.BOLD, 15));
            JPanel content = new JPanel(new BorderLayout(12, 12));
            content.setBorder(new EmptyBorder(18, 22, 18, 22));
            content.add(new JLabel("SmartStock has been inactive.", SwingConstants.CENTER),
                    BorderLayout.NORTH);
            content.add(warningLabel, BorderLayout.CENTER);
            JButton stayButton = new JButton("Stay Signed In");
            JButton logoutButton = new JButton("Logout Now");
            stayButton.addActionListener(ignored -> resetActivity());
            logoutButton.addActionListener(ignored -> SessionLogoutManager.logout(activeFrame()));
            JPanel actions = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
            actions.add(stayButton);
            actions.add(logoutButton);
            content.add(actions, BorderLayout.SOUTH);
            warningDialog.setContentPane(content);
            warningDialog.pack();
            warningDialog.setLocationRelativeTo(owner);
            warningLabel.setText("You will be logged out in " + seconds + " second"
                    + (seconds == 1 ? "." : "s."));
            JDialog dialogToShow = warningDialog;
            SwingUtilities.invokeLater(() -> {
                if (warningDialog == dialogToShow && !dialogToShow.isVisible()) {
                    dialogToShow.setVisible(true);
                }
            });
        }
        if (warningLabel != null) {
            warningLabel.setText("You will be logged out in " + seconds + " second"
                    + (seconds == 1 ? "." : "s."));
        }
    }

    private static void closeWarning() {
        if (warningDialog != null) {
            warningDialog.dispose();
            warningDialog = null;
            warningLabel = null;
        }
    }

    private static void refreshPolicy() {
        if (!running || SessionManager.getCurrentUserId() == null
                || !POLICY_REFRESH_RUNNING.compareAndSet(false, true)) return;
        new SwingWorker<LanApiClient.SessionPolicy, Void>() {
            @Override
            protected LanApiClient.SessionPolicy doInBackground() throws Exception {
                return LanApiClient.loadSessionPolicy();
            }

            @Override
            protected void done() {
                try {
                    applyPolicy(get());
                } catch (Exception ignored) {
                    // Retain the last successfully loaded policy during connectivity failures.
                } finally {
                    POLICY_REFRESH_RUNNING.set(false);
                }
            }
        }.execute();
    }

    private static JFrame activeFrame() {
        for (Window window : Window.getWindows()) {
            if (window instanceof JFrame frame && frame.isActive() && frame.isDisplayable()) return frame;
        }
        return null;
    }

    private static void runOnEdt(Runnable action) {
        if (SwingUtilities.isEventDispatchThread()) action.run();
        else SwingUtilities.invokeLater(action);
    }

    record Policy(boolean persistentLoginAllowed, boolean autoLogoutEnabled, int autoLogoutMinutes) {
        Policy {
            autoLogoutMinutes = Math.max(1, Math.min(480, autoLogoutMinutes));
        }

        static Policy from(LanApiClient.SessionPolicy value) {
            Objects.requireNonNull(value);
            return new Policy(value.persistentLoginAllowed(), value.autoLogoutEnabled(),
                    value.autoLogoutMinutes());
        }

        static Policy disabled() {
            return new Policy(false, false, 15);
        }

        boolean effective() {
            return autoLogoutEnabled && !persistentLoginAllowed;
        }

        Duration timeout() {
            return Duration.ofMinutes(autoLogoutMinutes);
        }
    }
}
