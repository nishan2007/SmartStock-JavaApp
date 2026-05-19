package services;

import managers.SessionManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;

public final class CustomOrderAuditService {
    private CustomOrderAuditService() {
    }

    public static void recordAudit(Connection conn, long orderId, String actionType, String fieldName,
                                   Object oldValue, Object newValue, String reason) throws SQLException {
        String sql = """
                INSERT INTO custom_order_audit_log (
                    custom_order_id, action_type, field_name, old_value, new_value,
                    reason, user_id, user_name, device_id, device_name
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, orderId);
            ps.setString(2, actionType);
            ps.setString(3, fieldName);
            ps.setString(4, valueText(oldValue));
            ps.setString(5, valueText(newValue));
            ps.setString(6, blankToNull(reason));
            setNullableInteger(ps, 7, SessionManager.getCurrentUserId());
            ps.setString(8, SessionManager.getCurrentUserDisplayName());
            ps.setString(9, blankToNull(DeviceContextService.currentDeviceId()));
            ps.setString(10, blankToNull(DeviceContextService.currentDeviceName()));
            ps.executeUpdate();
        }
    }

    public static void recordStatus(Connection conn, long orderId, String oldStatus, String newStatus, String reason) throws SQLException {
        String sql = """
                INSERT INTO custom_order_status_history (
                    custom_order_id, old_status, new_status, reason, user_id, user_name, device_id, device_name
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, orderId);
            ps.setString(2, blankToNull(oldStatus));
            ps.setString(3, newStatus);
            ps.setString(4, blankToNull(reason));
            setNullableInteger(ps, 5, SessionManager.getCurrentUserId());
            ps.setString(6, SessionManager.getCurrentUserDisplayName());
            ps.setString(7, blankToNull(DeviceContextService.currentDeviceId()));
            ps.setString(8, blankToNull(DeviceContextService.currentDeviceName()));
            ps.executeUpdate();
        }
        recordAudit(conn, orderId, "STATUS_CHANGE", "status", oldStatus, newStatus, reason);
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
