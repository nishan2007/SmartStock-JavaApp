package services;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import data.EnvironmentProfile;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** One-time recovery of the role damage caused by Rosehall's initial server enrollment. */
final class StoreRoleRecoveryService {
    static final String REPAIR_ID = "skeldon_rosehall_role_recovery_v1";
    private static final List<String> TABLES = List.of("roles", "permissions", "mobile_permissions",
            "role_permissions", "role_mobile_permissions");

    private StoreRoleRecoveryService() { }

    static void repairIfNeeded(Connection local, int locationId) throws SQLException {
        if (EnvironmentProfile.active() != EnvironmentProfile.PRODUCTION || completed(local)) return;
        String localName;
        try (PreparedStatement p = local.prepareStatement("SELECT name FROM locations WHERE location_id=?")) {
            p.setInt(1, locationId);
            try (ResultSet r = p.executeQuery()) { if (!r.next()) return; localName = r.getString(1); }
        }
        if (!"Skeldon".equalsIgnoreCase(localName) && !"Rosehall".equalsIgnoreCase(localName)) return;
        RecoveryPlan plan = downloadPlan();
        if (plan == null) return; // This environment has no record of the incident.
        boolean auto = local.getAutoCommit();
        if (!auto) throw new SQLException("Role recovery requires its own transaction.");
        local.setAutoCommit(false);
        try {
            applyPlan(local, locationId, plan);
            local.commit();
        } catch (Exception ex) {
            local.rollback();
            throw ex instanceof SQLException sql ? sql : new SQLException("Store role recovery failed.", ex);
        } finally { local.setAutoCommit(auto); }
    }

    static boolean completed(Connection c) throws SQLException {
        try (PreparedStatement p = c.prepareStatement("SELECT 1 FROM sync_cloud_state WHERE state_id=?")) {
            p.setString(1, REPAIR_ID);
            try (ResultSet r = p.executeQuery()) { return r.next(); }
        }
    }

    static RecoveryPlan downloadPlan() throws SQLException {
        try {
            SupabaseServerApi.Response response = SupabaseServerApi.getTablePage("locations", 0, 1_000);
            requireSuccess(response);
            JsonArray locations = JsonParser.parseString(response.body()).getAsJsonArray();
            Integer source = namedLocation(locations, "Skeldon"), peer = namedLocation(locations, "Rosehall");
            if (source == null || peer == null) return null;
            response = SupabaseServerApi.getRoleRecoveryHistory(peer, null, 0, 1);
            requireSuccess(response);
            JsonArray first = JsonParser.parseString(response.body()).getAsJsonArray();
            if (first.isEmpty()) return null;
            long boundary = first.get(0).getAsJsonObject().get("cloud_sequence").getAsLong();
            JsonArray history = new JsonArray();
            for (int offset = 0; ; offset += 1_000) {
                response = SupabaseServerApi.getRoleRecoveryHistory(source, boundary, offset, 1_000);
                requireSuccess(response);
                JsonArray page = JsonParser.parseString(response.body()).getAsJsonArray();
                history.addAll(page);
                if (page.size() < 1_000) break;
            }
            Map<Integer, EmployeeRole> employees = baselineRoles(history);
            if (employees.isEmpty()) throw new SQLException("Skeldon's original employee roles are unavailable; recovery will retry.");
            CloudSyncManifest manifest = CloudSyncManifest.fetchStoreSnapshot(source);
            if (!manifest.hasVerifiedSnapshot()) throw new SQLException("Skeldon's completed role snapshot is unavailable.");
            Map<String, JsonArray> catalog = new LinkedHashMap<>();
            for (String table : TABLES) {
                if (!manifest.hasTable(table)) throw new SQLException("Skeldon's role snapshot is incomplete: " + table);
                JsonArray rows = new JsonArray(); long cursor = 0;
                while (true) {
                    JsonObject page = CloudRecoveryService.fetchMirrorPage(source, manifest.snapshotGenerationId(), table, cursor);
                    JsonArray envelopes = page.getAsJsonArray("rows");
                    if (envelopes == null) throw new SQLException("Skeldon's role snapshot returned invalid rows.");
                    long next = cursor;
                    for (JsonElement element : envelopes) {
                        JsonObject envelope = element.getAsJsonObject();
                        next = Math.max(next, envelope.get("sequence").getAsLong());
                        if (!envelope.get("is_deleted").getAsBoolean()) rows.add(envelope.getAsJsonObject("row_data"));
                    }
                    if (!envelopes.isEmpty() && next <= cursor) throw new SQLException("Role snapshot cursor did not advance.");
                    cursor = next;
                    if (envelopes.size() < 1_000) break;
                }
                if (rows.size() != manifest.rowCount(table)) throw new SQLException("Skeldon's role snapshot count did not verify: " + table);
                catalog.put(table, rows);
            }
            return new RecoveryPlan(catalog, employees, boundary);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt(); throw new SQLException("Role recovery was interrupted.", ex);
        } catch (java.io.IOException | RuntimeException ex) {
            throw new SQLException("Skeldon's role recovery data could not be downloaded; sync will retry.", ex);
        }
    }

