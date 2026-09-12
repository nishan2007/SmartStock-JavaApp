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
        assertTrue(source.contains("showServiceQuickPickGroups(center, heading, serviceQuickPickProducts(cached.get()))"));
        assertTrue(source.contains("serviceQuickPickProducts(InventoryCatalogCache.warmIfNeeded().get())"));
        assertTrue(source.contains("\"SERVICE\".equalsIgnoreCase(product.productType())"));
        assertTrue(source.contains("Collectors.groupingBy(this::quickPickItemTypeName"));
        assertTrue(source.contains("showServiceQuickPickItems(center, heading, products, itemType)"));
        assertTrue(source.contains("createActionUtilityButton(\"Back to Item Types\")"));
        assertTrue(source.contains("product.itemTypeName()"));
        assertTrue(source.contains("addCatalogProductToCart(product, 1)"));
        assertTrue(source.contains("confirmationTimer.setRepeats(false)"));
    }

    @Test
    void configuredItemTypesUseAHiddenLeftLauncherAndStableIds() throws Exception {
        String source = Files.readString(Path.of("src/ui/screens/MakeASale.java"));
        assertTrue(source.contains("cartAndLauncher.add(itemTypeLauncher, BorderLayout.WEST)"));
        assertTrue(source.contains("itemTypeLauncher.setVisible(false)"));
        assertTrue(source.contains("product.itemTypeId() != null"));
        assertTrue(source.contains("groupingBy(LanApiClient.CatalogProduct::itemTypeId)"));
        assertTrue(source.contains("showConfiguredItemTypeProducts(option, matching)"));
        assertTrue(source.contains("createServiceQuickPickButton(product)"));
    }
}
