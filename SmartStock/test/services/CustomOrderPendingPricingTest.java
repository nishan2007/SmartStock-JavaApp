package services;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import services.CustomOrderPricingService.*;
import services.CustomOrderDataService.PrintAddonRequest;
import static org.junit.jupiter.api.Assertions.*;

class CustomOrderPendingPricingTest {
    private static BigDecimal n(String value){return new BigDecimal(value);}
    private static Snapshot fixed(){return new Snapshot(11L,1L,null,"Shirt","","FIXED",null,null,null,n("100"),null,null,null,false,List.of());}
    private static PricingLine input(String rate,String discount,List<PrintAddonRequest> addons){return new PricingLine(11,null,null,n(rate),addons,n(discount),"",null,"",null);}
    @Test void finalPriceUsesChargesAndTheHigherDiscountBeforeRounding() {
        var addon=new PrintAddonRequest(1L,"Print",null,"Custom","FIXED_PRESET","Logo",1,n("40"));
        var price=CustomOrderPricingService.calculate(fixed(),input("100","10",List.of(addon)),n("20"),true);
        assertEquals(n("140"),price.original());assertEquals(n("28.00"),price.discountAmount());assertEquals(n("112"),price.total());
        assertEquals(n("112"),CustomOrderPricingService.calculate(fixed(),input("100","10",List.of(addon)),n("20"),false).total());
    }
    @Test void areaPriceUsesConfiguredMaterialWidthAndConvertsUnits() {
        Snapshot area=new Snapshot(11,1L,null,"Banner","","AREA","IN","SQ_FT",n("48"),n("100"),null,null,null,false,List.of());
        PricingLine input=new PricingLine(11,n("30"),n("36"),n("100"),List.of(),BigDecimal.ZERO,"",null,"",null);
        var price=CustomOrderPricingService.calculate(area,input,BigDecimal.ZERO,false);
        assertEquals(0,n("12").compareTo(price.area()));assertEquals(n("1200"),price.total());
        assertThrows(IllegalArgumentException.class,()->CustomOrderPricingService.calculate(area,input("100","0",List.of()),BigDecimal.ZERO,false));
    }
    @Test void depositsAreDeductedAndExcessRequiresRefund() {
        assertEquals(n("9000"),CustomOrderPricingService.finalBalance(n("12000"),n("3000")));
        assertEquals(BigDecimal.ZERO,CustomOrderPricingService.finalBalance(n("3000"),n("3000")));
        assertTrue(assertThrows(IllegalArgumentException.class,()->CustomOrderPricingService.finalBalance(n("2000"),n("3000"))).getMessage().contains("Refund the excess"));
    }
    @Test void customerSuppliedItemsAndInvalidChargesCannotBypassCalculation() {
        Snapshot supplied=new Snapshot(11,null,null,"Customer shirt","","FIXED",null,null,null,null,null,null,null,true,List.of());
        assertThrows(IllegalArgumentException.class,()->CustomOrderPricingService.calculate(supplied,input("1","0",List.of()),BigDecimal.ZERO,false));
        assertThrows(IllegalArgumentException.class,()->CustomOrderPricingService.calculate(supplied,input("0","0",List.of()),BigDecimal.ZERO,false));
        assertThrows(IllegalArgumentException.class,()->CustomOrderPricingService.calculate(fixed(),input("-1","0",List.of()),BigDecimal.ZERO,false));
        assertThrows(IllegalArgumentException.class,()->CustomOrderPricingService.calculate(fixed(),input("100","101",List.of()),BigDecimal.ZERO,false));
    }
}
