package architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class QuickPickUsageOrderingArchitectureTest {
    @Test
    void serviceQuickPicksAreOrderedByStoreSalesUsage() throws Exception {
        String source = Files.readString(Path.of("src/services/LanApiServer.java"));

        assertTrue(source.contains("searchText.isBlank() && \"SERVICE\".equals(productType)"));
        assertTrue(source.contains("SUM(si.quantity) AS quantity_sold"));
        assertTrue(source.contains("WHERE s.location_id = ?"));
        assertTrue(source.contains("COALESCE(usage.quantity_sold, 0) DESC, p.name, p.product_id"));
    }
}
