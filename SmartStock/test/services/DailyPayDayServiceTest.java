package services;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.assertEquals;

class DailyPayDayServiceTest {
    @Test void explicitHalfDayAndWholeCurrencyRounding() {
        assertEquals(new BigDecimal("4000"), DailyPayDayService.dailyPay(new BigDecimal("4000"), false));
        assertEquals(new BigDecimal("2000"), DailyPayDayService.dailyPay(new BigDecimal("4000"), true));
        assertEquals(new BigDecimal("2001"), DailyPayDayService.dailyPay(new BigDecimal("4001"), true));
        assertEquals(BigDecimal.ZERO, DailyPayDayService.dailyPay(BigDecimal.ZERO, true));
    }
}
