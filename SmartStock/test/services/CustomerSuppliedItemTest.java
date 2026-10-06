package services;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CustomerSuppliedItemTest {
    @Test void trimsDetailsAndAllowsOptionalFields() {
        var item = new CustomerSuppliedItem(" Shirt ", " Blue ", " L ", " Acme ");
        assertEquals("Customer Item — Shirt", item.name());
        assertEquals("Customer-supplied item | Item Type: Shirt | Color: Blue | Size: L | Brand: Acme", item.details());
        assertEquals("Customer-supplied item | Item Type: Book", new CustomerSuppliedItem("Book", null, "", " ").details());
        assertThrows(IllegalArgumentException.class, () -> new CustomerSuppliedItem(" ", "", "", ""));
    }

    private JsonObject validLine() {
        return LanJson.create().fromJson("""
            {"customerItem":{"itemType":"Shirt","color":"Blue","size":"L","brand":"Acme"},
             "baseItemPrice":0,"originalBasePrice":0,"areaPrice":0,
             "originalLineTotal":100,"printCharge":100,
             "printAddons":[{"printCharge":60},{"printCharge":40}]}
            """, JsonObject.class);
    }
    private ServerCustomOrderDataService.OrderLineRequest decode(JsonObject line) {
        return LanJson.create().fromJson(line, ServerCustomOrderDataService.OrderLineRequest.class);
    }
    @Test void metadataSurvivesClientServerTransport() {
        var client = LanJson.create().fromJson(validLine(), CustomOrderDataService.OrderLineRequest.class);
        var server = LanJson.create().fromJson(LanJson.create().toJson(client), ServerCustomOrderDataService.OrderLineRequest.class);
        assertEquals(client.customerItem(), server.customerItem());
        assertDoesNotThrow(() -> CustomerSuppliedItem.validate(server));
    }
    @Test void rejectsStockReferencesBaseChargesAndIncorrectTotals() {
        for (String field : new String[]{"customItemId", "customVariantId", "baseItemPrice", "originalBasePrice", "areaPrice", "priceOverridePrice", "originalLineTotal", "printCharge"}) {
            var line = validLine(); line.addProperty(field, 1);
            assertThrows(IllegalArgumentException.class, () -> CustomerSuppliedItem.validate(decode(line)), field);
        }
    }
    @Test void requiresAnAddonAndRejectsNegativeCharges() {
        var line = validLine(); line.add("printAddons", new com.google.gson.JsonArray());
        var empty = decode(line);
        assertThrows(IllegalArgumentException.class, () -> CustomerSuppliedItem.validate(empty));
        line = validLine(); line.getAsJsonArray("printAddons").get(0).getAsJsonObject().addProperty("printCharge", -1);
        var invalid = decode(line);
        assertThrows(IllegalArgumentException.class, () -> CustomerSuppliedItem.validate(invalid));
    }
    @Test void legacyLinesRemainCompatible() {
        var line = validLine(); line.remove("customerItem"); line.addProperty("baseItemPrice", 200);
        assertDoesNotThrow(() -> CustomerSuppliedItem.validate(decode(line)));
    }
}
