package architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AdvancedReturnLookupArchitectureTest {
    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path));
    }

    @Test void permissionIsSeededForAdminAndEnforcedByTheServerService() throws Exception {
        String seed = read("database/v1/local/002_seed.sql");
        String migration = read("database/migrations/v1_after/20260820220000_complete_builtin_permissions.sql");
        String local = read("src/services/LanRefundService.java");
        String remote = read("src/services/CrossStoreSalesService.java");

        assertTrue(seed.contains("'ADVANCED_RETURN_LOOKUP'"));
        assertTrue(seed.contains("UPPER(r.role_name)='ADMIN' AND p.permission_key='ADVANCED_RETURN_LOOKUP'"));
        assertTrue(migration.contains("'ADVANCED_RETURN_LOOKUP'"));
        assertTrue(local.contains("requirePermission(connection, userId, \"ADVANCED_RETURN_LOOKUP\")"));
        assertTrue(remote.contains("hasPermission(c, userId, \"ADVANCED_RETURN_LOOKUP\")"));
    }

    @Test void lookupCombinesStoreDateAndReceiptItemMatch() throws Exception {
        String local = read("src/services/LanRefundService.java");
        String ui = read("src/ui/screens/ReturnSale.java");
        String server = read("src/services/LanApiServer.java");

        assertTrue(local.contains("AT TIME ZONE"));
        assertTrue(local.contains("EXISTS ("));
        assertTrue(local.contains("sale_items si"));
        assertTrue(local.contains("product_barcodes pb"));
        assertTrue(ui.contains("PermissionManager.hasPermission(\"ADVANCED_RETURN_LOOKUP\")"));
        assertTrue(ui.contains("advancedSearchSalesForReturn"));
        assertTrue(server.contains("/v1/sales/advanced-return-search"));
    }
}
