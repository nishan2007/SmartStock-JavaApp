package ui.screens;

import data.DatabaseConfig;
import data.DatabaseMode;
import managers.SessionManager;
import managers.SupabaseSessionManager;
import services.AppUpdateService;
import services.BadgeCredentialService;
import services.LanApiClient;
import ui.helpers.ThemeManager;
import ui.helpers.WindowHelper;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.Arrays;

/** Register login. Authentication and authorization are owned by the LAN service. */
public class Login extends JFrame {
    private JTextField usernameField;
    private JPasswordField passwordField;
    private JButton loginButton;
    private JButton clearButton;
    private long badgeEntryStartedAtMillis;
    private long badgeEntryLastKeyAtMillis;
    private int badgeEntryKeyCount;

    public Login() {
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

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        loginButton = new JButton("Login");
        clearButton = new JButton("Clear");
        buttonPanel.add(clearButton);
        buttonPanel.add(loginButton);
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
        loginButton.addActionListener(event -> loginUser());
        clearButton.addActionListener(event -> clearFields());
        getRootPane().setDefaultButton(loginButton);
        ThemeManager.applyToWindow(this);
        setVisible(true);
        SwingUtilities.invokeLater(this::attemptStoredSignIn);
    }

    private void loginUser() {
        String identifier = usernameField.getText().trim();
        char[] secret = passwordField.getPassword();
        if (identifier.isBlank() || secret.length == 0) {
            Arrays.fill(secret, '\0');
            JOptionPane.showMessageDialog(this,
                    "Enter username/email and password, or scan a badge and enter the employee PIN.");
            return;
        }
        if (BadgeCredentialService.looksLikeGeneratedBadge(BadgeCredentialService.normalizeBadge(identifier))
                && !isLikelyScannerBadgeEntry(identifier)) {
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
                    JOptionPane.showMessageDialog(Login.this,
                            "Login successful.\nUser: " + SessionManager.getCurrentUserDisplayName()
                                    + "\nRole: " + SessionManager.getCurrentRole()
                                    + "\nStore: " + SessionManager.getCurrentLocationName());
                    openMainMenu();
                } catch (Exception ex) {
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
                        setLoginControlsEnabled(true);
                        return;
                    }
                    applySession(restored);
                    openMainMenu();
                } catch (Exception ex) {
                    LanApiClient.clearEmployeeSession();
                    SessionManager.clearSessionState();
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
        if (result.supabaseAccessToken() != null && !result.supabaseAccessToken().isBlank()) {
            SessionManager.setCurrentAccessToken(result.supabaseAccessToken());
            SessionManager.setCurrentRefreshToken(result.supabaseRefreshToken());
            SupabaseSessionManager.setSession(result.supabaseAccessToken(), result.supabaseRefreshToken());
            if (result.persistentLoginAllowed()) {
                SupabaseSessionManager.savePersistedSession(user.userId(), user.locationId());
            } else {
                SupabaseSessionManager.clearPersistedSession();
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
        if (config.mode() != DatabaseMode.CLIENT && config.mode() != DatabaseMode.SERVER) {
            JOptionPane.showMessageDialog(this,
                    "SmartStock must be configured as a server or paired register.",
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

    private void setLoginControlsEnabled(boolean enabled) {
        usernameField.setEnabled(enabled);
        passwordField.setEnabled(enabled);
        loginButton.setEnabled(enabled);
        clearButton.setEnabled(enabled);
    }

    private void clearFields() {
        usernameField.setText("");
        passwordField.setText("");
        badgeEntryStartedAtMillis = 0;
        badgeEntryLastKeyAtMillis = 0;
        badgeEntryKeyCount = 0;
        LanApiClient.clearEmployeeSession();
        SessionManager.clearSessionState();
        SupabaseSessionManager.clearSession();
        usernameField.requestFocusInWindow();
    }

    private void openMainMenu() {
        MainMenu mainMenu = new MainMenu();
        WindowHelper.showPosWindow(mainMenu, this);
        AppUpdateService.checkForUpdatesAsync(mainMenu, false);
        dispose();
    }

    private static String rootCauseMessage(Exception exception) {
        Throwable cause = exception;
        while (cause.getCause() != null) cause = cause.getCause();
        return cause.getMessage() == null || cause.getMessage().isBlank()
                ? cause.getClass().getSimpleName() : cause.getMessage();
    }
}
