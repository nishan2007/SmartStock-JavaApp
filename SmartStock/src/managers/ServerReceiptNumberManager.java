package managers;

import services.SchemaContractService;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

public class ServerReceiptNumberManager {
    private static final Path CONFIG_PATH = Path.of(System.getProperty("user.home"), ".smartstock", "device.properties");
    private static final int CODE_LENGTH = 4;
    private static final int RECEIPT_SEQUENCE_PADDING = 6;

    private ServerReceiptNumberManager() {
    }

    /** Server-service transaction variant; receipt allocation commits with the owning mutation. */
    public static ReceiptNumber nextReceipt(Connection conn, int locationId, UUID deviceId) throws SQLException {
        String storeCode = resolveStoreCode(conn, locationId);
        String deviceCode = resolveDeviceCode(conn, deviceId, true);
        int sequence = nextStoreReceiptSequence(conn, locationId);
        return new ReceiptNumber(formatReceiptNumber(storeCode, deviceCode, sequence), deviceCode, sequence);
    }

    /** Server-service transaction variant; receive allocation commits with the owning mutation. */
    public static ReceiveNumber nextReceive(Connection conn, int locationId, UUID deviceId) throws SQLException {
        String storeCode = resolveStoreCode(conn, locationId);
        String deviceCode = resolveDeviceCode(conn, deviceId, true);
        int sequence = nextStoreReceiptSequence(conn, locationId);
        return new ReceiveNumber(formatReceiveId(storeCode, deviceCode, sequence), deviceCode, sequence);
    }

    /** Allocates a permanent return receipt number in the owning refund transaction. */
    public static ReturnNumber nextReturn(Connection conn, int locationId, UUID deviceId) throws SQLException {
        String storeCode = resolveStoreCode(conn, locationId);
        String deviceCode = resolveDeviceCode(conn, deviceId, false);
        int sequence = nextStoreReceiptSequence(conn, locationId);
        return new ReturnNumber(formatReturnNumber(storeCode, deviceCode, sequence), deviceCode, sequence);
    }

    /** Server-service variant scoped to the authenticated register and store. */
    public static DeviceReceiptSettings getDeviceReceiptSettings(Connection conn, int locationId,
                                                                  UUID deviceId) throws SQLException {
        String storeCode = resolveStoreCode(conn, locationId);
        String deviceCode = resolveDeviceCode(conn, deviceId, false);
        int nextSequence = currentStoreReceiptSequence(conn, locationId);
        return new DeviceReceiptSettings(CONFIG_PATH, deviceCode, storeCode, nextSequence,
                formatReceiptNumber(storeCode, deviceCode, nextSequence), nextSequence,
                formatReceiveId(storeCode, deviceCode, nextSequence));
    }

    /** Server-service variant; the authenticated device identity is never client-selected. */
    public static String updateDeviceId(Connection conn, UUID activeDeviceId, String requestedCode) throws SQLException {
        String sanitized = sanitizeCode(requestedCode);
        if (sanitized.isBlank()) throw new IllegalArgumentException("Device code cannot be blank.");
        try (PreparedStatement ps = conn.prepareStatement("""
                UPDATE devices SET receipt_device_code=?,last_seen=CURRENT_TIMESTAMP
                WHERE device_id=? AND COALESCE(is_blocked,FALSE)=FALSE
                """)) {
            ps.setString(1, sanitized); ps.setObject(2, activeDeviceId);
            if (ps.executeUpdate() != 1) throw new SQLException("This register is no longer approved.");
        }
        return sanitized;
    }

    public static String previewSanitizedDeviceId(String deviceId) {
        return sanitizeCode(deviceId);
    }

    public static Path getConfigPath() {
        return CONFIG_PATH;
    }

