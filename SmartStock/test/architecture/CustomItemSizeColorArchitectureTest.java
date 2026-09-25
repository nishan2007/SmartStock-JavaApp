package architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CustomItemSizeColorArchitectureTest {
    private static String source(String path)throws Exception{return Files.readString(Path.of(path));}

    @Test void schemaAndContractInstallCatalogAndSnapshotColumns()throws Exception{
        String baseline=source("database/v1/local/001_schema.sql");
        String migration=source("database/migrations/v1_after/20260918120000_custom_item_size_color.sql");
        String contract=source("src/services/SchemaContractService.java");
        for(String column:new String[]{"ADD COLUMN IF NOT EXISTS size text","ADD COLUMN IF NOT EXISTS color text","ADD COLUMN IF NOT EXISTS item_size text","ADD COLUMN IF NOT EXISTS item_color text"})assertTrue(migration.contains(column),column);
        assertTrue(baseline.contains("item_size text"));
        assertTrue(baseline.contains("item_color text"));
        assertTrue(contract.contains("20260918120000_custom_item_size_color.sql"));
    }

    @Test void desktopMobileAndSearchExposeBothIdentifiers()throws Exception{
        String desktop=source("src/ui/screens/customorders/CustomOrderItems.java");
        String mobile=source("src/mobile-web/app.js");
        String search=source("src/services/ProductSearchHelper.java");
        assertTrue(desktop.contains("row(f,r++,\"Size\",size)"));
        assertTrue(desktop.contains("row(f,r++,\"Color\",color)"));
        assertTrue(mobile.contains("input('size','Size'"));
        assertTrue(mobile.contains("input('color','Color'"));
        assertTrue(search.contains(".size"));
        assertTrue(search.contains(".color"));
    }

    @Test void variantsFallBackIndependentlyAndOrdersSnapshotEffectiveValues()throws Exception{
        String catalog=source("src/services/ServerCustomOrderDataService.java");
        String quotation=source("src/services/ServerQuotationInvoiceService.java");
        assertTrue(catalog.contains("COALESCE(NULLIF(BTRIM(v.size), ''), NULLIF(BTRIM(i.size), ''))"));
        assertTrue(catalog.contains("COALESCE(NULLIF(BTRIM(v.color), ''), NULLIF(BTRIM(i.color), ''))"));
        assertTrue(catalog.contains("item_size, item_color"));
        assertTrue(catalog.contains("resolveItemAttributes"));
        assertTrue(quotation.contains("withEffectiveAttributes"));
        assertTrue(quotation.contains("String itemSize,String itemColor"));
    }
}
