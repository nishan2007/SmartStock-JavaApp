package ui.screens.customorders;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class CustomOrderAreaPricingTest {
    @Test void smallerDesignIncludesUnusedMaterialWidth() {
        BigDecimal design = new BigDecimal("36");
        BigDecimal area = CustomOrders.billableArea(design, new BigDecimal("72"),
                new BigDecimal("44"), "IN", "SQ_FT");
        assertEquals(0, new BigDecimal("22").compareTo(area));
        assertEquals(new BigDecimal("36"), design);
    }

    @Test void orderLengthDeterminesMaterialConsumed() {
        assertEquals(0, new BigDecimal("11").compareTo(CustomOrders.billableArea(
                new BigDecimal("20"), new BigDecimal("36"), new BigDecimal("44"), "IN", "SQ_FT")));
    }

    @Test void legacyItemWithoutMaterialWidthUsesDesignWidth() {
        assertEquals(0, new BigDecimal("18").compareTo(CustomOrders.billableArea(
                new BigDecimal("36"), new BigDecimal("72"), null, "IN", "SQ_FT")));
    }
}
