import javax.swing.*;
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

    public static Integer currentUserId;
    public static String currentUsername;
    public static String currentRole;
    public static Integer currentLocationId;
    public static String currentLocationName;

    private static final String SUPABASE_URL = "https://wbffhygkttoaaodjcvuh.supabase.co";
    private static final String SUPABASE_ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6IndiZmZoeWdrdHRvYWFvZGpjdnVoIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NzU2NjQ5NTAsImV4cCI6MjA5MTI0MDk1MH0.i2RB7aPCgpH3LtMCcCofbhgkrAKfdYRjlhXVjxi_zSc";

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
                SELECT l.location_id, l.name
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
                                    storesRs.getString("name")
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
                    selectedLocation = (LocationOption) JOptionPane.showInputDialog(
                            this,
                            "Select store:",
                            "Store Selection",
                            JOptionPane.QUESTION_MESSAGE,
                            null,
                            locations.toArray(),
                            locations.get(0)
                    );

                    if (selectedLocation == null) {
                        return;
                    }
                }

                currentUserId = userId;
                currentUsername = foundUsername;
                currentRole = role;
                currentLocationId = selectedLocation.locationId;
                currentLocationName = selectedLocation.locationName;

                JOptionPane.showMessageDialog(
                        this,
                        "Login successful.\nUser: " + currentUsername +
                                "\nRole: " + currentRole +
                                "\nStore: " + currentLocationName
                );

                dispose();
                new MainMenu().setVisible(true);
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
        if (SUPABASE_ANON_KEY.contains("REPLACE_WITH")) {
            return new SupabaseLoginResult(false, "Set the Supabase anon key in Login.java before using auth login.");
        }

        try {
            String body = "{"
                    + "\"email\":\"" + escapeJson(email) + "\","
                    + "\"password\":\"" + escapeJson(password) + "\""
                    + "}";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(SUPABASE_URL + "/auth/v1/token?grant_type=password"))
                    .timeout(Duration.ofSeconds(20))
                    .header("apikey", SUPABASE_ANON_KEY)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return new SupabaseLoginResult(true, null);
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

            return new SupabaseLoginResult(false, errorMessage);
        } catch (Exception ex) {
            return new SupabaseLoginResult(false, "Unable to reach Supabase Auth: " + ex.getMessage());
        }
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

        private SupabaseLoginResult(boolean success, String message) {
            this.success = success;
            this.message = message;
        }
    }

    private void clearFields() {
        usernameField.setText("");
        passwordField.setText("");
        usernameField.requestFocusInWindow();
    }

    private static class LocationOption {
        private final int locationId;
        private final String locationName;

        private LocationOption(int locationId, String locationName) {
            this.locationId = locationId;
            this.locationName = locationName;
        }

        @Override
        public String toString() {
            return locationName + " (ID: " + locationId + ")";
        }
    }
}
