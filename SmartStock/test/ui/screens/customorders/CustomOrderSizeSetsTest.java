package ui.screens.customorders;

import org.junit.jupiter.api.Test;
import services.*;
import services.CustomOrderDataService.*;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CustomOrderSizeSetsTest {
    @org.junit.jupiter.api.io.TempDir java.nio.file.Path directory;
    private final CustomItemOption item=LanJson.create().fromJson("{\"customItemId\":1,\"name\":\"Shirt\",\"pricingType\":\"FIXED\",\"hasVariants\":true,\"fixedPrice\":100}",CustomItemOption.class);
    private VariantOption variant(long id,String size,String colour,Integer price) {
        return new VariantOption(id,size+" / "+colour,size,colour,size,colour,"shirt-"+id,price==null?null:BigDecimal.valueOf(price));
    }
    private OrderLineRequest shared(int discount) {
        return LanJson.create().fromJson("{\"customItemId\":1,\"itemName\":\"Shirt\",\"pricingType\":\"FIXED\",\"unitPrice\":125,\"printCharge\":25,\"printLineCount\":1,\"lineDiscountPercent\":"+discount+",\"lineDiscountReason\":\"Approved bulk price\",\"lineDiscountApprovalToken\":\"draft-approval\",\"customizationDetails\":\"Front logo\",\"orderInstructions\":\"Pack together\",\"printAddons\":[{\"materialName\":\"Vinyl\",\"printDescription\":\"Front logo\",\"printCharge\":25,\"printLineCount\":1}]}",OrderLineRequest.class);
    }
    private List<CustomOrderSizeSetEditor.SizeQuantity> sizes() {
        return List.of(new CustomOrderSizeSetEditor.SizeQuantity(variant(11,"Small","Black",100),5),new CustomOrderSizeSetEditor.SizeQuantity(variant(12,"Medium","Black",120),4));
    }
    @Test void variablePricedShirtsWithCatalogueSizePricesSupportSets() {
        var variable = new CustomItemOption(1L,"Shirt",null,null,null,"INVENTORY","VARIABLE",
            null,true,null,null,null,null,null);
        var sizes = sizes();
        assertEquals(2,CustomOrderSizeSetEditor.eligible(variable,
            sizes.stream().map(CustomOrderSizeSetEditor.SizeQuantity::variant).toList(),sizes.get(0).variant()).size());
        var json = LanJson.create().toJsonTree(shared(0)).getAsJsonObject();
        json.addProperty("pricingType","VARIABLE");
        var units = CustomOrderSizeSets.expand(variable,LanJson.create().fromJson(json,OrderLineRequest.class),sizes,false,false);
        assertEquals(9,units.size());
        assertEquals(new BigDecimal("125"),units.get(0).request().unitPrice());
        assertEquals(new BigDecimal("145"),units.get(5).request().unitPrice());
        assertEquals("VARIABLE",units.get(5).request().pricingType());
    }
    @Test void fiveSmallAndFourMediumShareDesignAndAddonsWithTheirOwnPrices() {
        var template=shared(0);var units=CustomOrderSizeSets.expand(item,template,sizes(),false,false);
        assertEquals(9,units.size());assertEquals(5,units.stream().filter(u->"Small".equals(u.request().itemSize())).count());
        assertEquals(4,units.stream().filter(u->"Medium".equals(u.request().itemSize())).count());
        assertEquals(new BigDecimal("125"),units.get(0).request().unitPrice());assertEquals(new BigDecimal("145"),units.get(5).request().unitPrice());
        assertEquals(new BigDecimal("1205"),units.stream().map(u->u.request().unitPrice()).reduce(BigDecimal.ZERO,BigDecimal::add));
        for(var unit:units){assertEquals(template.printAddons(),unit.request().printAddons());assertEquals(template.customizationDetails(),unit.request().customizationDetails());assertEquals(template.orderInstructions(),unit.request().orderInstructions());assertEquals("Black",unit.request().itemColor());assertNull(unit.request().priceOverridePrice());}
    }
    @Test void discountsApplyPerShirtButRoundingWaitsForOrderTotal() {
        var discounted=CustomOrderSizeSets.expand(item,shared(10),sizes(),false,false);
        assertEquals(new BigDecimal("113"),discounted.get(0).request().unitPrice());
        assertEquals(new BigDecimal("131"),discounted.get(5).request().unitPrice());
        var rounded=CustomOrderSizeSets.expand(item,shared(10),sizes(),false,true);
        assertEquals(new BigDecimal("113"),rounded.get(0).request().unitPrice());
        assertEquals(new BigDecimal("131"),rounded.get(5).request().unitPrice());
        assertEquals("draft-approval",rounded.get(0).request().lineDiscountApprovalToken());
        var pending=CustomOrderSizeSets.expand(item,shared(10),sizes(),true,true);
        assertTrue(pending.stream().allMatch(u->u.request().unitPrice().signum()==0));
        var fallback=CustomOrderSizeSets.expand(item,shared(0),List.of(new CustomOrderSizeSetEditor.SizeQuantity(variant(13,"Large","Black",null),1)),false,false);
        assertEquals(new BigDecimal("125"),fallback.get(0).request().unitPrice());
    }
    @Test void rejectsDuplicatesMixedColoursAndInvalidQuantities() {
        var small=sizes().get(0);var red=new CustomOrderSizeSetEditor.SizeQuantity(variant(14,"Medium","Red",100),2);
        assertThrows(IllegalArgumentException.class,()->CustomOrderSizeSets.expand(item,shared(0),List.of(small,small),false,false));
        assertThrows(IllegalArgumentException.class,()->CustomOrderSizeSets.expand(item,shared(0),List.of(small,red),false,false));
        for(String bad:List.of("-1","1.5","no","5001"))assertThrows(IllegalArgumentException.class,()->CustomOrderSizeSetEditor.quantity(bad));
        assertEquals(0,CustomOrderSizeSetEditor.quantity("0"));assertEquals(5,CustomOrderSizeSetEditor.quantity("5"));
        assertThrows(IllegalArgumentException.class,()->new CustomOrderSizeSetEditor.SizeQuantity(small.variant(),0));
    }
    @Test void onlySameColourFixedPriceCatalogueSizesAreOffered() {
        var small=sizes().get(0).variant();var medium=sizes().get(1).variant();var red=variant(14,"Medium","Red",100);
        assertEquals(List.of(small,medium),CustomOrderSizeSetEditor.eligible(item,List.of(small,medium,red),small));
        var area=LanJson.create().fromJson("{\"customItemId\":2,\"pricingType\":\"AREA\"}",CustomItemOption.class);
        assertTrue(CustomOrderSizeSetEditor.eligible(area,List.of(small),small).isEmpty());
        assertTrue(CustomOrderSizeSetEditor.eligible(null,List.of(small),small).isEmpty());
    }
    @Test void wholeSetReplacementAndRemovalPreserveOtherLinesAndOriginalOnFailure() {
        record Row(String name,String id){}
        var before=List.of(new Row("Other",null),new Row("Small","set"),new Row("Medium","set"),new Row("Last",null));
        var replacement=List.of(new Row("Large","set"));
        assertEquals(List.of(before.get(0),replacement.get(0),before.get(3)),CustomOrderSetCart.replace(before,replacement,2,"set",Row::id));
        assertEquals(List.of(before.get(0),before.get(3)),CustomOrderSetCart.remove(before,2,Row::id));
        assertEquals(4,before.size());
        assertThrows(IllegalArgumentException.class,()->CustomOrderSizeSets.expand(item,shared(101),sizes(),false,false));assertEquals(4,before.size());
    }
    @Test void heldDraftMetadataAndSharedFilesSurviveWithoutApprovalTokens() throws Exception {
        var units=CustomOrderSizeSets.expand(item,shared(10),sizes(),false,false);
        var lines=units.stream().map(u->new CustomOrderHeldDraft.Line(u.request(),item,u.variant(),List.of("file"),"set-id")).toList();
        var draft=new CustomOrderHeldDraft(null,false,false,"","","CASH","","0","",lines,List.of());
        var design=directory.resolve("logo.png");java.nio.file.Files.write(design,new byte[]{1,2,3});
        var safe=CustomOrderHoldTransfer.prepare("draft","Shirts",draft,Map.of("file",design));
        var restored=LanJson.create().fromJson(LanJson.create().toJson(safe.draft()),CustomOrderHeldDraft.class);
        assertEquals(9,restored.lines().size());
        assertEquals(1,restored.attachments().size());assertEquals(3,restored.attachments().get(0).size());
        for(var line:restored.lines()){assertEquals("set-id",line.setId());assertEquals(List.of("file"),line.files());assertNull(line.request().lineDiscountApprovalToken());}
        var legacy=LanJson.create().fromJson("{\"files\":[]}",CustomOrderHeldDraft.Line.class);assertNull(legacy.setId());
    }
    @Test void slipKeepsBothSizeQuantities() throws Exception {
        var units=CustomOrderSizeSets.expand(item,shared(0),sizes(),false,false);
        var lines=units.stream().map(u->new Receipt.CustomOrderSlipData.Line(u.request().itemName(),u.request().variantName(),u.request().customizationDetails(),u.request().orderInstructions(),u.request().unitPrice())).toList();
        var slip=new Receipt.CustomOrderSlipData("CO-test","Customer","","",null,null,"","","","CASH","","",new BigDecimal("1205"),BigDecimal.ZERO,new BigDecimal("1205"),"",lines);
        assertEquals(List.of(5,4),slip.printLines().stream().map(Receipt.CustomOrderSlipData.PrintLine::quantity).toList());
        var receipt=new managers.CompanyCustomizationManager.ReceiptSettings("Deckers","","","","","","","","","","","Thank you","",false,true,true,true,true,true,true,false,false,BigDecimal.ZERO,1,BigDecimal.ZERO,false,managers.CompanyCustomizationManager.AccountPaymentReceiptSettings.defaults());
        var settings=new managers.CompanyCustomizationManager.CustomOrderSlipSettings(true,false,"ORDER SLIP","","","",0,false,true,true,true,true,true,true,true,true,true,true,false,false,false);
        String text=Receipt.CustomOrderSlipFormatter.format40Column(slip,receipt,settings);
        assertTrue(text.contains("Small / Black x 5"),text);assertTrue(text.contains("Medium / Black x 4"),text);
        var image=new java.awt.image.BufferedImage(700,400,java.awt.image.BufferedImage.TYPE_INT_RGB);var graphics=image.createGraphics();
        try{Receipt.CustomOrderSlipRenderer.paintSlip(graphics,0,0,700,390,slip,receipt,settings,null);}finally{graphics.dispose();}
        var output=java.nio.file.Path.of("target","size-set-preview");java.nio.file.Files.createDirectories(output);
        javax.imageio.ImageIO.write(image,"png",output.resolve("size-set-slip.png").toFile());
    }
}
