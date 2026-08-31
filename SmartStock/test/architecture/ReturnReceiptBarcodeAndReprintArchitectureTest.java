package architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

final class ReturnReceiptBarcodeAndReprintArchitectureTest {
    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path));
    }

    @Test
    void returnReceiptsPrintTheirNumberAsCode128() throws Exception {
        String printer = read("src/Receipt/ReturnReceiptPrinter.java");
        assertTrue(printer.contains("appendEscPosBarcode(body, receipt.returnReceiptNumber())"));
        assertTrue(printer.contains("renderCode128(receipt.returnReceiptNumber()"));
    }

    @Test
    void viewSalesReprintsTheSelectedReturnInsteadOfTheOriginalSale() throws Exception {
        String viewSales = read("src/ui/screens/ViewSales.java");
        assertTrue(viewSales.contains("\"RETURN\".equalsIgnoreCase(selected.transactionType())"));
        assertTrue(viewSales.contains("reprintSelectedReturn(selected)"));
        assertTrue(viewSales.contains("item.returnId() == selected.returnId()"));
        assertTrue(viewSales.contains("ReturnReceiptPrinter.printToPosPrinter(receipt"));
    }
}
