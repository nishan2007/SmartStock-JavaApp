package services;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;

/** Restores materialized cloud tables through Supabase's server-only Data API. */
final class CloudRecoveryService {
    private static final int PAGE_SIZE = 1_000;

    private CloudRecoveryService() {
    }

    static int restore(Connection target, CloudSyncManifest manifest) throws SQLException {
        List<String> ordered = new ArrayList<>(ReferenceDataSyncService.cloudPullOrderForApi());
        Set<String> remaining = new LinkedHashSet<>(manifest.tables().keySet());
        remaining.removeAll(ordered);
        remaining.stream().filter(table -> !table.startsWith("sync_")).sorted().forEach(ordered::add);

        int restored = 0;
        for (String table : ordered) {
            if (!manifest.hasTable(table) || !tableExists(target, table)) continue;
            restored += restoreTable(target, table);
        }
        return restored;
    }

    static int restoreStoreMirror(Connection target, int locationId,
                                  CloudSyncManifest mirrorManifest) throws SQLException {
        List<String> ordered = new ArrayList<>(ReferenceDataSyncService.cloudPullOrderForApi());
        Set<String> remaining = new LinkedHashSet<>(mirrorManifest.tables().keySet());
        remaining.removeAll(ordered);
        remaining.stream().sorted().forEach(ordered::add);
        int restored = 0;
        for (String table : ordered) {
            if (!mirrorManifest.hasTable(table) || !tableExists(target, table)) continue;
            restored += restoreMirroredTable(target, locationId, table);
        }
        return restored;
    }

    private static int restoreMirroredTable(Connection target, int locationId, String table)
            throws SQLException {
        Map<String, String> targetTypes = writableColumnTypes(target, table);
        if (targetTypes.isEmpty()) return 0;
        int restored = 0;
        long cursor = 0;
        boolean oldAutoCommit = target.getAutoCommit();
        target.setAutoCommit(false);
        try {
            while (true) {
                JsonObject page = fetchMirrorPage(locationId, table, cursor);
                JsonArray envelopeRows = page.getAsJsonArray("rows");
                if (envelopeRows == null || envelopeRows.isEmpty()) break;
                JsonArray activeRows = new JsonArray();
                long nextCursor = cursor;
                for (JsonElement element : envelopeRows) {
                    JsonObject envelope = element.getAsJsonObject();
                    nextCursor = Math.max(nextCursor, envelope.get("sequence").getAsLong());
                    if (!envelope.get("is_deleted").getAsBoolean()) {
                        activeRows.add(envelope.getAsJsonObject("row_data"));
                    }
                }
                restored += insertRows(target, table, targetTypes, activeRows);
                if (nextCursor <= cursor) {
                    throw new SQLException("Cloud mirror cursor did not advance for " + table + ".");
                }
                cursor = nextCursor;
                if (envelopeRows.size() < 1_000) break;
            }
            target.commit();
            return restored;
        } catch (SQLException ex) {
            target.rollback();
            throw ex;
        } finally {
            target.setAutoCommit(oldAutoCommit);
        }
    }

    private static JsonObject fetchMirrorPage(int locationId, String table, long cursor)
            throws SQLException {
        JsonObject body = new JsonObject();
        body.addProperty("p_location_id", locationId);
        body.addProperty("p_table_name", table);
        body.addProperty("p_after_sequence", cursor);
        body.addProperty("p_limit", 1_000);
        try {
            SupabaseServerApi.Response response =
                    SupabaseServerApi.postRpc("smartstock_store_table_snapshot", body);
            if (!response.successful()) {
                throw new SQLException(SupabaseServerApi.failureMessage(
                        "Cloud mirror recovery for " + table, response));
            }
            JsonElement parsed = JsonParser.parseString(response.body());
            if (!parsed.isJsonObject()) {
                throw new SQLException("Cloud mirror recovery returned invalid rows for " + table + ".");
            }
            return parsed.getAsJsonObject();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new SQLException("Cloud mirror recovery was interrupted for " + table + ".", ex);
        } catch (IOException | RuntimeException ex) {
            throw new SQLException("Cloud mirror recovery could not download " + table + ".", ex);
        }
    }

