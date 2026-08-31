package ui.screens;

import data.DatabaseConfig;
import data.DatabaseMode;
import managers.NavigationManager;
import managers.SessionManager;
import managers.AutoLogoutManager;
import managers.SupabaseSessionManager;
import services.BadgeCredentialService;
import services.LanApiClient;
import services.PcscNfcService;
import services.EmployeePinService;
import ui.helpers.ThemeManager;
import ui.helpers.WindowHelper;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.Arrays;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

/** Register login. Authentication and authorization are owned by the LAN service. */
public class Login extends JFrame {
    private JTextField usernameField;
    private JPasswordField passwordField;
    private JButton loginButton;
    private JButton clearButton;
    private JButton backButton;
    private long badgeEntryStartedAtMillis;
    private long badgeEntryLastKeyAtMillis;
    private int badgeEntryKeyCount;
    private volatile boolean nfcMonitorRunning;
    private volatile Thread nfcMonitorThread;
    private String lastNfcBadgeIdentifier;
    private final AtomicBoolean authenticationInProgress = new AtomicBoolean(false);
    private final AtomicBoolean mainMenuOpened = new AtomicBoolean(false);

    public Login() {
        this(null);
    }

    public Login(PcscNfcService.ReadResult initialNfcCard) {
        setTitle("SmartStock Login");
        setSize(420, 260);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(EXIT_ON_CLOSE);

        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        JLabel titleLabel = new JLabel("SmartStock Login", SwingConstants.CENTER);
        titleLabel.setFont(new Font("Arial", Font.BOLD, 20));

        JPanel formPanel = new JPanel(new GridLayout(2, 2, 10, 10));
        usernameField = new JTextField();
        passwordField = new JPasswordField();
        formPanel.add(new JLabel("Username, Email, or Badge ID:"));
        formPanel.add(usernameField);
        formPanel.add(new JLabel("Password or Employee PIN:"));
        formPanel.add(passwordField);

        JPanel buttonPanel = new JPanel(new BorderLayout(8, 0));
        JPanel loginActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        backButton = new JButton("Back");
        loginButton = new JButton("Login");
        clearButton = new JButton("Clear");
        loginActions.add(clearButton);
        loginActions.add(loginButton);
        buttonPanel.add(backButton, BorderLayout.WEST);
        buttonPanel.add(loginActions, BorderLayout.EAST);
        panel.add(titleLabel, BorderLayout.NORTH);
        panel.add(formPanel, BorderLayout.CENTER);
        panel.add(buttonPanel, BorderLayout.SOUTH);
        add(panel);

        usernameField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyTyped(KeyEvent event) {
                if (Character.isISOControl(event.getKeyChar())) return;
                long now = System.currentTimeMillis();
                if (badgeEntryStartedAtMillis == 0 || now - badgeEntryLastKeyAtMillis > 250) {
                    badgeEntryStartedAtMillis = now;
                    badgeEntryKeyCount = 0;
                }
                badgeEntryLastKeyAtMillis = now;
                badgeEntryKeyCount++;
            }
        });
        backButton.addActionListener(event -> returnToWelcome());
        loginButton.addActionListener(event -> loginUser());
        clearButton.addActionListener(event -> clearFields());
        getRootPane().setDefaultButton(loginButton);
        ThemeManager.applyToWindow(this);
        setVisible(true);
        addWindowListener(new WindowAdapter() {
            @Override public void windowOpened(WindowEvent event) { ensureNfcMonitorRunning(); }
            @Override public void windowActivated(WindowEvent event) { ensureNfcMonitorRunning(); }
            @Override public void windowClosed(WindowEvent event) { stopNfcMonitor(); }
        });
        ensureNfcMonitorRunning();
        if (initialNfcCard != null) {
            SwingUtilities.invokeLater(() -> acceptNfcBadge(initialNfcCard));
        }
        SwingUtilities.invokeLater(this::attemptStoredSignIn);
    }

    private void loginUser() {
        String identifier = lastNfcBadgeIdentifier != null
                ? lastNfcBadgeIdentifier
                : usernameField.getText().trim();
        char[] secret = passwordField.getPassword();
        boolean badgeIdentifier = BadgeCredentialService.looksLikeGeneratedBadge(
                BadgeCredentialService.normalizeBadge(identifier));
        if (identifier.isBlank() || (secret.length == 0 && !badgeIdentifier)) {
            Arrays.fill(secret, '\0');
            JOptionPane.showMessageDialog(this,
                    "Enter username/email and password, or scan a badge and enter the employee PIN.");
            return;
        }
        if (BadgeCredentialService.looksLikeGeneratedBadge(BadgeCredentialService.normalizeBadge(identifier))
                && !isLikelyScannerBadgeEntry(identifier)
                && !BadgeCredentialService.normalizeBadge(identifier).equals(lastNfcBadgeIdentifier)) {
            Arrays.fill(secret, '\0');
            JOptionPane.showMessageDialog(this, "Generated badge IDs must be scanned, swiped, or tapped.");
            return;
        }
        Integer locationId = requiredRegisterLocation();
        if (locationId == null) {
            Arrays.fill(secret, '\0');
            return;
        }
        if (!LanApiClient.isPaired()) {
            Arrays.fill(secret, '\0');
            JOptionPane.showMessageDialog(this,
                    "This register needs its one-time administrator setup before employees can log in.",
                    "Register Setup Required", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (!authenticationInProgress.compareAndSet(false, true)) {
            Arrays.fill(secret, '\0');
            return;
        }

        setLoginControlsEnabled(false);
        setTitle("SmartStock Login - signing in...");
        SwingWorker<LanApiClient.LoginResult, Void> worker = new SwingWorker<>() {
            @Override
            protected LanApiClient.LoginResult doInBackground() throws Exception {
                return LanApiClient.loginWithCredentials(identifier, secret, locationId);
            }

            @Override
            protected void done() {
                Arrays.fill(secret, '\0');
                passwordField.setText("");
                setTitle("SmartStock Login");
                try {
                    LanApiClient.LoginResult result = get();
                    applySession(result);
                    openMainMenu();
                } catch (Exception | LinkageError ex) {
                    authenticationInProgress.set(false);
                    SessionManager.clearSessionState();
                    JOptionPane.showMessageDialog(Login.this,
                            "Login failed: " + rootCauseMessage(ex), "Login", JOptionPane.WARNING_MESSAGE);
                    setLoginControlsEnabled(true);
                }
            }
        };
        worker.execute();
    }

    private void attemptStoredSignIn() {
        if (!LanApiClient.isPaired() || !LanApiClient.hasEmployeeSession()) return;
        if (!authenticationInProgress.compareAndSet(false, true)) return;
        setLoginControlsEnabled(false);
        setTitle("SmartStock Login - restoring session...");
        SwingWorker<LanApiClient.LoginResult, Void> worker = new SwingWorker<>() {
            @Override
            protected LanApiClient.LoginResult doInBackground() throws Exception {
                return LanApiClient.refreshLoginSession();
            }

            @Override
            protected void done() {
                setTitle("SmartStock Login");
                try {
                    LanApiClient.LoginResult restored = get();
                    if (!restored.persistentLoginAllowed()) {
                        LanApiClient.clearEmployeeSession();
                        SupabaseSessionManager.clearPersistedSession();
                        SessionManager.clearSessionState();
                        authenticationInProgress.set(false);
                        setLoginControlsEnabled(true);
                        return;
                    }
                    applySession(restored);
                    openMainMenu();
                } catch (Exception ex) {
                    authenticationInProgress.set(false);
                    SessionManager.clearSessionState();
                    if (ex.getCause() instanceof LanApiClient.LanApiException apiFailure
                            && !apiFailure.retryable()) {
                        LanApiClient.clearEmployeeSession();
                    }
                    setLoginControlsEnabled(true);
                }
            }
        };
        worker.execute();
    }

    private void applySession(LanApiClient.LoginResult result) {
        LanApiClient.User user = result.user();
        SessionManager.setCurrentUserId(user.userId());
        SessionManager.setCurrentUsername(user.username());
        SessionManager.setCurrentUserDisplayName(
                user.fullName() == null || user.fullName().isBlank() ? user.username() : user.fullName());
        SessionManager.setCurrentRole(user.role());
        SessionManager.setCurrentLocationId(user.locationId());
        SessionManager.setCurrentLocationName(user.locationName());
        SessionManager.setCurrentLocationTimezone(user.locationTimezone());
        SessionManager.setCurrentDeviceId(result.deviceId());
        SessionManager.setCurrentPermissions(result.permissions());
        AutoLogoutManager.start(result);
        if (result.supabaseAccessToken() != null && !result.supabaseAccessToken().isBlank()) {
            SessionManager.setCurrentAccessToken(result.supabaseAccessToken());
            SessionManager.setCurrentRefreshToken(result.supabaseRefreshToken());
            if (result.persistentLoginAllowed()) {
                // File-system permission checks can stall on some Windows registers.
                // Persistence is best-effort and must never block the authenticated UI transition.
                SupabaseSessionManager.savePersistedSessionAsync(user.userId(), user.locationId());
            } else {
                SupabaseSessionManager.clearPersistedSessionAsync();
            }
        } else {
            // A restored LAN employee session does not contain Supabase tokens. Keep the
            // separately persisted Storage session only when it belongs to this same user
            // and store; otherwise image uploads incorrectly fail as "Session expired."
            SupabaseSessionManager.PersistedSession persisted = SupabaseSessionManager.loadPersistedSession();
            if (persisted != null
                    && persisted.userId().equals(user.userId())
                    && persisted.locationId().equals(user.locationId())) {
                SupabaseSessionManager.setSession(persisted.accessToken(), persisted.refreshToken());
            } else {
                SupabaseSessionManager.clearSession();
            }
        }
    }

    private Integer requiredRegisterLocation() {
        DatabaseConfig config = DatabaseConfig.load();
        if (config.mode() != DatabaseMode.CLIENT && config.mode() != DatabaseMode.SERVER
                && config.mode() != DatabaseMode.REMOTE_ADMIN) {
            JOptionPane.showMessageDialog(this,
                    "SmartStock must be configured as a server, paired register, or Remote Admin device.",
                    "Setup Required", JOptionPane.WARNING_MESSAGE);
            return null;
        }
        if (config.locationId() == null) {
            JOptionPane.showMessageDialog(this,
                    "An administrator must assign this installation to a store.",
                    "Store Assignment Required", JOptionPane.WARNING_MESSAGE);
            return null;
        }
        return config.locationId();
    }

    private boolean isLikelyScannerBadgeEntry(String identifier) {
        String normalized = BadgeCredentialService.normalizeBadge(identifier);
        if (!BadgeCredentialService.looksLikeGeneratedBadge(normalized)) return true;
        long elapsed = badgeEntryLastKeyAtMillis - badgeEntryStartedAtMillis;
        return badgeEntryKeyCount >= Math.min(identifier.length(), normalized.length())
                && elapsed >= 0 && elapsed <= 1_200;
    }

    private void startNfcMonitor() {
        Thread existing = nfcMonitorThread;
        if (nfcMonitorRunning && existing != null && existing.isAlive()) return;
        nfcMonitorRunning = true;
        Thread monitor = new Thread(() -> {
            String lastUid = null;
            while (nfcMonitorRunning && isDisplayable()) {
                try {
                    // A reader can be briefly unavailable while the previous screen's
                    // monitor releases it during logout. Keep retrying instead of
                    // permanently disabling badge login for this Login window.
                    if (!PcscNfcService.hasReader()) {
                        Thread.sleep(750);
                        continue;
                    }
                    PcscNfcService.ReadResult card = PcscNfcService.read(Duration.ofSeconds(2));
                    if (!card.cardUid().equals(lastUid)) {
                        lastUid = card.cardUid();
                        SwingUtilities.invokeLater(() -> acceptNfcBadge(card));
                    }
                    Thread.sleep(750);
                } catch (PcscNfcService.NoCardPresentException ex) {
                    lastUid = null;
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    return;
                } catch (Exception ex) {
                    try { Thread.sleep(1_500); } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }
        }, "smartstock-nfc-login");
        monitor.setDaemon(true);
        nfcMonitorThread = monitor;
        monitor.start();
    }

    private void ensureNfcMonitorRunning() {
        if (!isDisplayable() || authenticationInProgress.get() || mainMenuOpened.get()) return;
        startNfcMonitor();
    }

    private void stopNfcMonitor() {
        nfcMonitorRunning = false;
        Thread monitor = nfcMonitorThread;
        nfcMonitorThread = null;
        if (monitor != null) monitor.interrupt();
    }

    private void acceptNfcBadge(PcscNfcService.ReadResult card) {
        if (!isDisplayable() || !usernameField.isEnabled()) return;
        String normalized = BadgeCredentialService.normalizeBadge(card.payload());
        if (!BadgeCredentialService.looksLikeGeneratedBadge(normalized)) {
            JOptionPane.showMessageDialog(this, "The tapped card does not contain a valid SmartStock badge ID.",
                    "NFC Badge", JOptionPane.WARNING_MESSAGE);
            return;
        }
        lastNfcBadgeIdentifier = normalized;
        usernameField.setText("NFC badge detected");
        usernameField.setEditable(false);
        passwordField.setText("");
        passwordField.requestFocusInWindow();
        setTitle("SmartStock Login - NFC badge read; enter employee PIN");
        Toolkit.getDefaultToolkit().beep();
        checkFirstBadgePinSetup(normalized);
    }

    private void checkFirstBadgePinSetup(String badgeId) {
        Integer locationId = requiredRegisterLocation();
        if (locationId == null || !LanApiClient.isPaired()) return;
        SwingWorker<LanApiClient.BadgeStatus, Void> worker = new SwingWorker<>() {
            @Override protected LanApiClient.BadgeStatus doInBackground() throws Exception {
                return LanApiClient.badgeStatus(badgeId, locationId);
            }

            @Override protected void done() {
                if (!isDisplayable()) return;
                try {
                    LanApiClient.BadgeStatus status = get();
                    if (!status.pinRequired()) {
                        loginUser();
                    } else if (!status.pinConfigured()) {
                        showFirstBadgePinSetup(badgeId, locationId);
                    } else {
                        passwordField.requestFocusInWindow();
                    }
                } catch (Exception ex) {
                    // Normal PIN login remains available if this optional preflight is temporarily unavailable.
                    passwordField.requestFocusInWindow();
                }
            }
        };
        worker.execute();
    }

    private void showFirstBadgePinSetup(String badgeId, int locationId) {
        JPasswordField accountPasswordField = new JPasswordField();
        JPasswordField pinField = new JPasswordField();
        JPasswordField confirmPinField = new JPasswordField();
        JPanel panel = new JPanel(new GridLayout(0, 1, 4, 4));
        panel.add(new JLabel("This is the first badge login for this employee."));
        panel.add(new JLabel("Verify the normal account password, then create a 4–8 digit employee PIN."));
        panel.add(new JLabel("Account Password:"));
        panel.add(accountPasswordField);
        panel.add(new JLabel("New Employee PIN:"));
        panel.add(pinField);
        panel.add(new JLabel("Confirm Employee PIN:"));
        panel.add(confirmPinField);
        int choice = JOptionPane.showConfirmDialog(this, panel, "Create Employee PIN",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (choice != JOptionPane.OK_OPTION) {
            passwordField.requestFocusInWindow();
            return;
        }
        char[] accountPassword = accountPasswordField.getPassword();
        char[] pin = pinField.getPassword();
        char[] confirm = confirmPinField.getPassword();
        if (accountPassword.length == 0 || !EmployeePinService.validPin(pin) || !Arrays.equals(pin, confirm)) {
            Arrays.fill(accountPassword, '\0');
            Arrays.fill(pin, '\0');
            Arrays.fill(confirm, '\0');
            JOptionPane.showMessageDialog(this,
                    "Enter the account password and use matching 4–8 digit PINs.",
                    "Create Employee PIN", JOptionPane.WARNING_MESSAGE);
            showFirstBadgePinSetup(badgeId, locationId);
            return;
        }
        Arrays.fill(confirm, '\0');
        if (!authenticationInProgress.compareAndSet(false, true)) {
            Arrays.fill(accountPassword, '\0');
            Arrays.fill(pin, '\0');
            return;
        }
        setLoginControlsEnabled(false);
        setTitle("SmartStock Login - creating employee PIN...");
        SwingWorker<LanApiClient.LoginResult, Void> worker = new SwingWorker<>() {
            @Override protected LanApiClient.LoginResult doInBackground() throws Exception {
                return LanApiClient.setupBadgePin(badgeId, accountPassword, pin, locationId);
            }

            @Override protected void done() {
                Arrays.fill(accountPassword, '\0');
                Arrays.fill(pin, '\0');
                try {
                    LanApiClient.LoginResult result = get();
                    applySession(result);
                    JOptionPane.showMessageDialog(Login.this,
                            "Employee PIN created. Future badge taps will use this PIN.",
                            "Employee PIN", JOptionPane.INFORMATION_MESSAGE);
                    openMainMenu();
                } catch (Exception ex) {
                    authenticationInProgress.set(false);
                    setTitle("SmartStock Login - NFC badge read; enter employee PIN");
                    setLoginControlsEnabled(true);
                    usernameField.setEditable(false);
                    JOptionPane.showMessageDialog(Login.this,
                            "Employee PIN could not be created: " + rootCauseMessage(ex),
                            "Create Employee PIN", JOptionPane.WARNING_MESSAGE);
                    passwordField.requestFocusInWindow();
                }
            }
        };
        worker.execute();
    }

    private void setLoginControlsEnabled(boolean enabled) {
        usernameField.setEnabled(enabled);
        passwordField.setEnabled(enabled);
        loginButton.setEnabled(enabled);
        clearButton.setEnabled(enabled);
        backButton.setEnabled(enabled);
    }

    private void returnToWelcome() {
        if (authenticationInProgress.get()) return;
        stopNfcMonitor();
        passwordField.setText("");
        lastNfcBadgeIdentifier = null;
        NavigationManager.returnToWelcomeFromLogin(this);
    }

    private void clearFields() {
        usernameField.setText("");
        usernameField.setEditable(true);
        passwordField.setText("");
        badgeEntryStartedAtMillis = 0;
        badgeEntryLastKeyAtMillis = 0;
        badgeEntryKeyCount = 0;
        lastNfcBadgeIdentifier = null;
        LanApiClient.clearEmployeeSession();
        SessionManager.clearSessionState();
        SupabaseSessionManager.clearSession();
        usernameField.requestFocusInWindow();
    }

    private void openMainMenu() {
        if (!mainMenuOpened.compareAndSet(false, true)) {
            return;
        }
        stopNfcMonitor();
        NavigationManager.showMainMenuAfterLogin(this);
    }

    private static String rootCauseMessage(Throwable exception) {
        Throwable cause = exception;
        while (cause.getCause() != null) cause = cause.getCause();
        return cause.getMessage() == null || cause.getMessage().isBlank()
                ? cause.getClass().getSimpleName() : cause.getMessage();
    }
}
