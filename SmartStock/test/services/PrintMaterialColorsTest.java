package services;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PrintMaterialColorsTest {
    @Test void paletteRoundTripsAndBlankMeansArtworkColors(){
        var colors=PrintMaterialColors.parse("Black=#000000, Red=#FF0000");
        assertEquals("#ff0000",colors.get("Red").getAsString());
        assertEquals(colors,PrintMaterialColors.parse(PrintMaterialColors.format(colors)));
        assertTrue(PrintMaterialColors.parse("").isEmpty());
        for(String invalid:new String[]{"Red", "Red=blue", "Red=#ff0000, red=#ffffff", "Red=#ff0000,"})assertThrows(IllegalArgumentException.class,()->PrintMaterialColors.parse(invalid));
    }
    @Test void designerUsesMaterialPaletteAndRejectsForgedColorsAndModes(){
        JsonObject config=ShirtDesignerService.defaults();
        JsonArray source=JsonParser.parseString("""
            [{"id":10,"materialId":1,"name":"Vinyl","material":"HTV","price":50,"pricingMode":"FIXED_PRESET","availableColors":{"Red":"#ff0000"}},
             {"id":11,"materialId":2,"name":"Transfer","material":"Transfer","price":100,"pricingMode":"FIXED_PRESET","availableColors":{}}]
            """).getAsJsonArray();
        JsonArray options=ShirtDesignerService.systemAddOns(source,config);
        assertEquals("SINGLE",options.get(0).getAsJsonObject().get("mode").getAsString());
        assertEquals("FULL",options.get(1).getAsJsonObject().get("mode").getAsString());
        JsonObject item=ShirtDesignerServiceTest.item();item.add("config",config);item.add("addOns",options);
        JsonObject design=ShirtDesignerServiceTest.design(),layer=design.getAsJsonObject("areas").getAsJsonObject("FRONT").getAsJsonArray("layers").get(0).getAsJsonObject();
        layer.addProperty("presetId",10);layer.addProperty("printMode","SINGLE");layer.addProperty("vinylColor","#ff0000");
        assertDoesNotThrow(()->ShirtDesignerService.calculate(item,design,false));
        layer.addProperty("vinylColor","#000000");assertThrows(IllegalArgumentException.class,()->ShirtDesignerService.calculate(item,design,false));
        layer.addProperty("printMode","FULL");assertThrows(IllegalArgumentException.class,()->ShirtDesignerService.calculate(item,design,false));
        layer.addProperty("presetId",11);assertDoesNotThrow(()->ShirtDesignerService.calculate(item,design,false));
    }
}
