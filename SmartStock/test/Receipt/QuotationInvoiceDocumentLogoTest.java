package Receipt;

import managers.CompanyCustomizationManager;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertTrue;

class QuotationInvoiceDocumentLogoTest {
    @Test
    void quotationPreservesLanImageAssetReferenceForClientPrinting() {
        String logoReference = "smartstock-asset:123e4567-e89b-12d3-a456-426614174000";
        CompanyCustomizationManager.ReceiptSettings receipt =
                new CompanyCustomizationManager.ReceiptSettings("Deckers", "", "", "", "", "", "", "",
                        "", "", "", "Thank you", logoReference, true, true, true, true, true, true, true,
                        false, false, BigDecimal.ZERO, 1, BigDecimal.ZERO, false,
                        CompanyCustomizationManager.AccountPaymentReceiptSettings.defaults());
        CompanyCustomizationManager.QuotationInvoicePrintSettings print =
                new CompanyCustomizationManager.QuotationInvoicePrintSettings(
                        "QUOTE", "Valid for 30 days", "INVOICE", "DELIVERY BILL", "", true);

        String expected = "src='" + logoReference + "'";
        assertTrue(QuotationInvoiceDocumentBuilder.buildSampleQuotation(receipt, print).contains(expected),
                "Quotation logo must retain its LAN image reference");
        assertTrue(QuotationInvoiceDocumentBuilder.buildSampleInvoice(receipt, print).contains(expected),
                "Invoice logo must retain its LAN image reference");
        assertTrue(QuotationInvoiceDocumentBuilder.buildSampleDelivery(receipt, print).contains(expected),
                "Delivery bill logo must retain its LAN image reference");
    }
}
