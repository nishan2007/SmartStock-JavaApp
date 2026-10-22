package ui.screens.customorders;

import org.junit.jupiter.api.Test;
import services.CustomOrderDataService.*;
import services.LanJson;
import java.math.BigDecimal;
import java.util.*;
import javax.swing.*;
import static org.junit.jupiter.api.Assertions.*;

class CustomOrderShirtTextTest {
    private final VariantOption small=new VariantOption(11L,"Shirt","Small","Black","Small","Black","S",BigDecimal.valueOf(100));
    private final VariantOption medium=new VariantOption(12L,"Shirt","Medium","Black","Medium","Black","M",BigDecimal.valueOf(120));
    private List<CustomOrderSizeSetEditor.SizeQuantity> sizes(){return List.of(new CustomOrderSizeSetEditor.SizeQuantity(small,5),new CustomOrderSizeSetEditor.SizeQuantity(medium,4));}
    private OrderLineRequest template(){return LanJson.create().fromJson("""
        {"customItemId":1,"itemName":"Shirt","pricingType":"VARIABLE","unitPrice":150,"printCharge":50,
         "printAddons":[{"materialName":"Vinyl","printDescription":"School logo | Colour: Gold","printCharge":25},
                        {"materialName":"Vinyl","printDescription":"Back name | Colour: White","printCharge":25}]}
        """,OrderLineRequest.class);}
    @Test void eightShirtsProduceEightFieldsAndMultipleSizesKeepSeparateNumbers() throws Exception {
        SwingUtilities.invokeAndWait(()->{
            var eight=CustomOrderShirtText.slots(List.of(),11L,"Small",8);
            var editor=CustomOrderShirtText.editor(eight,Map.of());assertEquals(8,editor.fields().size());
            var slots=CustomOrderShirtText.slots(sizes(),null,null,1);
            assertEquals(9,slots.size());assertEquals(new CustomOrderShirtText.Slot("11:5","Small",5),slots.get(4));
            assertEquals(new CustomOrderShirtText.Slot("12:1","Medium",1),slots.get(5));
            var mixed=CustomOrderShirtText.editor(slots,Map.of("11:1","Alice","12:1","Bob"));
            assertEquals("Alice",mixed.fields().get("11:1").getText());assertEquals("Bob",mixed.fields().get("12:1").getText());
            assertEquals(9,mixed.fields().size());assertEquals(2,mixed.values().size());
        });
    }
    @Test void onlyChosenAddonVariesAndBlankShirtsKeepSharedDescriptionAndPrice() throws Exception {
        var item=LanJson.create().fromJson("{\"customItemId\":1,\"pricingType\":\"VARIABLE\",\"name\":\"Shirt\"}",CustomItemOption.class);
        var units=CustomOrderSizeSets.expand(item,template(),sizes(),false,false);
        var slots=CustomOrderShirtText.slots(sizes(),null,null,1);
        var entries=List.of(Map.<String,String>of(),Map.of("11:1","Alice","12:1","Bob"));
        CustomOrderShirtText.validate(slots,entries);
        List<CustomOrderHeldDraft.Line> held=new ArrayList<>();
        for(int i=0;i<units.size();i++) {
            var applied=CustomOrderShirtText.apply(units.get(i).request(),slots.get(i),entries);
            assertEquals(units.get(i).request().unitPrice(),applied.request().unitPrice());
            assertEquals(units.get(i).request().printCharge(),applied.request().printCharge());
            assertEquals("School logo | Colour: Gold",applied.request().printAddons().get(0).printDescription());
            String expected=i==0?" | Text: Alice":i==5?" | Text: Bob":"";
            assertEquals("Back name | Colour: White"+expected,applied.request().printAddons().get(1).printDescription());
            assertNotNull(applied.saved());assertEquals(template().printAddons(),applied.saved().sharedAddons());
            held.add(new CustomOrderHeldDraft.Line(applied.request(),item,units.get(i).variant(),List.of(),"set",applied.saved()));
        }
        var draft=new CustomOrderHeldDraft(null,false,false,"","","CASH","","0","",held,List.of());
        var prepared=CustomOrderHoldTransfer.prepare(UUID.randomUUID().toString(),"Names",draft,Map.of());
        var restored=LanJson.create().fromJson(LanJson.create().toJson(prepared.draft()),CustomOrderHeldDraft.class);
        assertEquals("Alice",restored.lines().get(0).personalization().texts().get(1));
        assertEquals("Bob",restored.lines().get(5).personalization().texts().get(1));
        assertEquals("",restored.lines().get(1).personalization().texts().get(1));
        assertEquals("Medium",restored.lines().get(5).request().itemSize());
    }
    @Test void quantitiesMustBeReviewedRatherThanSilentlyDroppingNames() {
        var entries=List.of(Map.of("11:8","Alice"));
        assertThrows(IllegalArgumentException.class,()->CustomOrderShirtText.validate(CustomOrderShirtText.slots(List.of(),11L,"Small",7),entries));
        assertDoesNotThrow(()->CustomOrderShirtText.validate(CustomOrderShirtText.slots(List.of(),11L,"Small",9),entries));
        var blank=CustomOrderShirtText.apply(template(),new CustomOrderShirtText.Slot("11:1","Small",1),List.of());
        assertEquals(template().printAddons(),blank.request().printAddons());assertNull(blank.saved());
        assertThrows(IllegalArgumentException.class,()->CustomOrderShirtText.validate(CustomOrderShirtText.slots(List.of(),11L,"Small",1),List.of(Map.of("11:1","X".repeat(501)))));
    }
}
