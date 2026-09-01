package architecture;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class InventoryInlineEditingArchitectureTest {
    @Test void enterEditsAndSavesWithServerEnforcedPermissions() throws Exception {
        String screen=Files.readString(Path.of("src/ui/screens/ViewInventory.java"));
        String client=Files.readString(Path.of("src/services/LanApiClient.java"));
        String server=Files.readString(Path.of("src/services/LanProductAdminService.java"));
        String api=Files.readString(Path.of("src/services/LanApiServer.java"));
        assertTrue(screen.contains("WHEN_ANCESTOR_OF_FOCUSED_COMPONENT"));
        assertTrue(screen.contains("inventoryTable.editCellAt"));
        assertTrue(screen.contains("stopCellEditing()"));
        assertTrue(screen.contains("PermissionManager.hasPermission(\"EDIT_ITEM\")"));
        assertTrue(screen.contains("PermissionManager.hasPermission(\"MANUAL_ADJUSTMENT\")"));
        assertTrue(client.contains("/v1/products/inline-update"));
        assertTrue(api.contains("products.inline-update.v1"));
        assertTrue(server.contains("requirePermission(connection,userId,\"EDIT_ITEM\")"));
        assertTrue(server.contains("requirePermission(connection,userId,\"MANUAL_ADJUSTMENT\")"));
        assertTrue(server.contains("\"STOCK_CHANGED\""));
        assertTrue(server.contains("LAN_PRODUCT_INLINE_UPDATED"));
    }
}
