package architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MultiItemCheckoutPerformanceArchitectureTest {
    @Test
    void checkoutUsesSetBasedCatalogPermissionsAndInventoryUpdates() throws Exception {
        String source = Files.readString(Path.of("src/services/LanSalesService.java"));

        assertTrue(source.contains("Set<String> permissions = loadPermissions(connection, userId)"));
        assertTrue(source.contains("Map<Integer, CatalogLine> catalogs = lockCatalogLines(connection, productIds)"));
        assertTrue(source.contains("WHERE p.product_id IN (%s) ORDER BY p.product_id FOR UPDATE OF p"));
        assertTrue(source.contains("WITH adjusted AS ("));
        assertTrue(source.contains("quantity_on_hand=inventory.quantity_on_hand+EXCLUDED.quantity_on_hand"));
        assertTrue(source.contains("WITH inserted AS ("));
        assertTrue(source.contains("RETURNING sale_item_id,product_id"));
        assertTrue(source.contains("insertSaleItemAndAudit(connection"));
        assertFalse(source.contains("catalogs.put(productId, lockCatalogLine(connection, productId))"));
        assertFalse(source.contains("validateMiscLine(connection, userId, requested, catalog)"));
        assertFalse(source.contains("int saleItemId = insertSaleItem(connection"));
    }
}
