package architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class InventoryPriceRoundingWorkflowArchitectureTest {
    @Test
    void reviewAndUpdateArePermissionCheckedAndServerEnforced() throws Exception {
        String service=source("src/services/LanProductAdminService.java");
        String screen=source("src/ui/screens/InventoryPriceRoundingDialog.java");

        assertTrue(service.contains("requirePermission(connection,userId,\"EDIT_ITEM\")"));
        assertTrue(service.contains("MOD(COALESCE(price,0),20)<>0"));
        assertTrue(service.contains("replacement.remainder(BigDecimal.valueOf(20))"));
        assertTrue(service.contains("AND COALESCE(price,0)=?"),
                "Bulk updates must reject prices changed since the review was loaded.");
        assertTrue(screen.contains("Update Selected Prices"));
        assertTrue(screen.contains("PriceRoundingLine"));
    }

    private static String source(String relativePath)throws Exception{
        return Files.readString(Path.of(System.getProperty("user.dir")).resolve(relativePath));
    }
}
