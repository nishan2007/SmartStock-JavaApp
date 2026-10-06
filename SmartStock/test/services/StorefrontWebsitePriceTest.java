package services;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;

class StorefrontWebsitePriceTest {
    @Test void validatesStaffEnteredWebsitePrices(){
        assertNull(StorefrontAdminService.priceField(JsonParser.parseString("\"\""),"website price"));
        assertEquals(new BigDecimal("125.50"),StorefrontAdminService.priceField(JsonParser.parseString("\"125.50\""),"website price"));
        for(String bad:new String[]{"0","-1","1.999","not a price","10000000000"})
            assertThrows(IllegalArgumentException.class,()->StorefrontAdminService.priceField(JsonParser.parseString("\""+bad+"\""),"website price"));
    }
}
