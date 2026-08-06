package ui.screens;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BalanceSheetOtherIncomeTest {
    @Test
    void acceptsPositiveWholeGydAmounts() {
        assertEquals(new BigDecimal("1250"), BalanceSheet.parseWholeGydAmount("$1,250"));
        assertEquals(new BigDecimal("1250"), BalanceSheet.parseWholeGydAmount("1250.00"));
    }

    @Test
    void rejectsCentsZeroNegativeAndMalformedAmounts() {
        IllegalArgumentException cents = assertThrows(IllegalArgumentException.class,
                () -> BalanceSheet.parseWholeGydAmount("1250.50"));
        assertTrue(cents.getMessage().contains("whole GYD"));
        assertThrows(IllegalArgumentException.class, () -> BalanceSheet.parseWholeGydAmount("0"));
        assertThrows(IllegalArgumentException.class, () -> BalanceSheet.parseWholeGydAmount("-1"));
        assertThrows(IllegalArgumentException.class, () -> BalanceSheet.parseWholeGydAmount("not money"));
    }

    @Test
    void rejectsAmountsLargerThanTheLedgerPrecision() {
        assertThrows(IllegalArgumentException.class,
                () -> BalanceSheet.parseWholeGydAmount("10000000000"));
    }
}
