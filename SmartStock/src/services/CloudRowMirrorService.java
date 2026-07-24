package services;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Materializes exact per-store rows in Supabase without exposing PostgreSQL
 * credentials or repeatedly downloading cloud tables during routine sync.
 */
public final class CloudRowMirrorService {
    private static final int BATCH_SIZE = 200;

    private CloudRowMirrorService() {
    }

    public static MirrorResult synchronize(Connection local, int locationId) throws SQLException {
        if (locationId <= 0) throw new SQLException("A valid store location is required.");
        SyncSchemaInstaller.ensureSchema(local);
        int uploaded = 0;
        int unchanged = 0;
        int deleted = 0;
        int activeRows = 0;
        JsonObject tableCounts = new JsonObject();
        for (String table : ReferenceDataSyncService.cloudMirrorTablesForApi()) {
            if (!tableExists(local, table)) continue;
            TableResult result = mirrorTable(local, locationId, table);
            if (!result.supported()) continue;
            uploaded += result.uploaded();
            unchanged += result.unchanged();
            deleted += result.deleted();
            activeRows += result.activeRows();
            tableCounts.addProperty(table, result.activeRows());
        }
        if (uploaded > 0 || deleted > 0
                || !hasMatchingCompletion(local, locationId, tableCounts, activeRows)) {
            finalizeMirror(local, locationId, tableCounts, activeRows);
            saveCompletion(local, locationId, tableCounts, activeRows);
        }
        return new MirrorResult(uploaded, unchanged, deleted, activeRows);
    }

