package ui.screens;

import managers.SupabaseSessionManager;
import services.DeviceService;
import managers.SessionManager;
import data.DB;
import ui.helpers.ThemeManager;
import ui.helpers.WindowHelper;

import javax.swing.*;
import javax.swing.plaf.basic.BasicButtonUI;
import javax.swing.plaf.basic.BasicComboBoxUI;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Login extends JFrame {

    private static final String SUPABASE_URL = getConfig("SUPABASE_URL", "https://wbffhygkttoaaodjcvuh.supabase.co");
    private static final String SUPABASE_PUBLISHABLE_KEY = getConfig("SUPABASE_PUBLISHABLE_KEY", "sb_publishable_A_Z2rTrylkxY9JIRCM1pRQ_Rf56Lqja");

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    private JTextField usernameField;
    private JPasswordField passwordField;
    private JButton loginButton;
    private JButton clearButton;

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

        formPanel.add(new JLabel("Username or Email:"));
        formPanel.add(usernameField);
        formPanel.add(new JLabel("Password:"));
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

        loginButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                loginUser();
            }
        });

        clearButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                clearFields();
            }
        });

        getRootPane().setDefaultButton(loginButton);
        ThemeManager.applyToWindow(this);
        setVisible(true);
    }

    private void loginUser() {
        String usernameOrEmail = usernameField.getText().trim();
        String password = new String(passwordField.getPassword()).trim();

        if (usernameOrEmail.isEmpty() || password.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Enter username/email and password.");
            return;
        }

        String userSql = """
                SELECT u.user_id,
                       u.username,
                       u.full_name,
                       u.email,
                       u.auth_user_id,
                       u.is_active,
                       COALESCE(r.role_name, 'USER') AS role
                FROM users u
                LEFT JOIN roles r ON u.role_id = r.role_id
                WHERE LOWER(u.username) = LOWER(?)
                   OR LOWER(u.email) = LOWER(?)
                """;
        String storesSql = """
                SELECT l.location_id,
                       l.name,
                       COALESCE(l.timezone, '') AS timezone
                FROM user_locations ul
                JOIN locations l ON ul.location_id = l.location_id
                WHERE ul.user_id = ?
                ORDER BY l.name
                """;

        try (Connection conn = DB.getConnection();
             PreparedStatement userPs = conn.prepareStatement(userSql)) {

            userPs.setString(1, usernameOrEmail);
            userPs.setString(2, usernameOrEmail);

            try (ResultSet userRs = userPs.executeQuery()) {
                if (!userRs.next()) {
                    JOptionPane.showMessageDialog(this, "User not found.");
                    return;
                }

                int userId = userRs.getInt("user_id");
                String foundUsername = userRs.getString("username");
                String fullName = userRs.getString("full_name");
                String email = userRs.getString("email");
                String authUserId = userRs.getString("auth_user_id");
                boolean isActive = userRs.getBoolean("is_active");
                String role = userRs.getString("role");

                if (!isActive) {
                    JOptionPane.showMessageDialog(this, "This employee account is inactive.");
                    return;
                }

                if (email == null || email.trim().isEmpty()) {
                    JOptionPane.showMessageDialog(this, "This employee does not have an email address linked for login.");
                    return;
                }

                if (authUserId == null || authUserId.trim().isEmpty()) {
                    JOptionPane.showMessageDialog(this, "This employee does not have a linked auth account yet.");
                    return;
                }

                SupabaseLoginResult authResult = authenticateWithSupabase(email.trim(), password);
                if (!authResult.success) {
                    JOptionPane.showMessageDialog(this, authResult.message);
                    return;
                }

                List<LocationOption> locations = new ArrayList<>();

                try (PreparedStatement storesPs = conn.prepareStatement(storesSql)) {
                    storesPs.setInt(1, userId);

                    try (ResultSet storesRs = storesPs.executeQuery()) {
                        while (storesRs.next()) {
                            locations.add(new LocationOption(
                                    storesRs.getInt("location_id"),
                                    storesRs.getString("name"),
                                    storesRs.getString("timezone")
                            ));
                        }
                    }
                }

                if (locations.isEmpty()) {
                    JOptionPane.showMessageDialog(this, "This user has no assigned stores.");
                    return;
                }

                LocationOption selectedLocation;
                if (locations.size() == 1) {
                    selectedLocation = locations.get(0);
                } else {
                    selectedLocation = selectStore(locations);
                    if (selectedLocation == null) {
                        return;
                    }
                }

                SessionManager.setCurrentUserId(userId);
                SessionManager.setCurrentUsername(foundUsername);
                SessionManager.setCurrentUserDisplayName((fullName == null || fullName.isBlank()) ? foundUsername : fullName);
                SessionManager.setCurrentRole(role);
                SessionManager.setCurrentLocationId(selectedLocation.locationId);
                SessionManager.setCurrentLocationName(selectedLocation.locationName);
                SessionManager.setCurrentLocationTimezone(selectedLocation.timezone);
                SessionManager.setCurrentAccessToken(authResult.accessToken);
                SessionManager.setCurrentRefreshToken(authResult.refreshToken);
                SupabaseSessionManager.setSession(SessionManager.getCurrentAccessToken(), SessionManager.getCurrentRefreshToken());
                DeviceService.registerOrUpdateDevice(conn, SessionManager.getCurrentUserId(), SessionManager.getCurrentLocationId());

                JOptionPane.showMessageDialog(
                        this,
                        "Login successful.\nUser: " + SessionManager.getCurrentUserDisplayName() +
                                "\nRole: " + SessionManager.getCurrentRole() +
                                "\nStore: " + SessionManager.getCurrentLocationName()
                );

                MainMenu mainMenu = new MainMenu();
                WindowHelper.showPosWindow(mainMenu, this);
                dispose();
            }

        } catch (SQLException ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this, "Login failed: " + ex.getMessage());
        } catch (Exception ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this, "Login failed: " + ex.getMessage());
        }
    }

    private SupabaseLoginResult authenticateWithSupabase(String email, String password) {
        if (SUPABASE_PUBLISHABLE_KEY == null || SUPABASE_PUBLISHABLE_KEY.isBlank()) {
            return new SupabaseLoginResult(false, "Set the Supabase publishable key before using auth login.", null, null);
        }

        try {
            String body = "{"
                    + "\"email\":\"" + escapeJson(email) + "\","
                    + "\"password\":\"" + escapeJson(password) + "\""
                    + "}";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(SUPABASE_URL + "/auth/v1/token?grant_type=password"))
                    .timeout(Duration.ofSeconds(20))
                    .header("apikey", SUPABASE_PUBLISHABLE_KEY)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                String accessToken = extractJsonString(response.body(), "access_token");
                String refreshToken = extractJsonString(response.body(), "refresh_token");

                if (accessToken == null || accessToken.isBlank()) {
                    return new SupabaseLoginResult(false, "Supabase login succeeded but no access token was returned.", null, null);
                }

                return new SupabaseLoginResult(true, null, accessToken, refreshToken);
            }

            String errorMessage = extractJsonString(response.body(), "msg");
            if (errorMessage == null || errorMessage.isBlank()) {
                errorMessage = extractJsonString(response.body(), "error_description");
            }
            if (errorMessage == null || errorMessage.isBlank()) {
                errorMessage = extractJsonString(response.body(), "message");
            }
            if (errorMessage == null || errorMessage.isBlank()) {
                errorMessage = "Invalid username/email or password.";
            }

            return new SupabaseLoginResult(false, errorMessage, null, null);
        } catch (Exception ex) {
            return new SupabaseLoginResult(false, "Unable to reach Supabase Auth: " + ex.getMessage(), null, null);
        }
    }

    private LocationOption selectStore(List<LocationOption> locations) {
        boolean dark = ThemeManager.isDarkModeEnabled();
        Color background = dark ? new Color(18, 18, 18) : UIManager.getColor("Panel.background");
        Color surface = dark ? new Color(30, 30, 30) : Color.WHITE;
        Color field = dark ? new Color(24, 24, 24) : Color.WHITE;
        Color text = dark ? Color.WHITE : new Color(17, 24, 39);
        Color buttonColor = dark ? new Color(42, 42, 42) : UIManager.getColor("Button.background");
        Color buttonText = dark ? Color.WHITE : UIManager.getColor("Button.foreground");
        final LocationOption[] selectedLocation = new LocationOption[1];

        JComboBox<LocationOption> storeBox = new JComboBox<>(locations.toArray(new LocationOption[0]));
        storeBox.setSelectedIndex(0);
        storeBox.setEditable(dark);
        if (dark) {
            storeBox.setUI(new BasicComboBoxUI() {
                @Override
                protected JButton createArrowButton() {
                    JButton button = new JButton("▼");
                    button.setUI(new BasicButtonUI());
                    button.setBackground(field);
                    button.setForeground(text);
                    button.setOpaque(true);
                    button.setContentAreaFilled(true);
                    button.setBorder(BorderFactory.createMatteBorder(0, 1, 0, 0, new Color(75, 75, 75)));
                    return button;
                }
            });
            storeBox.setBackground(field);
            storeBox.setForeground(text);
            Component editorComponent = storeBox.getEditor().getEditorComponent();
            if (editorComponent instanceof JTextField editorField) {
                editorField.setEditable(false);
                editorField.setText(String.valueOf(storeBox.getSelectedItem()));
                editorField.setBackground(field);
                editorField.setForeground(text);
                editorField.setCaretColor(text);
                editorField.setSelectionColor(field);
                editorField.setSelectedTextColor(text);
                editorField.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));
                storeBox.addActionListener(e -> editorField.setText(String.valueOf(storeBox.getSelectedItem())));
            }
            storeBox.setRenderer(new DefaultListCellRenderer() {
                @Override
                public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                    JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                    label.setOpaque(true);
                    label.setBackground(isSelected ? new Color(88, 88, 88) : field);
                    label.setForeground(text);
                    return label;
                }
            });
        }

        JDialog dialog = new JDialog(this, "Store Selection", true);
        dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        JPanel root = new JPanel(new BorderLayout(14, 14));
        root.setBorder(BorderFactory.createEmptyBorder(16, 18, 16, 18));
        root.setBackground(background);

        JLabel promptLabel = new JLabel("Select store:");
        promptLabel.setForeground(text);

        JPanel fieldPanel = new JPanel(new BorderLayout(0, 8));
        fieldPanel.setBackground(background);
        fieldPanel.add(promptLabel, BorderLayout.NORTH);
        fieldPanel.add(storeBox, BorderLayout.CENTER);

        JLabel iconLabel = new JLabel(UIManager.getIcon("OptionPane.questionIcon"));
        JPanel centerPanel = new JPanel(new BorderLayout(16, 0));
        centerPanel.setBackground(surface);
        centerPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        centerPanel.add(iconLabel, BorderLayout.WEST);
        centerPanel.add(fieldPanel, BorderLayout.CENTER);

        JButton cancelButton = createStoreDialogButton("Cancel", buttonColor, buttonText);
        JButton okButton = createStoreDialogButton("OK", buttonColor, buttonText);
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 0));
        buttonPanel.setBackground(background);
        buttonPanel.add(cancelButton);
        buttonPanel.add(okButton);

        cancelButton.addActionListener(e -> dialog.dispose());
        okButton.addActionListener(e -> {
            selectedLocation[0] = (LocationOption) storeBox.getSelectedItem();
            dialog.dispose();
        });

        root.add(centerPanel, BorderLayout.CENTER);
        root.add(buttonPanel, BorderLayout.SOUTH);
        dialog.setContentPane(root);
        dialog.getRootPane().setDefaultButton(okButton);
        dialog.pack();
        dialog.setSize(Math.max(dialog.getWidth(), 320), dialog.getHeight());
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
        return selectedLocation[0];
    }

    private JButton createStoreDialogButton(String text, Color background, Color foreground) {
        JButton button = new JButton(text);
        button.setUI(new BasicButtonUI());
        button.setBackground(background);
        button.setForeground(foreground);
        button.setOpaque(true);
        button.setContentAreaFilled(true);
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(90, 90, 90)),
                BorderFactory.createEmptyBorder(6, 22, 6, 22)
        ));
        button.setFocusPainted(false);
        return button;
    }

    private String escapeJson(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }

    private String extractJsonString(String json, String key) {
        Pattern pattern = Pattern.compile("\\\"" + Pattern.quote(key) + "\\\"\\s*:\\s*\\\"((?:\\\\.|[^\\\"])*)\\\"");
        Matcher matcher = pattern.matcher(json);
        if (!matcher.find()) {
            return null;
        }

        return matcher.group(1)
                .replace("\\\"", "\"")
                .replace("\\n", "\n")
                .replace("\\r", "\r")
                .replace("\\\\", "\\");
    }


    private static class SupabaseLoginResult {
        private final boolean success;
        private final String message;
        private final String accessToken;
        private final String refreshToken;

        private SupabaseLoginResult(boolean success, String message, String accessToken, String refreshToken) {
            this.success = success;
            this.message = message;
            this.accessToken = accessToken;
            this.refreshToken = refreshToken;
        }
    }

    private void clearFields() {
        usernameField.setText("");
        passwordField.setText("");
        SessionManager.clearSessionState();
        SupabaseSessionManager.clearSession();
        usernameField.requestFocusInWindow();
    }

    private static class LocationOption {
        private final int locationId;
        private final String locationName;
        private final String timezone;

        private LocationOption(int locationId, String locationName, String timezone) {
            this.locationId = locationId;
            this.locationName = locationName;
            this.timezone = timezone;
        }

        @Override
        public String toString() {
            return locationName + " (ID: " + locationId + ")";
        }
    }

    private static String getConfig(String key, String fallback) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) {
            value = System.getProperty(key);
        }
        if (value == null || value.isBlank()) {
            value = fallback;
        }
        return value;
    }
}
