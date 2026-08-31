package Receipt;

import models.CashDrawerSession;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CashDrawerCloseReceiptPrinterTest {
    @Test
    void formatsAllRequiredClosingAmounts() {
        CashDrawerSession session = new CashDrawerSession(
                42, 3, 1, "device-1", "Front Draw", "Register 1",
                new BigDecimal("40000"), new BigDecimal("125000"),
                new BigDecimal("124500"), new BigDecimal("84500"),
                new BigDecimal("-500"), "CLOSED",
                Timestamp.valueOf("2026-08-03 08:00:00"), "Alice",
                1, "Alice", 1, "Alice",
                Timestamp.valueOf("2026-08-03 18:00:00"), "Alice",
                1, "Alice", null, null, null
        );

        String receipt = CashDrawerCloseReceiptPrinter.formatText(
                session, new BigDecimal("84500"), new BigDecimal("40000"), List.of(
                        new CashDrawerCloseReceiptPrinter.BreakdownLine(5000, 10, 2, 8),
                        new CashDrawerCloseReceiptPrinter.BreakdownLine(1000, 20, 10, 10)
                ), List.of("Alice", "Bob", "Carol"),new BigDecimal("5500"));

        assertTrue(receipt.contains("Set Cash"));
        assertTrue(receipt.contains("Expected Cash"));
        assertTrue(receipt.contains("Total Expected CIH"));
        assertTrue(receipt.contains("$85,000"));
        assertTrue(receipt.contains("Returned Amount"));
        assertTrue(receipt.contains("$5,500"));
        assertTrue(receipt.contains("Counted Cash"));
        assertTrue(receipt.contains("Variance"));
        assertTrue(receipt.contains("CIH"));
        assertTrue(receipt.contains("Float"));
        assertTrue(receipt.contains("Cash to Remove"));
        assertTrue(receipt.contains("$$             QTY      FLOAT        CIH"));
        assertTrue(receipt.contains("$5,000          10          2          8"));
        assertTrue(receipt.contains("TOTAL"));
        assertTrue(receipt.contains("Cashier 1"));
        assertTrue(receipt.contains("Alice"));
        assertTrue(receipt.contains("Cashier 2"));
        assertTrue(receipt.contains("Bob"));
        assertTrue(receipt.contains("Cashier 3"));
        assertTrue(receipt.contains("Carol"));
    }
}
