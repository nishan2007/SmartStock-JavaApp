package ui.screens;

import org.junit.jupiter.api.Test;
import services.QuotationInvoiceViewService;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class DocumentStudioLayoutTest {
    @Test void statusLabelsHaveVisibleBoundsInTheDocumentList() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JTable table = new JTable(Quotations.readOnlyModel("Document", "Status"));
            ((DefaultTableModel)table.getModel()).addRow(new Object[]{"QT-42", "ACCEPTED"});
            DocumentStudio.table(table); table.setSize(400, 100); table.doLayout();
            Component rendered = table.prepareRenderer(table.getCellRenderer(0, 1), 0, 1);
            JLabel status = find((Container)rendered, JLabel.class, label -> label.getText().equals("Accepted"));
            assertNotNull(status); assertTrue(status.getWidth() > 0); assertTrue(status.getHeight() > 0);
        });
    }
    private static <T extends Component> T find(Container root, Class<T> kind, java.util.function.Predicate<T> test) {
        for (Component child : root.getComponents()) {
            if (kind.isInstance(child) && test.test(kind.cast(child))) return kind.cast(child);
            if (child instanceof Container container) { T found = find(container, kind, test); if (found != null) return found; }
        }
        return null;
    }

    @Test void compactLineTablePreservesStoredColumnsAndDoesNotAllowEditingDerivedAmounts() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            DefaultTableModel model = new DefaultTableModel(new String[]{"ID", "Item", "SKU", "Qty", "Unit", "Discount", "Delivery", "Notes", "Original", "Reason", "Approver", "Approver name", "Token", "Custom"}, 0);
            model.addRow(new Object[]{7, "Boots", "SB-1", 10, new BigDecimal("3500"), new BigDecimal("10"), "PICKUP", "", new BigDecimal("3500"), null, null, null, null, null});
            JTable table = Quotations.QuotationEditor.studioLineTable(model);
            assertEquals(6, table.getColumnCount());
            assertEquals(List.of("Item", "Type", "Qty", "Rate", "Discount %", "Amount"),
                    java.util.stream.IntStream.range(0, table.getColumnCount()).mapToObj(i -> table.getColumnModel().getColumn(i).getHeaderValue().toString()).toList());
            assertFalse(table.isCellEditable(0, 1)); assertFalse(table.isCellEditable(0, 5));
            assertTrue(table.isCellEditable(0, 2)); table.setValueAt(3, 0, 2);
            assertEquals(3, model.getValueAt(0, 3)); assertEquals(new BigDecimal("3500"), model.getValueAt(0, 4));
            JLabel amount = (JLabel)table.prepareRenderer(table.getCellRenderer(0, 5), 0, 5);
            assertEquals(utils.CurrencyFormatter.format(new BigDecimal("9450")), amount.getText());
        });
    }

    @Test void templateButtonsStayBoundToTheExistingTemplateSelection() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JComboBox<String> selection = new JComboBox<>(new String[]{"Grid", "Compact"});
            JComponent controls = DocumentStudio.templateSwitch(selection);
            JButton compact = find(controls, JButton.class, b -> b.getText().equals("Compact"));
            assertNotNull(compact); compact.doClick(); assertEquals(1, selection.getSelectedIndex());
            selection.setEnabled(false); assertFalse(compact.isEnabled());
        });
    }

    @Test void formViewportFitsItsAvailableWidthRatherThanClippingTheVariantColumn() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JComponent form = DocumentStudio.topAligned(DocumentStudio.row(
                    DocumentStudio.field("Item", new JTextField("Long item name")),
                    DocumentStudio.field("Variant", new JTextField("Large / Yellow"))));
            assertTrue(((Scrollable)form).getScrollableTracksViewportWidth());
        });
    }

    @Test void invoiceWorkspaceShowsOnlyItsPaymentsAndRetainsExistingActionHandlers() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JTable invoices = new JTable(Quotations.readOnlyModel("ID", "Invoice", "Customer"));
            ((DefaultTableModel)invoices.getModel()).addRow(new Object[]{42L, "INV-42", "Harbour"});
            AtomicInteger clicks = new AtomicInteger();
            JButton original = new JButton("Process Payment"); original.addActionListener(e -> clicks.incrementAndGet());
            InvoiceFulfillmentPanel panel = new InvoiceFulfillmentPanel(null, invoices, new JPanel(), List.of(original));
            invoices.setRowSelectionInterval(0, 0);
            panel.applySnapshot(new InvoiceFulfillmentPanel.Snapshot(
                    new QuotationInvoiceViewService.InvoiceFinancials(42, "INV-42", BigDecimal.TEN, BigDecimal.ONE, new BigDecimal("9"), BigDecimal.ZERO, BigDecimal.TEN, BigDecimal.TEN, 7, "Harbour", false),
                    List.of(new QuotationInvoiceViewService.PaymentSummary(1, 7, "INV-42", "Harbour", BigDecimal.ONE, "CASH", "", "", "2026-11-23"),
                            new QuotationInvoiceViewService.PaymentSummary(2, 8, "INV-43", "Other", BigDecimal.TEN, "CARD", "", "", "2026-11-23")), List.of()));
            JTable payments = find(panel, JTable.class, t -> t.getColumnCount() == 3);
            assertNotNull(payments); assertEquals(1, payments.getRowCount()); assertEquals("CASH", payments.getValueAt(0, 1));
            JButton action = find(panel, JButton.class, b -> b.getText().equals("Record payment"));
            assertNotNull(action); action.doClick(); assertEquals(1, clicks.get());
            JButton back = find(panel, JButton.class, b -> b.getText().equals("Back to invoices"));
            assertNotNull(back); back.doClick(); assertEquals(-1, invoices.getSelectedRow());
        });
    }
}
