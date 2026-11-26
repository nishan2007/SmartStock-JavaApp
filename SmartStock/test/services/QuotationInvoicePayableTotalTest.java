package services;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class QuotationInvoicePayableTotalTest {
    @Test void payableTotalMatchesPrintedWholeDollarAmount() {
        for (String amount : new String[]{"750.25", "750.50", "750.75", "1000.00", "12541.49"}) {
            BigDecimal value = new BigDecimal(amount);
            assertEquals(utils.CurrencyFormatter.format(value),
                    utils.CurrencyFormatter.format(ServerQuotationInvoiceService.payableTotal(value)));
        }
        assertEquals(new BigDecimal("750.00"), ServerQuotationInvoiceService.payableTotal(new BigDecimal("750.25")));
        assertEquals(new BigDecimal("751.00"), ServerQuotationInvoiceService.payableTotal(new BigDecimal("750.50")));
    }

    @Test void discountedLinesAreSummedBeforeRoundingThePayableTotal() {
        BigDecimal line = new BigDecimal("750.25");
        BigDecimal total = ServerQuotationInvoiceService.payableTotal(line.multiply(BigDecimal.valueOf(3)));
        assertEquals(new BigDecimal("2251.00"), total);
        assertEquals(BigDecimal.ZERO.setScale(2), total.subtract(new BigDecimal("2251.00")));
    }
}
