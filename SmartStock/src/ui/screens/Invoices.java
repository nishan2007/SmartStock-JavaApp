package ui.screens;

import Receipt.QuotationInvoiceDocumentBuilder;
import services.QuotationInvoiceService;
import services.QuotationInvoiceViewService;
import ui.components.AppMenuBar;
import ui.helpers.WindowHelper;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class Invoices extends JFrame {
    private final DefaultTableModel invoiceModel = Quotations.readOnlyModel("ID", "Invoice #", "Customer", "Status", "Payment", "Balance", "Quotation #");
    private final DefaultTableModel deliveryModel = Quotations.readOnlyModel("ID", "Delivery #", "Invoice #", "Customer", "Method", "Balance", "Created");
    private final DefaultTableModel auditModel = Quotations.readOnlyModel("Time", "Document", "Action", "Field", "Old", "New", "User", "Reason");
    private final JTable invoiceTable = new JTable(invoiceModel);
    private final JTable deliveryTable = new JTable(deliveryModel);
    private final JTable auditTable = new JTable(auditModel);

    public Invoices() {
        setTitle("Invoices");
        setSize(1280, 760);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setJMenuBar(AppMenuBar.create(this, "Invoices"));

        JPanel mainPanel = new JPanel(new BorderLayout(12, 12));
        mainPanel.setBorder(new EmptyBorder(14, 14, 14, 14));
        add(mainPanel, BorderLayout.CENTER);

        JLabel title = new JLabel("Invoices");
        title.setFont(new Font("SansSerif", Font.BOLD, 26));
        mainPanel.add(title, BorderLayout.NORTH);

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Invoices", tablePanel(invoiceTable, invoiceButtons()));
        tabs.addTab("Deliveries", tablePanel(deliveryTable, deliveryButtons()));
        tabs.addTab("Audit", tablePanel(auditTable, auditButtons()));
        mainPanel.add(tabs, BorderLayout.CENTER);

        refreshAll();
        WindowHelper.configurePosWindow(this);
    }

    private JPanel invoiceButtons() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        JButton payment = new JButton("Process Payment");
        JButton delivery = new JButton("Post Delivery");
        JButton preview = new JButton("Preview Invoice");
        JButton refresh = new JButton("Refresh");
        panel.add(payment);
        panel.add(delivery);
        panel.add(preview);
        panel.add(refresh);
        Quotations.styleSecondaryButton(payment);
        Quotations.stylePrimaryButton(delivery);
        Quotations.styleSecondaryButton(preview);
        Quotations.styleSecondaryButton(refresh);
        payment.addActionListener(e -> addPayment());
        delivery.addActionListener(e -> postDelivery());
        preview.addActionListener(e -> previewInvoice());
        refresh.addActionListener(e -> refreshAll());
        return panel;
    }

    private JPanel deliveryButtons() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        JButton preview = new JButton("Preview Delivery Bill");
        JButton refresh = new JButton("Refresh");
        panel.add(preview);
        panel.add(refresh);
        Quotations.stylePrimaryButton(preview);
        Quotations.styleSecondaryButton(refresh);
        preview.addActionListener(e -> previewDelivery());
        refresh.addActionListener(e -> refreshAll());
        return panel;
    }

    private JPanel auditButtons() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        JButton refresh = new JButton("Refresh");
        panel.add(refresh);
        Quotations.styleSecondaryButton(refresh);
        refresh.addActionListener(e -> refreshAll());
        return panel;
    }

    private JPanel tablePanel(JTable table, JPanel buttons) {
        table.setAutoCreateRowSorter(true);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        Quotations.styleTable(table);
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.add(buttons, BorderLayout.NORTH);
        panel.add(new JScrollPane(table), BorderLayout.CENTER);
        return panel;
    }

    private void refreshAll() {
        loadInvoices();
        loadDeliveries();
        loadAudit();
    }

    private void loadInvoices() {
        invoiceModel.setRowCount(0);
        try {
            for (QuotationInvoiceViewService.InvoiceSummary row : QuotationInvoiceViewService.listInvoices()) {
                invoiceModel.addRow(new Object[]{
                        row.invoiceId(),
                        row.invoiceNumber(),
                        row.customerName(),
                        row.status(),
                        row.paymentStatus(),
                        row.balanceDue(),
                        row.quotationNumber()
                });
            }
        } catch (SQLException ex) {
            showError("Failed to load invoices", ex);
        }
    }

    private void loadDeliveries() {
        deliveryModel.setRowCount(0);
        try {
            for (QuotationInvoiceViewService.DeliverySummary row : QuotationInvoiceViewService.listDeliveries()) {
                deliveryModel.addRow(new Object[]{
                        row.deliveryEventId(),
                        row.deliveryNumber(),
                        row.invoiceNumber(),
                        row.customerName(),
                        row.deliveryMethod(),
                        row.balanceDue(),
                        row.createdAt()
                });
            }
        } catch (SQLException ex) {
            showError("Failed to load deliveries", ex);
        }
    }

    private void loadAudit() {
        auditModel.setRowCount(0);
        try {
            for (QuotationInvoiceViewService.AuditEntry row : QuotationInvoiceViewService.listAudit()) {
                auditModel.addRow(new Object[]{
                        row.createdAt(),
                        row.document(),
                        row.actionType(),
                        row.fieldName(),
                        row.oldValue(),
                        row.newValue(),
                        row.userName(),
                        row.reason()
                });
            }
        } catch (SQLException ex) {
            showError("Failed to load audit", ex);
        }
    }

    private void addPayment() {
        Long invoiceId = selectedId(invoiceTable);
        if (invoiceId == null) return;
        try {
            QuotationInvoiceViewService.InvoiceFinancials financials = QuotationInvoiceViewService.loadInvoiceFinancials(invoiceId);
            if (financials.balanceDue().compareTo(BigDecimal.ZERO) <= 0) {
                JOptionPane.showMessageDialog(this, "Sales invoice " + financials.invoiceNumber() + " has no remaining balance.");
                return;
            }
            JTextField amount = new JTextField(financials.balanceDue().toPlainString());
            JComboBox<String> method = new JComboBox<>(new String[]{"CASH", "CARD", "CHEQUE", "MMG"});
            JTextField reference = new JTextField();
            JPanel panel = new JPanel(new BorderLayout(8, 8));
            panel.add(new JLabel("<html>Invoice " + financials.invoiceNumber()
                    + "<br>Total: " + financials.totalAmount()
                    + "<br>Paid: " + financials.amountPaid()
                    + "<br>Balance: " + financials.balanceDue()
                    + "<br><br>Payment will be recorded through the customer account ledger.</html>"), BorderLayout.NORTH);
            panel.add(Quotations.formPanel(new String[]{"Amount", "Method", "Reference"}, new JComponent[]{amount, method, reference}), BorderLayout.CENTER);
            if (JOptionPane.showConfirmDialog(this, panel, "Process Invoice Payment", JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) {
                return;
            }
            BigDecimal paymentAmount = Quotations.parseMoney(amount.getText());
            if (paymentAmount.compareTo(BigDecimal.ZERO) <= 0) {
                JOptionPane.showMessageDialog(this, "Payment amount must be greater than zero.", "Invoices", JOptionPane.ERROR_MESSAGE);
                return;
            }
            QuotationInvoiceService.recordPayment(invoiceId, paymentAmount, (String) method.getSelectedItem(), reference.getText());
            refreshAll();
        } catch (Exception ex) {
            showError("Failed to process payment", ex);
        }
    }

    private void postDelivery() {
        Long invoiceId = selectedId(invoiceTable);
        if (invoiceId == null) return;
        DeliveryDialog dialog = new DeliveryDialog(this, invoiceId);
        dialog.setVisible(true);
        if (dialog.created) {
            refreshAll();
        }
    }

    private void previewInvoice() {
        Long invoiceId = selectedId(invoiceTable);
        if (invoiceId == null) return;
        try {
            WindowHelper.showPosWindow(new QuotationInvoiceDocumentPreview("Invoice Preview", QuotationInvoiceDocumentBuilder.buildInvoice(invoiceId)), this);
        } catch (SQLException ex) {
            showError("Failed to preview invoice", ex);
        }
    }

    private void previewDelivery() {
        Long deliveryId = selectedId(deliveryTable);
        if (deliveryId == null) return;
        try {
            WindowHelper.showPosWindow(new QuotationInvoiceDocumentPreview("Delivery Bill Preview", QuotationInvoiceDocumentBuilder.buildDelivery(deliveryId)), this);
        } catch (SQLException ex) {
            showError("Failed to preview delivery bill", ex);
        }
    }

    private Long selectedId(JTable table) {
        int row = table.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "Select a row first.");
            return null;
        }
        Object value = table.getValueAt(row, 0);
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(String.valueOf(value));
    }

    private void showError(String title, Exception ex) {
        JOptionPane.showMessageDialog(this, title + ".\n\n" + ex.getMessage(), "Invoices", JOptionPane.ERROR_MESSAGE);
    }

    static class DeliveryDialog extends JDialog {
        private final long invoiceId;
        private final JComboBox<String> methodBox = new JComboBox<>(new String[]{"PICKUP", "LOCAL_DELIVERY", "SHIP", "INSTALLATION"});
        private final JTextField receiverField = new JTextField();
        private final JTextField notesField = new JTextField();
        private final DefaultTableModel lineModel = new DefaultTableModel(new String[]{"Line ID", "Item", "Invoiceed", "Delivered", "Remaining", "Available", "Deliver Now"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return column == 6;
            }
        };
        private boolean created;
        private Long deliveryEventId;

        DeliveryDialog(JFrame owner, long invoiceId) {
            super(owner, "Post Invoice Delivery", true);
            this.invoiceId = invoiceId;
            setSize(820, 540);
            setLocationRelativeTo(owner);
            setLayout(new BorderLayout(8, 8));
            JPanel main = new JPanel(new BorderLayout(8, 8));
            main.setBorder(new EmptyBorder(12, 12, 12, 12));
            add(main, BorderLayout.CENTER);
            main.add(Quotations.formPanel(new String[]{"Method", "Receiver", "Notes"}, new JComponent[]{methodBox, receiverField, notesField}), BorderLayout.NORTH);
            JTable table = new JTable(lineModel);
            Quotations.styleTable(table);
            main.add(new JScrollPane(table), BorderLayout.CENTER);
            JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
            JButton post = new JButton("Post Delivery");
            JButton cancel = new JButton("Cancel");
            buttons.add(post);
            buttons.add(cancel);
            Quotations.stylePrimaryButton(post);
            Quotations.styleSecondaryButton(cancel);
            main.add(buttons, BorderLayout.SOUTH);
            post.addActionListener(e -> post());
            cancel.addActionListener(e -> dispose());
            loadLines();
        }

        private void loadLines() {
            try {
                for (QuotationInvoiceViewService.DeliverableLine line : QuotationInvoiceViewService.listDeliverableLines(invoiceId)) {
                    lineModel.addRow(new Object[]{
                            line.invoiceLineId(),
                            line.itemName(),
                            line.quantityInvoiceed(),
                            line.quantityDelivered(),
                            line.remaining(),
                            line.productId() == null ? "Manual" : line.availableStock(),
                            0
                    });
                }
            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(this, "Failed to load invoice lines.\n\n" + ex.getMessage(), "Delivery", JOptionPane.ERROR_MESSAGE);
            }
        }

        private void post() {
            List<QuotationInvoiceService.DeliveryLineInput> lines = new ArrayList<>();
            try {
                for (int i = 0; i < lineModel.getRowCount(); i++) {
                    int qty = parseQuantity(lineModel.getValueAt(i, 6));
                    int remaining = parseQuantity(lineModel.getValueAt(i, 4));
                    int available = availableQuantity(lineModel.getValueAt(i, 5));
                    String item = String.valueOf(lineModel.getValueAt(i, 1));
                    if (qty < 0) {
                        throw new IllegalArgumentException("Delivery quantity for " + item + " cannot be negative.");
                    }
                    if (qty > remaining) {
                        throw new IllegalArgumentException("Delivery quantity for " + item + " cannot be more than the remaining " + remaining + ".");
                    }
                    if (available >= 0 && qty > available) {
                        throw new IllegalArgumentException("Only " + available + " in stock for " + item + "; cannot deliver " + qty + ".");
                    }
                    if (qty > 0) {
                        lines.add(new QuotationInvoiceService.DeliveryLineInput(
                                Long.parseLong(String.valueOf(lineModel.getValueAt(i, 0))),
                                qty
                        ));
                    }
                }
                if (lines.isEmpty()) {
                    throw new IllegalArgumentException("Enter at least one quantity in Deliver Now.");
                }
            } catch (IllegalArgumentException ex) {
                JOptionPane.showMessageDialog(this, ex.getMessage(), "Delivery", JOptionPane.ERROR_MESSAGE);
                return;
            }
            try {
                QuotationInvoiceService.DeliveryResult result = QuotationInvoiceService.postDelivery(
                        invoiceId,
                        String.valueOf(methodBox.getSelectedItem()),
                        receiverField.getText(),
                        notesField.getText(),
                        lines
                );
                created = true;
                deliveryEventId = result.deliveryEventId();
                JOptionPane.showMessageDialog(this, "Posted delivery " + result.deliveryNumber() + ".");
                dispose();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Failed to post delivery.\n\n" + ex.getMessage(), "Delivery", JOptionPane.ERROR_MESSAGE);
            }
        }

        private int parseQuantity(Object value) {
            String raw = value == null ? "" : String.valueOf(value).trim();
            if (raw.isBlank()) {
                return 0;
            }
            try {
                return Integer.parseInt(raw);
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("Delivery quantities must be whole numbers.");
            }
        }

        private int availableQuantity(Object value) {
            if (value == null || "Manual".equals(String.valueOf(value))) {
                return -1;
            }
            return parseQuantity(value);
        }

        Long deliveryEventId() {
            return deliveryEventId;
        }
    }
}
