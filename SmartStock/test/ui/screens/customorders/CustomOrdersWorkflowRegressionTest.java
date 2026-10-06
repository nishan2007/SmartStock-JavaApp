package ui.screens.customorders;

import org.junit.jupiter.api.Test;
import services.CustomOrderDataService.PrintMaterialOption;
import ui.design.DeckersPalette;

import javax.swing.*;
import javax.swing.plaf.basic.BasicButtonUI;
import java.awt.*;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class CustomOrdersWorkflowRegressionTest {
    @Test
    void laterPaymentAcceptsFormattedAmountsAndRejectsEmptyOrNonpositiveAmounts() {
        assertEquals(new BigDecimal("15000.50"),CustomOrders.parseOrderPaymentAmount(" 15,000.50 "));
        for(String value:new String[]{"", "bad", "0", "-1"})
            assertThrows(IllegalArgumentException.class,()->CustomOrders.parseOrderPaymentAmount(value));
    }
    @Test
    void editingLineChangesActionAndCancelRestoresAdding() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var panel = new CustomOrdersNewOrderTabPanel(new NoOpHandler());
            panel.setEditingLine(true);
            assertEquals("Update Line", panel.addLineButton.getText());
            assertTrue(panel.cancelLineEditButton.isVisible());
            assertFalse(panel.lineQuantityField.isEnabled());
            panel.setEditingLine(false);
            assertEquals("Add Line", panel.addLineButton.getText());
            assertFalse(panel.cancelLineEditButton.isVisible());
            assertTrue(panel.lineQuantityField.isEnabled());
        });
    }
    @Test
    void customerItemButtonsOpenTheSameDetailsAction() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            int[] clicks = {0};
            var panel = new CustomOrdersNewOrderTabPanel(new NoOpHandler() {
                @Override public void customerItem() { clicks[0]++; }
            });
            assertFalse(panel.editCustomerItemButton.isVisible());
            panel.customerItemButton.doClick();
            panel.editCustomerItemButton.setVisible(true);
            panel.editCustomerItemButton.doClick();
            assertEquals(2, clicks[0]);
        });
    }
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

            assertEquals(Boolean.TRUE, panel.getClientProperty("SmartStock.preserveThemeColors"));
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

            assertEquals(DeckersPalette.background(), panel.getBackground());
            assertEquals(DeckersPalette.fieldBackground(), normalBackground);
            assertEquals(DeckersPalette.text(), normalForeground);
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
    void paletteButtonsUseADelegateThatPaintsTheirBackgroundOnWindows() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            CustomOrdersNewOrderTabPanel panel = new CustomOrdersNewOrderTabPanel(new NoOpHandler());
            // Window theme refresh replaces delegates before applying preserved colors.
            SwingUtilities.updateComponentTreeUI(panel);
            try {
                var apply = ui.helpers.ThemeManager.class.getDeclaredMethod("applyToComponent", Component.class);
                apply.setAccessible(true);
                apply.invoke(null, panel);
            } catch (ReflectiveOperationException failure) {
                throw new AssertionError(failure);
            }
            List<AbstractButton> buttons = new ArrayList<>();
            collectButtons(panel, buttons);

            for (String label : List.of("Lookup", "Add Placement", "Delete Line", "Clear Order", "Back", "Next")) {
                AbstractButton button = buttons.stream().filter(candidate -> label.equals(candidate.getText()))
                        .findFirst().orElseThrow(() -> new AssertionError("Missing button: " + label));
                assertTrue(button.getUI() instanceof BasicButtonUI, label + " must paint its configured background");
                assertFalse(button.getForeground().equals(button.getBackground()), label + " text must remain visible");
            }
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

    private static void collectButtons(Container container, List<AbstractButton> buttons) {
        for (Component child : container.getComponents()) {
            if (child instanceof AbstractButton button) buttons.add(button);
            if (child instanceof Container nested) collectButtons(nested, buttons);
        }
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