    private static boolean hasMatchingCompletion(Connection local, int locationId,
                                                 JsonObject tableCounts, int activeRows)
            throws SQLException {
        try (PreparedStatement ps = local.prepareStatement("""
                SELECT 1 FROM sync_row_mirror_completion
                WHERE location_id=? AND table_counts=?::jsonb AND active_row_count=?
                """)) {
            ps.setInt(1, locationId);
            ps.setString(2, tableCounts.toString());
            ps.setLong(3, activeRows);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static void saveCompletion(Connection local, int locationId,
                                       JsonObject tableCounts, int activeRows)
            throws SQLException {
        try (PreparedStatement ps = local.prepareStatement("""
                INSERT INTO sync_row_mirror_completion(
                    location_id,table_counts,active_row_count,completed_at
                )
                VALUES(?,?::jsonb,?,CURRENT_TIMESTAMP)
                ON CONFLICT(location_id) DO UPDATE SET
                    table_counts=EXCLUDED.table_counts,
                    active_row_count=EXCLUDED.active_row_count,
                    completed_at=CURRENT_TIMESTAMP
                """)) {
            ps.setInt(1, locationId);
            ps.setString(2, tableCounts.toString());
            ps.setLong(3, activeRows);
            ps.executeUpdate();
        }
    }

    private static TableResult mirrorTable(Connection local, int locationId, String table)
            throws SQLException {
        List<String> primaryKeys = primaryKeys(local, table);
        if (primaryKeys.isEmpty()) return new TableResult(0, 0, 0, 0, false);
        Map<String, String> existing = existingHashes(local, locationId, table);
        List<MirrorRow> pending = new ArrayList<>();
        int unchanged = 0;
        int activeRows = 0;

        try (PreparedStatement ps = local.prepareStatement("SELECT * FROM " + quote(table));
             ResultSet rs = ps.executeQuery()) {
            ResultSetMetaData metadata = rs.getMetaData();
            while (rs.next()) {
                activeRows++;
                JsonObject key = new JsonObject();
                for (String primaryKey : primaryKeys) {
                    String value = rs.getString(primaryKey);
                    if (value == null) {
                        throw new SQLException("Cloud mirror primary key is null: "
                                + table + "." + primaryKey);
                    }
                    key.addProperty(primaryKey, value);
                }
                JsonObject data = new JsonObject();
                String sourceUpdatedAt = null;
                for (int index = 1; index <= metadata.getColumnCount(); index++) {
                    String column = metadata.getColumnName(index);
                    Object value = rs.getObject(index);
                    if ("updated_at".equals(column) && value != null) {
                        Timestamp timestamp = rs.getTimestamp(index);
                        if (timestamp != null) sourceUpdatedAt = timestamp.toInstant().toString();
                    }
                    if (sensitiveColumn(table, column)
                            || excludedOperationalColumn(table, column)) continue;
                    if (value == null) {
                        data.add(column, null);
                    } else if (value instanceof byte[] bytes) {
                        data.addProperty(column, "\\x" + hex(bytes));
                    } else {
                        data.addProperty(column, rs.getString(index));
                    }
                }
                String keyJson = key.toString();
                String hash = sha256(data.toString());
                String priorHash = existing.remove(keyJson);
                if (hash.equals(priorHash)) {
                    unchanged++;
                } else {
                    pending.add(new MirrorRow(table, keyJson, data, hash, false, sourceUpdatedAt));
                }
            }
        }

        for (String deletedKey : existing.keySet()) {
            pending.add(new MirrorRow(table, deletedKey, new JsonObject(),
                    sha256("deleted:" + deletedKey), true, null));
        }

        int uploaded = 0;
        int deleted = 0;
        for (int offset = 0; offset < pending.size(); offset += BATCH_SIZE) {
            List<MirrorRow> batch = pending.subList(offset,
                    Math.min(offset + BATCH_SIZE, pending.size()));
            uploadBatch(local, locationId, batch);
            persistBatchState(local, locationId, batch);
            uploaded += batch.size();
            deleted += (int) batch.stream().filter(MirrorRow::deleted).count();
        }
        return new TableResult(uploaded, unchanged, deleted, activeRows, true);
    }

    private static void finalizeMirror(Connection local, int locationId,
                                       JsonObject tableCounts, int activeRows)
            throws SQLException {
        JsonObject body = new JsonObject();
        body.addProperty("p_location_id", locationId);
        body.add("p_table_counts", tableCounts);
        try {
            SupabaseServerApi.Response response =
                    SupabaseServerApi.postRpc("smartstock_finalize_store_mirror", body);
            CloudTransferMetrics.record(local, "finalize_store_mirror",
                    body.toString(), response.body());
            if (!response.successful()) {
                throw new SQLException(SupabaseServerApi.failureMessage(
                        "Supabase mirror completion", response));
            }
            JsonObject result = JsonParser.parseString(response.body()).getAsJsonObject();
            if (!result.has("completed") || !result.get("completed").getAsBoolean()
                    || !result.has("active_row_count")
                    || result.get("active_row_count").getAsInt() != activeRows) {
                throw new SQLException("Supabase did not confirm the complete store mirror.");
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new SQLException("Supabase mirror completion was interrupted.", ex);
        } catch (IOException | RuntimeException ex) {
            throw new SQLException("Supabase mirror completion failed.", ex);
        }
    }

    private static void uploadBatch(Connection local, int locationId,
                                    List<MirrorRow> batch) throws SQLException {
        JsonObject body = new JsonObject();
        body.addProperty("p_location_id", locationId);
        JsonArray rows = new JsonArray();
        for (MirrorRow row : batch) {
            JsonObject item = new JsonObject();
            item.addProperty("table_name", row.table());
            item.add("row_key", JsonParser.parseString(row.keyJson()));
            item.add("row_data", row.data());
            item.addProperty("row_hash", row.hash());
            item.addProperty("is_deleted", row.deleted());
            if (row.sourceUpdatedAt() != null) {
                item.addProperty("source_updated_at", row.sourceUpdatedAt());
            }
            rows.add(item);
        }
        body.add("p_rows", rows);
        try {
            SupabaseServerApi.Response response =
                    SupabaseServerApi.postRpc("smartstock_materialize_store_rows", body);
            CloudTransferMetrics.record(local, "materialize_store_rows",
                    body.toString(), response.body());
            if (!response.successful()) {
                throw new SQLException(SupabaseServerApi.failureMessage(
                        "Supabase row materialization", response));
            }
            JsonObject result = JsonParser.parseString(response.body()).getAsJsonObject();
            if (!result.has("acknowledged")
                    || result.get("acknowledged").getAsInt() != batch.size()) {
                throw new SQLException("Supabase did not acknowledge the complete row batch.");
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new SQLException("Supabase row materialization was interrupted.", ex);
        } catch (IOException | RuntimeException ex) {
            throw new SQLException("Supabase row materialization failed.", ex);
        }
    }

    private static void persistBatchState(Connection local, int locationId, List<MirrorRow> batch)
            throws SQLException {
        boolean oldAutoCommit = local.getAutoCommit();
        local.setAutoCommit(false);
        try (PreparedStatement upsert = local.prepareStatement("""
                     INSERT INTO sync_row_mirror_state(
                         location_id,table_name,row_key,row_hash,materialized_at
                     )
                     VALUES (?,?,?,?,CURRENT_TIMESTAMP)
                     ON CONFLICT(location_id,table_name,row_key) DO UPDATE
                     SET row_hash=EXCLUDED.row_hash,materialized_at=CURRENT_TIMESTAMP
                     """);
             PreparedStatement delete = local.prepareStatement("""
                     DELETE FROM sync_row_mirror_state
                     WHERE location_id=? AND table_name=? AND row_key=?
                     """)) {
            for (MirrorRow row : batch) {
                PreparedStatement statement = row.deleted() ? delete : upsert;
                statement.setInt(1, locationId);
                statement.setString(2, row.table());
                statement.setString(3, row.keyJson());
                if (!row.deleted()) statement.setString(4, row.hash());
                statement.addBatch();
            }
            upsert.executeBatch();
            delete.executeBatch();
            local.commit();
        } catch (SQLException ex) {
            local.rollback();
            throw ex;
        } finally {
            local.setAutoCommit(oldAutoCommit);
        }
    }

    private static Map<String, String> existingHashes(Connection local, int locationId,
                                                       String table) throws SQLException {
        Map<String, String> result = new HashMap<>();
        try (PreparedStatement ps = local.prepareStatement("""
                SELECT row_key,row_hash FROM sync_row_mirror_state
                WHERE location_id=? AND table_name=?
                """)) {
            ps.setInt(1, locationId);
            ps.setString(2, table);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) result.put(rs.getString(1), rs.getString(2));
            }
        }
        return result;
    }

    private static List<String> primaryKeys(Connection local, String table) throws SQLException {
        Map<Short, String> ordered = new LinkedHashMap<>();
        DatabaseMetaData metadata = local.getMetaData();
        try (ResultSet rs = metadata.getPrimaryKeys(null, "public", table)) {
            while (rs.next()) ordered.put(rs.getShort("KEY_SEQ"), rs.getString("COLUMN_NAME"));
        }
        return ordered.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .map(Map.Entry::getValue).toList();
    }

    private static boolean sensitiveColumn(String table, String column) {
        String clean = column.toLowerCase(Locale.ROOT);
        if ("users".equals(table)
                && ("badge_secret_salt".equals(clean) || "badge_secret_hash".equals(clean))) {
            return false;
        }
        return clean.contains("password")
                || clean.equals("pin") || clean.startsWith("pin_") || clean.endsWith("_pin")
                || clean.contains("_pin_")
                || clean.contains("token")
                || clean.contains("credential")
                || clean.contains("secret");
    }

    /**
     * Omits device telemetry and derived counters from the durable business
     * snapshot. These fields change during normal polling/login activity and
     * are reconstructed by the server, so mirroring them would create cloud
     * egress without improving recovery.
     */
    static boolean excludedOperationalColumn(String table, String column) {
        if (!"devices".equals(table) || column == null) return false;
        return switch (column.toLowerCase(Locale.ROOT)) {
            case "last_seen", "updated_at", "session_count" -> true;
            default -> false;
        };
    }

    private static boolean tableExists(Connection local, String table) throws SQLException {
        try (PreparedStatement ps = local.prepareStatement("""
                SELECT EXISTS(
                  SELECT 1 FROM information_schema.tables
                  WHERE table_schema='public' AND table_name=?
                )
                """)) {
            ps.setString(1, table);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getBoolean(1);
            }
        }
    }

    private static String quote(String identifier) throws SQLException {
        if (identifier == null || !identifier.matches("[a-z][a-z0-9_]{0,100}")) {
            throw new SQLException("Unsafe cloud mirror table name.");
        }
        return "\"" + identifier + "\"";
    }

    private static String sha256(String value) throws SQLException {
        try {
            return hex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new SQLException("SHA-256 is unavailable.", ex);
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) result.append(String.format("%02x", value));
        return result.toString();
    }

    private record MirrorRow(String table, String keyJson, JsonObject data, String hash,
                             boolean deleted, String sourceUpdatedAt) {
    }

    private record TableResult(int uploaded, int unchanged, int deleted, int activeRows,
                               boolean supported) {
    }

    public record MirrorResult(int uploaded, int unchanged, int deleted, int activeRows) {
    }
}
