package services;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/** Transfers whole policies using names and keys, never another store's numeric IDs. */
final class CrossStoreRoleSyncService {
    private CrossStoreRoleSyncService() { }

    static int announceCustomRoles(Connection c, int location) throws SQLException {
        List<Integer> ids = new ArrayList<>();
        // Built-in seed policies must never replace an established store's policy.
        // Explicit saves of built-in roles are published by record().
        try (PreparedStatement p = c.prepareStatement("""
                SELECT r.role_id FROM roles r
                WHERE UPPER(r.role_name) NOT IN ('USER','ADMIN','MANAGER')
                  OR EXISTS (SELECT 1 FROM sync_outbox o
                    WHERE o.event_type='ROLE_PERMISSIONS_UPDATED'
                      AND o.payload->>'role_id'=r.role_id::text)
                ORDER BY r.role_name
                """);
             ResultSet r = p.executeQuery()) {
            while (r.next()) ids.add(r.getInt(1));
        }
        int count = 0;
        for (int id : ids) if (publish(c, id, location)) count++;
        return count;
    }

    static void record(Connection c, int role, int location) throws SQLException {
        try (PreparedStatement p = c.prepareStatement(
                "UPDATE roles SET updated_at=CURRENT_TIMESTAMP WHERE role_id=?")) {
            p.setInt(1, role); p.executeUpdate();
        }
        publish(c, role, location);
    }

    private static boolean publish(Connection c, int role, int location) throws SQLException {
        JsonObject row = snapshot(c, role);
        String key = row.get("role_name").getAsString().toUpperCase(java.util.Locale.ROOT);
        String hash = LanSecurity.sha256("role_policy|UPSERT|" + key + "|" + row);
        if (CrossStoreReferenceSyncService.alreadyKnown(c, "role_policy", key, hash)) return false;
        JsonObject payload = new JsonObject();
        payload.addProperty("table_name", "role_policy");
        payload.addProperty("operation", "UPSERT");
        payload.addProperty("row_key", key); payload.addProperty("row_hash", hash);
        payload.add("row_data", row);
        SyncOutboxService.recordJsonEvent(c, CrossStoreReferenceSyncService.EVENT, payload, location, null, null);
        return true;
    }

    static JsonObject snapshot(Connection c, int role) throws SQLException {
        try (PreparedStatement p = c.prepareStatement("""
                SELECT jsonb_build_object('role_name',r.role_name,'description',r.description,
                  'updated_at',GREATEST(r.updated_at,(SELECT MAX(o.created_at)
                    FROM sync_outbox o WHERE o.event_type='ROLE_PERMISSIONS_UPDATED'
                      AND o.payload->>'role_id'=r.role_id::text)),
                  'permissions',COALESCE((
                    SELECT jsonb_agg(p.permission_key ORDER BY p.permission_key)
                    FROM role_permissions rp JOIN permissions p USING(permission_id)
                    WHERE rp.role_id=r.role_id),'[]'::jsonb),
                  'mobile_permissions',COALESCE((SELECT jsonb_agg(permission_key ORDER BY permission_key)
                    FROM role_mobile_permissions WHERE role_id=r.role_id),'[]'::jsonb))::text
                FROM roles r WHERE role_id=?
                """)) {
            p.setInt(1, role);
            try (ResultSet r = p.executeQuery()) {
                if (!r.next()) throw new SQLException("Role no longer exists.");
                return JsonParser.parseString(r.getString(1)).getAsJsonObject();
            }
        }
    }

    static void apply(Connection c, JsonObject row) throws SQLException {
        apply(c, row, false);
    }

    static void apply(Connection c, JsonObject row, boolean recovery) throws SQLException {
        String name = row.get("role_name").getAsString();
        int role;
        try (PreparedStatement p = c.prepareStatement("""
                INSERT INTO roles(role_name,description,updated_at) VALUES(?,?,?::timestamptz)
                ON CONFLICT(role_name) DO UPDATE SET description=EXCLUDED.description,
                  updated_at=EXCLUDED.updated_at WHERE ? OR roles.updated_at<EXCLUDED.updated_at
                RETURNING role_id
                """)) {
            p.setString(1, name);
            p.setString(2, row.get("description").isJsonNull() ? null : row.get("description").getAsString());
            p.setString(3, row.get("updated_at").getAsString());
            p.setBoolean(4, recovery);
            try (ResultSet r = p.executeQuery()) { if (!r.next()) return; role = r.getInt(1); }
        }
        replace(c, role, row, "permissions", "role_permissions", "permissions", "permission_id");
        replace(c, role, row, "mobile_permissions", "role_mobile_permissions", "mobile_permissions", "permission_key");
    }

    private static void replace(Connection c, int role, JsonObject row, String field,
                                String assignments, String definitions, String column) throws SQLException {
        List<String> keys = new ArrayList<>();
        row.getAsJsonArray(field).forEach(v -> keys.add(v.getAsString()));
        // Validate before deleting: missing definitions fail the transaction and retry later.
        try (PreparedStatement p = c.prepareStatement(
                "SELECT 1 FROM " + definitions + " WHERE UPPER(permission_key)=UPPER(?)")) {
            for (String key : keys) {
                p.setString(1, key);
                try (ResultSet r = p.executeQuery()) {
                    if (!r.next()) throw new SQLException("Shared role references unavailable permission: " + key);
                }
            }
        }
        try (PreparedStatement p = c.prepareStatement("DELETE FROM " + assignments + " WHERE role_id=?")) {
            p.setInt(1, role); p.executeUpdate();
        }
        try (PreparedStatement p = c.prepareStatement("INSERT INTO " + assignments + "(role_id," + column
                + ") SELECT ?," + column + " FROM " + definitions
                + " WHERE UPPER(permission_key)=UPPER(?) ON CONFLICT DO NOTHING")) {
            for (String key : keys) { p.setInt(1, role); p.setString(2, key); p.addBatch(); }
            p.executeBatch();
        }
    }
}
