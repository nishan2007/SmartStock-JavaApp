package Receipt;

import managers.CompanyCustomizationManager;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ReturnReceiptFormatterTest {
    @Test
    void formatsReturnUsingReceiptBrandingAndReturnedLines() {
        ReturnReceiptData receipt = new ReturnReceiptData(91, "RET-0007-0002-000123", 42, "R-0042",
                Instant.parse("2026-08-09T17:15:00Z"), "Alex", "CASH",
                new BigDecimal("15.00"), "Wrong size",
                List.of(new ReceiptItem("Work Shirt", "WS-1", 2,
                        new BigDecimal("7.50"), new BigDecimal("7.50"), BigDecimal.ZERO,
                        new BigDecimal("15.00"))));
        CompanyCustomizationManager.ReceiptSettings settings =
                new CompanyCustomizationManager.ReceiptSettings("SmartStock", "", "", "", "", "", "", "",
                        "", "", "", "Thank you", "", false, true, true, true, true, true, true,
                        false, false, BigDecimal.ZERO, 1, BigDecimal.ZERO, false,
                        CompanyCustomizationManager.AccountPaymentReceiptSettings.defaults());

        String text = ReturnReceiptFormatter.formatText(receipt, settings);

        assertTrue(text.contains("RETURN RECEIPT"));
        assertTrue(text.contains("Return ID"));
        assertTrue(text.contains("RET-0007-0002-000123"));
        assertTrue(text.contains("91"));
        assertTrue(text.contains("Original Receipt"));
        assertTrue(text.contains("R-0042"));
        assertTrue(text.contains("Work Shirt"));
        assertTrue(text.contains("Refund Total"));
        assertTrue(text.contains("Wrong size"));

        String reprint = ReturnReceiptFormatter.formatText(receipt, settings, true);
        assertTrue(reprint.contains("DUPLICATE / REPRINT"));
    }
}
