package services;

import data.DB;
import managers.SessionManager;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public final class QuotationInvoiceNumberManager {
    private QuotationInvoiceNumberManager() {
    }

    public static synchronized String nextQuotationNumber(Connection conn, int locationId) throws SQLException {
        return nextDocumentNumber(conn, locationId, "next_quotation_counter", "Q");
    }

    public static synchronized String nextInvoiceNumber(Connection conn, int locationId) throws SQLException {
        return nextDocumentNumber(conn, locationId, "next_invoice_counter", "SO");
    }

    public static synchronized String nextDeliveryNumber(Connection conn, int locationId) throws SQLException {
        return nextDocumentNumber(conn, locationId, "next_invoice_delivery_counter", "DEL");
    }

    public static int defaultQuotationValidityDays(Connection conn, int locationId) throws SQLException {
        ensureCounterRow(conn, locationId);
        try (PreparedStatement ps = conn.prepareStatement("""
                SELECT COALESCE(quotation_default_valid_days, 30) AS days
                FROM company_customization
                WHERE location_id = ?
                """)) {
            ps.setInt(1, locationId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Math.max(rs.getInt("days"), 1) : 30;
            }
        }
    }

    public static String previewQuotationNumber(int locationId) throws SQLException, IOException {
        try (Connection conn = DB.getConnection()) {
            ensureCounterRow(conn, locationId);
            String storeCode = storeCode(conn, locationId);
            String deviceCode = deviceCode(locationId);
            int counter = currentCounter(conn, locationId, "next_quotation_counter");
            return format("Q", storeCode, deviceCode, counter);
        }
    }

    private static String nextDocumentNumber(Connection conn, int locationId, String counterColumn, String prefix) throws SQLException {
        ensureCounterRow(conn, locationId);
        String storeCode = storeCode(conn, locationId);
        String deviceCode = deviceCode(locationId);
        repairCounterFloor(conn, locationId, counterColumn, prefix, storeCode, deviceCode);
        String sql = """
                UPDATE company_customization
                SET %s = GREATEST(COALESCE(%s, 1), 1) + 1,
                    updated_at = NOW()
                WHERE location_id = ?
                RETURNING GREATEST(COALESCE(%s, 1), 1) - 1 AS sequence
                """.formatted(counterColumn, counterColumn, counterColumn);
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, locationId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return format(prefix, storeCode, deviceCode, Math.max(rs.getInt("sequence"), 1));
                }
            }
        }
        throw new SQLException("Unable to advance " + prefix + " counter.");
    }

    private static void repairCounterFloor(Connection conn, int locationId, String counterColumn,
                                           String prefix, String storeCode, String deviceCode) throws SQLException {
        DocumentTable table = documentTable(prefix);
        String regex = "^" + prefix + "-" + storeCode + "-" + deviceCode + "-([0-9]+)$";
        String like = prefix + "-" + storeCode + "-" + deviceCode + "-%";
        String maxSql = """
                SELECT COALESCE(MAX(SUBSTRING(%s FROM ?)::INTEGER), 0) + 1 AS next_counter
                FROM %s
                WHERE %s LIKE ?
                """.formatted(table.numberColumn(), table.tableName(), table.numberColumn());
        int nextCounter = 1;
        try (PreparedStatement ps = conn.prepareStatement(maxSql)) {
            ps.setString(1, regex);
            ps.setString(2, like);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    nextCounter = Math.max(rs.getInt("next_counter"), 1);
                }
            }
        }
        String updateSql = """
                UPDATE company_customization
                SET %s = GREATEST(COALESCE(%s, 1), ?)
                WHERE location_id = ?
                """.formatted(counterColumn, counterColumn);
        try (PreparedStatement ps = conn.prepareStatement(updateSql)) {
            ps.setInt(1, nextCounter);
            ps.setInt(2, locationId);
            ps.executeUpdate();
        }
    }

    private static DocumentTable documentTable(String prefix) throws SQLException {
        return switch (prefix) {
            case "Q" -> new DocumentTable("quotations", "quotation_number");
            case "SO" -> new DocumentTable("invoices", "invoice_number");
            case "DEL" -> new DocumentTable("invoice_delivery_events", "delivery_number");
            default -> throw new SQLException("Unsupported document prefix: " + prefix);
        };
    }

    private static int currentCounter(Connection conn, int locationId, String counterColumn) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT GREATEST(COALESCE(" + counterColumn + ", 1), 1) AS counter FROM company_customization WHERE location_id = ?")) {
            ps.setInt(1, locationId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt("counter") : 1;
            }
        }
    }

    private static void ensureCounterRow(Connection conn, int locationId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO company_customization (
                    location_id,
                    next_quotation_counter, next_invoice_counter, next_invoice_delivery_counter,
                    quotation_default_valid_days
                )
                SELECT ?, 1, 1, 1, 30
                WHERE EXISTS (SELECT 1 FROM locations WHERE location_id = ?)
                ON CONFLICT (location_id) DO NOTHING
                """)) {
            ps.setInt(1, locationId);
            ps.setInt(2, locationId);
            ps.executeUpdate();
        }
    }

    private static String storeCode(Connection conn, int locationId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT COALESCE(receipt_store_code, '') AS code FROM locations WHERE location_id = ?")) {
            ps.setInt(1, locationId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String code = sanitizeCode(rs.getString("code"));
                    return code.isBlank() ? "0001" : code;
                }
            }
        }
        return "0001";
    }

    private static String deviceCode(int locationId) {
        return sanitizeCode(SessionManager.getCurrentDeviceId());
    }

    private static String sanitizeCode(String value) {
        if (value == null) {
            return "0001";
        }
        String digits = value.replaceAll("\\D+", "");
        if (digits.isBlank()) {
            return "0001";
        }
        int number;
        try {
            number = Integer.parseInt(digits);
        } catch (NumberFormatException ex) {
            number = 1;
        }
        number = Math.max(1, Math.min(number, 9999));
        return String.format("%04d", number);
    }

    private static String format(String prefix, String storeCode, String deviceCode, int sequence) {
        return prefix + "-" + storeCode + "-" + deviceCode + "-" + String.format("%06d", sequence);
    }

    private record DocumentTable(String tableName, String numberColumn) {
    }
}
