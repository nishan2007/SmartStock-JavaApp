package architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CustomItemPermissionArchitectureTest {
    private static String source(String path) throws Exception { return Files.readString(Path.of(path)); }

    @Test void customCatalogGrantOpensDesktopAndIsEnforcedByServer() throws Exception {
        String permission = source("src/managers/PermissionManager.java");
        String menu = source("src/ui/screens/MainMenu.java");
        String appMenu = source("src/ui/components/AppMenuBar.java");
        String server = source("src/services/LanApiServer.java");
        assertTrue(permission.contains("case \"CustomOrderItems\" -> canManageCustomItems()"));
        assertTrue(permission.contains("hasPermission(\"MANAGE_CUSTOM_ORDER_ITEMS\")"));
        assertTrue(menu.contains("canCustomOrderItems = PermissionManager.canManageCustomItems()"));
        assertTrue(menu.contains("PermissionManager.requireCustomItemPermission(this)"));
        assertTrue(appMenu.contains("PermissionManager.requireCustomItemPermission(parent)"));
        assertFalse(menu.contains("requirePermission(\"MANUAL_ADJUSTMENT\", this, \"Custom Order Items\")"));
        assertTrue(server.contains("requireAnyPermission(c,s.userId(),\"MANAGE_CUSTOM_ORDER_ITEMS\",\"CUSTOM_ORDER_ITEMS\",\"MANAGE_CUSTOM_ORDERS\")"));
    }

    @Test void existingRoleAssignmentsKeepTheirKeyAndGetClearLabel() throws Exception {
        String migration = source("database/migrations/v1_after/20260919180000_name_custom_item_permission.sql");
        String contract = source("src/services/SchemaContractService.java");
        assertTrue(migration.contains("permission_key = 'MANAGE_CUSTOM_ORDER_ITEMS'"));
        assertTrue(migration.contains("permission_name = 'Add/Edit Custom Items'"));
        assertTrue(contract.contains("ensureCustomItemPermissionNameUpgrade(connection)"));
    }
}
