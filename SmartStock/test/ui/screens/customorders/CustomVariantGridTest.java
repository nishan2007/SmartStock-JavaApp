package ui.screens.customorders;

import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import services.LanCustomOrderCatalogAdminService.Item;
import services.LanJson;

import javax.swing.SwingUtilities;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

class CustomVariantGridTest {
    @Test void bothViewsCommitEditsToTheCorrectRecordAfterSorting() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            Item item = LanJson.create().fromJson("""
                {"itemId":1,"name":"Book","brand":"Deckers","pricingType":"AREA","variants":[
                  {"itemId":1,"variantId":10,"name":"Zebra","brand":"","quantity":12,"extraBarcodes":[],"additionalImageUrls":[]},
                  {"itemId":1,"variantId":20,"name":"Aster","brand":"","quantity":2,"extraBarcodes":[],"additionalImageUrls":[]}
                ]}
                """, Item.class);
            var ids = new ArrayList<Long>();
            var changes = new ArrayList<JsonObject>();
            var table = new CustomVariantGrid(null, item, (variant, update) -> {
                ids.add(variant.variantId());
                changes.add(update);
            });
            assertEquals(11, table.getColumnCount());
            assertFalse(table.isCellEditable(0, 0));
            assertFalse(table.isCellEditable(0, 5));
            assertTrue(table.isCellEditable(0, 1));
            assertTrue(table.isCellEditable(0, 6));
            table.getRowSorter().toggleSortOrder(8);
            table.setValueAt("Renamed", 0, 1);
            assertEquals(20L, ids.get(0));
            assertEquals("Renamed", changes.get(0).get("name").getAsString());
            table.setFullScreen(true);
            assertEquals(17, table.getColumnCount());
            assertFalse(table.isCellEditable(0, 0));
            assertFalse(table.isCellEditable(0, 5));
            assertEquals("Template width", table.getColumnName(12));
            assertEquals("Template height", table.getColumnName(13));
            assertEquals("Template unit", table.getColumnName(14));
            assertTrue(table.isCellEditable(0, 12));
            assertTrue(table.isCellEditable(0, 13));
            assertTrue(table.isCellEditable(0, 16));
            table.setValueAt("3.5", 0, 8);
            assertEquals(20L, ids.get(1));
            assertEquals("3.5", changes.get(1).get("quantity").getAsString());
            assertEquals("2", table.getValueAt(0, 8));
            table.setEditable(false);
            assertFalse(table.isCellEditable(0, 8));
            table.setFullScreen(false);
            assertEquals(11, table.getColumnCount());
            table.setEditable(true);
            assertTrue(table.isCellEditable(0, 1));
        });
    }

    @Test void clearingAnOverrideSurvivesLanSerialization() {
        JsonObject fields = new JsonObject();
        fields.add("templateUsesOrderArea", JsonNull.INSTANCE);
        var wire = JsonParser.parseString(LanJson.create().toJson(CustomOrderItems.wireCells(fields))).getAsJsonObject();
        assertTrue(wire.getAsJsonArray("templateUsesOrderArea").get(0).isJsonNull());
    }
}
