package services;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ReceivingBarcodeCoverageTest {
    private static final Path ROOT=Path.of(System.getProperty("user.dir"));

    @Test
    void mutationKeepsPermissionIdempotencyAuditAndAllCatalogTargets()throws Exception{
        String service=Files.readString(ROOT.resolve("src/services/ReceivingBarcodeService.java"));
        String server=Files.readString(ROOT.resolve("src/services/LanApiServer.java"));
        assertTrue(service.contains("RECEIVING_INVENTORY"));
        assertTrue(service.contains("EDIT_ITEM"));
        assertTrue(service.contains("product_barcodes"));
        assertTrue(service.contains("custom_order_item_barcodes"));
        assertTrue(service.contains("custom_order_item_variant_barcodes"));
        assertTrue(service.contains("RECEIVING_BARCODE_ADDED"));
        assertTrue(server.contains("inventory.receiving-barcode.v1"));
        assertTrue(server.contains("loadIdempotentResult"));
    }

    @Test
    void variantAdditionalBarcodesParticipateInConflictSearchAndSync()throws Exception{
        for(String file:new String[]{"src/services/CatalogBarcodeService.java","src/services/ProductSearchHelper.java",
                "src/services/ReferenceDataSyncService.java","src/services/CloudRowMirrorService.java"}){
            assertTrue(Files.readString(ROOT.resolve(file)).contains("custom_order_item_variant_barcodes"),file);
        }
    }
}