    private static int currentStoreReceiptSequence(Connection conn, int locationId) throws SQLException {
        ensureReceiptCounterRow(conn, locationId);
        String sql = """
                WITH local_counter AS (
                    SELECT GREATEST(COALESCE(next_receipt_counter, 1), 1) AS counter
                    FROM company_customization
                    WHERE location_id = ?
                ),
                max_sale_sequence AS (
                    SELECT GREATEST(GREATEST(
                               COALESCE(MAX(receipt_sequence), 0),
                               COALESCE(MAX(
                                   CASE
                                       WHEN COALESCE(receipt_number, '') ~ '^[0-9]{4}-[0-9]{4}-[0-9]{6}$'
                                       THEN RIGHT(receipt_number, 6)::INT
                                       ELSE NULL
                                   END
                               ), 0)
                           ), COALESCE((SELECT MAX(receipt_sequence) FROM sale_returns WHERE location_id=?),0)) AS max_sequence
                    FROM sales
                    WHERE location_id = ?
                )
                SELECT GREATEST(local_counter.counter, max_sale_sequence.max_sequence + 1) AS next_receipt_counter
                FROM local_counter, max_sale_sequence
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, locationId);
            ps.setInt(2, locationId);
            ps.setInt(3, locationId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new SQLException("Missing company_customization row for location_id=" + locationId);
                }
                return parsePositiveInt(rs.getString("next_receipt_counter"), 1);
            }
        }
    }

    private static int nextStoreReceiptSequence(Connection conn, int locationId) throws SQLException {
        ensureReceiptCounterRow(conn, locationId);
        String sql = """
                WITH store_counter AS (
                    SELECT GREATEST(COALESCE(next_receipt_counter, 1), 1) AS counter
                    FROM company_customization
                    WHERE location_id = ?
                    FOR UPDATE
                ),
                max_sale_sequence AS (
                    SELECT GREATEST(GREATEST(
                               COALESCE(MAX(receipt_sequence), 0),
                               COALESCE(MAX(
                                   CASE
                                       WHEN COALESCE(receipt_number, '') ~ '^[0-9]{4}-[0-9]{4}-[0-9]{6}$'
                                       THEN RIGHT(receipt_number, 6)::INT
                                       ELSE NULL
                                   END
                               ), 0)
                           ), COALESCE((SELECT MAX(receipt_sequence) FROM sale_returns WHERE location_id=?),0)) AS max_sequence
                    FROM sales
                    WHERE location_id = ?
                ),
                next_sequence AS (
                    SELECT GREATEST(store_counter.counter, max_sale_sequence.max_sequence + 1) AS sequence
                    FROM store_counter, max_sale_sequence
                )
                UPDATE company_customization
                SET next_receipt_counter = (SELECT sequence + 1 FROM next_sequence),
                    updated_at = NOW()
                WHERE location_id = ?
                RETURNING (SELECT sequence FROM next_sequence) AS sequence
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, locationId);
            ps.setInt(2, locationId);
            ps.setInt(3, locationId);
            ps.setInt(4, locationId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("sequence");
                }
            }
        }
        throw new SQLException("Unable to advance receipt sequence for store.");
    }

    private static void ensureReceiptCounterRow(Connection conn, int locationId) throws SQLException {
        SchemaContractService.requireLocalReady(conn);
        String insertSql = """
                WITH max_sale_sequence AS (
                    SELECT GREATEST(GREATEST(
                               COALESCE(MAX(receipt_sequence), 0),
                               COALESCE(MAX(
                                   CASE
                                       WHEN COALESCE(receipt_number, '') ~ '^[0-9]{4}-[0-9]{4}-[0-9]{6}$'
                                       THEN RIGHT(receipt_number, 6)::INT
                                       ELSE NULL
                                   END
                               ), 0)
                           ), COALESCE((SELECT MAX(receipt_sequence) FROM sale_returns WHERE location_id=?),0)) AS max_sequence
                    FROM sales
                    WHERE location_id = ?
                )
                INSERT INTO company_customization (location_id, next_receipt_counter)
                VALUES (?, (SELECT GREATEST(max_sequence + 1, 1) FROM max_sale_sequence))
                ON CONFLICT (location_id) DO NOTHING
                """;
        try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
            ps.setInt(1, locationId);
            ps.setInt(2, locationId);
            ps.setInt(3, locationId);
            ps.executeUpdate();
        }
    }

    private static String resolveStoreCode(Connection conn, int locationId) throws SQLException {
        String sql = "SELECT COALESCE(receipt_store_code, '') AS receipt_store_code FROM locations WHERE location_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, locationId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new SQLException("Location not found for location_id=" + locationId);
                }
                return requireCode(rs.getString("receipt_store_code"), "location.receipt_store_code");
            }
        }
    }

    private static String resolveDeviceCode(Connection conn, UUID deviceId, boolean requireSalesAllowed) throws SQLException {
        String sql = """
                SELECT COALESCE(receipt_device_code, '') AS receipt_device_code,
                       COALESCE(allow_sales, TRUE) AS allow_sales
                FROM devices
                WHERE device_id = ? AND COALESCE(is_blocked, FALSE) = FALSE
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, deviceId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new SQLException("This register is no longer approved.");
                if (requireSalesAllowed && !rs.getBoolean("allow_sales")) {
                    throw new SQLException("This device is not allowed to make sales. Enable Allow Sales in Device Management.");
                }
                return requireCode(rs.getString("receipt_device_code"), "devices.receipt_device_code");
            }
        }
    }

    private static int parsePositiveInt(String value, int fallback) {
        try {
            int parsed = Integer.parseInt(value);
            return parsed > 0 ? parsed : fallback;
        } catch (Exception ex) {
            return fallback;
        }
    }

    private static String formatReceiptNumber(String storeCode, String deviceId, int sequence) {
        return storeCode + "-" + deviceId + "-" + String.format("%0" + RECEIPT_SEQUENCE_PADDING + "d", sequence);
    }

    private static String formatReceiveId(String storeCode, String deviceId, int sequence) {
        return storeCode + "-" + deviceId + "-" + String.format("%0" + RECEIPT_SEQUENCE_PADDING + "d", sequence);
    }

    static String formatReturnNumber(String storeCode, String deviceId, int sequence) {
        return "RET-" + formatReceiptNumber(storeCode, deviceId, sequence);
    }

    private static String requireCode(String value, String sourceField) throws SQLException {
        String sanitized = sanitizeCode(value);
        if (sanitized.isBlank()) {
            throw new SQLException("Missing or invalid code in " + sourceField);
        }
        return sanitized;
    }

    private static String sanitizeCode(String value) {
        if (value == null) return "";
        String digits = value.replaceAll("\\D+", "");
        if (digits.isBlank()) return "";
        int number = parsePositiveInt(digits, 1);
        if (number < 1) number = 1;
        if (number > 9999) number = 9999;
        return String.format("%0" + CODE_LENGTH + "d", number);
    }

    public record ReceiptNumber(String receiptNumber, String deviceId, int sequence) {
    }

    public record ReceiveNumber(String receiveId, String deviceId, int sequence) {
    }

    public record ReturnNumber(String returnReceiptNumber, String deviceId, int sequence) {
    }

    public record DeviceReceiptSettings(
            Path configPath,
            String deviceId,
            String storeCode,
            int nextSequence,
            String nextReceiptPreview,
            int nextReceiveSequence,
            String nextReceivePreview
    ) {
    }
}
