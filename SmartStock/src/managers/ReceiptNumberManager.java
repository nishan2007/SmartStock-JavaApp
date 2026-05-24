package managers;

import data.DB;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class ReceiptNumberManager {
    private static final Path CONFIG_PATH = Path.of(System.getProperty("user.home"), ".smartstock", "device.properties");
    private static final int CODE_LENGTH = 4;
    private static final int RECEIPT_SEQUENCE_PADDING = 6;

    private ReceiptNumberManager() {
    }

    public static synchronized ReceiptNumber nextReceipt(int locationId) throws IOException {
        try (Connection conn = DB.getConnection()) {
            conn.setAutoCommit(false);
            try {
                String storeCode = resolveStoreCode(conn, locationId);
                String deviceCode = resolveDeviceCode(conn);
                int sequence = nextStoreReceiptSequence(conn, locationId);
                String receiptNumber = formatReceiptNumber(storeCode, deviceCode, sequence);
                conn.commit();
                return new ReceiptNumber(receiptNumber, deviceCode, sequence);
            } catch (Exception ex) {
                conn.rollback();
                throw ex;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException ex) {
            throw new IOException("Unable to generate next receipt number.", ex);
        }
    }

    public static synchronized ReceiveNumber nextReceive(int locationId) throws IOException {
        try (Connection conn = DB.getConnection()) {
            conn.setAutoCommit(false);
            try {
                String storeCode = resolveStoreCode(conn, locationId);
                String deviceCode = resolveDeviceCode(conn);
                int sequence = nextStoreReceiptSequence(conn, locationId);
                String receiveId = formatReceiveId(storeCode, deviceCode, sequence);
                conn.commit();
                return new ReceiveNumber(receiveId, deviceCode, sequence);
            } catch (Exception ex) {
                conn.rollback();
                throw ex;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException ex) {
            throw new IOException("Unable to generate next receive number.", ex);
        }
    }

    public static synchronized DeviceReceiptSettings getDeviceReceiptSettings(int locationId) throws IOException {
        try (Connection conn = DB.getConnection()) {
            String storeCode = resolveStoreCode(conn, locationId);
            String deviceCode = resolveDeviceCode(conn);
            int nextSequence = currentStoreReceiptSequence(conn, locationId);
            String nextReceiptPreview = formatReceiptNumber(storeCode, deviceCode, nextSequence);
            String nextReceivePreview = formatReceiveId(storeCode, deviceCode, nextSequence);
            return new DeviceReceiptSettings(
                    CONFIG_PATH,
                    deviceCode,
                    storeCode,
                    nextSequence,
                    nextReceiptPreview,
                    nextSequence,
                    nextReceivePreview
            );
        } catch (SQLException ex) {
            throw new IOException("Unable to load receipt settings.", ex);
        }
    }

    public static synchronized String updateDeviceId(String deviceId) throws IOException {
        String sanitized = sanitizeCode(deviceId);
        if (sanitized.isBlank()) {
            throw new IllegalArgumentException("Device code cannot be blank.");
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
        String sql = """
                SELECT COALESCE(next_receipt_counter, 1) AS next_receipt_counter
                FROM company_customization
                WHERE location_id = ?
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, locationId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new SQLException("Missing company_customization row for location_id=" + locationId);
                }
                return parsePositiveInt(rs.getString("next_receipt_counter"), 1);
            }
        }
    }

    private static int nextStoreReceiptSequence(Connection conn, int locationId) throws SQLException {
        String sql = """
                WITH store_counter AS (
                    SELECT GREATEST(COALESCE(next_receipt_counter, 1), 1) AS counter
                    FROM company_customization
                    WHERE location_id = ?
                    FOR UPDATE
                ),
                max_sale_sequence AS (
                    SELECT COALESCE(
                               MAX(
                                   CASE
                                       WHEN COALESCE(receipt_number, '') ~ '^[0-9]{4}-[0-9]{4}-[0-9]{6}$'
                                       THEN RIGHT(receipt_number, 6)::INT
                                       ELSE NULL
                                   END
                               ),
                               0
                           ) AS max_sequence
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
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("sequence");
                }
            }
        }
        throw new SQLException("Unable to advance receipt sequence for store.");
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

    private static String resolveDeviceCode(Connection conn) throws SQLException {
        String currentDeviceId = SessionManager.getCurrentDeviceId();
        if (currentDeviceId == null || currentDeviceId.isBlank()) {
            throw new SQLException("No active device session found for receipt numbering.");
        }
        String sql = "SELECT COALESCE(receipt_device_code, '') AS receipt_device_code FROM devices WHERE device_id = ?::uuid";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, currentDeviceId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new SQLException("Device not found for device_id=" + currentDeviceId);
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
