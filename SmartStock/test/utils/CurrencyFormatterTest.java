package utils;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CurrencyFormatterTest {
    @Test
    void roundsGuyanaDollarAmountsToNearestTwentyHalfUp() {
        assertRounded("100", "100");
        assertRounded("109", "100");
        assertRounded("110", "120");
        assertRounded("111", "120");
        assertRounded("119", "120");
        assertRounded("120", "120");
        assertRounded("0", "0");
    }

    @Test
    void roundsOnlyAfterTheFullPreciseCalculation() {
        BigDecimal areaPriceWithAddonAndDiscount = new BigDecimal("93.75")
                .add(new BigDecimal("25.50"))
                .subtract(new BigDecimal("5.25"));

        assertEquals(new BigDecimal("120"),
                CurrencyFormatter.roundToNearestTwenty(areaPriceWithAddonAndDiscount));
    }

    @Test
    void rejectsNegativeAmountsAndInvalidIncrements() {
        assertThrows(IllegalArgumentException.class,
                () -> CurrencyFormatter.roundToNearestTwenty(new BigDecimal("-1")));
        assertThrows(IllegalArgumentException.class,
                () -> CurrencyFormatter.roundToIncrement(BigDecimal.ONE, BigDecimal.ZERO));
    }

    private static void assertRounded(String input, String expected) {
        assertEquals(new BigDecimal(expected),
                CurrencyFormatter.roundToNearestTwenty(new BigDecimal(input)));
    }
}
