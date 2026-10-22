package services;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class OverseasOrderRulesTest {
    @Test void trackingPreservesPartialArrivalsAndChangesProcessingAfterPayment(){
        var pending=new OverseasOrderRules.Quantities(3,0,0,0,0);
        assertEquals("WAITING_FOR_PAYMENT",OverseasOrderRules.tracking("ISSUED",BigDecimal.ZERO,BigDecimal.TEN,pending));
        assertEquals("PROCESSING",OverseasOrderRules.tracking("ISSUED",BigDecimal.ONE,BigDecimal.TEN,pending));
        assertEquals("PARTIALLY_ARRIVED",OverseasOrderRules.tracking("ISSUED",BigDecimal.ONE,BigDecimal.TEN,new OverseasOrderRules.Quantities(3,3,3,1,0)));
    }
    @Test void quantityChainRejectsUnarrivedAndExcessCollections() {
        assertThrows(IllegalArgumentException.class,()->new OverseasOrderRules.Quantities(3,3,3,1,2));
        assertThrows(IllegalArgumentException.class,()->new OverseasOrderRules.Quantities(3,4,3,1,0));
        assertThrows(IllegalArgumentException.class,()->new OverseasOrderRules.Quantities(3,2,3,1,0));
        assertThrows(IllegalArgumentException.class,()->new OverseasOrderRules.Quantities(3,3,2,3,0));
    }
    @Test void partialPickupMayLeaveAnyBalanceButFinalPickupCannot() {
        var first=new OverseasOrderRules.Quantities(3,3,3,3,2);
        assertDoesNotThrow(()->OverseasOrderRules.requireCollectionBalance(List.of(first),new BigDecimal("5000")));
        var last=new OverseasOrderRules.Quantities(3,3,3,3,3);
        assertThrows(IllegalArgumentException.class,()->OverseasOrderRules.requireCollectionBalance(List.of(last),new BigDecimal("0.01")));
        assertDoesNotThrow(()->OverseasOrderRules.requireCollectionBalance(List.of(last),BigDecimal.ZERO));
    }
    @Test void totalUsesSellingPricesAndShippingWithExactDecimalArithmetic() {
        assertEquals(new BigDecimal("1010.30"),OverseasOrderRules.total(List.of(new BigDecimal("0.10"),new BigDecimal("0.20"),new BigDecimal("1000")),new BigDecimal("10")));
        assertThrows(IllegalArgumentException.class,()->OverseasOrderRules.money(new BigDecimal("-1")));
    }
}
