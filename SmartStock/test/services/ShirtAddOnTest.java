package services;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ShirtAddOnTest {
    private JsonObject item(){
        JsonObject item=ShirtDesignerServiceTest.item();
        item.add("addOns",JsonParser.parseString("""
            [{"id":10,"materialId":1,"name":"3in line","material":"Vinyl","price":50,"kind":"TEXT","widthIn":3,"heightIn":0.5},
             {"id":11,"materialId":2,"name":"Logo","material":"Transfer paper","price":150,"kind":"IMAGE","widthIn":3,"heightIn":3}]
            """));
        JsonObject areas=new JsonObject();for(String name:ShirtDesignerService.AREAS){JsonObject area=new JsonObject();area.addProperty("widthIn",10);area.addProperty("heightIn",12);areas.add(name,area);}item.getAsJsonObject("config").add("areas",areas);return item;
    }
    private JsonObject design(){
        JsonObject d=ShirtDesignerServiceTest.design(),area=d.getAsJsonObject("areas").getAsJsonObject("FRONT");JsonObject text=area.getAsJsonArray("layers").get(0).getAsJsonObject();text.addProperty("presetId",10);text.addProperty("w",.3);text.addProperty("h",.04);
        JsonObject back=area.deepCopy();back.getAsJsonArray("layers").get(0).getAsJsonObject().addProperty("text","First line");JsonObject second=back.getAsJsonArray("layers").get(0).deepCopy().getAsJsonObject();second.addProperty("text","Second line");second.addProperty("y",.2);back.getAsJsonArray("layers").add(second);d.getAsJsonObject("areas").add("BACK",back);
        text.addProperty("type","IMAGE");text.addProperty("presetId",11);text.addProperty("h",.25);text.addProperty("fileId","00000000-0000-0000-0000-000000000001");return d;
    }
    @Test void logoAndRepeatedTextLinesEachCarryTheirOwnChargeAndContent(){
        JsonObject result=ShirtDesignerService.calculate(item(),design(),false);assertEquals(3,result.getAsJsonArray("charges").size());assertEquals(250,result.getAsJsonArray("lines").get(0).getAsJsonObject().get("printPrice").getAsInt());assertEquals(2506,result.get("total").getAsInt());
        assertEquals("First line",result.getAsJsonArray("charges").get(1).getAsJsonObject().get("description").getAsString());assertEquals("Second line",result.getAsJsonArray("charges").get(2).getAsJsonObject().get("description").getAsString());
        JsonObject d=design();d.getAsJsonObject("areas").getAsJsonObject("BACK").getAsJsonArray("layers").remove(1);assertEquals(2406,ShirtDesignerService.calculate(item(),d,false).get("total").getAsInt());
    }
    @Test void pricesComeFromCurrentAllowedCatalogAndPriceChangesChangeFingerprint(){
        JsonObject d=design();d.getAsJsonObject("areas").getAsJsonObject("BACK").getAsJsonArray("layers").get(0).getAsJsonObject().addProperty("price",0);assertEquals(2506,ShirtDesignerService.calculate(item(),d,false).get("total").getAsInt());
        JsonObject item=item(),before=ShirtDesignerService.calculate(item,d,false);item.getAsJsonArray("addOns").get(0).getAsJsonObject().addProperty("price",70);JsonObject after=ShirtDesignerService.calculate(item,d,false);assertEquals(2586,after.get("total").getAsInt());assertNotEquals(before.get("fingerprint"),after.get("fingerprint"));
    }
    @Test void unknownMissingWrongKindAndOversizedPresetsCannotBeQuoted(){
        for(int preset:new int[]{999,10}){JsonObject d=design();d.getAsJsonObject("areas").getAsJsonObject("FRONT").getAsJsonArray("layers").get(0).getAsJsonObject().addProperty("presetId",preset);assertThrows(IllegalArgumentException.class,()->ShirtDesignerService.calculate(item(),d,false));}
        JsonObject missing=design();missing.getAsJsonObject("areas").getAsJsonObject("FRONT").getAsJsonArray("layers").get(0).getAsJsonObject().remove("presetId");assertThrows(IllegalArgumentException.class,()->ShirtDesignerService.calculate(item(),missing,false));
        JsonObject oversized=design();oversized.getAsJsonObject("areas").getAsJsonObject("BACK").getAsJsonArray("layers").get(0).getAsJsonObject().addProperty("w",.8);assertThrows(IllegalArgumentException.class,()->ShirtDesignerService.calculate(item(),oversized,false));
        JsonObject removed=item();removed.add("addOns",new JsonArray());assertThrows(IllegalArgumentException.class,()->ShirtDesignerService.calculate(removed,design(),false));
    }
    @Test void configuredCustomerOptionsAreBoundedAndRoundTrip(){
        JsonObject config=ShirtDesignerService.defaults();JsonArray options=item().getAsJsonArray("addOns").deepCopy();for(var value:options){JsonObject o=value.getAsJsonObject();o.add("presetId",o.remove("id"));}config.add("addOns",options);
        assertEquals(2,ShirtDesignerService.configuration(config,false).getAsJsonArray("addOns").size());options.add(options.get(0).deepCopy());assertThrows(IllegalArgumentException.class,()->ShirtDesignerService.configuration(config,false));
    }
    @Test void systemCatalogueIncludesEveryMaterialAndFuturePresetWithoutAnAllowlist(){
        JsonArray source=JsonParser.parseString("""
            [{"id":10,"materialId":1,"name":"3in line","material":"Vinyl","price":50,"pricingMode":"PER_LINE"},
             {"id":11,"materialId":2,"name":"Logo","material":"Transfer paper","price":150,"pricingMode":"FIXED_PRESET"},
             {"id":12,"materialId":3,"name":"Large crest","material":"Embroidery","price":700,"pricingMode":"FIXED_PRESET"}]
            """).getAsJsonArray();
        JsonObject config=ShirtDesignerService.defaults();JsonArray options=ShirtDesignerService.systemAddOns(source,config);assertEquals(3,options.size());assertEquals("TEXT",options.get(0).getAsJsonObject().get("kind").getAsString());assertEquals("BOTH",options.get(2).getAsJsonObject().get("kind").getAsString());assertFalse(options.get(2).getAsJsonObject().has("widthIn"));assertFalse(options.get(2).getAsJsonObject().has("mode"));
        JsonObject future=source.get(2).deepCopy().getAsJsonObject();future.addProperty("id",13);future.addProperty("materialId",4);future.addProperty("material","Future decoration");future.addProperty("name","New option");source.add(future);assertEquals(4,ShirtDesignerService.systemAddOns(source,config).size());
        JsonObject shirt=item();shirt.add("addOns",options);JsonObject d=design();JsonObject logo=d.getAsJsonObject("areas").getAsJsonObject("FRONT").getAsJsonArray("layers").get(0).getAsJsonObject();logo.addProperty("presetId",12);assertEquals(3606,ShirtDesignerService.calculate(shirt,d,false).get("total").getAsInt());
        JsonObject invalid=design();JsonObject text=invalid.getAsJsonObject("areas").getAsJsonObject("BACK").getAsJsonArray("layers").get(0).getAsJsonObject();text.addProperty("text","Two\nlines");assertThrows(IllegalArgumentException.class,()->ShirtDesignerService.calculate(shirt,invalid,false));
        logo.addProperty("presetId",10);assertThrows(IllegalArgumentException.class,()->ShirtDesignerService.calculate(shirt,d,false));
    }
    @Test void automaticPresetsNeedNoLegacyPriceMappingsAndEmptyCatalogCannotFallBackToAnAreaCharge(){
        JsonObject config=ShirtDesignerService.defaults();config.getAsJsonObject("colors").addProperty("Black","#000000");for(String name:ShirtDesignerService.AREAS){JsonObject a=config.getAsJsonObject("areas").getAsJsonObject(name);a.addProperty("widthIn",10);a.addProperty("heightIn",12);}
        assertTrue(ShirtDesignerService.configuration(config,true).get("useSystemPresets").getAsBoolean());JsonObject shirt=item();shirt.getAsJsonObject("config").addProperty("useSystemPresets",true);shirt.add("addOns",new JsonArray());assertThrows(IllegalArgumentException.class,()->ShirtDesignerService.calculate(shirt,design(),false));
    }
}
