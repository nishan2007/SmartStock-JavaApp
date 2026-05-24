package services;

import data.DB;

import javax.swing.*;
import java.awt.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ManagerApprovalService {
    private static final String SUPABASE_URL = getConfig("SUPABASE_URL", "https://wbffhygkttoaaodjcvuh.supabase.co");
    private static final String SUPABASE_PUBLISHABLE_KEY = getConfig("SUPABASE_PUBLISHABLE_KEY", "sb_publishable_A_Z2rTrylkxY9JIRCM1pRQ_Rf56Lqja");
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    private ManagerApprovalService() {
    }

    public static ApprovalResult requestApproval(Component parent, String requiredPermission, String actionLabel, String reasonPrompt) {
        JTextField loginField = new JTextField();
        JPasswordField passwordField = new JPasswordField();
        JTextArea reasonArea = new JTextArea(3, 28);
        reasonArea.setLineWrap(true);
        reasonArea.setWrapStyleWord(true);

        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1;

        gbc.gridx = 0;
        gbc.gridy = 0;
        panel.add(new JLabel("Manager Username / Email / Badge ID:"), gbc);
        gbc.gridy = 1;
        panel.add(loginField, gbc);
        gbc.gridy = 2;
        panel.add(new JLabel("Manager Password:"), gbc);
        gbc.gridy = 3;
        panel.add(passwordField, gbc);
        gbc.gridy = 4;
        panel.add(new JLabel(reasonPrompt), gbc);
        gbc.gridy = 5;
        panel.add(new JScrollPane(reasonArea), gbc);

        int result = JOptionPane.showConfirmDialog(
                parent,
                panel,
                "Manager Approval Required - " + actionLabel,
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.WARNING_MESSAGE
        );
        if (result != JOptionPane.OK_OPTION) {
            return null;
        }

        String loginIdentifier = loginField.getText() == null ? "" : loginField.getText().trim();
        String password = new String(passwordField.getPassword());
        String reason = reasonArea.getText() == null ? "" : reasonArea.getText().trim();
        if (loginIdentifier.isBlank()) {
            throw new IllegalStateException("Manager login is required.");
        }
        if (password == null || password.isBlank()) {
            throw new IllegalStateException("Manager password is required.");
        }
        if (reason.isBlank()) {
            throw new IllegalStateException("Override reason is required.");
        }

        ManagerIdentity identity = loadManagerIdentity(loginIdentifier);
        if (!identity.active()) {
            throw new IllegalStateException("Manager account is inactive.");
        }
        if (identity.email() == null || identity.email().isBlank()) {
            throw new IllegalStateException("Manager account is missing an email for credential verification.");
        }
        if (!authenticateWithSupabase(identity.email(), password)) {
            throw new IllegalStateException("Invalid manager credentials.");
        }
        if (!hasPermission(identity.userId(), requiredPermission)) {
            throw new IllegalStateException("Manager does not have required permission: " + requiredPermission + ".");
        }

        return new ApprovalResult(identity.userId(), identity.displayName(), reason);
    }

    private static ManagerIdentity loadManagerIdentity(String loginIdentifier) {
        String sql = """
                SELECT u.user_id,
                       COALESCE(NULLIF(TRIM(u.full_name), ''), u.username, 'Unknown') AS display_name,
                       COALESCE(u.email, '') AS email,
                       COALESCE(u.is_active, FALSE) AS is_active
                FROM users u
                WHERE LOWER(u.username) = LOWER(?)
                   OR LOWER(u.email) = LOWER(?)
                   OR LOWER(u.badge_id) = LOWER(?)
                LIMIT 1
                """;
        try (Connection conn = DB.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, loginIdentifier);
            ps.setString(2, loginIdentifier);
            ps.setString(3, loginIdentifier);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new IllegalStateException("Manager account not found.");
                }
                return new ManagerIdentity(
                        rs.getInt("user_id"),
                        rs.getString("display_name"),
                        rs.getString("email"),
                        rs.getBoolean("is_active")
                );
            }
        } catch (Exception ex) {
            if (ex instanceof IllegalStateException) {
                throw (IllegalStateException) ex;
            }
            throw new IllegalStateException("Failed to load manager account: " + ex.getMessage(), ex);
        }
    }

    private static boolean hasPermission(int userId, String permissionKey) {
        String sql = """
                SELECT 1
                FROM users u
                JOIN roles r ON r.role_id = u.role_id
                JOIN role_permissions rp ON rp.role_id = r.role_id
                JOIN permissions p ON p.permission_id = rp.permission_id
                WHERE u.user_id = ?
                  AND UPPER(p.permission_key) = UPPER(?)
                LIMIT 1
                """;
        try (Connection conn = DB.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setString(2, permissionKey);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to verify manager permission: " + ex.getMessage(), ex);
        }
    }

    private static boolean authenticateWithSupabase(String email, String password) {
        if (SUPABASE_PUBLISHABLE_KEY == null || SUPABASE_PUBLISHABLE_KEY.isBlank()) {
            throw new IllegalStateException("Supabase publishable key is not configured.");
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

            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                String accessToken = extractJsonString(response.body(), "access_token");
                return accessToken != null && !accessToken.isBlank();
            }
            return false;
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to verify manager credentials: " + ex.getMessage(), ex);
        }
    }

    private static String extractJsonString(String json, String key) {
        if (json == null || key == null) {
            return null;
        }
        String pattern = "\""+ Pattern.quote(key) + "\"\\s*:\\s*\"((?:\\\\.|[^\\\\\"])*)\"";
        Matcher matcher = Pattern.compile(pattern).matcher(json);
        if (!matcher.find()) {
            return null;
        }
        return unescapeJson(matcher.group(1));
    }

    private static String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private static String unescapeJson(String value) {
        if (value == null) {
            return null;
        }
        return value
                .replace("\\\"", "\"")
                .replace("\\\\", "\\")
                .replace("\\n", "\n")
                .replace("\\r", "\r")
                .replace("\\t", "\t");
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

    private record ManagerIdentity(int userId, String displayName, String email, boolean active) {
    }

    public record ApprovalResult(int approvedByUserId, String approvedByName, String reason) {
    }
}
