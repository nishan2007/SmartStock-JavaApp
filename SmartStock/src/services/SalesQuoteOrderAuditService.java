package services;

import managers.SessionManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;

public final class SalesQuoteOrderAuditService {
    private SalesQuoteOrderAuditService() {
    }

    public static void recordQuoteAudit(Connection conn, long quoteId, String actionType, String fieldName,
                                        Object oldValue, Object newValue, String reason) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO sales_quote_audit_log (
                    sales_quote_id, action_type, field_name, old_value, new_value,
                    reason, user_id, user_name, device_id, device_name
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """)) {
            bindAudit(ps, quoteId, actionType, fieldName, oldValue, newValue, reason);
            ps.executeUpdate();
        }
    }

    public static void recordOrderAudit(Connection conn, long orderId, String actionType, String fieldName,
                                        Object oldValue, Object newValue, String reason) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO sales_order_audit_log (
                    sales_order_id, action_type, field_name, old_value, new_value,
                    reason, user_id, user_name, device_id, device_name
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """)) {
            bindAudit(ps, orderId, actionType, fieldName, oldValue, newValue, reason);
            ps.executeUpdate();
        }
    }

    public static void recordQuoteStatus(Connection conn, long quoteId, String oldStatus, String newStatus, String reason) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO sales_quote_status_history (
                    sales_quote_id, old_status, new_status, reason, user_id, user_name, device_id, device_name
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """)) {
            bindStatus(ps, quoteId, oldStatus, newStatus, reason);
            ps.executeUpdate();
        }
        recordQuoteAudit(conn, quoteId, "STATUS_CHANGE", "status", oldStatus, newStatus, reason);
    }

    public static void recordOrderStatus(Connection conn, long orderId, String oldStatus, String newStatus, String reason) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO sales_order_status_history (
                    sales_order_id, old_status, new_status, reason, user_id, user_name, device_id, device_name
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """)) {
            bindStatus(ps, orderId, oldStatus, newStatus, reason);
            ps.executeUpdate();
        }
        recordOrderAudit(conn, orderId, "STATUS_CHANGE", "status", oldStatus, newStatus, reason);
    }

    private static void bindAudit(PreparedStatement ps, long parentId, String actionType, String fieldName,
                                  Object oldValue, Object newValue, String reason) throws SQLException {
        ps.setLong(1, parentId);
        ps.setString(2, actionType);
        ps.setString(3, blankToNull(fieldName));
        ps.setString(4, valueText(oldValue));
        ps.setString(5, valueText(newValue));
        ps.setString(6, blankToNull(reason));
        setNullableInteger(ps, 7, SessionManager.getCurrentUserId());
        ps.setString(8, SessionManager.getCurrentUserDisplayName());
        ps.setString(9, blankToNull(DeviceContextService.currentDeviceId()));
        ps.setString(10, blankToNull(DeviceContextService.currentDeviceName()));
    }

    private static void bindStatus(PreparedStatement ps, long parentId, String oldStatus, String newStatus, String reason) throws SQLException {
        ps.setLong(1, parentId);
        ps.setString(2, blankToNull(oldStatus));
        ps.setString(3, newStatus);
        ps.setString(4, blankToNull(reason));
        setNullableInteger(ps, 5, SessionManager.getCurrentUserId());
        ps.setString(6, SessionManager.getCurrentUserDisplayName());
        ps.setString(7, blankToNull(DeviceContextService.currentDeviceId()));
        ps.setString(8, blankToNull(DeviceContextService.currentDeviceName()));
    }

    private static void setNullableInteger(PreparedStatement ps, int index, Integer value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.INTEGER);
        } else {
            ps.setInt(index, value);
        }
    }

    private static String valueText(Object value) {
        return value == null ? null : value.toString();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
