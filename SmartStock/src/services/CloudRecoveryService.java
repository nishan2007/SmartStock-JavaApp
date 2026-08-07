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
        RecoveryReferences references = new RecoveryReferences();
        for (String table : ordered) {
            if (!mirrorManifest.hasTable(table) || !tableExists(target, table)) continue;
            restored += restoreMirroredTable(target, locationId, table, references);
        }
        repairOwnedSequences(target);
        return restored;
    }

    static int restoreReferenceRows(Connection target, Map<String, JsonArray> tables)
            throws SQLException {
        RecoveryReferences references = new RecoveryReferences();
        int restored = 0;
        for (String table : List.of("roles", "permissions", "mobile_permissions",
                "role_permissions", "role_mobile_permissions", "users")) {
            JsonArray rows = tables.get(table);
            if (rows == null || rows.isEmpty() || !tableExists(target, table)) continue;
            restored += restoreMirrorRows(target, table, writableColumnTypes(target, table),
                    rows, references);
        }
        repairOwnedSequences(target);
        return restored;
    }

    static int restoreRows(Connection target, String table, JsonArray rows) throws SQLException {
        if (!tableExists(target, table)) return 0;
        return insertRows(target, table, writableColumnTypes(target, table), rows);
    }

    private static int restoreMirroredTable(Connection target, int locationId, String table,
                                            RecoveryReferences references)
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
                restored += restoreMirrorRows(target, table, targetTypes, activeRows, references);
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

    private static int restoreMirrorRows(Connection target, String table,
                                         Map<String, String> targetTypes, JsonArray rows,
                                         RecoveryReferences references) throws SQLException {
        if ("roles".equals(table)) {
            rememberIntegerKey(rows, "role_id", "role_name", references.roleNames);
            return insertRows(target, table, without(targetTypes, "role_id"), rows);
        }
        if ("permissions".equals(table)) {
            rememberIntegerKey(rows, "permission_id", "permission_key",
                    references.permissionKeys);
            return insertRows(target, table, without(targetTypes, "permission_id"), rows);
        }
        if ("categories".equals(table)) {
            rememberIntegerKey(rows, "category_id", "name", references.categoryNames);
            int restored = insertRows(target, table, without(targetTypes, "category_id"), rows);
            resolveNamedIds(target, "categories", "category_id", "name",
                    references.categoryNames, references.categoryIds);
            return restored;
        }
        if ("item_types".equals(table)) {
            rememberItemTypes(rows, references);
            JsonArray translated = translateCatalogIds(table, rows, references, true);
            int restored = insertRows(target, table, without(targetTypes, "item_type_id"), translated);
            resolveItemTypeIds(target, references);
            return restored;
        }
        if ("role_permissions".equals(table)) {
            return restoreRolePermissions(target, rows, references);
        }
        if ("role_mobile_permissions".equals(table)) {
            return restoreRoleMobilePermissions(target, rows, references);
        }
        if ("users".equals(table)) {
            rows = translateUserRoleIds(target, rows, references);
        }
        rows = translateCatalogIds(table, rows, references, false);
        return insertRows(target, table, targetTypes, rows);
    }

    private static void rememberItemTypes(JsonArray rows, RecoveryReferences references)
            throws SQLException {
        for (JsonElement element : rows) {
            JsonObject row = element.getAsJsonObject();
            int id = requiredInt(row, "item_type_id");
            int categoryId = requiredInt(row, "category_id");
            String name = requiredText(row, "name");
            references.itemTypes.put(id, new ItemTypeReference(categoryId, name));
        }
    }

    private static JsonArray translateCatalogIds(String table, JsonArray rows,
                                                  RecoveryReferences references,
                                                  boolean requireCategory) throws SQLException {
        JsonArray translated = new JsonArray();
        for (JsonElement element : rows) {
            JsonObject row = element.getAsJsonObject().deepCopy();
            if (row.has("category_id") && !row.get("category_id").isJsonNull()) {
                int cloudId = row.get("category_id").getAsInt();
                Integer localId = references.categoryIds.get(cloudId);
                if (localId == null && requireCategory) {
                    throw new SQLException("Cloud " + table
                            + " row references an unknown category.");
                }
                if (localId != null) row.addProperty("category_id", localId);
                else row.add("category_id", com.google.gson.JsonNull.INSTANCE);
            }
            if (row.has("item_type_id") && !row.get("item_type_id").isJsonNull()) {
                Integer localId = references.itemTypeIds.get(row.get("item_type_id").getAsInt());
                if (localId == null) {
                    row.add("item_type_id", com.google.gson.JsonNull.INSTANCE);
                } else {
                    row.addProperty("item_type_id", localId);
                }
            }
            translated.add(row);
        }
        return translated;
    }

    private static void resolveNamedIds(Connection target, String table, String idColumn,
                                        String nameColumn, Map<Integer, String> names,
                                        Map<Integer, Integer> ids) throws SQLException {
        String sql = "SELECT " + quote(idColumn) + " FROM " + quote(table)
                + " WHERE UPPER(" + quote(nameColumn) + ")=UPPER(?)";
        try (PreparedStatement ps = target.prepareStatement(sql)) {
            for (Map.Entry<Integer, String> entry : names.entrySet()) {
                ps.setString(1, entry.getValue());
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) throw new SQLException("Local " + table
                            + " reference was not restored: " + entry.getValue());
                    ids.put(entry.getKey(), rs.getInt(1));
                }
            }
        }
    }

    private static void resolveItemTypeIds(Connection target, RecoveryReferences references)
            throws SQLException {
        try (PreparedStatement ps = target.prepareStatement("""
                SELECT item_type_id FROM item_types
                WHERE category_id=? AND UPPER(name)=UPPER(?)
                """)) {
            for (Map.Entry<Integer, ItemTypeReference> entry : references.itemTypes.entrySet()) {
                Integer categoryId = references.categoryIds.get(entry.getValue().categoryId());
                if (categoryId == null) throw new SQLException("Item type category was not restored.");
                ps.setInt(1, categoryId);
                ps.setString(2, entry.getValue().name());
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) throw new SQLException("Local item type was not restored: "
                            + entry.getValue().name());
                    references.itemTypeIds.put(entry.getKey(), rs.getInt(1));
                }
            }
        }
    }

    private static Map<String, String> without(Map<String, String> columns, String excluded) {
        Map<String, String> result = new LinkedHashMap<>(columns);
        result.remove(excluded);
        return result;
    }

    private static void rememberIntegerKey(JsonArray rows, String idField, String keyField,
                                           Map<Integer, String> target) throws SQLException {
        for (JsonElement element : rows) {
            JsonObject row = element.getAsJsonObject();
            if (!row.has(idField) || !row.has(keyField)
                    || row.get(idField).isJsonNull() || row.get(keyField).isJsonNull()) {
                throw new SQLException("Cloud recovery row is missing " + idField
                        + " or " + keyField + ".");
            }
            target.put(row.get(idField).getAsInt(), row.get(keyField).getAsString());
        }
    }

    private static int restoreRolePermissions(Connection target, JsonArray rows,
                                              RecoveryReferences references) throws SQLException {
        String sql = """
                INSERT INTO role_permissions (role_id, permission_id, updated_at)
                SELECT r.role_id, p.permission_id, COALESCE(?::timestamptz, CURRENT_TIMESTAMP)
                FROM roles r JOIN permissions p ON UPPER(p.permission_key)=UPPER(?)
                WHERE UPPER(r.role_name)=UPPER(?)
                ON CONFLICT (role_id, permission_id) DO UPDATE
                SET updated_at=GREATEST(role_permissions.updated_at, EXCLUDED.updated_at)
                """;
        int restored = 0;
        try (PreparedStatement ps = target.prepareStatement(sql)) {
            for (JsonElement element : rows) {
                JsonObject row = element.getAsJsonObject();
                String role = references.roleNames.get(requiredInt(row, "role_id"));
                String permission = references.permissionKeys.get(requiredInt(row, "permission_id"));
                if (role == null || permission == null) {
                    throw new SQLException("Cloud role-permission references an unknown role or permission.");
                }
                ps.setString(1, optionalText(row, "updated_at"));
                ps.setString(2, permission);
                ps.setString(3, role);
                restored += ps.executeUpdate();
            }
        }
        return restored;
    }

    private static int restoreRoleMobilePermissions(Connection target, JsonArray rows,
                                                    RecoveryReferences references)
            throws SQLException {
        String sql = """
                INSERT INTO role_mobile_permissions (role_id, permission_key, updated_at)
                SELECT r.role_id, mp.permission_key, COALESCE(?::timestamptz, CURRENT_TIMESTAMP)
                FROM roles r JOIN mobile_permissions mp ON UPPER(mp.permission_key)=UPPER(?)
                WHERE UPPER(r.role_name)=UPPER(?)
                ON CONFLICT (role_id, permission_key) DO UPDATE
                SET updated_at=GREATEST(role_mobile_permissions.updated_at, EXCLUDED.updated_at)
                """;
        int restored = 0;
        try (PreparedStatement ps = target.prepareStatement(sql)) {
            for (JsonElement element : rows) {
                JsonObject row = element.getAsJsonObject();
                String role = references.roleNames.get(requiredInt(row, "role_id"));
                String permission = requiredText(row, "permission_key");
                if (role == null) throw new SQLException("Cloud mobile permission references an unknown role.");
                ps.setString(1, optionalText(row, "updated_at"));
                ps.setString(2, permission);
                ps.setString(3, role);
                restored += ps.executeUpdate();
            }
        }
        return restored;
    }

    private static JsonArray translateUserRoleIds(Connection target, JsonArray rows,
                                                  RecoveryReferences references)
            throws SQLException {
        JsonArray translated = new JsonArray();
        for (JsonElement element : rows) {
            JsonObject row = element.getAsJsonObject().deepCopy();
            if (row.has("role_id") && !row.get("role_id").isJsonNull()) {
                String role = references.roleNames.get(row.get("role_id").getAsInt());
                if (role == null) throw new SQLException("Cloud user references an unknown role.");
                try (PreparedStatement ps = target.prepareStatement(
                        "SELECT role_id FROM roles WHERE UPPER(role_name)=UPPER(?)")) {
                    ps.setString(1, role);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) throw new SQLException("Local role was not restored: " + role);
                        row.addProperty("role_id", rs.getInt(1));
                    }
                }
            }
            translated.add(row);
        }
        return translated;
    }

    private static void repairOwnedSequences(Connection target) throws SQLException {
        List<SequenceColumn> sequences = new ArrayList<>();
        try (PreparedStatement ps = target.prepareStatement("""
                SELECT t.relname, a.attname,
                       pg_get_serial_sequence(format('%I.%I', n.nspname, t.relname), a.attname)
                FROM pg_class t
                JOIN pg_namespace n ON n.oid=t.relnamespace
                JOIN pg_attribute a ON a.attrelid=t.oid AND a.attnum>0 AND NOT a.attisdropped
                JOIN pg_attrdef d ON d.adrelid=t.oid AND d.adnum=a.attnum
                WHERE n.nspname='public' AND t.relkind='r'
                  AND pg_get_serial_sequence(format('%I.%I', n.nspname, t.relname), a.attname)
                      IS NOT NULL
                """);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                sequences.add(new SequenceColumn(rs.getString(1), rs.getString(2),
                        rs.getString(3)));
            }
        }
        for (SequenceColumn sequence : sequences) {
            String sql = "SELECT setval(?::regclass, GREATEST(COALESCE(MAX("
                    + quote(sequence.column()) + "), 1), 1), COUNT(*) > 0) FROM "
                    + quote(sequence.table());
            try (PreparedStatement ps = target.prepareStatement(sql)) {
                ps.setString(1, sequence.sequence());
                ps.execute();
            }
        }
    }

    private static int requiredInt(JsonObject row, String field) throws SQLException {
        if (!row.has(field) || row.get(field).isJsonNull()) {
            throw new SQLException("Cloud recovery row is missing " + field + ".");
        }
        return row.get(field).getAsInt();
    }

    private static String requiredText(JsonObject row, String field) throws SQLException {
        String value = optionalText(row, field);
        if (value == null || value.isBlank()) {
            throw new SQLException("Cloud recovery row is missing " + field + ".");
        }
        return value;
    }

    private static String optionalText(JsonObject row, String field) {
        return row.has(field) && !row.get(field).isJsonNull()
                ? row.get(field).getAsString() : null;
    }

    private static final class RecoveryReferences {
        private final Map<Integer, String> roleNames = new LinkedHashMap<>();
        private final Map<Integer, String> permissionKeys = new LinkedHashMap<>();
        private final Map<Integer, String> categoryNames = new LinkedHashMap<>();
        private final Map<Integer, Integer> categoryIds = new LinkedHashMap<>();
        private final Map<Integer, ItemTypeReference> itemTypes = new LinkedHashMap<>();
        private final Map<Integer, Integer> itemTypeIds = new LinkedHashMap<>();
    }

    private record ItemTypeReference(int categoryId, String name) { }

    private record SequenceColumn(String table, String column, String sequence) { }

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
