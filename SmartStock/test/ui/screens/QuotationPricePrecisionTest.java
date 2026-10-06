package ui.screens;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;
class QuotationPricePrecisionTest {
    @Test void amountsKeepCentsAndPercentagesAreNotRoundedAsMoney() {
        assertEquals(new BigDecimal("750.25"),Quotations.parseMoney("$750.25"));
        assertEquals(new BigDecimal("24.9750"),Quotations.parsePercent("24.9750"));
        assertThrows(ArithmeticException.class,()->Quotations.parseMoney("750.251"));
    }
}
