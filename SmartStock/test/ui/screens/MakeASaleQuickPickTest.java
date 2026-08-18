package ui.screens;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class MakeASaleQuickPickTest {
    @Test
    void quickPickUsesServiceFilteredCatalogAndExistingCartPath() throws Exception {
        String source = Files.readString(Path.of("src/ui/screens/MakeASale.java"));

        assertTrue(source.contains("createActionUtilityButton(\"Quick Pick Items\")"));
        assertTrue(source.indexOf("actionPanel.add(quickPickItemsBtn)")
                < source.indexOf("actionPanel.add(removeCartItemBtn)"));
        assertTrue(source.contains("LanApiClient.searchCatalog(\"\", \"SERVICE\")"));
        assertTrue(source.contains("addCatalogProductToCart(product, 1)"));
        assertTrue(source.contains("confirmationTimer.setRepeats(false)"));
    }
}
