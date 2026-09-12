package architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SaleItemTypeQuickPickArchitectureTest {
    @Test
    void preferenceIsLocationScopedOrderedAndServerEnforced() throws Exception {
        String migration=Files.readString(Path.of("database/migrations/v1_after/20260911180000_sale_quick_pick_item_types.sql"));
        String server=Files.readString(Path.of("src/services/LanApiServer.java"));
        String preferences=Files.readString(Path.of("src/ui/screens/CompanyCustomization.java"));
        assertTrue(migration.contains("sale_quick_pick_item_type_ids jsonb"));
        assertTrue(server.contains("case \"ITEM_TYPE_QUICK_PICK\""));
        assertTrue(server.contains("WHERE location_id=?"));
        assertTrue(server.contains("Quick-pick item types cannot contain duplicates"));
        assertTrue(preferences.contains("Sale Item-Type Buttons"));
        assertTrue(preferences.contains("Move Up"));
        assertTrue(preferences.contains("selectedItemTypeQuickPickIds()"));
    }
}
