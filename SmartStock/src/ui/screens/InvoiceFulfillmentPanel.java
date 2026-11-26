package ui.screens;

import services.QuotationInvoiceViewService;
import ui.helpers.UiTaskRunner;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.List;

/** Selected invoice details from the approved payment-and-fulfillment layout. */
final class InvoiceFulfillmentPanel extends JPanel {
    record Snapshot(QuotationInvoiceViewService.InvoiceFinancials financials,
            List<QuotationInvoiceViewService.PaymentSummary> payments,
            List<QuotationInvoiceViewService.DeliverableLine> deliveries) { }
    private final CardLayout cards = new CardLayout();
    private final JTable invoices;
    private final JTextField customer = new JTextField("Loading customer…"), number = new JTextField();
    private final JLabel total = DocumentStudio.label("");
    private final DefaultTableModel payments = Quotations.readOnlyModel("Date", "Method", "Amount");
    private final DefaultTableModel deliveries = Quotations.readOnlyModel("Item", "Ordered", "Delivered", "Remaining");
    private long generation;

    InvoiceFulfillmentPanel(Window owner, JTable invoices, JComponent list, List<JButton> commands) {
        super(); this.invoices = invoices; setLayout(cards);
        add(list, "list");
        JPanel detail = DocumentStudio.surface(new BorderLayout(0, 18));
        detail.setBorder(BorderFactory.createEmptyBorder(22, 22, 22, 22));
        JButton back = new JButton("Back to invoices"); DocumentStudio.button(back, false);
        back.addActionListener(e -> { invoices.clearSelection(); cards.show(this, "list"); });
        JPanel identity = DocumentStudio.row(DocumentStudio.field("Customer", customer), DocumentStudio.field("Invoice no.", number));
        customer.setEditable(false); number.setEditable(false);
        JTable paymentRows = new JTable(payments), deliveryRows = new JTable(deliveries);
        DocumentStudio.table(paymentRows); DocumentStudio.table(deliveryRows);
        JScrollPane paymentScroll = DocumentStudio.scroll(paymentRows), deliveryScroll = DocumentStudio.scroll(deliveryRows);
        paymentScroll.setColumnHeaderView(paymentRows.getTableHeader()); deliveryScroll.setColumnHeaderView(deliveryRows.getTableHeader());
        paymentScroll.setPreferredSize(new Dimension(480, 112)); deliveryScroll.setPreferredSize(new Dimension(480, 130));
        paymentScroll.setToolTipText("Up to 500 recent payments for this invoice.");
        JPanel pay = DocumentStudio.section("Payment history", DocumentStudio.stack(paymentScroll, action(commands, "Process Payment", "Record payment")));
        JPanel fulfill = DocumentStudio.section("Delivery / fulfillment", DocumentStudio.stack(deliveryScroll, action(commands, "Post Delivery", "Record delivery")));
        JPanel workflow = DocumentStudio.surface(new ui.components.WrappingToolbarLayout());
        workflow.add(DocumentStudio.workflow(2)); workflow.add(back);
        JPanel body = DocumentStudio.stack(workflow, identity, pay, fulfill);
        detail.add(DocumentStudio.scroll(DocumentStudio.topAligned(body)), BorderLayout.CENTER);
        total.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 15)); total.setHorizontalAlignment(SwingConstants.RIGHT);
        detail.add(total, BorderLayout.SOUTH); add(detail, "detail");
        invoices.getSelectionModel().addListSelectionListener(event -> {
            if (event.getValueIsAdjusting()) return;
            Long id = SalesDocumentWorkspace.selectedModelId(invoices);
            long request = ++generation;
            if (id == null) { cards.show(this, "list"); return; }
            cards.show(this, "detail"); payments.setRowCount(0); deliveries.setRowCount(0); total.setText("Loading invoice…");
            int row = invoices.convertRowIndexToModel(invoices.getSelectedRow());
            customer.setText(String.valueOf(invoices.getModel().getValueAt(row, 2)));
            number.setText(String.valueOf(invoices.getModel().getValueAt(row, 1)));
            if (owner == null) return;
            UiTaskRunner.submit(owner, "invoice.fulfillment-detail", () -> new Snapshot(
                    QuotationInvoiceViewService.loadInvoiceFinancials(id), QuotationInvoiceViewService.listInvoicePayments(id),
                    QuotationInvoiceViewService.listDeliverableLines(id)), snapshot -> {
                if (request == generation) applySnapshot(snapshot);
            }, error -> { if (request == generation) total.setText("Could not load invoice. Return to the list and refresh."); });
        });
    }
    private JComponent action(List<JButton> commands, String command, String title) {
        JPanel panel = DocumentStudio.surface(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        JButton control = new JButton(title); DocumentStudio.button(control, true);
        JButton original = commands.stream().filter(button -> command.equals(button.getText())).findFirst().orElse(null);
        control.setEnabled(original != null && original.isEnabled());
        if (original != null) { control.addActionListener(e -> original.doClick()); original.addPropertyChangeListener("enabled", e -> control.setEnabled(original.isEnabled())); }
        panel.add(control); return panel;
    }
    void applySnapshot(Snapshot snapshot) {
        var finance = snapshot.financials(); if (finance == null) return;
        customer.setText(finance.customerName()); number.setText(finance.invoiceNumber());
        payments.setRowCount(0);
        for (var payment : snapshot.payments()) if (finance.invoiceNumber().equals(payment.invoiceNumber()))
            payments.addRow(new Object[]{payment.createdAt() != null && payment.createdAt().length() >= 10
                    ? payment.createdAt().substring(0, 10) : payment.createdAt(), payment.paymentMethod(), utils.CurrencyFormatter.format(payment.amount())});
        deliveries.setRowCount(0);
        for (var line : snapshot.deliveries()) deliveries.addRow(new Object[]{line.itemName(), line.quantityInvoiceed(), line.quantityDelivered(), line.remaining()});
        total.setText("Total " + utils.CurrencyFormatter.format(finance.totalAmount()) + "    Paid "
                + utils.CurrencyFormatter.format(finance.amountPaid()) + "    Balance " + utils.CurrencyFormatter.format(finance.balanceDue()));
        cards.show(this, "detail");
    }
}
