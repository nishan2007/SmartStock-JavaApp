package services;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ShirtDesignerServiceTest {
    @Test void searchLabelsFollowSavedChoices(){
        JsonObject c=ShirtDesignerService.defaults();
        assertEquals("",ShirtDesignerService.searchLabels(false,c));
        assertTrue(ShirtDesignerService.searchLabels(true,c).contains("round neck"));
        c.addProperty("neckline","V");
        assertTrue(ShirtDesignerService.searchLabels(true,c).contains("v-neck"));
        assertFalse(ShirtDesignerService.searchLabels(true,c).contains("round neck"));
        c.addProperty("shirtType","COLLARED");c.addProperty("fabric","DRY_FIT");c.addProperty("websiteEnabled",true);
        String labels=ShirtDesignerService.searchLabels(true,c);
        assertTrue(labels.contains("polo"));assertTrue(labels.contains("dri fit"));assertTrue(labels.contains("designer enabled"));
        assertFalse(labels.contains("v-neck"));assertFalse(labels.contains("t-shirt"));
    }
    @Test void shirtTypesAndNecklinesAreExplicitAndLegacyDefaultsStayRound(){
        JsonObject legacy=ShirtDesignerService.defaults();legacy.remove("shirtType");legacy.remove("neckline");JsonObject round=ShirtDesignerService.configuration(legacy,false);assertEquals("TEE",round.get("shirtType").getAsString());assertEquals("ROUND",round.get("neckline").getAsString());
        for(String neck:List.of("ROUND","V")){legacy.addProperty("shirtType","TEE");legacy.addProperty("neckline",neck);assertEquals(neck,ShirtDesignerService.configuration(legacy,false).get("neckline").getAsString());}
        legacy.addProperty("shirtType","COLLARED");legacy.addProperty("neckline","COLLAR");assertEquals("Collared shirt",ShirtDesignerService.styleLabel(ShirtDesignerService.configuration(legacy,false)));
        legacy.addProperty("neckline","V");assertThrows(IllegalArgumentException.class,()->ShirtDesignerService.configuration(legacy,false));
        assertEquals("/shop/shirt-vneck-front.png",ShirtDesignerService.defaultMockup("TEE","V",false));assertEquals("/shop/shirt-collared-back.png",ShirtDesignerService.defaultMockup("COLLARED","COLLAR",true));
    }
    @Test void changedNecklineRequiresReviewRatherThanSilentlyChangingTheGarment(){JsonObject d=design();d.addProperty("styleKey","TEE:V");assertThrows(IllegalArgumentException.class,()->ShirtDesignerService.calculate(item(),d,true));JsonObject shirt=item();shirt.getAsJsonObject("config").addProperty("neckline","V");assertDoesNotThrow(()->ShirtDesignerService.calculate(shirt,d,true));}
    @Test void fabricIsValidatedAndDefaultsForExistingShirts(){
        JsonObject config=ShirtDesignerService.defaults();config.remove("fabric");assertEquals("NORMAL",ShirtDesignerService.configuration(config,false).get("fabric").getAsString());
        config.addProperty("fabric","DRY_FIT");assertEquals("DRY_FIT",ShirtDesignerService.configuration(config,false).get("fabric").getAsString());
        config.addProperty("fabric","unknown");assertThrows(IllegalArgumentException.class,()->ShirtDesignerService.configuration(config,false));
    }
    static JsonObject design(){return JsonParser.parseString("""
        {"version":1,"itemId":1,"groups":[{"variantId":11,"quantity":2}],"areas":{"FRONT":{"mode":"FULL","vinylColor":"#ffffff","layers":[{"type":"TEXT","text":"Deckers","font":"Deckers Sans","color":"#ff0000","x":0.1,"y":0.1,"w":0.8,"h":0.2,"rotation":0}]}}}
        """).getAsJsonObject();}
    static JsonObject item(){JsonObject item=JsonParser.parseString("""
        {"id":1,"name":"Cotton tee","variants":[{"id":11,"name":"Medium black","size":"M","color":"Black","price":1003},{"id":12,"name":"Large blue","size":"L","color":"Blue","price":1104}],"config":{"vinylColors":["#000000","#ffffff"]},"guides":{}}
        """).getAsJsonObject();for(String area:ShirtDesignerService.AREAS){JsonObject guide=new JsonObject();for(String mode:List.of("SINGLE","FULL")){JsonObject p=new JsonObject();p.addProperty("id",mode.equals("SINGLE")?1:area.equals("CHEST")?3:2);p.addProperty("materialId",mode.equals("SINGLE")?1:2);p.addProperty("name",mode.equals("SINGLE")?"Vinyl":area.equals("CHEST")?"Logo":"Full sheet");p.addProperty("material",mode.equals("SINGLE")?"Vinyl":"Transfer paper");p.addProperty("price",mode.equals("SINGLE")?100:area.equals("CHEST")?150:400);guide.add(mode,p);}item.getAsJsonObject("guides").add(area,guide);}return item;}
    @Test void allSixMappingsAndEachOccupiedAreaAreChargedIndependently(){
        for(String area:ShirtDesignerService.AREAS)for(String mode:List.of("SINGLE","FULL")){JsonObject d=design(),a=d.getAsJsonObject("areas").getAsJsonObject("FRONT");d.getAsJsonObject("areas").remove("FRONT");a.addProperty("mode",mode);d.getAsJsonObject("areas").add(area,a);JsonObject estimate=ShirtDesignerService.calculate(item(),d,false);assertEquals(mode.equals("SINGLE")?"2206":area.equals("CHEST")?"2306":"2806",estimate.get("total").getAsBigDecimal().toPlainString().replace(".00",""));}
        JsonObject d=design();for(String name:List.of("CHEST","BACK"))d.getAsJsonObject("areas").add(name,d.getAsJsonObject("areas").get("FRONT").deepCopy());JsonObject e=ShirtDesignerService.calculate(item(),d,false);assertEquals(3906,e.get("total").getAsInt());assertEquals(3,e.getAsJsonArray("charges").size());
    }
    @Test void groupingUsesVariantPricesAndRoundsOnlyTheWholeOrder(){JsonObject d=design();JsonObject group=new JsonObject();group.addProperty("variantId",12);group.addProperty("quantity",3);d.getAsJsonArray("groups").add(group);JsonObject e=ShirtDesignerService.calculate(item(),d,true);assertEquals(7320,e.get("total").getAsInt());assertEquals(7318,e.get("subtotal").getAsInt());assertEquals(0,e.get("vat").getAsInt());}
    @Test void rejectsWrongVariantsQuantitiesModesColorsAndUnboundedLayers(){
        JsonObject d=design();d.getAsJsonArray("groups").get(0).getAsJsonObject().addProperty("variantId",999);assertThrows(IllegalArgumentException.class,()->ShirtDesignerService.calculate(item(),d,true));
        JsonObject duplicate=design();duplicate.getAsJsonArray("groups").add(duplicate.getAsJsonArray("groups").get(0).deepCopy());assertThrows(IllegalArgumentException.class,()->ShirtDesignerService.design(duplicate));
        JsonObject negative=design();negative.getAsJsonArray("groups").get(0).getAsJsonObject().addProperty("quantity",-1);assertThrows(IllegalArgumentException.class,()->ShirtDesignerService.design(negative));
        JsonObject oversized=design();oversized.getAsJsonObject("areas").getAsJsonObject("FRONT").getAsJsonArray("layers").get(0).getAsJsonObject().addProperty("w",2);assertThrows(IllegalArgumentException.class,()->ShirtDesignerService.design(oversized));
        JsonObject vinyl=design();vinyl.getAsJsonObject("areas").getAsJsonObject("FRONT").addProperty("mode","SINGLE");vinyl.getAsJsonObject("areas").getAsJsonObject("FRONT").addProperty("vinylColor","#123456");assertThrows(IllegalArgumentException.class,()->ShirtDesignerService.calculate(item(),vinyl,true));
        JsonObject layers=design();var list=layers.getAsJsonObject("areas").getAsJsonObject("FRONT").getAsJsonArray("layers");for(int i=0;i<20;i++)list.add(list.get(0).deepCopy());assertThrows(IllegalArgumentException.class,()->ShirtDesignerService.design(layers));
    }
    @Test void systemAddOnsDoNotRequireDuplicateDimensionsOrSwatches(){JsonObject c=ShirtDesignerService.defaults();c.addProperty("websiteEnabled",true);assertDoesNotThrow(()->ShirtDesignerService.configuration(c,true));c.addProperty("useSystemPresets",false);assertThrows(IllegalArgumentException.class,()->ShirtDesignerService.configuration(c,true));}
    @Test void inventoryColorsAreKeptWithoutRequiringManualSwatches(){JsonObject c=ShirtDesignerService.defaults();ShirtDesignerService.ensureInventoryColor(c,"Black");assertEquals("#000000",c.getAsJsonObject("colors").get("Black").getAsString());ShirtDesignerService.ensureInventoryColor(c,"Custom fabric");assertTrue(c.getAsJsonObject("colors").has("Custom fabric"));c.getAsJsonObject("colors").addProperty("Black","#121212");ShirtDesignerService.ensureInventoryColor(c,"Black");assertEquals("#121212",c.getAsJsonObject("colors").get("Black").getAsString());}
    @Test void configurationCannotPublishExternalMockups(){JsonObject c=ShirtDesignerService.defaults();c.addProperty("frontMockup","https://example.com/tracker.png");assertThrows(IllegalArgumentException.class,()->ShirtDesignerService.configuration(c,false));}
    @Test void forgedTotalsAndExtraPayloadAreNeverRetained(){JsonObject d=design();d.addProperty("total",1);d.addProperty("customerToken","secret");JsonObject clean=ShirtDesignerService.design(d);assertFalse(clean.has("total"));assertFalse(clean.has("customerToken"));assertEquals(2800,ShirtDesignerService.calculate(item(),d,true).get("total").getAsInt());}
    @Test void invalidImagesAreRejectedAndValidImagesDecode()throws Exception{assertThrows(IllegalArgumentException.class,()->ShirtDesignerService.validateImage(new byte[]{1,2,3}));var image=new java.awt.image.BufferedImage(4,4,java.awt.image.BufferedImage.TYPE_INT_ARGB);var bytes=new java.io.ByteArrayOutputStream();javax.imageio.ImageIO.write(image,"png",bytes);assertDoesNotThrow(()->ShirtDesignerService.validateImage(bytes.toByteArray()));}
}
