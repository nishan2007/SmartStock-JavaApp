package services;

import com.google.gson.JsonObject;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** LOCATION_MANAGEMENT operations for server-owned Gmail credentials. */
final class LanGmailService {
    private LanGmailService() { }

    static Map<String, Object> clientStatus(Connection connection, int userId) throws Exception {
        require(connection, userId);
        GmailOAuthService.ClientImportResult status = GmailOAuthService.clientStatus();
        return map("configured", status.configured(), "clientIdHint", status.clientIdHint());
    }

    static Map<String, Object> importClient(Connection connection, JsonObject body, int userId, UUID deviceId) throws Exception {
        require(connection, userId);
        String json = required(body, "clientJson", "Select a Google Desktop OAuth client JSON file.");
        GmailOAuthService.ClientImportResult result = GmailOAuthService.importClientJson(json);
        audit(connection, "GMAIL_OAUTH_CLIENT_IMPORTED", deviceId, userId, "Google OAuth desktop client replaced; hint=" + result.clientIdHint());
        return map("configured", true, "clientIdHint", result.clientIdHint());
    }

    static Map<String, Object> status(Connection connection, JsonObject body, int userId) throws Exception {
        require(connection, userId);
        GmailOAuthService.ConnectionStatus status = GmailOAuthService.connectionStatus(required(body, "senderEmail", "Sender Gmail is required."));
        return statusMap(status);
    }

    static Map<String, Object> begin(Connection connection, JsonObject body, int userId) throws Exception {
        require(connection, userId);
        GmailOAuthService.AuthorizationStart start = GmailOAuthService.beginAuthorization(
                required(body, "senderEmail", "Sender Gmail is required."),
                required(body, "redirectUri", "The local OAuth callback address is required."));
        return map("state", start.state(), "authorizationUrl", start.authorizationUrl(), "expiresAt", start.expiresAt());
    }

    static Map<String, Object> complete(Connection connection, JsonObject body, int userId, UUID deviceId) throws Exception {
        require(connection, userId);
        GmailOAuthService.ConnectionStatus status = GmailOAuthService.completeAuthorization(
                required(body, "state", "OAuth state is required."), required(body, "code", "OAuth code is required."));
        int requeued = ServerEmailOutboxService.requeueAuthorizationFailures(status.senderEmail());
        audit(connection, "GMAIL_SENDER_CONNECTED", deviceId, userId,
                "Gmail sender connected: " + status.senderEmail() + "; requeued=" + requeued);
        Map<String, Object> result = statusMap(status);
        result.put("requeued", requeued);
        return result;
    }

    static Map<String, Object> disconnect(Connection connection, JsonObject body, int userId, UUID deviceId) throws Exception {
        require(connection, userId);
        String sender = GmailOAuthService.normalizeEmail(required(body, "senderEmail", "Sender Gmail is required."));
        GmailOAuthService.disconnect(sender);
        audit(connection, "GMAIL_SENDER_DISCONNECTED", deviceId, userId, "Gmail sender disconnected: " + sender);
        return statusMap(GmailOAuthService.connectionStatus(sender));
    }

    static Map<String, Object> sendTest(Connection connection, JsonObject body, int userId, UUID deviceId) throws Exception {
        require(connection, userId);
        int locationId = integer(body, "locationId", "Select a saved location first.");
        String recipient = required(body, "recipientEmail", "Enter a test recipient email.");
        Sender sender = loadSender(connection, locationId);
        GmailOAuthService.SendResult result = GmailOAuthService.send(new GmailOAuthService.GmailMessage(
                sender.email(), sender.name(), recipient, null,
                "SmartStock Gmail Test - " + sender.locationName(),
                "SmartStock successfully connected this Gmail sender for " + sender.locationName() + ".",
                "<p>SmartStock successfully connected this Gmail sender for <strong>" + escapeHtml(sender.locationName()) + "</strong>.</p>",
                null, null, null));
        try (PreparedStatement ps = connection.prepareStatement("""
                UPDATE locations SET email_connected_at=COALESCE(email_connected_at,CURRENT_TIMESTAMP),
                  email_last_tested_at=CURRENT_TIMESTAMP WHERE location_id=?
                """)) {
            ps.setInt(1, locationId);
            ps.executeUpdate();
        }
        audit(connection, "GMAIL_TEST_SENT", deviceId, userId,
                "Gmail test sent; sender=" + result.senderEmail() + "; recipient=" + result.recipientEmail() + "; messageId=" + result.messageId());
        return map("senderEmail", result.senderEmail(), "recipientEmail", result.recipientEmail(),
                "messageId", result.messageId(), "category", "SENT");
    }

    private static Sender loadSender(Connection connection, int locationId) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement("""
                SELECT name,COALESCE(email_sender_address,''),COALESCE(email_sender_name,'')
                FROM locations WHERE location_id=?
                """)) {
            ps.setInt(1, locationId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw rule(404, "LOCATION_NOT_FOUND", "The selected location no longer exists.");
                String email = GmailOAuthService.normalizeEmail(rs.getString(2));
                if (email.isBlank()) throw rule(400, "SENDER_REQUIRED", "Save a sender Gmail address for this location first.");
                return new Sender(rs.getString(1), email, rs.getString(3));
            }
        }
    }

    private static void require(Connection connection, int userId) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement("""
                SELECT 1 FROM users u JOIN role_permissions rp ON rp.role_id=u.role_id
                JOIN permissions p ON p.permission_id=rp.permission_id
                WHERE u.user_id=? AND UPPER(p.permission_key)='LOCATION_MANAGEMENT' LIMIT 1
                """)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) { if (rs.next()) return; }
        }
        throw rule(403, "PERMISSION_DENIED", "You do not have permission to manage Gmail connections.");
    }

    private static void audit(Connection connection, String type, UUID deviceId, int userId, String details) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("""
                INSERT INTO security_audit_events(event_type,device_id,actor_user_id,details) VALUES(?,?,?,?)
                """)) {
            ps.setString(1, type); ps.setObject(2, deviceId); ps.setInt(3, userId); ps.setString(4, details); ps.executeUpdate();
        }
    }

    private static Map<String, Object> statusMap(GmailOAuthService.ConnectionStatus status) {
        return map("senderEmail", status.senderEmail(), "status", status.status(), "message", status.message());
    }
    private static String required(JsonObject body, String key, String message) throws RuleViolation {
        String value = body.has(key) && !body.get(key).isJsonNull() ? body.get(key).getAsString().trim() : "";
        if (value.isBlank()) throw rule(400, "VALIDATION_ERROR", message);
        return value;
    }
    private static int integer(JsonObject body, String key, String message) throws RuleViolation {
        try { int value = body.get(key).getAsInt(); if (value > 0) return value; } catch (Exception ignored) { }
        throw rule(400, "VALIDATION_ERROR", message);
    }
    private static String escapeHtml(String value) { return value == null ? "" : value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;"); }
    private static Map<String, Object> map(Object... values) { Map<String, Object> map = new LinkedHashMap<>(); for (int i=0;i<values.length;i+=2) map.put((String) values[i], values[i+1]); return map; }
    private static RuleViolation rule(int status, String code, String message) { return new RuleViolation(status, code, message); }
    private record Sender(String locationName, String email, String name) { }
    static final class RuleViolation extends Exception {
        private final int status; private final String code; private final String safeMessage;
        RuleViolation(int status, String code, String message) { super(message); this.status=status; this.code=code; this.safeMessage=message; }
        int status(){return status;} String code(){return code;} String safeMessage(){return safeMessage;}
    }
}
