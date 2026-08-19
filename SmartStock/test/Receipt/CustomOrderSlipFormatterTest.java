package Receipt;

import managers.CompanyCustomizationManager;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CustomOrderSlipFormatterTest {
    @Test
    void fortyColumnSlipDoesNotReserveBlankDetailLines() {
        CustomOrderSlipData data = new CustomOrderSlipData(
                "CO-001", "Alex Customer", "555-0199", "CA-000100",
                LocalDate.of(2026, 5, 30), Timestamp.from(Instant.parse("2026-05-22T12:00:00Z")),
                "Sample Cashier", "Main Store", "POS-01", "CASH", "", "PARTIAL",
                new BigDecimal("87.00"), new BigDecimal("30.00"), new BigDecimal("57.00"),
                "Use royal blue thread.",
                List.of(new CustomOrderSlipData.Line("Logo T-Shirt", "Large / Black",
                        "Front print / 4 lines", "Place logo centered on chest.", new BigDecimal("43.00"))));
        CompanyCustomizationManager.ReceiptSettings receiptSettings =
                new CompanyCustomizationManager.ReceiptSettings("Deckers", "", "", "", "", "", "", "",
                        "", "", "", "Thank you", "", false, true, true, true, true, true, true,
                        false, false, BigDecimal.ZERO, 1, BigDecimal.ZERO, false,
                        CompanyCustomizationManager.AccountPaymentReceiptSettings.defaults());
        CompanyCustomizationManager.CustomOrderSlipSettings slipSettings =
                new CompanyCustomizationManager.CustomOrderSlipSettings(true, false, "CUSTOMER'S ORDER SLIP",
                        "", "", "Order footer", 8, false, true, true, true, true, true, true,
                        true, true, true, true, false, false, false);

        String text = CustomOrderSlipFormatter.format40Column(data, receiptSettings, slipSettings);

        assertTrue(text.contains("Logo T-Shirt / Large / Black $43"));
        assertTrue(text.contains("Notes: Use royal blue thread."));
        assertFalse(text.contains("________________________________________"),
                "40-column slips must not reserve unused writing rows");
    }
}
