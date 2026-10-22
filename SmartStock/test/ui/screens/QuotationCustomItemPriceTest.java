package ui.screens;

import org.junit.jupiter.api.Test;
import services.CustomOrderDataService.CustomItemOption;
import services.CustomOrderDataService.VariantOption;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;

class QuotationCustomItemPriceTest {
    private CustomItemOption item(String type, BigDecimal fixed, BigDecimal area) {
        return new CustomItemOption(1L,"Item",null,null,"SKU","CUSTOM",type,fixed,true,area,"SQ_FT","FT",null,null);
    }
    private VariantOption variant(BigDecimal price) {
        return new VariantOption(2L,"Variant",null,null,null,null,"V",price);
    }
    @Test void selectedVariantUsesItsSavedPriceIncludingZero() {
        var item=item("FIXED",new BigDecimal("100"),null);
        assertEquals(new BigDecimal("175.50"),Quotations.customQuotationCatalogPrice(item,variant(new BigDecimal("175.50"))));
        assertEquals(BigDecimal.ZERO,Quotations.customQuotationCatalogPrice(item,variant(BigDecimal.ZERO)));
        assertEquals(new BigDecimal("100"),Quotations.customQuotationCatalogPrice(item,variant(null)));
        assertEquals(new BigDecimal("100"),Quotations.customQuotationCatalogPrice(item,null));
    }
    @Test void areaPricingUsesConfiguredRate() {
        assertEquals(new BigDecimal("12.50"),Quotations.customQuotationCatalogPrice(item("AREA",null,new BigDecimal("12.50")),variant(new BigDecimal("175"))));
    }
    @Test void unpricedOrClearedSelectionDoesNotReuseAnotherItemsPrice() {
        assertNull(Quotations.customQuotationCatalogPrice(item("FIXED",null,null),null));
        assertNull(Quotations.customQuotationCatalogPrice(null,null));
    }
}
