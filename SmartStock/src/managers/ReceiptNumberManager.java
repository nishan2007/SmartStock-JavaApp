package managers;

import data.DB;
import utils.DeviceUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Properties;
import java.util.UUID;

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
                String deviceCode = resolveDeviceCode(conn, true);
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
        try (Connection conn = DB.getConnection()) {
            String activeDeviceId = resolveActiveDeviceId(conn);
            String sql = """
                    UPDATE devices
                    SET receipt_device_code = ?,
                        last_seen = CURRENT_TIMESTAMP
                    WHERE device_id = ?
                    """;
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, sanitized);
                ps.setObject(2, UUID.fromString(activeDeviceId));
                int updated = ps.executeUpdate();
                if (updated == 0) {
                    throw new SQLException("Device not found for device_id=" + activeDeviceId);
                }
            }
            SessionManager.setCurrentDeviceId(activeDeviceId);
            persistLocalDeviceSettings(activeDeviceId, sanitized);
            return resolveDeviceCode(conn);
        } catch (SQLException ex) {
            throw new IOException("Unable to save workstation ID.", ex);
        }
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
                    SELECT GREATEST(
                               COALESCE(MAX(receipt_sequence), 0),
                               COALESCE(MAX(
                                   CASE
                                       WHEN COALESCE(receipt_number, '') ~ '^[0-9]{4}-[0-9]{4}-[0-9]{6}$'
                                       THEN RIGHT(receipt_number, 6)::INT
                                       ELSE NULL
                                   END
                               ), 0)
                           ) AS max_sequence
                    FROM sales
                    WHERE location_id = ?
                )
                SELECT GREATEST(local_counter.counter, max_sale_sequence.max_sequence + 1) AS next_receipt_counter
                FROM local_counter, max_sale_sequence
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, locationId);
            ps.setInt(2, locationId);
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
                    SELECT GREATEST(
                               COALESCE(MAX(receipt_sequence), 0),
                               COALESCE(MAX(
                                   CASE
                                       WHEN COALESCE(receipt_number, '') ~ '^[0-9]{4}-[0-9]{4}-[0-9]{6}$'
                                       THEN RIGHT(receipt_number, 6)::INT
                                       ELSE NULL
                                   END
                               ), 0)
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

    private static void ensureReceiptCounterRow(Connection conn, int locationId) throws SQLException {
        try (PreparedStatement create = conn.prepareStatement("""
                CREATE TABLE IF NOT EXISTS company_customization (
                    customization_id SERIAL PRIMARY KEY,
                    location_id INTEGER NOT NULL REFERENCES locations(location_id) ON DELETE CASCADE,
                    receipt_header_line TEXT NOT NULL DEFAULT '',
                    receipt_footer_line TEXT NOT NULL DEFAULT 'Thank you',
                    show_logo BOOLEAN NOT NULL DEFAULT FALSE,
                    show_sale_id BOOLEAN NOT NULL DEFAULT TRUE,
                    show_device BOOLEAN NOT NULL DEFAULT TRUE,
                    show_customer BOOLEAN NOT NULL DEFAULT TRUE,
                    show_sku BOOLEAN NOT NULL DEFAULT TRUE,
                    show_item_discount BOOLEAN NOT NULL DEFAULT TRUE,
                    show_payment_status BOOLEAN NOT NULL DEFAULT TRUE,
                    vat_enabled BOOLEAN NOT NULL DEFAULT FALSE,
                    vat_use_department_rates BOOLEAN NOT NULL DEFAULT FALSE,
                    vat_fixed_rate_percent NUMERIC(6, 2) NOT NULL DEFAULT 0,
                    next_receipt_counter INTEGER NOT NULL DEFAULT 1,
                    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                    UNIQUE (location_id)
                )
                """)) {
            create.executeUpdate();
        }
        try (PreparedStatement alter = conn.prepareStatement("""
                ALTER TABLE company_customization
                ADD COLUMN IF NOT EXISTS next_receipt_counter INTEGER NOT NULL DEFAULT 1
                """)) {
            alter.executeUpdate();
        }
        try (PreparedStatement alter = conn.prepareStatement("""
                ALTER TABLE company_customization
                ADD COLUMN IF NOT EXISTS vat_enabled BOOLEAN NOT NULL DEFAULT FALSE
                """)) {
            alter.executeUpdate();
        }
        try (PreparedStatement alter = conn.prepareStatement("""
                ALTER TABLE company_customization
                ADD COLUMN IF NOT EXISTS vat_use_department_rates BOOLEAN NOT NULL DEFAULT FALSE
                """)) {
            alter.executeUpdate();
        }
        try (PreparedStatement alter = conn.prepareStatement("""
                ALTER TABLE company_customization
                ADD COLUMN IF NOT EXISTS vat_fixed_rate_percent NUMERIC(6, 2) NOT NULL DEFAULT 0
                """)) {
            alter.executeUpdate();
        }
        String insertSql = """
                WITH max_sale_sequence AS (
                    SELECT GREATEST(
                               COALESCE(MAX(receipt_sequence), 0),
                               COALESCE(MAX(
                                   CASE
                                       WHEN COALESCE(receipt_number, '') ~ '^[0-9]{4}-[0-9]{4}-[0-9]{6}$'
                                       THEN RIGHT(receipt_number, 6)::INT
                                       ELSE NULL
                                   END
                               ), 0)
                           ) AS max_sequence
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

    private static String resolveDeviceCode(Connection conn) throws SQLException {
        return resolveDeviceCode(conn, false);
    }

    private static String resolveDeviceCode(Connection conn, boolean requireSalesAllowed) throws SQLException {
        String currentDeviceId = resolveActiveDeviceId(conn, requireSalesAllowed);
        String sql = """
                SELECT COALESCE(receipt_device_code, '') AS receipt_device_code,
                       COALESCE(allow_sales, TRUE) AS allow_sales
                FROM devices
                WHERE device_id = ?::uuid
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, currentDeviceId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new SQLException("Device not found for device_id=" + currentDeviceId);
                }
                if (requireSalesAllowed && !rs.getBoolean("allow_sales")) {
                    throw new SQLException("This device is not allowed to make sales. Enable Allow Sales in Device Management.");
                }
                return requireCode(rs.getString("receipt_device_code"), "devices.receipt_device_code");
            }
        }
    }

    private static String resolveActiveDeviceId(Connection conn) throws SQLException {
        return resolveActiveDeviceId(conn, false);
    }

    private static String resolveActiveDeviceId(Connection conn, boolean requireSalesAllowed) throws SQLException {
        String currentDeviceId = SessionManager.getCurrentDeviceId();
        if (deviceExists(conn, currentDeviceId, requireSalesAllowed)) {
            return currentDeviceId.trim();
        }

        String installationId = DeviceUtils.collectDeviceInfo().getInstallationId();
        String sql = """
                SELECT device_id
                FROM devices
                WHERE installation_id = ?
                  AND is_blocked = FALSE
                  AND (? = FALSE OR COALESCE(allow_sales, TRUE) = TRUE)
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, installationId);
            ps.setBoolean(2, requireSalesAllowed);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String deviceId = rs.getString("device_id");
                    SessionManager.setCurrentDeviceId(deviceId);
                    return deviceId;
                }
            }
        }

        if (requireSalesAllowed) {
            throw new SQLException("This device is not allowed to make sales. Enable Allow Sales in Device Management.");
        }
        throw new SQLException("No active device record found for this workstation.");
    }

    private static boolean deviceExists(Connection conn, String deviceId) throws SQLException {
        return deviceExists(conn, deviceId, false);
    }

    private static boolean deviceExists(Connection conn, String deviceId, boolean requireSalesAllowed) throws SQLException {
        if (deviceId == null || deviceId.isBlank()) {
            return false;
        }
        try {
            UUID.fromString(deviceId.trim());
        } catch (IllegalArgumentException ex) {
            return false;
        }

        String sql = """
                SELECT 1
                FROM devices
                WHERE device_id = ?
                  AND is_blocked = FALSE
                  AND (? = FALSE OR COALESCE(allow_sales, TRUE) = TRUE)
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, UUID.fromString(deviceId.trim()));
            ps.setBoolean(2, requireSalesAllowed);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static void persistLocalDeviceSettings(String deviceId, String receiptDeviceCode) throws IOException {
        Files.createDirectories(CONFIG_PATH.getParent());
        Properties properties = new Properties();
        if (Files.exists(CONFIG_PATH)) {
            try (InputStream inputStream = Files.newInputStream(CONFIG_PATH)) {
                properties.load(inputStream);
            }
        }
        properties.setProperty("device_id", deviceId);
        properties.setProperty("workstation_id", receiptDeviceCode);
        properties.setProperty("receipt_device_code", receiptDeviceCode);
        try (OutputStream outputStream = Files.newOutputStream(CONFIG_PATH)) {
            properties.store(outputStream, "SmartStock workstation settings");
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
