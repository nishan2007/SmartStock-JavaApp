package services;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StoreRoleRecoveryServiceTest {
    @Test
    void keepsLatestOriginalRoleAndDoesNotGuessMissingHistory() throws Exception {
        JsonArray history = JsonParser.parseString("""
                [{"user_id":"1","role_name":"USER","auth_user_id":"test-auth-1"},
                 {"user_id":"2","role_name":"SALES ASSOCIATE","auth_user_id":"test-auth-2"},
                 {"user_id":"1","role_name":"MANAGER","auth_user_id":"test-auth-1"},
                 {"user_id":"2","role_name":null,"auth_user_id":"test-auth-2"}]
                """).getAsJsonArray();
        var roles = StoreRoleRecoveryService.baselineRoles(history);
        assertEquals(1, roles.size());
        assertEquals("MANAGER", roles.get(1).roleName());
        assertEquals("test-auth-1", roles.get(1).authUserId());
    }

    @Test
    void repairsMissingRolesAndUnexpectedAdministratorAssignments() {
        assertTrue(StoreRoleRecoveryService.shouldRestore(null, "TRAINEE"));
        assertTrue(StoreRoleRecoveryService.shouldRestore("ADMIN", "SALES ASSOCIATE"));
        assertFalse(StoreRoleRecoveryService.shouldRestore("ADMIN", "ADMIN"));
        assertFalse(StoreRoleRecoveryService.shouldRestore("SUPERVISOR", "SALES ASSOCIATE"));
        assertFalse(StoreRoleRecoveryService.shouldRestore("MANAGER", "MANAGER"));
    }

    @Test
    void translatesCloudNumericPermissionIdsIntoStableKeys() throws Exception {
        var catalog = new java.util.LinkedHashMap<String, JsonArray>();
        catalog.put("roles", JsonParser.parseString("""
                [{"role_id":912,"role_name":"CUSTOM","description":"Test"}]
                """).getAsJsonArray());
        catalog.put("permissions", JsonParser.parseString("""
                [{"permission_id":813,"permission_key":"MAKE_SALE"}]
                """).getAsJsonArray());
        catalog.put("role_permissions", JsonParser.parseString("""
                [{"role_id":912,"permission_id":813}]
                """).getAsJsonArray());
        catalog.put("role_mobile_permissions", JsonParser.parseString("""
                [{"role_id":912,"permission_key":"MOBILE_TEST"}]
                """).getAsJsonArray());
        var policy = StoreRoleRecoveryService.policies(catalog,"2026-09-30T00:00:00Z").get(0).getAsJsonObject();
        assertEquals("MAKE_SALE", policy.getAsJsonArray("permissions").get(0).getAsString());
        assertEquals("MOBILE_TEST", policy.getAsJsonArray("mobile_permissions").get(0).getAsString());
        assertFalse(policy.has("role_id"));
    }
}
