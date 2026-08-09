package services;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class CleanDatabaseProvisioningIntegrationTest {
    @Test
    void completePackagedSetupRunsAgainstCleanPostgres() throws Exception {
        String jdbcUrl = System.getProperty("smartstock.test.jdbc", "");
        String user = System.getProperty("smartstock.test.dbUser", "");
        String password = System.getProperty("smartstock.test.dbPassword", "");
        assumeTrue(!jdbcUrl.isBlank() && !user.isBlank(),
                "Set the isolated PostgreSQL integration-test properties to run this test.");

        try (Connection connection = DriverManager.getConnection(jdbcUrl, user, password)) {
            SchemaContractService.installLocalBaseline(connection);
            assertTrue(SchemaContractService.validateLocal(connection).ready());
            if (Boolean.getBoolean("smartstock.test.cloudStoreRestore")) {
                verifyConfiguredCloudStoreRestore(connection);
            }
            verifyCloudReferenceIdsAreReconciled(connection);
        }
    }

    private static void verifyConfiguredCloudStoreRestore(Connection connection) throws Exception {
        var stores = ServerStoreSetupService.listCloud();
        assertFalse(stores.isEmpty());
        var store = stores.get(0);
        CloudRecoveryService.restoreStoreMirror(connection, store.locationId(),
                CloudSyncManifest.fetchStoreSnapshot(store.locationId()));
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT COUNT(*) FROM locations WHERE location_id=?")) {
            ps.setInt(1, store.locationId());
            try (var rs = ps.executeQuery()) {
                rs.next();
                assertEquals(1, rs.getInt(1));
            }
        }
        try (PreparedStatement ps = connection.prepareStatement("""
                SELECT COUNT(*) FILTER (WHERE UPPER(r.role_name)='ADMIN'),
                       COUNT(*) FILTER (WHERE UPPER(r.role_name)='ADMIN'
                           AND u.auth_user_id IS NOT NULL),
                       COUNT(*) FILTER (WHERE UPPER(r.role_name)='ADMIN'
                           AND u.auth_user_id IS NOT NULL AND ul.user_id IS NOT NULL)
                FROM users u
                JOIN roles r ON r.role_id=u.role_id
                LEFT JOIN user_locations ul ON ul.user_id=u.user_id
                    AND ul.location_id=?
                WHERE COALESCE(u.is_active,TRUE)=TRUE
                """)) {
            ps.setInt(1, store.locationId());
            try (var rs = ps.executeQuery()) {
                rs.next();
                System.out.printf("Restored administrator evidence: admins=%d authLinked=%d storeLinked=%d%n",
                        rs.getInt(1), rs.getInt(2), rs.getInt(3));
                assertTrue(rs.getInt(3) > 0, "Existing store must restore an Auth-linked administrator");
            }
        }
    }

    private static void verifyCloudReferenceIdsAreReconciled(Connection connection)
            throws Exception {
        var roles = JsonParser.parseString("""
                [{"role_id":9999,"role_name":"ADMIN","description":"Cloud admin"}]
                """).getAsJsonArray();
        var permissions = JsonParser.parseString("""
                [{"permission_id":9999,"permission_key":"DEVICE_MANAGEMENT",
                  "permission_name":"Device Management"}]
                """).getAsJsonArray();
        var rolePermissions = JsonParser.parseString("""
                [{"role_id":9999,"permission_id":9999,
                  "updated_at":"2026-08-06T12:00:00Z"}]
                """).getAsJsonArray();
        var users = JsonParser.parseString("""
                [{"user_id":9999,"username":"cloud-restore-test",
                  "full_name":"Cloud Restore Test","role_id":9999}]
                """).getAsJsonArray();

        CloudRecoveryService.restoreReferenceRows(connection, Map.of(
                "roles", roles,
                "permissions", permissions,
                "role_permissions", rolePermissions,
                "users", users));

        try (PreparedStatement ps = connection.prepareStatement("""
                SELECT COUNT(*)
                FROM role_permissions rp
                JOIN roles r ON r.role_id=rp.role_id
                JOIN permissions p ON p.permission_id=rp.permission_id
                WHERE r.role_name='ADMIN' AND p.permission_key='DEVICE_MANAGEMENT'
                """); var rs = ps.executeQuery()) {
            rs.next();
            assertEquals(1, rs.getInt(1));
        }
        try (PreparedStatement ps = connection.prepareStatement("""
                SELECT r.role_name FROM users u JOIN roles r ON r.role_id=u.role_id
                WHERE u.username='cloud-restore-test'
                """); var rs = ps.executeQuery()) {
            rs.next();
            assertEquals("ADMIN", rs.getString(1));
        }
    }

}
