package architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CustomVariantPersistenceArchitectureTest {
    private static String source(String path)throws Exception{return Files.readString(Path.of(path));}

    @Test void variantsMayShareANameWhenTheirOptionsDiffer()throws Exception{
        String baseline=source("database/v1/local/001_schema.sql");
        String migration=source("database/migrations/v1_after/20260924120000_allow_same_name_custom_variants.sql");
        String contract=source("src/services/SchemaContractService.java");
        assertFalse(baseline.contains("custom_order_item_variants_item_name_uidx UNIQUE"));
        assertTrue(migration.contains("DROP CONSTRAINT IF EXISTS custom_order_item_variants_item_name_uidx"));
        assertTrue(contract.contains("ensureSameNameCustomVariantsUpgrade(connection)"));
    }

    @Test void imageUploadFailureDoesNotTriggerGlobalConnectionLock()throws Exception{
        String client=source("src/services/LanApiClient.java");
        String helper=source("src/ui/helpers/ProductImageHelper.java");
        String server=source("src/services/LanApiServer.java");
        assertTrue(client.contains("IMAGE_UPLOAD_TIMEOUT"));
        assertTrue(client.contains("if (imageUpload)"));
        assertTrue(client.contains("IMAGE_UPLOAD_UNAVAILABLE"));
        assertTrue(helper.contains("MAX_PRODUCT_UPLOAD_BYTES,"));
        assertFalse(helper.contains("MAX_PRODUCT_UPLOAD_BYTES,\r\n                false"));
        assertFalse(helper.contains("MAX_PRODUCT_UPLOAD_BYTES,\n                false"));
        assertFalse(server.substring(server.indexOf("private ApiResult uploadCloudFile"),server.indexOf("private ApiResult fetchImageAsset")).contains("ServerImageAssetService.synchronize"));
    }

    @Test void variantEditorCanExpandToTheUsableScreen()throws Exception{
        String screen=source("src/ui/screens/customorders/CustomOrderItems.java");
        String design=source("src/ui/screens/customorders/CustomCatalogDesign.java");
        assertTrue(screen.contains("expand = fullScreen(dialog, this::finishEditing"));
        assertTrue(screen.contains("table.setFullScreen(full)"));
        assertTrue(screen.contains("photo, fullScreen(dialog)"));
        assertTrue(design.contains("dialog.setBounds(usableBounds(dialog))"));
        assertTrue(design.contains("dialog.setBounds(restoreBounds)"));
    }

    @Test void variantEditorStaysOpenForConsecutiveAdds()throws Exception{
        String screen=source("src/ui/screens/customorders/CustomOrderItems.java");
        String save=screen.substring(screen.indexOf("private void save()"),screen.indexOf("private void deactivate()"));
        assertFalse(save.contains("dialog.dispose()"));
        assertTrue(save.contains("reloadAfterSaved(dialog, () ->"));
        assertTrue(save.contains("reset()"));
        assertTrue(save.contains("Variant saved. You can add another variant now."));
    }
}
