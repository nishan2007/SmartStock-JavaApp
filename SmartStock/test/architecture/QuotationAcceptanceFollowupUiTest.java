package architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class QuotationAcceptanceFollowupUiTest {
    private static String source(String file) throws Exception {
        return Files.readString(Path.of(file));
    }

    @Test
    void unpaidBalanceRequiresExplicitAccountChargeConfirmation() throws Exception {
        String quotations = source("src/ui/screens/Quotations.java");
        String invoices = source("src/ui/screens/Invoices.java");

        assertTrue(quotations.contains("if (payment == null) return;"));
        assertTrue(quotations.contains("confirmAccountCharge(this, updated)"));
        assertTrue(invoices.contains("Quotations.confirmAccountCharge(this, updated)"));
        assertTrue(quotations.contains("Choose No to leave the invoice open and unpaid."));
    }

    @Test
    void paymentAndDeliveryDialogsApplyThemeAfterAddingControls() throws Exception {
        String quotations = source("src/ui/screens/Quotations.java");
        String invoices = source("src/ui/screens/Invoices.java");

        String paymentDialog = quotations.substring(quotations.indexOf("static class PaymentPrompt"),
                quotations.indexOf("static class QuotationEditor"));
        String deliveryDialog = invoices.substring(invoices.indexOf("static class DeliveryDialog"));
        assertTrue(paymentDialog.indexOf("ThemeManager.applyToWindow(this);")
                > paymentDialog.indexOf("new ui.components.DocumentWorkspace"));
        assertTrue(deliveryDialog.indexOf("ThemeManager.applyToWindow(this);")
                > deliveryDialog.indexOf("new ui.components.DocumentWorkspace"));
        assertTrue(invoices.contains("\"Invoiced\""));
    }
}
