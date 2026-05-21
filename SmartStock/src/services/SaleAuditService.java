package services;

import managers.SessionManager;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;

public final class SaleAuditService {
    private SaleAuditService() {
    }

    public static void record(Connection conn,
                              Integer saleId,
                              Integer saleItemId,
                              Long returnId,
                              Long returnItemId,
                              Integer customerId,
                              Integer productId,
                              Integer locationId,
                              String actionType,
                              String actionScope,
                              String fieldName,
                              Object oldValue,
                              Object newValue,
                              BigDecimal amount,
                              Integer quantity,
                              String reason,
                              String note) throws SQLException {
        String sql = """
                INSERT INTO sale_audit_log (
                    sale_id, sale_item_id, return_id, return_item_id,
                    customer_id, product_id, location_id,
                    action_type, action_scope, field_name,
                    old_value, new_value, amount, quantity,
                    reason, note,
                    user_id, user_name, device_id, device_name
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            setNullableInteger(ps, 1, saleId);
            setNullableInteger(ps, 2, saleItemId);
            setNullableLong(ps, 3, returnId);
            setNullableLong(ps, 4, returnItemId);
            setNullableInteger(ps, 5, customerId);
            setNullableInteger(ps, 6, productId);
            setNullableInteger(ps, 7, locationId);
            ps.setString(8, actionType);
            ps.setString(9, actionScope);
            ps.setString(10, blankToNull(fieldName));
            ps.setString(11, valueText(oldValue));
            ps.setString(12, valueText(newValue));
            setNullableBigDecimal(ps, 13, amount);
            setNullableInteger(ps, 14, quantity);
            ps.setString(15, blankToNull(reason));
            ps.setString(16, blankToNull(note));
            setNullableInteger(ps, 17, SessionManager.getCurrentUserId());
            ps.setString(18, SessionManager.getCurrentUserDisplayName());
            ps.setString(19, blankToNull(DeviceContextService.currentDeviceId()));
            ps.setString(20, blankToNull(DeviceContextService.currentDeviceName()));
            ps.executeUpdate();
        }
    }

    public static void recordSale(Connection conn, int saleId, Integer customerId, int locationId,
                                  String actionType, BigDecimal amount, String note) throws SQLException {
        record(conn, saleId, null, null, null, customerId, null, locationId,
                actionType, "SALE", null, null, null, amount, null, null, note);
    }

    public static void recordLine(Connection conn, int saleId, int saleItemId, int productId, int locationId,
                                  String actionType, BigDecimal amount, Integer quantity, String note) throws SQLException {
        record(conn, saleId, saleItemId, null, null, null, productId, locationId,
                actionType, "SALE_ITEM", null, null, null, amount, quantity, null, note);
    }

    public static void recordHeldCart(Connection conn, Integer locationId, String actionType, Integer quantity, BigDecimal amount, String note) throws SQLException {
        record(conn, null, null, null, null, null, null, locationId,
                actionType, "HELD_CART", null, null, null, amount, quantity, null, note);
    }

    private static void setNullableInteger(PreparedStatement ps, int index, Integer value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.INTEGER);
        } else {
            ps.setInt(index, value);
        }
    }

    private static void setNullableLong(PreparedStatement ps, int index, Long value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.BIGINT);
        } else {
            ps.setLong(index, value);
        }
    }

    private static void setNullableBigDecimal(PreparedStatement ps, int index, BigDecimal value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.NUMERIC);
        } else {
            ps.setBigDecimal(index, value);
        }
    }

    private static String valueText(Object value) {
        return value == null ? null : value.toString();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