    static Map<Integer, EmployeeRole> baselineRoles(JsonArray history) throws SQLException {
        Map<Integer, EmployeeRole> roles = new LinkedHashMap<>();
        for (JsonElement element : history) {
            JsonObject row = element.getAsJsonObject();
            if (!row.has("user_id") || row.get("user_id").isJsonNull()) throw new SQLException("Employee role history has no user identity.");
            int user = row.get("user_id").getAsInt();
            String name = nullable(row, "role_name"), auth = nullable(row, "auth_user_id");
            if (name == null || auth == null) roles.remove(user);
            else roles.put(user, new EmployeeRole(user, name, auth));
        }
        return Map.copyOf(roles);
    }

    /** Caller owns the transaction; the audit and completion marker commit with the repair. */
    static void applyPlan(Connection c, int locationId, RecoveryPlan plan) throws SQLException {
        if (c.getAutoCommit()) throw new SQLException("Role recovery must be transactional.");
        if (completed(c)) return;
        JsonArray policies = policies(plan.catalog(), Instant.now().toString());
        JsonArray previousPolicies = new JsonArray(), previousEmployees = new JsonArray();
        try (PreparedStatement p = c.prepareStatement("SELECT role_id FROM roles ORDER BY role_id"); ResultSet r = p.executeQuery()) {
            while (r.next()) previousPolicies.add(CrossStoreRoleSyncService.snapshot(c, r.getInt(1)));
        }
        CloudRecoveryService.restoreRoleCatalog(c, plan.catalog());
        for (JsonElement element : policies) {
            JsonObject policy = element.getAsJsonObject();
            CrossStoreRoleSyncService.apply(c, policy, true);
            int role = roleId(c, policy.get("role_name").getAsString());
            JsonObject actual = CrossStoreRoleSyncService.snapshot(c, role);
            for (String field : List.of("permissions", "mobile_permissions")) {
                if (!keys(actual.getAsJsonArray(field)).equals(keys(policy.getAsJsonArray(field))))
                    throw new SQLException("Recovered role permissions did not verify.");
            }
            CrossStoreRoleSyncService.record(c, role, locationId);
        }
        for (EmployeeRole expected : plan.employees().values()) {
            try (PreparedStatement p = c.prepareStatement("""
                    SELECT u.auth_user_id::text,r.role_name FROM users u LEFT JOIN roles r USING(role_id)
                    WHERE u.user_id=? FOR UPDATE OF u
                    """)) {
                p.setInt(1, expected.userId());
                try (ResultSet r = p.executeQuery()) {
                    if (!r.next()) continue;
                    if (!expected.authUserId().equalsIgnoreCase(r.getString(1)))
                        throw new SQLException("Employee identity does not match Skeldon's role history; recovery stopped.");
                    String current = r.getString(2);
                    if (!shouldRestore(current, expected.roleName())) continue;
                    JsonObject old = new JsonObject(); old.addProperty("user_id", expected.userId());
                    old.addProperty("role_name", current); previousEmployees.add(old);
                }
            }
            int role = roleId(c, expected.roleName());
            try (PreparedStatement p = c.prepareStatement("UPDATE users SET role_id=?,updated_at=CURRENT_TIMESTAMP WHERE user_id=?")) {
                p.setInt(1, role); p.setInt(2, expected.userId()); p.executeUpdate();
            }
        }
        JsonObject audit = new JsonObject(); audit.addProperty("repair_id", REPAIR_ID);
        audit.addProperty("baseline_before_sequence", plan.boundary());
        audit.add("previous_policies", previousPolicies); audit.add("previous_employee_roles", previousEmployees);
        audit.addProperty("roles_restored", policies.size()); audit.addProperty("employees_restored", previousEmployees.size());
        SyncOutboxService.recordJsonEvent(c, "STORE_ROLE_RECOVERY_COMPLETED", audit, locationId, null, null);
        try (PreparedStatement p = c.prepareStatement("INSERT INTO sync_cloud_state(state_id,cursor_value) VALUES(?,?)")) {
            p.setString(1, REPAIR_ID); p.setLong(2, plan.boundary()); p.executeUpdate();
        }
    }

