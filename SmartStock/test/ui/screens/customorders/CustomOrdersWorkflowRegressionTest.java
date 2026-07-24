package ui.screens.customorders;

import org.junit.jupiter.api.Test;
import services.CustomOrderDataService.PrintMaterialOption;

import javax.swing.*;
import java.awt.*;
import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class CustomOrdersWorkflowRegressionTest {
    @Test
    void missingPaymentSelectionDoesNotCrashOrderTotalsOrCustomerNext() {
        assertFalse(CustomOrders.requiresPaymentReference(null));
        assertFalse(CustomOrders.requiresPaymentReference("CASH"));
        assertTrue(CustomOrders.requiresPaymentReference("CARD"));
        assertTrue(CustomOrders.requiresPaymentReference("CHEQUE"));
        assertTrue(CustomOrders.requiresPaymentReference("MMG"));
    }

    @Test
    void printAddonStateAndSelectorsAreReadyBeforeTheSheetIsOpened() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            CustomOrdersNewOrderTabPanel panel = new CustomOrdersNewOrderTabPanel(new NoOpHandler());

            assertNotNull(panel.printAddonModel);
            assertNotNull(panel.printAddonTable);

            @SuppressWarnings("unchecked")
            ListCellRenderer<Object> renderer =
                    (ListCellRenderer<Object>) panel.printMaterialBox.getRenderer();
            JList<Object> list = new JList<>();
            Component normal = renderer.getListCellRendererComponent(
                    list, new PrintMaterialOption(1L, "Vinyl"), 0, false, false);
            Color normalBackground = normal.getBackground();
            Color normalForeground = normal.getForeground();
            Component selected = renderer.getListCellRendererComponent(
                    list, new PrintMaterialOption(1L, "Vinyl"), 0, true, false);

            assertEquals(new Color(9, 13, 19), normalBackground);
            assertEquals(new Color(238, 242, 247), normalForeground);
            assertEquals(new Color(29, 78, 216), selected.getBackground());
            assertEquals(Color.WHITE, selected.getForeground());
        });
    }

    @Test
    void paymentMethodsAreSelectableAndClearlyHighlighted() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            RecordingHandler handler = new RecordingHandler();
            CustomOrdersNewOrderTabPanel panel = new CustomOrdersNewOrderTabPanel(handler);

            panel.cardPaymentButton.doClick();

            assertTrue(panel.cardPaymentButton.isSelected());
            assertFalse(panel.cashPaymentButton.isSelected());
            assertEquals("CARD", handler.selectedPaymentMethod);
            assertEquals(new Color(29, 78, 216), panel.cardPaymentButton.getBackground());
            assertEquals(Color.WHITE, panel.cardPaymentButton.getForeground());
        });
    }

    @Test
    void customOrderMoneyUsesWholeGuyanaDollars() {
        assertEquals("101", CustomOrders.money(new BigDecimal("100.50")));
        assertEquals(new BigDecimal("100"), CustomOrders.wholeDollar(
                new JTextField("100.00"), "upfront payment", true));
        assertThrows(IllegalArgumentException.class, () -> CustomOrders.wholeDollar(
                new JTextField("100.50"), "upfront payment", true));
    }

    private static class NoOpHandler implements CustomOrdersNewOrderTabPanel.Handler {
        @Override public void orderItemChanged() { }
        @Override public void orderLookup() { }
        @Override public void variantChanged() { }
        @Override public void printMaterialChanged() { }
        @Override public void printPresetChanged() { }
        @Override public Runnable printLineCountChanged() { return () -> { }; }
        @Override public void addPrintAddon() { }
        @Override public void removePrintAddon() { }
        @Override public Runnable areaChanged() { return () -> { }; }
        @Override public void addPlacement() { }
        @Override public void addOrderLine() { }
        @Override public void removeOrderLine() { }
        @Override public void editLineDiscount() { }
        @Override public void cartSelectionChanged() { }
        @Override public void selectPaymentMethod(String method) { }
        @Override public Runnable upfrontChanged() { return () -> { }; }
        @Override public boolean canLeaveStep(int step) { return true; }
        @Override public void enterStep(int step) { }
        @Override public void saveOrder(boolean printOrderSlip) { }
        @Override public void clearOrder() { }
    }

    private static final class RecordingHandler extends NoOpHandler {
        private String selectedPaymentMethod;

        @Override public void selectPaymentMethod(String method) {
            selectedPaymentMethod = method;
        }
    }
}
