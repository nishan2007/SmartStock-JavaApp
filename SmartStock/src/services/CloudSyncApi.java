package services;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Server-only HTTPS transport for batched Supabase synchronization.
 *
 * <p>The privileged key stays on the store server. Registers never call this
 * API and never receive either the secret key or database credentials.</p>
 */
public final class CloudSyncApi {
    static final int MAX_UPLOAD_EVENTS = 100;
    static final int DEFAULT_DELTA_LIMIT = 100;
    private static final Gson GSON = new Gson();

    private CloudSyncApi() {
    }

    public static ExchangeResult exchange(Connection local, int locationId) throws SQLException {
        if (locationId <= 0) {
            throw new IllegalArgumentException("A valid store location is required for cloud synchronization.");
        }
        SyncSchemaInstaller.ensureSchema(local);
        long cursor = loadCursor(local);
        List<OutboundEvent> events = pendingEvents(local, MAX_UPLOAD_EVENTS);
        JsonObject requestBody = requestBody(locationId, cursor, events, DEFAULT_DELTA_LIMIT);
        SupabaseServerApi.Response response;
        try {
            response = SupabaseServerApi.postRpc("smartstock_sync_exchange", requestBody);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new SQLException("Supabase sync API request was interrupted.", ex);
        } catch (IOException ex) {
            throw new SQLException("Supabase sync API is unavailable: " + ex.getMessage(), ex);
        }
        CloudTransferMetrics.record(local, "sync_exchange",
                GSON.toJson(requestBody), response.body());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new SQLException(apiError(response.statusCode(), response.body()));
        }
        ExchangeResponse exchange;
        try {
            exchange = parseResponse(response.body(), cursor);
        } catch (IOException ex) {
            throw new SQLException(ex.getMessage(), ex);
        }
        persistResponse(local, exchange);
        return new ExchangeResult(exchange.acknowledgedEventIds().size(),
                exchange.changes().size(), exchange.nextCursor(), exchange.hasMore());
    }

    static JsonObject requestBody(int locationId, long cursor, List<OutboundEvent> events, int limit) {
        JsonObject body = new JsonObject();
        body.addProperty("p_location_id", locationId);
        body.addProperty("p_cursor", Math.max(cursor, 0));
        body.addProperty("p_limit", Math.min(Math.max(limit, 1), 500));
        JsonArray payload = new JsonArray();
        for (OutboundEvent event : events) {
            JsonObject item = new JsonObject();
            item.addProperty("event_id", event.eventId().toString());
            item.addProperty("event_type", event.eventType());
            if (event.deviceId() != null) item.addProperty("device_id", event.deviceId());
            if (event.userId() != null) item.addProperty("user_id", event.userId());
            item.add("payload", parseJsonObject(event.payload()));
            if (event.createdAt() != null) item.addProperty("created_at", event.createdAt().toString());
            payload.add(item);
        }
        body.add("p_events", payload);
        return body;
    }

    static ExchangeResponse parseResponse(String body, long previousCursor) throws IOException {
        try {
            JsonElement parsed = JsonParser.parseString(body == null ? "" : body);
            if (!parsed.isJsonObject()) throw new IllegalArgumentException("Response is not an object.");
            JsonObject object = parsed.getAsJsonObject();
            Set<UUID> acknowledged = new HashSet<>();
            JsonArray ackArray = array(object, "acknowledged_event_ids");
            for (JsonElement element : ackArray) acknowledged.add(UUID.fromString(element.getAsString()));

            List<InboundEvent> changes = new ArrayList<>();
            long highestSequence = previousCursor;
            for (JsonElement element : array(object, "changes")) {
                JsonObject change = element.getAsJsonObject();
                long sequence = requiredLong(change, "sequence");
                UUID eventId = UUID.fromString(requiredString(change, "event_id"));
                String eventType = requiredString(change, "event_type");
                changes.add(new InboundEvent(sequence, eventId, eventType,
                        nullableInteger(change, "location_id"), nullableString(change, "device_id"),
                        nullableInteger(change, "user_id"),
                        change.has("payload") && !change.get("payload").isJsonNull()
                                ? GSON.toJson(change.get("payload")) : "{}",
                        nullableInteger(change, "origin_location_id"),
                        nullableString(change, "origin_device_id"),
                        nullableInstant(change, "created_at")));
                highestSequence = Math.max(highestSequence, sequence);
            }
            long nextCursor = object.has("next_cursor") && !object.get("next_cursor").isJsonNull()
                    ? object.get("next_cursor").getAsLong() : highestSequence;
            if (nextCursor < highestSequence || nextCursor < previousCursor) {
                throw new IllegalArgumentException("Cloud cursor moved backwards.");
            }
            boolean hasMore = object.has("has_more") && object.get("has_more").getAsBoolean();
            return new ExchangeResponse(Set.copyOf(acknowledged), List.copyOf(changes),
                    nextCursor, hasMore);
        } catch (RuntimeException ex) {
            throw new IOException("Supabase returned an invalid sync response.", ex);
        }
    }

    private static List<OutboundEvent> pendingEvents(Connection local, int limit) throws SQLException {
        List<OutboundEvent> result = new ArrayList<>();
        try (PreparedStatement ps = local.prepareStatement("""
                SELECT event_id, event_type, device_id, user_id, payload, created_at
                FROM sync_outbox
                WHERE status IN ('PENDING', 'FAILED')
                ORDER BY created_at, event_id
                LIMIT ?
                """)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Timestamp createdAt = rs.getTimestamp("created_at");
                    result.add(new OutboundEvent(
                            rs.getObject("event_id", UUID.class),
                            rs.getString("event_type"),
                            rs.getString("device_id"),
                            (Integer) rs.getObject("user_id"),
                            rs.getString("payload"),
                            createdAt == null ? null : createdAt.toInstant()));
                }
            }
        }
        return List.copyOf(result);
    }

    private static long loadCursor(Connection local) throws SQLException {
        try (PreparedStatement ps = local.prepareStatement("""
                SELECT cursor_value FROM sync_cloud_state WHERE state_id='event_delta'
                """);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? Math.max(rs.getLong(1), 0) : 0;
        }
    }

    private static void persistResponse(Connection local, ExchangeResponse response) throws SQLException {
        boolean oldAutoCommit = local.getAutoCommit();
        local.setAutoCommit(false);
        try {
            markAcknowledged(local, response.acknowledgedEventIds());
            insertChanges(local, response.changes());
            try (PreparedStatement ps = local.prepareStatement("""
                    INSERT INTO sync_cloud_state(state_id,cursor_value,updated_at)
                    VALUES ('event_delta',?,CURRENT_TIMESTAMP)
                    ON CONFLICT(state_id) DO UPDATE
                    SET cursor_value=GREATEST(sync_cloud_state.cursor_value,EXCLUDED.cursor_value),
                        updated_at=CURRENT_TIMESTAMP
                    """)) {
                ps.setLong(1, response.nextCursor());
                ps.executeUpdate();
            }
            local.commit();
        } catch (SQLException ex) {
            local.rollback();
            throw ex;
        } finally {
            local.setAutoCommit(oldAutoCommit);
        }
    }

    private static void markAcknowledged(Connection local, Set<UUID> eventIds) throws SQLException {
        if (eventIds.isEmpty()) return;
        try (PreparedStatement ps = local.prepareStatement("""
                UPDATE sync_outbox
                SET status='SYNCED_TO_CLOUD_OUTBOX', synced_at=CURRENT_TIMESTAMP, last_error=NULL
                WHERE event_id=?
                """)) {
            for (UUID eventId : eventIds) {
                ps.setObject(1, eventId);
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private static void insertChanges(Connection local, List<InboundEvent> changes) throws SQLException {
        if (changes.isEmpty()) return;
        try (PreparedStatement ps = local.prepareStatement("""
                INSERT INTO sync_inbox(
                    cloud_sequence,event_id,event_type,location_id,device_id,user_id,payload,
                    origin_location_id,origin_device_id,origin_created_at
                )
                VALUES (?,?,?,?,?,?,?::jsonb,?,?,?)
                ON CONFLICT(cloud_sequence) DO NOTHING
                """)) {
            for (InboundEvent event : changes) {
                ps.setLong(1, event.sequence());
                ps.setObject(2, event.eventId());
                ps.setString(3, event.eventType());
                setNullableInteger(ps, 4, event.locationId());
                ps.setString(5, event.deviceId());
                setNullableInteger(ps, 6, event.userId());
                ps.setString(7, event.payload());
                setNullableInteger(ps, 8, event.originLocationId());
                ps.setString(9, event.originDeviceId());
                ps.setTimestamp(10, event.createdAt() == null ? null : Timestamp.from(event.createdAt()));
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private static String apiError(int statusCode, String body) {
        String detail = "Supabase sync API returned HTTP " + statusCode + ".";
        try {
            JsonObject error = JsonParser.parseString(body == null ? "" : body).getAsJsonObject();
            String message = nullableString(error, "message");
            if (message != null && !message.isBlank()) {
                detail += " " + message.substring(0, Math.min(message.length(), 300));
            }
        } catch (RuntimeException ignored) {
            // Do not echo arbitrary proxy responses or credentials into logs.
        }
        return detail;
    }

    private static JsonObject parseJsonObject(String value) {
        try {
            JsonElement parsed = JsonParser.parseString(value == null ? "{}" : value);
            return parsed.isJsonObject() ? parsed.getAsJsonObject() : new JsonObject();
        } catch (RuntimeException ex) {
            return new JsonObject();
        }
    }

    private static JsonArray array(JsonObject object, String name) {
        return object.has(name) && object.get(name).isJsonArray()
                ? object.getAsJsonArray(name) : new JsonArray();
    }

    private static String requiredString(JsonObject object, String name) {
        String value = nullableString(object, name);
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Missing " + name);
        return value;
    }

    private static long requiredLong(JsonObject object, String name) {
        if (!object.has(name) || object.get(name).isJsonNull()) {
            throw new IllegalArgumentException("Missing " + name);
        }
        return object.get(name).getAsLong();
    }

    private static String nullableString(JsonObject object, String name) {
        return object.has(name) && !object.get(name).isJsonNull()
                ? object.get(name).getAsString() : null;
    }

    private static Integer nullableInteger(JsonObject object, String name) {
        return object.has(name) && !object.get(name).isJsonNull()
                ? object.get(name).getAsInt() : null;
    }

    private static Instant nullableInstant(JsonObject object, String name) {
        String value = nullableString(object, name);
        return value == null || value.isBlank() ? null : Instant.parse(value);
    }

    private static void setNullableInteger(PreparedStatement ps, int index, Integer value)
            throws SQLException {
        if (value == null) ps.setNull(index, java.sql.Types.INTEGER);
        else ps.setInt(index, value);
    }

    record OutboundEvent(UUID eventId, String eventType, String deviceId, Integer userId,
                         String payload, Instant createdAt) {
    }

    record InboundEvent(long sequence, UUID eventId, String eventType, Integer locationId,
                        String deviceId, Integer userId, String payload, Integer originLocationId,
                        String originDeviceId, Instant createdAt) {
    }

    record ExchangeResponse(Set<UUID> acknowledgedEventIds, List<InboundEvent> changes,
                            long nextCursor, boolean hasMore) {
    }

    public record ExchangeResult(int acknowledged, int downloaded, long nextCursor,
                                 boolean hasMore) {
    }
}
