package services;

import data.DB;
import managers.ReceiptNumberManager;
import managers.SessionManager;
import utils.DeviceUtils;
import models.DeviceInfo;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

public final class DeviceContextService {
    private DeviceContextService() {
    }

    public static String currentDeviceId() {
        return blankToNull(SessionManager.getCurrentDeviceId());
    }

    public static String currentDeviceName() {
        String managedDeviceName = currentManagedDeviceName();
        if (managedDeviceName != null) {
            return managedDeviceName;
        }

        String localDeviceName = currentLocalDeviceName();
        if (localDeviceName != null) {
            return localDeviceName;
        }

        String receiptDeviceName = currentReceiptDeviceName();
        if (receiptDeviceName != null) {
            return receiptDeviceName;
        }

        return currentDeviceId();
    }

    public static void requireSalesAllowed(Connection conn) throws SQLException {
        requireCapability(conn, "allow_sales", "This device is not allowed to make sales. Enable Allow Sales in Device Management.");
    }

    public static void requireOrdersAllowed(Connection conn) throws SQLException {
        requireCapability(conn, "allow_orders", "This device is not allowed to create orders. Enable Allow Orders in Device Management.");
    }

    private static String currentReceiptDeviceName() {
        Integer locationId = SessionManager.getCurrentLocationId();
        if (locationId == null) {
            return null;
        }
        try {
            return blankToNull(ReceiptNumberManager.getDeviceReceiptSettings(locationId).deviceId());
        } catch (IOException ex) {
            return null;
        }
    }

    private static String currentManagedDeviceName() {
        String deviceId = currentDeviceId();
        if (deviceId == null) {
            return null;
        }
        String sql = """
                SELECT COALESCE(
                           NULLIF(TRIM(NULLIF(device_name, device_id::text)), ''),
                           NULLIF(TRIM(hostname), ''),
                           NULLIF(TRIM(local_username), '')
                       ) AS device_name
                FROM devices
                WHERE device_id = ?::uuid
                """;
        try (Connection conn = DB.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, deviceId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return blankToNull(rs.getString("device_name"));
                }
            }
        } catch (SQLException ex) {
            return null;
        }
        return null;
    }

    private static String currentLocalDeviceName() {
        try {
            DeviceInfo info = DeviceUtils.collectDeviceInfo();
            String deviceName = blankToNull(info.getDeviceName());
            if (deviceName != null && !deviceName.equals(currentDeviceId())) {
                return deviceName;
            }
            return blankToNull(info.getHostname());
        } catch (Exception ex) {
            return null;
        }
    }

    private static void requireCapability(Connection conn, String capabilityColumn, String message) throws SQLException {
        String deviceId = resolveCurrentDeviceId(conn);
        String sql = "SELECT COALESCE(" + capabilityColumn + ", TRUE) AS allowed FROM devices WHERE device_id = ?::uuid AND COALESCE(is_blocked, FALSE) = FALSE";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, deviceId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next() || !rs.getBoolean("allowed")) {
                    throw new SQLException(message);
                }
            }
        }
    }

    private static String resolveCurrentDeviceId(Connection conn) throws SQLException {
        String deviceId = currentDeviceId();
        if (deviceId != null && isUuid(deviceId) && activeDeviceExists(conn, deviceId)) {
            return deviceId;
        }

        String installationId = DeviceUtils.collectDeviceInfo().getInstallationId();
        String sql = """
                SELECT device_id::text AS device_id
                FROM devices
                WHERE installation_id = ?
                  AND COALESCE(is_blocked, FALSE) = FALSE
                ORDER BY last_seen DESC NULLS LAST
                LIMIT 1
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, installationId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String resolvedDeviceId = rs.getString("device_id");
                    SessionManager.setCurrentDeviceId(resolvedDeviceId);
                    return resolvedDeviceId;
                }
            }
        }

        throw new SQLException("No active device record found for this workstation.");
    }

    private static boolean activeDeviceExists(Connection conn, String deviceId) throws SQLException {
        String sql = "SELECT 1 FROM devices WHERE device_id = ?::uuid AND COALESCE(is_blocked, FALSE) = FALSE";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, deviceId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static boolean isUuid(String value) {
        try {
            UUID.fromString(value.trim());
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
