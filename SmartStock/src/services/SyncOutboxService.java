package services;

import com.google.gson.JsonObject;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Map;

public final class SyncOutboxService {
    private SyncOutboxService() {
    }

    public static void recordEvent(Connection conn, String eventType, Map<String, ?> payload) throws SQLException {
        recordEvent(conn, eventType, payload, ServerRequestIdentity.locationId(),
                ServerRequestIdentity.deviceId(), ServerRequestIdentity.userId());
    }

    public static void recordEvent(Connection conn, String eventType, Map<String, ?> payload,
                                   Integer locationId, String deviceId, Integer userId) throws SQLException {
        recordJsonEvent(conn,eventType,SyncJson.object(payload == null ? Map.of() : payload),
                locationId,deviceId,userId);
    }

    static void recordJsonEvent(Connection conn,String eventType,JsonObject payload,
                                Integer locationId,String deviceId,Integer userId)throws SQLException{
        recordJsonEvent(conn,eventType,payload==null?"{}":payload.toString(),locationId,deviceId,userId);
    }

    private static void recordJsonEvent(Connection conn,String eventType,String payload,
                                        Integer locationId,String deviceId,Integer userId)throws SQLException{
        if (eventType == null || eventType.isBlank()) {
            return;
        }
        String sql = """
                INSERT INTO sync_outbox (
                    event_type, location_id, device_id, user_id, payload,
                    origin_location_id, origin_device_id
                )
                VALUES (?, ?, ?, ?, ?::jsonb, ?, ?)
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, eventType);
            setNullableInteger(ps, 2, locationId);
            ps.setString(3, blankToNull(deviceId));
            setNullableInteger(ps, 4, userId);
            ps.setString(5, payload);
            setNullableInteger(ps, 6, locationId);
            ps.setString(7, blankToNull(deviceId));
            ps.executeUpdate();
        }
    }

    private static void setNullableInteger(PreparedStatement ps, int index, Integer value) throws SQLException {
        if (value == null) {
            ps.setNull(index, java.sql.Types.INTEGER);
        } else {
            ps.setInt(index, value);
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
