package services;

import data.DB;
import managers.ReceiptNumberManager;
import managers.SessionManager;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public final class DeviceContextService {
    private DeviceContextService() {
    }

    public static String currentDeviceId() {
        return blankToNull(SessionManager.getCurrentDeviceId());
    }

    public static String currentDeviceName() {
        String receiptDeviceName = currentReceiptDeviceName();
        if (receiptDeviceName != null) {
            return receiptDeviceName;
        }

        String managedDeviceName = currentManagedDeviceName();
        if (managedDeviceName != null) {
            return managedDeviceName;
        }

        return currentDeviceId();
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
        String sql = "SELECT NULLIF(TRIM(device_name), '') AS device_name FROM devices WHERE device_id = ?::uuid";
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

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
