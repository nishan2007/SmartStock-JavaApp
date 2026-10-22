package services;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Objects;
import java.util.UUID;

/** Sends explicit, audited corrections to the store that owns the shift. */
final class TimeClockCorrectionSyncService {
    private static final String TABLE = "employee_time_clock_adjustments";
    private static final String[] PUNCHES = {
            "clock_in", "lunch_start", "lunch_end", "break_start", "break_end", "clock_out"};

    private TimeClockCorrectionSyncService() { }

    // Also picks up corrections saved before this delivery path existed. Imported
    // audits are excluded: a replica must never turn received history into a command.
    static int announce(Connection c, int localLocationId) throws SQLException {
        int count = 0;
        try (PreparedStatement p = c.prepareStatement("""
                SELECT a.adjustment_id::text,
                       (to_jsonb(a) - 'clock_id' || jsonb_build_object(
                         'clock_uuid',tc.clock_uuid,'location_id',tc.location_id))::text
                FROM employee_time_clock_adjustments a
                JOIN employee_time_clock tc ON tc.clock_id=a.clock_id
                WHERE tc.location_id<>? AND a.action_type='CORRECT'
                  AND NOT EXISTS (
                    SELECT 1 FROM sync_inbox i WHERE i.event_type='REFERENCE_ROW_CHANGED'
                    AND i.payload->>'table_name'='employee_time_clock_adjustments'
                    AND i.payload->'row_data'->>'adjustment_id'=a.adjustment_id::text)
                ORDER BY a.created_at,a.adjustment_id
                """)) {
            p.setInt(1, localLocationId);
            try (ResultSet r = p.executeQuery()) {
                while (r.next()) {
                    String key = r.getString(1), data = r.getString(2);
                    String hash = LanSecurity.sha256(TABLE + "|CORRECT_OWNER|" + key + "|" + data);
                    if (CrossStoreReferenceSyncService.alreadyKnown(c, TABLE, key, hash)) continue;
                    JsonObject row = JsonParser.parseString(data).getAsJsonObject();
                    JsonObject payload = new JsonObject();
                    payload.addProperty("table_name", TABLE);
                    payload.addProperty("operation", "CORRECT_OWNER");
                    payload.addProperty("row_key", key);
                    payload.addProperty("row_hash", hash);
                    payload.add("row_data", row);
                    SyncOutboxService.recordJsonEvent(c, CrossStoreReferenceSyncService.EVENT,
                            payload, localLocationId, null, row.get("actor_user_id").isJsonNull()
                                    ? null : row.get("actor_user_id").getAsInt());
                    count++;
                }
            }
        }
        return count;
    }

    static void apply(Connection c, JsonObject row, int localLocationId,
                      Integer originLocationId) throws SQLException {
        if (row == null) throw new SQLException("Time-clock correction details are missing.");
        int owner = row.get("location_id").getAsInt();
        if (owner != localLocationId) return;
        if (originLocationId == null || originLocationId <= 0 || originLocationId == owner)
            throw new SQLException("Time-clock correction has an invalid source store.");
        UUID adjustmentId = UUID.fromString(row.get("adjustment_id").getAsString());
        UUID clockUuid = UUID.fromString(row.get("clock_uuid").getAsString());
        long clockId;
        int userId = row.get("user_id").getAsInt();
        try (PreparedStatement p = c.prepareStatement(
                "SELECT clock_id,user_id,location_id FROM employee_time_clock WHERE clock_uuid=?")) {
            p.setObject(1, clockUuid);
            try (ResultSet r = p.executeQuery()) {
                if (!r.next()) throw new SQLException("The owning store cannot find the corrected time-clock session.");
                if (r.getInt("user_id") != userId || r.getInt("location_id") != owner)
                    throw new SQLException("Time-clock correction does not match the owning store's employee/session.");
                clockId = r.getLong("clock_id");
            }
        }
        ManualTimeClockService.lockEmployee(c, userId);
        try (PreparedStatement p = c.prepareStatement(
                "SELECT 1 FROM employee_time_clock_adjustments WHERE adjustment_id=?")) {
            p.setObject(1, adjustmentId);
            try (ResultSet r = p.executeQuery()) { if (r.next()) return; }
        }
        int actorId = row.get("actor_user_id").getAsInt();
        String actorName;
        // Recheck authorization at the owning server, including deactivated users.
        try (PreparedStatement p = c.prepareStatement("""
                SELECT COALESCE(u.full_name,u.username) FROM users u
                JOIN role_permissions rp ON rp.role_id=u.role_id
                JOIN permissions p ON p.permission_id=rp.permission_id
                WHERE u.user_id=? AND u.is_active=TRUE
                  AND UPPER(p.permission_key)='TIME_CLOCK_MANAGEMENT' LIMIT 1
                """)) {
            p.setInt(1, actorId);
            try (ResultSet r = p.executeQuery()) {
                if (!r.next()) throw new SQLException("The correction author no longer has time-clock management permission.");
                actorName = r.getString(1);
            }
        }
        ZoneId zone;
        try (PreparedStatement p = c.prepareStatement("""
                SELECT tc.clock_in,tc.lunch_start,tc.lunch_end,tc.break_start,tc.break_end,tc.clock_out,
                       COALESCE(NULLIF(l.timezone,''),'America/Guyana') AS timezone
                FROM employee_time_clock tc LEFT JOIN locations l ON l.location_id=tc.location_id
                WHERE tc.clock_id=? FOR UPDATE OF tc
                """)) {
            p.setLong(1, clockId);
            try (ResultSet r = p.executeQuery()) {
                if (!r.next()) throw new SQLException("Time-clock session was not found.");
                for (String punch : PUNCHES) {
                    Timestamp value = r.getTimestamp(punch);
                    if (!Objects.equals(value == null ? null : value.toInstant(), instant(row, "before_" + punch)))
                        throw new SQLException("Time-clock correction conflicts with newer punches or another correction; review it at the owning store.");
                }
                zone = ZoneId.of(r.getString("timezone"));
            }
        }
        if (!"CORRECT".equals(row.get("action_type").getAsString()))
            throw new SQLException("Only explicit time-clock corrections may be applied remotely.");
        var correction = new TimeClockAutoCloseService.Correction(
                local(row,"after_clock_in",zone), local(row,"after_lunch_start",zone),
                local(row,"after_lunch_end",zone), local(row,"after_break_start",zone),
                local(row,"after_break_end",zone), local(row,"after_clock_out",zone),
                row.get("reason").getAsString());
        TimeClockAutoCloseService.correctSyncedSession(c,clockId,correction,actorId,actorName,adjustmentId);
    }

    private static Instant instant(JsonObject row, String name) {
        if (!row.has(name) || row.get(name).isJsonNull()) return null;
        return java.time.OffsetDateTime.parse(row.get(name).getAsString()).toInstant();
    }

    private static LocalDateTime local(JsonObject row, String name, ZoneId zone) {
        Instant value = instant(row,name);
        return value == null ? null : LocalDateTime.ofInstant(value,zone);
    }
}
