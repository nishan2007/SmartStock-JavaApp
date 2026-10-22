package services;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.List;

class CustomCatalogSearchTest {
    @Test void identifiersAcceptScannerSeparatorsAndBarcodesWithOrWithoutCheckDigits(){
        assertTrue(CustomCatalogSearch.matches("Blue shirt",List.of("BLUE-L-0042"),"bluel0042"));
        assertTrue(CustomCatalogSearch.matches("Blue shirt",List.of("AB123456"),"ab-123 456"));
        assertTrue(CustomCatalogSearch.matches("Blue shirt",List.of("036000291452"),"03600029145"));
        assertFalse(CustomCatalogSearch.matches("Blue shirt",List.of("AB123456"),"AB123457"));
        assertFalse(CustomCatalogSearch.matches("Blue shirt",List.of(""),"-"));
    }
    @Test void catalogDetailsFindParentByVariantIdentifiersAndCombinedAttributes(){
        var state=LanJson.create().fromJson("""
            {"items":[{"itemId":1,"name":"Cotton shirt","barcode":"PARENT-123","extraBarcodes":["PARENT-ALT"],"variants":[
              {"variantId":11,"name":"Small blue","size":"S","color":"Blue","sku":"BLUE-S-0042","barcode":"12345678","extraBarcodes":["ALT-777"]}
            ]}]}
            """,LanCustomOrderCatalogAdminService.State.class);
        var item=state.items().get(0);
        for(String query:List.of("BLUE-S-0042","blues0042","1234-5678","ALT777","PARENT-ALT","blue cotton S"))
            assertTrue(CustomCatalogSearch.matchesItem(item,query),query);
        assertFalse(CustomCatalogSearch.matchesItem(item,"red cotton"));
    }
}
