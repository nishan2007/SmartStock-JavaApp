package ui.screens;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PayrollPaymentAmountTest {
    @Test
    void acceptsActualRoundedWholeGydAmount() {
        assertEquals(new BigDecimal("2940"), PayrollDashboard.parsePaymentAmount("$2,940"));
        assertEquals(new BigDecimal("2950"), PayrollDashboard.parsePaymentAmount("2950.00"));
    }

    @Test
    void rejectsInvalidPaymentAmounts() {
        assertThrows(IllegalArgumentException.class, () -> PayrollDashboard.parsePaymentAmount("2940.50"));
        assertThrows(IllegalArgumentException.class, () -> PayrollDashboard.parsePaymentAmount("0"));
        assertThrows(IllegalArgumentException.class, () -> PayrollDashboard.parsePaymentAmount("not money"));
    }

    @Test
    void suggestsCashAmountThatSettlesTheRoundedPayrollTotal() {
        assertEquals(new BigDecimal("39160"), PayrollDashboard.cashPaymentAmountDue(
                new BigDecimal("39167"), BigDecimal.ZERO));
        assertEquals(new BigDecimal("20"), PayrollDashboard.cashPaymentAmountDue(
                new BigDecimal("39167"), new BigDecimal("39140")));
    }
}
