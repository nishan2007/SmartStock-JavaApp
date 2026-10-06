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
        String quotation = QuotationInvoiceDocumentBuilder.buildSampleQuotation(receipt, print);
        assertTrue(quotation.contains(
                ".logo img { width: 100%; height: auto; max-height: 96px; max-width: 390px; }"),
                "Quotation logos must grow to fill the available header width without changing aspect ratio");
        int unitPrice = quotation.indexOf("U/PRICE");
        int original = quotation.indexOf("ORIG. TOTAL");
        int discount = quotation.indexOf("DISC. %");
        int amount = quotation.indexOf("AMOUNT");
        assertTrue(unitPrice >= 0 && unitPrice < original && original < discount && discount < amount,
                "Quotation lines must show unit price, discount percent, and final amount in order");
        assertTrue(quotation.contains("SUBTOTAL") && quotation.contains("GRAND TOTAL"),
                "Quotation must show subtotal and cash grand total");
        assertTrue(quotation.contains(">0%</td>"),
                "Quotation lines must render the applied discount as a percentage");
        for (String compact : new String[]{
                ServerQuotationInvoiceDocumentBuilder.buildSampleQuotation(receipt, print, true),
                ServerQuotationInvoiceDocumentBuilder.buildSampleInvoice(receipt, print, true)}) {
            assertTrue(compact.contains("data-template='compact'"));
            assertTrue(compact.contains("Item Description"));
            assertTrue(compact.contains("Ext. Price"));
            assertTrue(compact.contains("class='document-barcode' align='center'"));
            assertTrue(compact.indexOf("class='document-barcode'") > compact.indexOf("GRAND TOTAL"));
            assertTrue(compact.contains("class='grid-note' align='center'"));
            assertTrue(!compact.contains("class='document-grid'"));
            assertTrue(!compact.contains("class='blank'"));
            assertTrue(!compact.contains("ORIG. TOTAL"));
            assertTrue(compact.contains("$504.00"));
            assertTrue(compact.contains("$190.00"));
            assertTrue(compact.contains("TOTAL SAVED") && compact.contains("SUBTOTAL") && compact.contains("GRAND TOTAL"));
            assertTrue(compact.indexOf("TOTAL SAVED") < compact.indexOf("SUBTOTAL"));
            assertTrue(compact.indexOf("Commercial Filter Set") < compact.indexOf("Installation Kit"));
        }
        String delivery = QuotationInvoiceDocumentBuilder.buildSampleDelivery(receipt, print);
        assertTrue(!delivery.contains("DISC. %"),
                "Delivery bills must retain their delivery-focused columns");
    }

    @Test
    void documentHeadersIncludeScannableNumberBarcodesWithoutImageTags() {
        String quote = ServerQuotationInvoiceDocumentBuilder.documentBarcodeHtml("Q-MAIN-POS1-000123");
        String invoice = ServerQuotationInvoiceDocumentBuilder.documentBarcodeHtml("INV-MAIN-POS1-000088");

        assertTrue(quote.contains("class='document-barcode'"));
        assertTrue(quote.contains("data-barcode-value='Q-MAIN-POS1-000123'"));
        assertTrue(quote.contains("bgcolor='#000000'"));
        assertTrue(invoice.contains("data-barcode-value='INV-MAIN-POS1-000088'"));
        assertTrue(!quote.contains("<img"), "Document barcodes must survive the print renderer's image filtering");
    }

    @Test
    void cashTotalRoundsWhileSubtotalKeepsCents() throws Exception {
        var method = ServerQuotationInvoiceDocumentBuilder.class.getDeclaredMethod("appendGridSignatureRows",
                StringBuilder.class, int.class, String.class, BigDecimal.class,
                boolean.class, boolean.class, boolean.class, String.class);
        method.setAccessible(true);
        for (boolean signatures : new boolean[]{true, false}) {
            StringBuilder html = new StringBuilder();
            method.invoke(null, html, 6, "GRAND TOTAL", new BigDecimal("279000.11"), signatures, false, false, null);
            assertTrue(html.toString().contains("SUBTOTAL</td><td class='total-amount' colspan='2' style=' padding:6px 8px; border-top:2px solid #111; border-left:2px solid #111; font-size:12px; vertical-align:middle'>$279,000.11</td>"));
            assertTrue(html.toString().contains("GRAND TOTAL</td><td class='total-amount' colspan='2' style=' padding:6px 8px; border-top:2px solid #111; border-left:2px solid #111; font-size:12px; vertical-align:middle'>$279,000</td>"));
            html.setLength(0);
            method.invoke(null, html, 6, "GRAND TOTAL", new BigDecimal("279000.50"), signatures, false, false, null);
            assertTrue(html.toString().contains("$279,001</td>"));
        }
    }
    @Test
    void savingsSubtractsRoundedPayableFromOriginalTotal() {
        org.junit.jupiter.api.Assertions.assertEquals(new BigDecimal("128500"),
                ServerQuotationInvoiceDocumentBuilder.totalSaved(new BigDecimal("407500"), new BigDecimal("279000.11")));
        org.junit.jupiter.api.Assertions.assertEquals(new BigDecimal("128499"),
                ServerQuotationInvoiceDocumentBuilder.totalSaved(new BigDecimal("407500"), new BigDecimal("279000.50")));
    }
}
