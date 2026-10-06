package architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PriceTagPrintingQuantityArchitectureTest {
    private static String source(String path) throws Exception { return Files.readString(Path.of(path)); }

    @Test void priceTagSearchShowsCurrentQuantityForEveryCatalogItemType() throws Exception {
        String screen = source("src/ui/screens/PriceTagPrinting.java");
        String service = source("src/services/LanProductAdminService.java");
        String client = source("src/services/LanApiClient.java");
        assertTrue(screen.contains("\"Current Qty\""));
        assertTrue(screen.contains("item.quantity()"));
        assertTrue(screen.contains("\"Variant\""));
        assertTrue(screen.contains("\"Color\""));
        assertTrue(screen.contains("\"Brand\""));
        assertTrue(screen.contains("PriceTagItem(String.valueOf(cart.getValueAt(r,0))"));
        assertTrue(service.contains("current_quantity"));
        assertTrue(service.contains("COALESCE(coi.quantity_on_hand,0)"));
        assertTrue(service.contains("COALESCE(v.quantity_on_hand,0)"));
        assertTrue(service.contains("NULLIF(BTRIM(v.size),'')"));
        assertTrue(service.contains("NULLIF(BTRIM(v.color),'')"));
        assertTrue(service.contains("COALESCE(v.brand_id,coi.brand_id)"));
        assertTrue(client.contains("String variantName,String size,String color,String brand,String description,String code"));
        assertTrue(client.contains("BigDecimal price,BigDecimal quantity,long itemId"));

        String printer=Files.readString(Path.of("src/services/PriceTagPrintService.java"));
        assertTrue(printer.contains("case \"size\", \"color\" -> s.showSize()"));
        assertTrue(printer.contains("case \"color\" -> item.color()"));
        assertTrue(printer.contains("r.put(\"color\""));
    }
}
