package architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class InvoicePaymentHistoryArchitectureTest {
    @Test
    void invoiceScreenListsPaymentsAndCanReopenTheirReceipts() throws Exception {
        String screen = Files.readString(Path.of("src/ui/screens/Invoices.java"));
        String client = Files.readString(Path.of("src/services/QuotationInvoiceViewService.java"));
        String server = Files.readString(Path.of("src/services/ServerQuotationInvoiceViewService.java"));
        String api = Files.readString(Path.of("src/services/LanApiServer.java"));

        assertTrue(screen.contains("tabs.addTab(\"Payments\""));
        assertTrue(screen.contains("new AccountPaymentReceiptPreview(customerId, transactionId)"));
        assertTrue(client.contains("listPayments()"));
        assertTrue(server.contains("t.transaction_type = 'PAYMENT'"));
        assertTrue(api.contains("case\"LIST_PAYMENTS\""));
    }
}
