package architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductArchivingArchitectureTest {
    @Test
    void productArchiveIsServerEnforcedAuditedAndReversible() throws Exception {
        String service=Files.readString(Path.of("src/services/LanProductAdminService.java"));
        String server=Files.readString(Path.of("src/services/LanApiServer.java"));
        String migration=Files.readString(Path.of("database/migrations/v1_after/20260831170000_product_archiving.sql"));

        assertTrue(service.contains("requirePermission(connection,userId,\"PRODUCT_ARCHIVE\")"));
        assertTrue(service.contains("i.quantity_on_hand<>0 FOR UPDATE OF i"));
        assertTrue(service.contains("PRODUCT_ARCHIVED"));
        assertTrue(service.contains("PRODUCT_RESTORED"));
        assertTrue(server.contains("/v1/products/archive"));
        assertTrue(server.contains("/v1/products/restore"));
        assertTrue(migration.contains("product_lifecycle_audit"));
        assertTrue(migration.contains("PRODUCT_ARCHIVE"));
        String contract=Files.readString(Path.of("src/services/SchemaContractService.java"));
        assertTrue(contract.indexOf("ensureProductArchivingUpgrade(connection)")
                < contract.indexOf("Readiness readiness = validateLocal(connection)"));
        assertTrue(contract.contains("20260831170000_product_archiving.sql"));
    }

    @Test
    void activeCatalogSearchesExcludeArchivedProducts() throws Exception {
        String search=Files.readString(Path.of("src/services/ProductSearchHelper.java"));
        String sales=Files.readString(Path.of("src/services/LanSalesService.java"));
        String inventory=Files.readString(Path.of("src/services/LanInventoryService.java"));

        assertTrue(search.contains(".is_active = TRUE"));
        assertTrue(sales.contains("A cart product was archived"));
        assertTrue(sales.contains("!\"SMARTSTOCK-MISC\".equals(sku)"));
        assertTrue(sales.contains("\"PRODUCT_ARCHIVED\""));
        assertTrue(inventory.contains("p.is_active=TRUE"));
    }
}
