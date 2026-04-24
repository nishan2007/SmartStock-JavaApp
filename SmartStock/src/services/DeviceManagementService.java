package services;

import models.DeviceSessionRecord;
import models.ManagedDevice;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class DeviceManagementService {

    private DeviceManagementService() {
    }

    public static List<ManagedDevice> getAllDevices(Connection conn) throws SQLException {
        String sql = """
                SELECT d.device_id::text AS device_id,
                       d.installation_id,
                       COALESCE(d.device_name, '') AS device_name,
                       COALESCE(d.hostname, '') AS hostname,
                       COALESCE(d.os_name, '') AS os_name,
                       COALESCE(d.os_version, '') AS os_version,
                       COALESCE(d.os_arch, '') AS os_arch,
                       COALESCE(d.java_version, '') AS java_version,
                       COALESCE(d.app_version, '') AS app_version,
                       COALESCE(d.local_username, '') AS local_username,
                       COALESCE(d.mac_addresses, '') AS mac_addresses,
                       COALESCE(last_user.full_name, last_user.username, '') AS last_user_name,
                       COALESCE(last_store.name, '') AS last_store_name,
                       COALESCE(d.is_approved, false) AS is_approved,
                       COALESCE(d.is_blocked, false) AS is_blocked,
                       d.first_seen,
                       d.last_seen,
                       d.approved_at,
                       COALESCE(approved_by.full_name, approved_by.username, '') AS approved_by_name,
                       d.blocked_at,
                       COALESCE(blocked_by.full_name, blocked_by.username, '') AS blocked_by_name,
                       COALESCE(d.status_notes, '') AS status_notes,
                       COALESCE(session_totals.session_count, 0) AS session_count,
                       COALESCE(session_totals.active_session_count, 0) AS active_session_count,
                       latest_session.login_time AS latest_login_time,
                       latest_session.logout_time AS latest_logout_time,
                       COALESCE(latest_session.session_status, '') AS latest_session_status
                FROM devices d
                LEFT JOIN users last_user ON last_user.user_id = d.last_login_user_id
                LEFT JOIN locations last_store ON last_store.location_id = d.last_store_id
                LEFT JOIN users approved_by ON approved_by.user_id = d.approved_by_user_id
                LEFT JOIN users blocked_by ON blocked_by.user_id = d.blocked_by_user_id
                LEFT JOIN LATERAL (
                    SELECT COUNT(*) AS session_count,
                           COUNT(*) FILTER (
                               WHERE UPPER(COALESCE(ds.session_status, '')) = 'ACTIVE'
                                 AND ds.logout_time IS NULL
                           ) AS active_session_count
                    FROM device_sessions ds
                    WHERE ds.device_id = d.device_id
                ) session_totals ON TRUE
                LEFT JOIN LATERAL (
                    SELECT ds.login_time,
                           ds.logout_time,
                           ds.session_status
                    FROM device_sessions ds
                    WHERE ds.device_id = d.device_id
                    ORDER BY ds.login_time DESC NULLS LAST, ds.session_id DESC
                    LIMIT 1
                ) latest_session ON TRUE
                ORDER BY COALESCE(d.last_seen, d.first_seen) DESC NULLS LAST, d.device_name, d.hostname
                """;

        List<ManagedDevice> devices = new ArrayList<>();

        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                devices.add(new ManagedDevice(
                        rs.getString("device_id"),
                        rs.getString("installation_id"),
                        rs.getString("device_name"),
                        rs.getString("hostname"),
                        rs.getString("os_name"),
                        rs.getString("os_version"),
                        rs.getString("os_arch"),
                        rs.getString("java_version"),
                        rs.getString("app_version"),
                        rs.getString("local_username"),
                        rs.getString("mac_addresses"),
                        rs.getString("last_user_name"),
                        rs.getString("last_store_name"),
                        rs.getBoolean("is_approved"),
                        rs.getBoolean("is_blocked"),
                        rs.getTimestamp("first_seen"),
                        rs.getTimestamp("last_seen"),
                        rs.getTimestamp("approved_at"),
                        rs.getString("approved_by_name"),
                        rs.getTimestamp("blocked_at"),
                        rs.getString("blocked_by_name"),
                        rs.getString("status_notes"),
                        rs.getLong("session_count"),
                        rs.getLong("active_session_count"),
                        rs.getTimestamp("latest_login_time"),
                        rs.getTimestamp("latest_logout_time"),
                        rs.getString("latest_session_status")
                ));
            }
        }

        return devices;
    }

    public static List<DeviceSessionRecord> getDeviceSessionHistory(Connection conn, String deviceId, int limit) throws SQLException {
        String sql = """
                SELECT ds.session_id,
                       COALESCE(u.full_name, u.username, '') AS user_name,
                       COALESCE(l.name, '') AS store_name,
                       ds.login_time,
                       ds.logout_time,
                       COALESCE(ds.session_status, '') AS session_status
                FROM device_sessions ds
                LEFT JOIN users u ON u.user_id = ds.user_id
                LEFT JOIN locations l ON l.location_id = ds.store_id
                WHERE ds.device_id = ?
                ORDER BY ds.login_time DESC NULLS LAST, ds.session_id DESC
                LIMIT ?
                """;

        List<DeviceSessionRecord> sessions = new ArrayList<>();

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, UUID.fromString(deviceId));
            ps.setInt(2, limit);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    sessions.add(new DeviceSessionRecord(
                            rs.getLong("session_id"),
                            rs.getString("user_name"),
                            rs.getString("store_name"),
                            rs.getTimestamp("login_time"),
                            rs.getTimestamp("logout_time"),
                            rs.getString("session_status")
                    ));
                }
            }
        }

        return sessions;
    }

    public static void updateDeviceApproval(Connection conn, String deviceId, Integer actingUserId, boolean approved, String notes) throws SQLException {
        String sql = """
                UPDATE devices
                SET is_approved = ?,
                    is_blocked = FALSE,
                    approved_at = ?,
                    approved_by_user_id = ?,
                    blocked_at = NULL,
                    blocked_by_user_id = NULL,
                    status_notes = ?
                WHERE device_id = ?
                """;

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setBoolean(1, approved);
            if (approved) {
                ps.setTimestamp(2, new java.sql.Timestamp(System.currentTimeMillis()));
            } else {
                ps.setNull(2, Types.TIMESTAMP);
            }

            if (actingUserId == null) {
                ps.setNull(3, Types.INTEGER);
            } else {
                ps.setInt(3, actingUserId);
            }
            ps.setString(4, normalizeNotes(notes));
            ps.setObject(5, UUID.fromString(deviceId));
            ps.executeUpdate();
        }
    }

    public static void blockDevice(Connection conn, String deviceId, Integer actingUserId, String notes) throws SQLException {
        String deviceSql = """
                UPDATE devices
                SET is_blocked = TRUE,
                    is_approved = FALSE,
                    blocked_at = CURRENT_TIMESTAMP,
                    blocked_by_user_id = ?,
                    status_notes = ?
                WHERE device_id = ?
                """;

        String sessionSql = """
                UPDATE device_sessions
                SET logout_time = CURRENT_TIMESTAMP,
                    session_status = 'BLOCKED'
                WHERE device_id = ?
                  AND logout_time IS NULL
                  AND UPPER(COALESCE(session_status, '')) = 'ACTIVE'
                """;

        try (PreparedStatement devicePs = conn.prepareStatement(deviceSql);
             PreparedStatement sessionPs = conn.prepareStatement(sessionSql)) {
            if (actingUserId == null) {
                devicePs.setNull(1, Types.INTEGER);
            } else {
                devicePs.setInt(1, actingUserId);
            }
            devicePs.setString(2, normalizeNotes(notes));
            devicePs.setObject(3, UUID.fromString(deviceId));
            devicePs.executeUpdate();

            sessionPs.setObject(1, UUID.fromString(deviceId));
            sessionPs.executeUpdate();
        }
    }

    private static String normalizeNotes(String notes) {
        if (notes == null) {
            return null;
        }
        String trimmed = notes.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