    static boolean shouldRestore(String current, String baseline) {
        return current == null || "ADMIN".equalsIgnoreCase(current) && !current.equalsIgnoreCase(baseline);
    }

    static JsonArray policies(Map<String, JsonArray> catalog, String timestamp) throws SQLException {
        Map<Integer, String> permissionKeys = new LinkedHashMap<>();
        for (JsonElement e : catalog.get("permissions")) {
            JsonObject p = e.getAsJsonObject(); permissionKeys.put(p.get("permission_id").getAsInt(), p.get("permission_key").getAsString());
        }
        JsonArray result = new JsonArray();
        for (JsonElement e : catalog.get("roles")) {
            JsonObject role = e.getAsJsonObject(), policy = new JsonObject();
            int id = role.get("role_id").getAsInt();
            policy.add("role_name", role.get("role_name")); policy.add("description", role.get("description"));
            policy.addProperty("updated_at", timestamp); JsonArray desktop = new JsonArray(), mobile = new JsonArray();
            for (JsonElement a : catalog.get("role_permissions")) {
                JsonObject assignment = a.getAsJsonObject();
                if (assignment.get("role_id").getAsInt() != id) continue;
                String key = permissionKeys.get(assignment.get("permission_id").getAsInt());
                if (key == null) throw new SQLException("Skeldon's role snapshot contains an unresolved permission.");
                desktop.add(key);
            }
            for (JsonElement a : catalog.get("role_mobile_permissions")) {
                JsonObject assignment = a.getAsJsonObject();
                if (assignment.get("role_id").getAsInt() == id) mobile.add(assignment.get("permission_key"));
            }
            policy.add("permissions", desktop); policy.add("mobile_permissions", mobile); result.add(policy);
        }
        if (result.isEmpty()) throw new SQLException("Skeldon's role catalog is empty.");
        return result;
    }

    private static java.util.Set<String> keys(JsonArray array) {
        java.util.Set<String> result = new java.util.HashSet<>();
        array.forEach(e -> result.add(e.getAsString().toUpperCase(java.util.Locale.ROOT))); return result;
    }

    private static int roleId(Connection c, String name) throws SQLException {
        try (PreparedStatement p = c.prepareStatement("SELECT role_id FROM roles WHERE UPPER(role_name)=UPPER(?)")) {
            p.setString(1, name); try (ResultSet r = p.executeQuery()) {
                if (!r.next()) throw new SQLException("An original employee role is absent from Skeldon's catalog.");
                return r.getInt(1);
            }
        }
    }

    private static Integer namedLocation(JsonArray rows, String name) throws SQLException {
        Integer id = null;
        for (JsonElement e : rows) {
            JsonObject r = e.getAsJsonObject();
            if (!name.equalsIgnoreCase(r.get("name").getAsString())) continue;
            if (id != null) throw new SQLException("Role recovery store name is ambiguous.");
            id = r.get("location_id").getAsInt();
        }
        return id;
    }

    private static String nullable(JsonObject row, String key) {
        return row.has(key) && !row.get(key).isJsonNull() ? row.get(key).getAsString() : null;
    }

    private static void requireSuccess(SupabaseServerApi.Response response) throws SQLException {
        if (!response.successful()) throw new SQLException(SupabaseServerApi.failureMessage("Role recovery", response));
    }

    record EmployeeRole(int userId, String roleName, String authUserId) { }
    record RecoveryPlan(Map<String, JsonArray> catalog, Map<Integer, EmployeeRole> employees, long boundary) { }
}
