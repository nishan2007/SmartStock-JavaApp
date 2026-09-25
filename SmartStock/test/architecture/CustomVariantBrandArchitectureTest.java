package architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CustomVariantBrandArchitectureTest {
    private static String source(String path)throws Exception{return Files.readString(Path.of(path));}

    @Test void schemaAndInstallerIncludeVariantBrand()throws Exception{
        String baseline=source("database/v1/local/001_schema.sql");
        String migration=source("database/migrations/v1_after/20260919120000_custom_variant_brand.sql");
        String contract=source("src/services/SchemaContractService.java");
        assertTrue(baseline.contains("custom_order_item_variants_brand_id_fkey"));
        assertTrue(migration.contains("ADD COLUMN IF NOT EXISTS brand_id"));
        assertTrue(contract.contains("ensureCustomVariantBrandUpgrade(connection)"));
    }

    @Test void catalogFormsAndSearchUseVariantBrand()throws Exception{
        String catalog=source("src/services/LanCustomOrderCatalogAdminService.java");
        String desktop=source("src/ui/screens/customorders/CustomOrderItems.java");
        String mobile=source("src/mobile-web/app.js");
        assertTrue(catalog.contains("ItemDetailsService.resolveBrand(c,r.brand())"));
        assertTrue(catalog.contains("String brand,String barcode"));
        assertTrue(desktop.contains("Brand (blank inherits item)"));
        assertTrue(mobile.contains("lookup('brand','Brand (blank inherits item)'"));
        assertTrue(mobile.contains("brand:val(f,'brand')"));
        assertTrue(source("src/services/ProductSearchHelper.java").contains("%2$s.brand_id"));
    }
}
