package services;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import utils.CurrencyFormatter;
import static org.junit.jupiter.api.Assertions.*;

class CustomOrderTotalRoundingTest {
    @Test void thirtyDollarUnitsDoNotAccumulateRoundingInLargeQuantities() {
        var saved=new CustomOrderPricingService.Snapshot(11L,1L,null,"Shirt","","FIXED",null,null,null,new BigDecimal("30"),null,null,null,false,List.of());
        var input=new CustomOrderPricingService.PricingLine(11,null,null,new BigDecimal("30"),List.of(),BigDecimal.ZERO,"",null,"",null);
        var unit=CustomOrderPricingService.calculate(saved,input,BigDecimal.ZERO,true).total();
        assertEquals(0,new BigDecimal("30").compareTo(unit));
        for(int quantity:new int[]{2,100,200,1000}) {
            BigDecimal subtotal=unit.multiply(BigDecimal.valueOf(quantity));
            assertEquals(0,subtotal.compareTo(CurrencyFormatter.customOrderTotal(subtotal,true)));
        }
        assertEquals(new BigDecimal("40"),CurrencyFormatter.customOrderTotal(unit,true));
        assertEquals(new BigDecimal("100"),CurrencyFormatter.customOrderTotal(unit.multiply(BigDecimal.valueOf(3)),true));
        assertEquals(new BigDecimal("90"),CurrencyFormatter.customOrderTotal(unit.multiply(BigDecimal.valueOf(3)),false));
    }
    @Test void discountsAndMixedItemsAreSummedBeforeOneFinalAdjustment() {
        assertEquals(new BigDecimal("3000"),CurrencyFormatter.customOrderTotal(new BigDecimal("30").multiply(new BigDecimal("100")),true));
        assertEquals(new BigDecimal("2720"),CurrencyFormatter.customOrderTotal(new BigDecimal("27").multiply(new BigDecimal("101")),true));
        assertEquals(new BigDecimal("80"),CurrencyFormatter.customOrderTotal(new BigDecimal("30").add(new BigDecimal("47")),true));
        assertEquals(BigDecimal.ZERO,CurrencyFormatter.customOrderTotal(BigDecimal.ZERO,true));
    }
}
