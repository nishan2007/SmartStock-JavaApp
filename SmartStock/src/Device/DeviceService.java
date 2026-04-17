package device;
import Managers.SessionManager;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.UUID;

public class DeviceService {

    public static void registerOrUpdateDevice(Connection conn, Integer userId, Integer storeId) throws Exception {
        DeviceInfo info = DeviceUtils.collectDeviceInfo();

        String checkSql = """
                select device_id, is_approved, is_blocked
                from devices
                where installation_id = ?
                """;

        try (PreparedStatement checkStmt = conn.prepareStatement(checkSql)) {
            checkStmt.setString(1, info.getInstallationId());

            try (ResultSet rs = checkStmt.executeQuery()) {
                if (rs.next()) {
                    String deviceId = rs.getString("device_id");
                    boolean approved = rs.getBoolean("is_approved");
                    boolean blocked = rs.getBoolean("is_blocked");

                    if (blocked) {
                        throw new RuntimeException("This device has been blocked.");
                    }

                    if (!approved) {
                        throw new RuntimeException("This device is pending approval.");
                    }

                    String updateSql = """
                            update devices
                            set
                                device_fingerprint = ?,
                                device_name = ?,
                                hostname = ?,
                                os_name = ?,
                                os_version = ?,
                                os_arch = ?,
                                java_version = ?,
                                app_version = ?,
                                local_username = ?,
                                mac_addresses = ?,
                                last_seen = current_timestamp,
                                last_login_user_id = ?,
                                last_store_id = ?
                            where installation_id = ?
                            """;

                    try (PreparedStatement updateStmt = conn.prepareStatement(updateSql)) {
                        updateStmt.setString(1, info.getFingerprint());
                        updateStmt.setString(2, info.getDeviceName());
                        updateStmt.setString(3, info.getHostname());
                        updateStmt.setString(4, info.getOsName());
                        updateStmt.setString(5, info.getOsVersion());
                        updateStmt.setString(6, info.getOsArch());
                        updateStmt.setString(7, info.getJavaVersion());
                        updateStmt.setString(8, info.getAppVersion());
                        updateStmt.setString(9, info.getLocalUsername());
                        updateStmt.setString(10, info.getMacAddresses());

                        if (userId == null) {
                            updateStmt.setNull(11, Types.INTEGER);
                        } else {
                            updateStmt.setInt(11, userId);
                        }

                        if (storeId == null) {
                            updateStmt.setNull(12, Types.INTEGER);
                        } else {
                            updateStmt.setInt(12, storeId);
                        }

                        updateStmt.setString(13, info.getInstallationId());
                        updateStmt.executeUpdate();
                    }

                    SessionManager.setCurrentDeviceId(deviceId);
                } else {
                    String insertSql = """
                            insert into devices (
                                installation_id,
                                device_fingerprint,
                                device_name,
                                hostname,
                                os_name,
                                os_version,
                                os_arch,
                                java_version,
                                app_version,
                                local_username,
                                mac_addresses,
                                first_seen,
                                last_seen,
                                last_login_user_id,
                                last_store_id,
                                is_approved,
                                is_blocked
                            ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, current_timestamp, current_timestamp, ?, ?, true, false)
                            returning device_id
                            """;

                    try (PreparedStatement insertStmt = conn.prepareStatement(insertSql)) {
                        insertStmt.setString(1, info.getInstallationId());
                        insertStmt.setString(2, info.getFingerprint());
                        insertStmt.setString(3, info.getDeviceName());
                        insertStmt.setString(4, info.getHostname());
                        insertStmt.setString(5, info.getOsName());
                        insertStmt.setString(6, info.getOsVersion());
                        insertStmt.setString(7, info.getOsArch());
                        insertStmt.setString(8, info.getJavaVersion());
                        insertStmt.setString(9, info.getAppVersion());
                        insertStmt.setString(10, info.getLocalUsername());
                        insertStmt.setString(11, info.getMacAddresses());

                        if (userId == null) {
                            insertStmt.setNull(12, Types.INTEGER);
                        } else {
                            insertStmt.setInt(12, userId);
                        }

                        if (storeId == null) {
                            insertStmt.setNull(13, Types.INTEGER);
                        } else {
                            insertStmt.setInt(13, storeId);
                        }

                        try (ResultSet insertRs = insertStmt.executeQuery()) {
                            if (insertRs.next()) {
                                SessionManager.setCurrentDeviceId(insertRs.getString("device_id"));
                            }
                        }
                    }
                }
            }
        }

        if (SessionManager.getCurrentDeviceId() != null && !SessionManager.getCurrentDeviceId().isBlank()) {
            startDeviceSession(conn, SessionManager.getCurrentDeviceId(), userId, storeId);
        }
    }

    public static void startDeviceSession(Connection conn, String deviceId, Integer userId, Integer storeId) throws SQLException {
        String sessionSql = """
                insert into device_sessions (
                    device_id,
                    user_id,
                    store_id,
                    login_time,
                    session_status
                ) values (?, ?, ?, current_timestamp, 'ACTIVE')
                returning session_id
                """;

        try (PreparedStatement sessionStmt = conn.prepareStatement(sessionSql)) {
            sessionStmt.setObject(1, UUID.fromString(deviceId));

            if (userId == null) {
                sessionStmt.setNull(2, Types.INTEGER);
            } else {
                sessionStmt.setInt(2, userId);
            }

            if (storeId == null) {
                sessionStmt.setNull(3, Types.INTEGER);
            } else {
                sessionStmt.setInt(3, storeId);
            }

            try (ResultSet sessionRs = sessionStmt.executeQuery()) {
                if (sessionRs.next()) {
                    SessionManager.setCurrentDeviceSessionId(sessionRs.getLong("session_id"));
                }
            }
        }
    }

    public static void endCurrentSession(Connection conn) throws SQLException {
        if (SessionManager.getCurrentDeviceSessionId() == null) {
            return;
        }

        String endSql = """
                update device_sessions
                set logout_time = current_timestamp,
                    session_status = 'ENDED'
                where session_id = ?
                """;

        try (PreparedStatement endStmt = conn.prepareStatement(endSql)) {
            endStmt.setLong(1, SessionManager.getCurrentDeviceSessionId());
            endStmt.executeUpdate();
        }

        SessionManager.setCurrentDeviceSessionId(null);
    }
}