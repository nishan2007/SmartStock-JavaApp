package ui.screens.customorders;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OrdersAddonInstructionsTest {
  @Test void itemDescriptionsShowWorkTypeAndOnlyTheirOwnAddOns() {
    JsonObject data = fixture();
    JsonObject shirt = data.getAsJsonArray("lines").get(0).getAsJsonObject();
    assertEquals("Customer shirt\nEMB · Small chest · Left chest: Deckers logo in white",
        OrdersDetailWorkspace.itemDescription(data, shirt));
    JsonObject polo = data.getAsJsonArray("lines").get(2).getAsJsonObject();
    assertEquals("Polo\nVINYL · A4 · Back: STAFF", OrdersDetailWorkspace.itemDescription(data, polo));
    data.getAsJsonArray("addons").get(2).getAsJsonObject().addProperty("print_material_name", "transfer_paper");
    assertEquals("Polo\nTransfer Paper · A4 · Back: STAFF", OrdersDetailWorkspace.itemDescription(data, polo));
    data.add("addons", new JsonArray());
    assertEquals("Polo", OrdersDetailWorkspace.itemDescription(data, polo));
  }
  private JsonObject fixture(){return JsonParser.parseString("""
    {"summary":{"order_notes":"Keep sizes labelled."},"lines":[
      {"custom_order_line_id":1,"item_name":"Customer shirt","item_size":"2XL","item_color":"White","customization_details":"Customer supplied","order_instructions":"Fold individually"},
      {"custom_order_line_id":2,"item_name":"Customer shirt","item_size":"2XL","item_color":"White","customization_details":"Customer supplied","order_instructions":"Fold individually"},
      {"custom_order_line_id":3,"item_name":"Polo","item_size":"M","item_color":"Navy"}],
     "addons":[
      {"custom_order_line_id":1,"print_material_name":"Embroidery","print_size_name":"Small chest","print_description":"Left chest: Deckers logo in white"},
      {"custom_order_line_id":2,"print_material_name":"Embroidery","print_size_name":"Small chest","print_description":"Left chest: Deckers logo in white"},
      {"custom_order_line_id":3,"print_material_name":"Vinyl","print_size_name":"A4","print_description":"Back: STAFF"}]}
    """).getAsJsonObject();}

  @Test void addOnsIdentifyTheMatchingGroupedItemAndPreserveExistingInstructions(){
    String instructions=OrdersDetailWorkspace.instructionsText(fixture());
    assertTrue(instructions.startsWith("Keep sizes labelled.\nCustomer supplied\nFold individually"));
    assertTrue(instructions.contains("Item 1 — 2 × Customer shirt / 2XL / White\nAdd-on: Embroidery · Small chest · Left chest: Deckers logo in white"));
    assertTrue(instructions.contains("Item 2 — 1 × Polo / M / Navy\nAdd-on: Vinyl · A4 · Back: STAFF"));
    assertEquals(1,instructions.split("Left chest: Deckers logo in white",-1).length-1);
  }

  @Test void differentDesignsOnMatchingSizesRemainSeparate(){
    JsonObject data=fixture();data.getAsJsonArray("addons").get(1).getAsJsonObject().addProperty("print_description","Right sleeve: NAME");
    String instructions=OrdersDetailWorkspace.instructionsText(data);
    assertTrue(instructions.contains("Item 1 — 1 × Customer shirt"));assertTrue(instructions.contains("Item 2 — 1 × Customer shirt"));
    assertTrue(instructions.indexOf("Right sleeve: NAME")>instructions.indexOf("Item 2 —"));
  }

  @Test void noAddOnsKeepTheOriginalInstructionsWithoutEmptyHeadings(){
    JsonObject data=fixture();data.add("addons",new JsonArray());assertEquals("Keep sizes labelled.\nCustomer supplied\nFold individually",OrdersDetailWorkspace.instructionsText(data));
    JsonObject addon=new JsonObject();addon.addProperty("custom_order_line_id",1);addon.addProperty("print_description","Front: customer’s exact wording");data.getAsJsonArray("addons").add(addon);
    assertTrue(OrdersDetailWorkspace.instructionsText(data).contains("Add-on: Front: customer’s exact wording"));
  }
}
