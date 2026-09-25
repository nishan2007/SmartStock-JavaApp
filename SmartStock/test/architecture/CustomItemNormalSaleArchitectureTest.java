package architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CustomItemNormalSaleArchitectureTest {
    private static final Path ROOT=Path.of(System.getProperty("user.dir"));

    @Test void optInFlowsFromBothEditorsToServerEnforcedPosCheckout()throws Exception{
        String admin=Files.readString(ROOT.resolve("src/services/LanCustomOrderCatalogAdminService.java"));
        String api=Files.readString(ROOT.resolve("src/services/LanApiServer.java"));
        String sales=Files.readString(ROOT.resolve("src/services/LanSalesService.java"));
        String desktop=Files.readString(ROOT.resolve("src/ui/screens/customorders/CustomOrderItems.java"));
        String mobile=Files.readString(ROOT.resolve("src/mobile-web/app.js"));
        assertTrue(admin.contains("sell_in_pos=?"));
        assertTrue(desktop.contains("Sell in normal POS (without printing)"));
        assertTrue(mobile.contains("'sellInPos','Sell in normal POS (without printing)'"));
        assertTrue(api.contains("ci.sell_in_pos AND ci.is_active"));
        assertTrue(api.contains("column_name='sell_in_pos'"));
        assertTrue(sales.contains("ci.sell_in_pos AND ci.is_active"));
        assertTrue(sales.contains("applyCustomInventory"));
    }

    @Test void schemasAndReturnsRetainCustomSaleIdentity()throws Exception{
        String base=Files.readString(ROOT.resolve("database/v1/local/001_schema.sql"));
        String migration=Files.readString(ROOT.resolve("database/migrations/v1_after/20260921120000_custom_items_in_pos.sql"));
        String refund=Files.readString(ROOT.resolve("src/services/LanRefundService.java"));
        for(String source:new String[]{base,migration}){
            assertTrue(source.contains("sell_in_pos"));
            assertTrue(source.contains("catalog_source"));
            assertTrue(source.contains("custom_variant_id"));
            assertTrue(source.contains("size_snapshot"));
        }
        assertTrue(refund.contains("restoreCustomInventory"));
        String contract=Files.readString(ROOT.resolve("src/services/SchemaContractService.java"));
        assertTrue(contract.contains("ensureCustomItemsInPosUpgrade(connection)"));
        assertTrue(contract.contains("20260921120000_custom_items_in_pos.sql"));
    }
}
