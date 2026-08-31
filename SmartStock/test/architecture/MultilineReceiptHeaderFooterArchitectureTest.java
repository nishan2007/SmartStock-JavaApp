package architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class MultilineReceiptHeaderFooterArchitectureTest {
    @Test
    void receiptEditorAndFormattersPreserveIntentionalLineBreaks() throws Exception {
        String screen = read("src/ui/screens/CompanyCustomization.java");
        String panel = read("src/ui/screens/companyprefs/SaleReceiptPanel.java");

        assertTrue(screen.contains("private final JTextArea headerLineField = receiptTextArea()"));
        assertTrue(screen.contains("private final JTextArea footerLineField = receiptTextArea()"));
        assertTrue(screen.contains("area.setLineWrap(true)"));
        assertTrue(screen.contains("area.setWrapStyleWord(true)"));
        assertTrue(panel.contains("addMultilineRow(this, 1, \"Header Lines\""));
        assertTrue(panel.contains("addMultilineRow(this, 2, \"Footer Lines\""));

        for (String path : new String[]{
                "src/Receipt/ReceiptFormatter.java",
                "src/Receipt/AccountPaymentReceiptFormatter.java",
                "src/Receipt/ReturnReceiptFormatter.java",
                "src/Receipt/CashDrawerCloseReceiptPrinter.java",
                "src/Receipt/ServerQuotationInvoiceDocumentBuilder.java"}) {
            String formatter = read(path);
            assertTrue(formatter.contains("split(\"\\n\", -1)") || formatter.contains("split(\"\\n\",-1)"), path);
        }
    }

    private static String read(String relative) throws Exception {
        return Files.readString(Path.of(relative));
    }
}
