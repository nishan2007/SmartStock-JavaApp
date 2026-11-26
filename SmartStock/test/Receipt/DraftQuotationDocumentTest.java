package Receipt;

import managers.CompanyCustomizationManager;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class DraftQuotationDocumentTest {
    @Test void unsavedQuotationUsesExistingLayoutsAndEscapesCustomerContent() {
        var receipt = new CompanyCustomizationManager.ReceiptSettings("Deckers", "", "", "", "", "", "", "",
                "", "", "", "", "", false, true, true, true, true, true, true,
                false, false, BigDecimal.ZERO, 1, BigDecimal.ZERO, false,
                CompanyCustomizationManager.AccountPaymentReceiptSettings.defaults());
        var settings = new CompanyCustomizationManager.QuotationInvoicePrintSettings(
                "QUOTATION", "Valid until the stated date", "INVOICE", "DELIVERY NOTE", "Thank you", true);
        var lines = List.of(new ServerQuotationInvoiceDocumentBuilder.DocumentLine(2, null, null,
                "Custom <logo>", BigDecimal.TEN, BigDecimal.ZERO, new BigDecimal("20")));
        for (boolean compact : List.of(false, true)) {
            String html = ServerQuotationInvoiceDocumentBuilder.renderDraftQuotation(receipt, "", settings,
                    "Harbour <Supplies>", "555-0100", "sales@example.test", "2026-12-07", "2026-12-14",
                    lines, new BigDecimal("20"), compact);
            assertTrue(html.contains("UNSAVED"));
            assertTrue(html.contains("DRAFT PREVIEW"));
            assertTrue(html.contains("Harbour &lt;Supplies&gt;"));
            assertTrue(html.contains("Custom &lt;logo&gt;"));
            assertTrue(html.contains("2026-12-14"));
            assertTrue(html.contains("GRAND TOTAL"));
            assertEquals(compact, html.contains("data-template='compact'"));
        }
    }
}
