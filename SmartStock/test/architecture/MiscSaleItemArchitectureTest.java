package architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class MiscSaleItemArchitectureTest {
    private static String read(String path) throws Exception { return Files.readString(Path.of(path)); }

    @Test void miscItemsArePermissionGatedAndServerValidated() throws Exception {
        String ui=read("src/ui/screens/MakeASale.java");
        String sales=read("src/services/LanSalesService.java");
        assertTrue(ui.contains("ADD_MISC_SALE_ITEM_PERMISSION"));
        assertTrue(ui.contains("Add Misc Item"));
        assertTrue(sales.contains("validateMiscLine(canAddMiscItem, requested, catalog)"));
        assertTrue(sales.contains("permissions.contains(\"ADD_MISC_SALE_ITEM\")"));
        assertTrue(sales.contains("requested.miscItem() ? BigDecimal.ZERO"));
        assertTrue(sales.contains("SMARTSTOCK-MISC"));
    }

    @Test void miscNamesPersistAndReturnsExcludeThem() throws Exception {
        String migration=read("database/migrations/v1_after/20260826180000_misc_sale_items.sql");
        String refund=read("src/services/LanRefundService.java");
        String receipt=read("src/services/LanDocumentDataService.java");
        assertTrue(migration.contains("item_name text"));
        assertTrue(migration.contains("is_misc_item boolean"));
        assertTrue(refund.contains("NOT si.is_misc_item"));
        assertTrue(receipt.contains("si.item_name"));
    }

    @Test void existingStoresInstallTheMigrationBeforeContractValidation() throws Exception {
        String installer=read("src/services/LanApiSchemaInstaller.java");
        String contract=read("src/services/SchemaContractService.java");
        String migration=read("database/migrations/v1_after/20260826180000_misc_sale_items.sql");
        assertTrue(installer.indexOf("ensureMiscSaleItemsUpgrade(connection)")
                < installer.indexOf("requireLocalReady(connection)"));
        assertTrue(contract.indexOf("ensureMiscSaleItemsUpgrade(connection)",contract.indexOf("requireLocalReady"))>0);
        assertFalse(migration.contains("ON CONFLICT (sku)"));
        assertTrue(migration.contains("WHERE NOT EXISTS"));
    }
}
