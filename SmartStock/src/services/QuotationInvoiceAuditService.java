package services;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;

import managers.SessionManager;

public final class QuotationInvoiceAuditService {
    private QuotationInvoiceAuditService() {
    }

    public static void recordQuotationAudit(Connection conn, long quotationId, String actionType, String fieldName,
                                        Object oldValue, Object newValue, String reason) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO quotation_audit_log (
                    quotation_id, action_type, field_name, old_value, new_value,
                    reason, user_id, user_name, device_id, device_name
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """)) {
            bindAudit(ps, quotationId, actionType, fieldName, oldValue, newValue, reason);
            ps.executeUpdate();
        }
    }

    public static void recordInvoiceAudit(Connection conn, long invoiceId, String actionType, String fieldName,
                                        Object oldValue, Object newValue, String reason) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO invoice_audit_log (
                    invoice_id, action_type, field_name, old_value, new_value,
                    reason, user_id, user_name, device_id, device_name
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """)) {
            bindAudit(ps, invoiceId, actionType, fieldName, oldValue, newValue, reason);
            ps.executeUpdate();
        }
    }

    public static void recordQuotationStatus(Connection conn, long quotationId, String oldStatus, String newStatus, String reason) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO quotation_status_history (
                    quotation_id, old_status, new_status, reason, user_id, user_name, device_id, device_name
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """)) {
            bindStatus(ps, quotationId, oldStatus, newStatus, reason);
            ps.executeUpdate();
        }
        recordQuotationAudit(conn, quotationId, "STATUS_CHANGE", "status", oldStatus, newStatus, reason);
    }

    public static void recordInvoiceStatus(Connection conn, long invoiceId, String oldStatus, String newStatus, String reason) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO invoice_status_history (
                    invoice_id, old_status, new_status, reason, user_id, user_name, device_id, device_name
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """)) {
            bindStatus(ps, invoiceId, oldStatus, newStatus, reason);
            ps.executeUpdate();
        }
        recordInvoiceAudit(conn, invoiceId, "STATUS_CHANGE", "status", oldStatus, newStatus, reason);
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
        ps.setString(9, blankToNull(currentDocumentDeviceId()));
        ps.setString(10, blankToNull(currentDocumentDeviceName()));
    }

    private static void bindStatus(PreparedStatement ps, long parentId, String oldStatus, String newStatus, String reason) throws SQLException {
        ps.setLong(1, parentId);
        ps.setString(2, blankToNull(oldStatus));
        ps.setString(3, newStatus);
        ps.setString(4, blankToNull(reason));
        setNullableInteger(ps, 5, SessionManager.getCurrentUserId());
        ps.setString(6, SessionManager.getCurrentUserDisplayName());
        ps.setString(7, blankToNull(currentDocumentDeviceId()));
        ps.setString(8, blankToNull(currentDocumentDeviceName()));
    }

    private static String currentDocumentDeviceId() {
        return DeviceContextService.currentDeviceId();
    }

    private static String currentDocumentDeviceName() {
        return DeviceContextService.currentDeviceName();
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
