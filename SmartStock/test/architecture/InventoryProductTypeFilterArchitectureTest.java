package architecture;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class InventoryProductTypeFilterArchitectureTest {
    @Test void overviewCanShowOrHideEachProductClassification() throws Exception {
        String screen=Files.readString(Path.of("src/ui/screens/ViewInventory.java"));
        String client=Files.readString(Path.of("src/services/LanApiClient.java"));
        String server=Files.readString(Path.of("src/services/LanInventoryService.java"));
        for(String option:new String[]{"Inventory Only","Service Only","Non Inventory Only",
                "Hide Inventory","Hide Services","Hide Non Inventory"})
            assertTrue(screen.contains("\""+option+"\""));
        assertTrue(screen.contains("selectedFilter(productTypeFilterCombo, \"All Product Types\")"));
        assertTrue(client.contains("String department,String productType,String itemType"));
        assertTrue(server.contains("appendProductTypeFilter(sql, productType)"));
        assertTrue(server.contains("case \"Hide Services\""));
    }
}
