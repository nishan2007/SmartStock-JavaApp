package services;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import java.sql.DriverManager;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class CrossStoreRoleSyncIntegrationTest {
    @Test
    void replayingUnchangedCredentialsDoesNotMakeEmployeeRowNewer() throws Exception {
        String jdbc = System.getProperty("smartstock.test.jdbc", "");
        String user = System.getProperty("smartstock.test.dbUser", "");
        assumeTrue(!jdbc.isBlank() && !user.isBlank());
        try (var c = DriverManager.getConnection(jdbc, user,
                System.getProperty("smartstock.test.dbPassword", ""))) {
            c.setAutoCommit(false);
            try {
                JsonObject row;
                java.sql.Timestamp before;
                try (var p = c.prepareStatement("""
                        SELECT (to_jsonb(u)||jsonb_build_object('role_name',r.role_name))::text,u.updated_at
                        FROM users u JOIN roles r USING(role_id)
                        WHERE u.employee_pin_updated_at IS NOT NULL OR u.badge_generated_at IS NOT NULL
                        ORDER BY u.user_id LIMIT 1
                        """); var r = p.executeQuery()) {
                    assumeTrue(r.next());
                    row = com.google.gson.JsonParser.parseString(r.getString(1)).getAsJsonObject();
                    before = r.getTimestamp(2);
                }
                JsonObject payload = new JsonObject();
                payload.addProperty("table_name", "users"); payload.addProperty("operation", "UPSERT");
                payload.add("row_data", row); payload.add("protected_credentials", row);
                CrossStoreReferenceSyncService.applyPayload(c, payload);
                CrossStoreReferenceSyncService.applyPayload(c, payload);
                try (var p = c.prepareStatement("SELECT updated_at FROM users WHERE user_id=?")) {
                    p.setInt(1, row.get("user_id").getAsInt());
                    try (var r = p.executeQuery()) { assertTrue(r.next()); assertEquals(before, r.getTimestamp(1)); }
                }
            } finally { c.rollback(); }
        }
    }

    @Test
    void appliesPolicyByNameAndRevokesDesktopAndMobileAssignments() throws Exception {
        String jdbc = System.getProperty("smartstock.test.jdbc", "");
        String user = System.getProperty("smartstock.test.dbUser", "");
        assumeTrue(!jdbc.isBlank() && !user.isBlank());
        try (var c = DriverManager.getConnection(jdbc, user,
                System.getProperty("smartstock.test.dbPassword", ""))) {
            c.setAutoCommit(false);
            try {
                String desktop, mobile;
                try (var p = c.prepareStatement("SELECT permission_key FROM permissions LIMIT 1");
                     var r = p.executeQuery()) { assertTrue(r.next()); desktop = r.getString(1); }
                try (var p = c.prepareStatement("SELECT permission_key FROM mobile_permissions LIMIT 1");
                     var r = p.executeQuery()) { assertTrue(r.next()); mobile = r.getString(1); }
                JsonObject row = new JsonObject();
                row.addProperty("role_name", "SYNC_TEST_" + java.util.UUID.randomUUID());
                row.addProperty("description", "Rollback-only role");
                row.addProperty("updated_at", "2090-01-01T00:00:00Z");
                JsonArray desktopKeys = new JsonArray(); desktopKeys.add(desktop);
                JsonArray mobileKeys = new JsonArray(); mobileKeys.add(mobile);
                row.add("permissions", desktopKeys); row.add("mobile_permissions", mobileKeys);
                CrossStoreRoleSyncService.apply(c, row);
                CrossStoreRoleSyncService.apply(c, row);
                assertCounts(c, row, 1);
                row.addProperty("updated_at", "2090-01-02T00:00:00Z");
                row.add("permissions", new JsonArray()); row.add("mobile_permissions", new JsonArray());
                CrossStoreRoleSyncService.apply(c, row);
                assertCounts(c, row, 0);
                row.addProperty("updated_at", "2090-01-01T00:00:00Z");
                row.add("permissions", desktopKeys); row.add("mobile_permissions", mobileKeys);
                CrossStoreRoleSyncService.apply(c, row);
                assertCounts(c, row, 0);
            } finally { c.rollback(); }
        }
    }

    private static void assertCounts(java.sql.Connection c, JsonObject row, int expected) throws Exception {
        for (String table : java.util.List.of("role_permissions", "role_mobile_permissions")) {
            try (var p = c.prepareStatement("SELECT count(*) FROM " + table
                    + " p JOIN roles r USING(role_id) WHERE r.role_name=?")) {
                p.setString(1, row.get("role_name").getAsString());
                try (var r = p.executeQuery()) { assertTrue(r.next()); assertEquals(expected, r.getInt(1)); }
            }
        }
    }
}
