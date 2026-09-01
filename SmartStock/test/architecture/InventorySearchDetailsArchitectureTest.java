package architecture;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class InventorySearchDetailsArchitectureTest {
    @Test void editAndReceivingResultsShowDetailsAndHoverImages() throws Exception {
        String edit=Files.readString(Path.of("src/ui/screens/EditItem.java"));
        String receiving=Files.readString(Path.of("src/ui/screens/EnterInventory.java"));
        String service=Files.readString(Path.of("src/services/LanInventoryService.java"));
        String client=Files.readString(Path.of("src/services/LanApiClient.java"));

        assertTrue(edit.contains("TableImageHoverPreview.install(this, table, 15"));
        assertTrue(edit.contains("int[] hiddenColumns = {0, 5, 6, 10, 11, 13, 15, 18, 19}"));
        for(String heading:new String[]{"Item Type", "Brand", "Price", "Image URL"})
            assertTrue(receiving.contains("\""+heading+"\""));
        assertTrue(receiving.contains("TableImageHoverPreview.install(this, searchResultsTable, 9"));
        assertTrue(service.contains("COALESCE(it.name,'') item_type_name"));
        assertTrue(service.contains("COALESCE(ib.name,'') brand_name"));
        assertTrue(client.contains("String itemTypeName,String brandName,BigDecimal price,String imageUrl"));
    }
}
