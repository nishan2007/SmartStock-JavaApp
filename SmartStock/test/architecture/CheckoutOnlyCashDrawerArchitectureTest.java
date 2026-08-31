package architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CheckoutOnlyCashDrawerArchitectureTest {
    @Test
    void checkoutOpensDrawerImmediatelyAfterCommitAndDoesNotPulseItAgainWithReceipt() throws Exception {
        String source = Files.readString(Path.of("src/ui/screens/MakeASale.java"));
        int checkout = source.indexOf("private void checkoutThroughLanApi");
        int committed = source.indexOf("result = LanApiClient.checkout(request, checkoutKey)", checkout);
        int cashBranch = source.indexOf("if (\"CASH\".equals(paymentMethod))", committed);
        int drawerCall = source.indexOf("EpsonReceiptPrintService.openDrawer(printer)", cashBranch);
        int receiptLoad = source.indexOf("ReceiptBuilder.loadSaleReceipt", drawerCall);

        assertTrue(checkout >= 0);
        assertTrue(committed > checkout);
        assertTrue(cashBranch > committed);
        assertTrue(drawerCall > cashBranch);
        assertTrue(receiptLoad > drawerCall);
        assertTrue(source.contains("ReceiptPrinter.printToPosPrinter(receipt, printer, false, false)"));
    }
}
