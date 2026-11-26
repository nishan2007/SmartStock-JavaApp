package services;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.sql.SQLException;
import static org.junit.jupiter.api.Assertions.*;

class PrintPresetGuideSizeTest {
    @Test void dimensionsAreAPairAndServerEnforcesTheirRangeAndPrecision(){
        assertDoesNotThrow(()->LanCustomOrderCatalogAdminService.validatePresetGuideSize(new BigDecimal("10"),new BigDecimal("2")));
        assertDoesNotThrow(()->LanCustomOrderCatalogAdminService.validatePresetGuideSize(null,null));
        assertThrows(SQLException.class,()->LanCustomOrderCatalogAdminService.validatePresetGuideSize(BigDecimal.ONE,null));
        for(String invalid:new String[]{"0","-1","60.001","1.0001"})assertThrows(SQLException.class,()->LanCustomOrderCatalogAdminService.validatePresetGuideSize(new BigDecimal(invalid),BigDecimal.ONE));
    }
    @Test void sharedPresetDimensionsOverrideOldPerShirtGuideOverrides(){
        var presets=JsonParser.parseString("""
            [{"id":1,"name":"Vinyl line","pricingMode":"PER_LINE","widthIn":10,"heightIn":2}]
            """).getAsJsonArray();
        var config=ShirtDesignerService.defaults();
        config.add("addOns",JsonParser.parseString("[{\"presetId\":1,\"widthIn\":3,\"heightIn\":1}]"));
        var result=ShirtDesignerService.systemAddOns(presets,config).get(0).getAsJsonObject();
        assertEquals(10,result.get("widthIn").getAsInt());assertEquals(2,result.get("heightIn").getAsInt());
        assertEquals(3,config.getAsJsonArray("addOns").get(0).getAsJsonObject().get("widthIn").getAsInt());
    }
    @Test void oldClientsCanStillDeserializePresetsWithoutDimensions(){
        var preset=LanJson.create().fromJson("{\"presetId\":1,\"materialId\":2,\"name\":\"Vinyl\",\"pricingMode\":\"PER_LINE\",\"price\":100,\"active\":true}",LanCustomOrderCatalogAdminService.PresetSave.class);
        assertNull(preset.widthIn());assertNull(preset.heightIn());assertEquals(1,preset.presetId());
    }
}