    private static int restoreTable(Connection target, String table) throws SQLException {
        Map<String, String> targetTypes = writableColumnTypes(target, table);
        if (targetTypes.isEmpty()) return 0;
        int restored = 0;
        int offset = 0;
        boolean oldAutoCommit = target.getAutoCommit();
        target.setAutoCommit(false);
        try {
            while (true) {
                JsonArray rows = fetchPage(table, offset);
                if (rows.isEmpty()) break;
                restored += insertRows(target, table, targetTypes, rows);
                offset += rows.size();
                if (rows.size() < PAGE_SIZE) break;
            }
            target.commit();
            return restored;
        } catch (SQLException ex) {
            target.rollback();
            throw ex;
        } finally {
            target.setAutoCommit(oldAutoCommit);
        }
    }

    private static JsonArray fetchPage(String table, int offset) throws SQLException {
        try {
            SupabaseServerApi.Response response =
                    SupabaseServerApi.getTablePage(table, offset, PAGE_SIZE);
            if (!response.successful()) {
                throw new SQLException(SupabaseServerApi.failureMessage(
                        "Cloud recovery download for " + table, response));
            }
            JsonElement parsed = JsonParser.parseString(response.body());
            if (!parsed.isJsonArray()) {
                throw new SQLException("Cloud recovery returned invalid rows for " + table + ".");
            }
            return parsed.getAsJsonArray();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new SQLException("Cloud recovery was interrupted while downloading " + table + ".", ex);
        } catch (IOException | RuntimeException ex) {
            throw new SQLException("Cloud recovery could not download " + table + ".", ex);
        }
    }

    private static int insertRows(Connection target, String table, Map<String, String> targetTypes,
                                  JsonArray rows) throws SQLException {
        if (rows.isEmpty()) return 0;
        List<String> columns = new ArrayList<>();
        for (String column : targetTypes.keySet()) {
            boolean supplied = false;
            for (JsonElement row : rows) {
                if (row.isJsonObject() && row.getAsJsonObject().has(column)) {
                    supplied = true;
                    break;
                }
            }
            if (supplied) columns.add(column);
        }
        if (columns.isEmpty()) return 0;

        StringJoiner names = new StringJoiner(",");
        StringJoiner values = new StringJoiner(",");
        for (String column : columns) {
            names.add(quote(column));
            values.add("?::" + targetTypes.get(column));
        }
        String sql = "INSERT INTO " + quote(table) + " (" + names + ") VALUES ("
                + values + ") ON CONFLICT DO NOTHING";
        int inserted = 0;
        try (PreparedStatement ps = target.prepareStatement(sql)) {
            for (JsonElement rowElement : rows) {
                JsonObject row = rowElement.getAsJsonObject();
                for (int index = 0; index < columns.size(); index++) {
                    JsonElement value = row.get(columns.get(index));
                    if (value == null || value.isJsonNull()) {
                        ps.setNull(index + 1, Types.NULL);
                    } else if (value.isJsonPrimitive()) {
                        ps.setString(index + 1, value.getAsString());
                    } else {
                        ps.setString(index + 1, value.toString());
                    }
                }
                ps.addBatch();
            }
            for (int result : ps.executeBatch()) {
                if (result > 0) inserted += result;
                else if (result == java.sql.Statement.SUCCESS_NO_INFO) inserted++;
            }
        }
        return inserted;
    }

    private static Map<String, String> writableColumnTypes(Connection connection, String table)
            throws SQLException {
        Map<String, String> result = new LinkedHashMap<>();
        try (PreparedStatement ps = connection.prepareStatement("""
                SELECT a.attname, pg_catalog.format_type(a.atttypid, a.atttypmod),
                       coalesce(c.is_generated, 'NEVER'), coalesce(c.identity_generation, '')
                FROM pg_catalog.pg_attribute a
                JOIN pg_catalog.pg_class t ON t.oid=a.attrelid
                JOIN pg_catalog.pg_namespace n ON n.oid=t.relnamespace
                LEFT JOIN information_schema.columns c
                  ON c.table_schema=n.nspname AND c.table_name=t.relname
                 AND c.column_name=a.attname
                WHERE n.nspname='public' AND t.relname=?
                  AND a.attnum>0 AND NOT a.attisdropped
                ORDER BY a.attnum
                """)) {
            ps.setString(1, table);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    if ("ALWAYS".equalsIgnoreCase(rs.getString(3))
                            || "ALWAYS".equalsIgnoreCase(rs.getString(4))) continue;
                    result.put(rs.getString(1), rs.getString(2));
                }
            }
        }
        return result;
    }

    private static boolean tableExists(Connection connection, String table) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("""
                SELECT EXISTS (
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
            throw new SQLException("Unsafe cloud recovery identifier.");
        }
        return "\"" + identifier + "\"";
    }
}
