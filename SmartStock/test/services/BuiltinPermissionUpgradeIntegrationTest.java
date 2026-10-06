package services;

import org.junit.jupiter.api.Test;
import java.sql.DriverManager;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class BuiltinPermissionUpgradeIntegrationTest {
    @Test
    void existingStoreMissingManualEntryPermissionRepairsAndStarts() throws Exception {
        String url = System.getProperty("smartstock.permission.test.jdbc", "");
        assumeTrue(!url.isBlank()); // A dedicated disposable database only.
        try (var connection = DriverManager.getConnection(url, "permission_test", "")) {
            SchemaContractService.installLocalBaseline(connection);
            try (var statement = connection.createStatement()) {
                statement.executeUpdate("DELETE FROM role_permissions WHERE permission_id IN (SELECT permission_id FROM permissions WHERE permission_key='MANUAL_CUSTOM_ORDER_ENTRY')");
                statement.executeUpdate("DELETE FROM permissions WHERE permission_key='MANUAL_CUSTOM_ORDER_ENTRY'");
            }
            SchemaContractService.requireLocalReady(connection);
            try (var statement = connection.createStatement(); var rows = statement.executeQuery("""
                    SELECT COUNT(*) FROM permissions p JOIN role_permissions rp USING(permission_id)
                    JOIN roles r USING(role_id)
                    WHERE p.permission_key='MANUAL_CUSTOM_ORDER_ENTRY' AND UPPER(r.role_name)='ADMIN'
                    """)) {
                assertTrue(rows.next());
                assertEquals(1, rows.getInt(1));
            }
            assertTrue(SchemaContractService.validateLocal(connection).ready());
            SchemaContractService.requireLocalReady(connection);
        }
    }
}
