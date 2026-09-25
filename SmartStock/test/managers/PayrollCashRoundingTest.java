package managers;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PayrollCashRoundingTest {
    @Test
    void cashPaymentAtNearestTwentySettlesSmallDifference() {
        assertEquals(BigDecimal.ZERO, ServerTimeClockManager.paymentAmountDue(
                new BigDecimal("39167"), new BigDecimal("39160"), "CASH"));
        assertEquals(new BigDecimal("20"), ServerTimeClockManager.paymentAmountDue(
                new BigDecimal("39167"), new BigDecimal("39140"), "CASH"));
        assertEquals(BigDecimal.ZERO, ServerTimeClockManager.paymentAmountDue(
                new BigDecimal("39173"), new BigDecimal("39180"), "CASH"));
    }

    @Test
    void bankPaymentKeepsExactBalance() {
        assertEquals(new BigDecimal("7"), ServerTimeClockManager.paymentAmountDue(
                new BigDecimal("39167"), new BigDecimal("39160"), "BANK"));
    }
}
