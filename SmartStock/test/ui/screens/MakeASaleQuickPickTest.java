package ui.screens;

import org.junit.jupiter.api.Test;
import services.LanApiClient;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

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
        assertTrue(source.contains("showConfiguredItemTypeProducts(option, matching, separateBySize, showPhotos)"));
        assertTrue(source.contains("showConfiguredItemTypeSizes(center,heading,option,products,showPhotos,photoPreview)"));
        assertTrue(source.contains("createActionUtilityButton(\"Back to Sizes\")"));
        assertTrue(source.contains("createServiceQuickPickButton(product)"));
        assertTrue(source.contains("ITEM_TYPE_BUTTON_ACCENTS"));
        assertTrue(source.contains("DeckersPalette.tilePressed(accent)"));
        assertTrue(source.contains("setPreferredSize(new Dimension(210, 52))"));
    }

    @Test
    void blankSizesHaveTheirOwnButton() {
        assertEquals("No Size", MakeASale.quickPickSizeName(null));
        assertEquals("No Size", MakeASale.quickPickSizeName("  "));
        assertEquals("A4", MakeASale.quickPickSizeName(" A4 "));
    }

    @Test
    void photoCardsGroupOnlyExistingInventoryVariantsAndShowTheirOptions() {
        var blue=pen(11,"Blue",new LanApiClient.VariantInfo("pen-group","Fine Pen",List.of("Color"),1,Map.of("Color","Blue")));
        var red=pen(12,"Red",new LanApiClient.VariantInfo("pen-group","Fine Pen",List.of("Color"),1,Map.of("Color","Red")));
        var unrelated=pen(13,"Black",null);
        assertEquals(MakeASale.quickPickPhotoGroupKey(blue),MakeASale.quickPickPhotoGroupKey(red));
        assertEquals("item:13",MakeASale.quickPickPhotoGroupKey(unrelated));
        assertEquals("Blue",MakeASale.quickPickVariantLabel(blue));
        var point=pen(14,"",new LanApiClient.VariantInfo("pen-group","Fine Pen",List.of("Point"),1,Map.of("Point","Fine")));
        assertEquals("Fine",MakeASale.quickPickVariantLabel(point));
    }

    private LanApiClient.CatalogProduct pen(int id,String color,LanApiClient.VariantInfo variant) {
        return new LanApiClient.CatalogProduct(id,"Fine Pen","","","SKU"+id,
                BigDecimal.valueOf(100),"INVENTORY",1,10,"Brand","image", "",variant,color,"",7,"PENS");
    }
}
