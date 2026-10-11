package ui.screens.customorders;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CustomOrderQuantityGroupsTest {
    @Test void identicalItemsGroupWithoutLosingTheirLineIds(){
        var data=CustomOrderDetailsPanelTest.fixture("0",false);
        var lines=data.getAsJsonArray("lines");var original=lines.get(0).getAsJsonObject();
        for(int i=2;i<=16;i++){var next=original.deepCopy();next.addProperty("custom_order_line_id",i);lines.add(next);}
        var groups=CustomOrderQuantityGroups.group(data);
        assertEquals(1,groups.size());assertEquals(16,groups.get(0).size());
        assertEquals(16,groups.get(0).get(15).get("custom_order_line_id").getAsInt());
        lines.get(1).getAsJsonObject().addProperty("item_color","Blue");
        assertEquals(2,CustomOrderQuantityGroups.group(data).size());
    }
    @Test void differentDesignsAddonsOrProgressRemainSeparate(){
        var data=CustomOrderDetailsPanelTest.fixture("0",false);var original=data.getAsJsonArray("lines").get(0).getAsJsonObject();
        var next=original.deepCopy();next.addProperty("custom_order_line_id",2);data.getAsJsonArray("lines").add(next);
        next.addProperty("order_instructions","Different logo");assertEquals(2,CustomOrderQuantityGroups.group(data).size());
        next.addProperty("order_instructions",original.get("order_instructions").getAsString());
        data.getAsJsonArray("addons").add(JsonParser.parseString("{\"custom_order_line_id\":2,\"print_description\":\"Chest logo\"}"));
        assertEquals(2,CustomOrderQuantityGroups.group(data).size());
        data.getAsJsonArray("addons").remove(0);next.addProperty("production_status","READY");
        assertEquals(2,CustomOrderQuantityGroups.group(data).size());
    }
}
