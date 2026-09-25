package architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class QuotationAcceptanceArchitectureTest {
    @Test
    void invoiceConversionIncludesEveryBoundDiscountSnapshotColumn() throws Exception {
        String source = Files.readString(Path.of("src/services/ServerQuotationInvoiceService.java"));
        int method = source.indexOf("private static long insertInvoiceFromQuotation");
        int nextMethod = source.indexOf("private static void copyQuotationLinesToInvoice", method);
        String conversion = source.substring(method, nextMethod);

        assertTrue(conversion.contains("customer_discount_percent, customer_discount_applied"));
        assertTrue(conversion.contains("ps.setBigDecimal(22,quotation.customerDiscountPercent())"));
        assertTrue(conversion.contains("ps.setBoolean(23,quotation.customerDiscountApplied())"));
    }
}
