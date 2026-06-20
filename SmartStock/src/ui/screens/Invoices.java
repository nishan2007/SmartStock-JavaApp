package ui.screens;

import Receipt.AccountPaymentReceiptBuilder;
import Receipt.AccountPaymentReceiptData;
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
    public enum InitialTab {
        QUOTATIONS,
        INVOICES
    }

    private final DefaultTableModel quotationModel = Quotations.readOnlyModel("ID", "Quotation #", "Customer", "Status", "Valid Until", "Total");
    private final DefaultTableModel invoiceModel = Quotations.readOnlyModel("ID", "Invoice #", "Customer", "Status", "Payment", "Balance", "Quotation #");
    private final DefaultTableModel deliveryModel = Quotations.readOnlyModel("ID", "Delivery #", "Invoice #", "Customer", "Method", "Balance", "Created");
    private final DefaultTableModel auditModel = Quotations.readOnlyModel("Time", "Document", "Action", "Field", "Old", "New", "User", "Reason");
    private final JTable quotationTable = new JTable(quotationModel);
    private final JTable invoiceTable = new JTable(invoiceModel);
    private final JTable deliveryTable = new JTable(deliveryModel);
    private final JTable auditTable = new JTable(auditModel);

    public Invoices() {
        this(InitialTab.QUOTATIONS);
    }

    public Invoices(InitialTab initialTab) {
        setTitle("Quotations & Invoices");
        setSize(1280, 760);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setJMenuBar(AppMenuBar.create(this, "Invoices"));

        JPanel mainPanel = new JPanel(new BorderLayout(12, 12));
        mainPanel.setBorder(new EmptyBorder(14, 14, 14, 14));
        add(mainPanel, BorderLayout.CENTER);

        JLabel title = new JLabel("Quotations & Invoices");
        title.setFont(new Font("SansSerif", Font.BOLD, 26));
        mainPanel.add(title, BorderLayout.NORTH);

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Quotations", tablePanel(quotationTable, quotationButtons()));
        tabs.addTab("Invoices", tablePanel(invoiceTable, invoiceButtons()));
        tabs.addTab("Deliveries", tablePanel(deliveryTable, deliveryButtons()));
        tabs.addTab("Audit", tablePanel(auditTable, auditButtons()));
        tabs.setSelectedIndex(initialTab == InitialTab.QUOTATIONS ? 0 : 1);
        mainPanel.add(tabs, BorderLayout.CENTER);

        refreshAll();
        WindowHelper.configurePosWindow(this);
    }

    private JPanel quotationButtons() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        JButton newQuotation = new JButton("New Quotation");
        JButton editDraft = new JButton("Edit Draft");
        JButton issue = new JButton("Issue");
        JButton accept = new JButton("Accept Quotation");
        JButton preview = new JButton("Preview Quotation");
        JButton refresh = new JButton("Refresh");
        panel.add(newQuotation);
        panel.add(editDraft);
        panel.add(issue);
        panel.add(accept);
        panel.add(preview);
        panel.add(refresh);
        Quotations.stylePrimaryButton(newQuotation);
        Quotations.styleSecondaryButton(editDraft);
        Quotations.styleSecondaryButton(issue);
        Quotations.styleSecondaryButton(accept);
        Quotations.styleSecondaryButton(preview);
        Quotations.styleSecondaryButton(refresh);
        newQuotation.addActionListener(e -> openQuotationDialog());
        editDraft.addActionListener(e -> editSelectedDraftQuotation());
        issue.addActionListener(e -> issueSelectedQuotation());
        accept.addActionListener(e -> acceptSelectedQuotation());
        preview.addActionListener(e -> previewQuotation());
        refresh.addActionListener(e -> loadQuotations());
        return panel;
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
        loadQuotations();
        loadInvoices();
        loadDeliveries();
        loadAudit();
    }

    private void loadQuotations() {
        quotationModel.setRowCount(0);
        try {
            for (QuotationInvoiceViewService.QuotationSummary row : QuotationInvoiceViewService.listQuotations()) {
                quotationModel.addRow(new Object[]{
                        row.quotationId(),
                        row.quotationNumber(),
                        row.customerName(),
                        row.status(),
                        row.validUntil(),
                        row.totalAmount()
                });
            }
        } catch (SQLException ex) {
            showError("Failed to load quotations", ex);
        }
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
            QuotationInvoiceService.PaymentReceiptRef receiptRef = QuotationInvoiceService.recordPayment(invoiceId, paymentAmount, (String) method.getSelectedItem(), reference.getText());
            openPaymentReceipt(receiptRef);
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
            try {
                openDeliveryPrintDialog(dialog.deliveryEventId());
            } catch (SQLException ex) {
                showError("Failed to open delivery bill printout", ex);
            }
        }
    }

    private void openPaymentReceipt(QuotationInvoiceService.PaymentReceiptRef receiptRef) {
        if (receiptRef == null) {
            return;
        }
        try {
            AccountPaymentReceiptData receipt = AccountPaymentReceiptBuilder.loadPaymentReceipt(receiptRef.customerId(), receiptRef.transactionId());
            WindowHelper.showPosWindow(new AccountPaymentReceiptPreview(receipt), this);
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(
                    this,
                    "Payment was recorded, but the receipt preview could not be loaded: " + ex.getMessage(),
                    "Payment Receipt",
                    JOptionPane.WARNING_MESSAGE
            );
        }
    }

    private void previewInvoice() {
        Long invoiceId = selectedId(invoiceTable);
        if (invoiceId == null) return;
        try {
            WindowHelper.showPosWindow(new QuotationInvoiceDocumentPreview("Invoice Preview", QuotationInvoiceDocumentBuilder.buildInvoice(invoiceId), false, "INVOICE", invoiceId), this);
        } catch (SQLException ex) {
            showError("Failed to preview invoice", ex);
        }
    }

    private void previewDelivery() {
        Long deliveryId = selectedId(deliveryTable);
        if (deliveryId == null) return;
        try {
            WindowHelper.showPosWindow(new QuotationInvoiceDocumentPreview("Delivery Bill Preview", QuotationInvoiceDocumentBuilder.buildDelivery(deliveryId), false, "DELIVERY_BILL", deliveryId), this);
        } catch (SQLException ex) {
            showError("Failed to preview delivery bill", ex);
        }
    }

    private void openQuotationDialog() {
        Quotations.QuotationEditor editor = new Quotations.QuotationEditor(this);
        editor.setVisible(true);
        if (editor.created) {
            refreshAll();
        }
    }

    private void editSelectedDraftQuotation() {
        Long quotationId = selectedId(quotationTable);
        if (quotationId == null) return;
        try {
            QuotationInvoiceViewService.QuotationEditData quotation = QuotationInvoiceViewService.loadQuotationForEdit(quotationId);
            if (!"DRAFT".equals(quotation.status())) {
                JOptionPane.showMessageDialog(this, "Only draft quotations can be edited.", "Quotations", JOptionPane.WARNING_MESSAGE);
                return;
            }
            Quotations.QuotationEditor editor = new Quotations.QuotationEditor(this, quotation);
            editor.setVisible(true);
            if (editor.created) {
                refreshAll();
            }
        } catch (SQLException ex) {
            showError("Failed to load draft quotation", ex);
        }
    }

    private void issueSelectedQuotation() {
        Long quotationId = selectedId(quotationTable);
        if (quotationId == null) return;
        try {
            QuotationInvoiceService.issueQuotation(quotationId);
            refreshAll();
            openQuotationPrintDialog(quotationId);
        } catch (SQLException ex) {
            showError("Failed to issue quotation", ex);
        }
    }

    private void acceptSelectedQuotation() {
        Long quotationId = selectedId(quotationTable);
        if (quotationId == null) return;
        try {
            QuotationInvoiceService.InvoiceResult result = QuotationInvoiceService.acceptQuotation(quotationId);
            promptForAcceptedQuotationPayment(result.invoiceId());
            Long deliveryEventId = promptForAcceptedQuotationDelivery(result.invoiceId());
            refreshAll();
            openSalesInvoicePrintDialog(result.invoiceId());
            if (deliveryEventId != null) {
                openDeliveryPrintDialog(deliveryEventId);
            }
        } catch (SQLException ex) {
            showError("Failed to accept quotation", ex);
        }
    }

    private void promptForAcceptedQuotationPayment(long invoiceId) {
        try {
            QuotationInvoiceViewService.InvoiceFinancials financials = QuotationInvoiceViewService.loadInvoiceFinancials(invoiceId);
            Quotations.PaymentPrompt prompt = new Quotations.PaymentPrompt(this, financials);
            prompt.setVisible(true);
            Quotations.PaymentInput payment = prompt.paymentInput();
            boolean paymentRecorded = false;
            if (payment.amount().compareTo(BigDecimal.ZERO) > 0) {
                try {
                    QuotationInvoiceService.PaymentReceiptRef receiptRef = QuotationInvoiceService.recordPayment(invoiceId, payment.amount(), payment.method(), payment.reference());
                    paymentRecorded = true;
                    openPaymentReceipt(receiptRef);
                } catch (SQLException ex) {
                    showError("Payment was not recorded; remaining balance will be placed on account if possible", ex);
                }
            }
            QuotationInvoiceViewService.InvoiceFinancials updated = QuotationInvoiceViewService.loadInvoiceFinancials(invoiceId);
            if (!paymentRecorded && updated.balanceDue().compareTo(BigDecimal.ZERO) > 0) {
                QuotationInvoiceService.chargeInvoiceToAccount(invoiceId, "Remaining balance from accepted quotation.");
            }
        } catch (SQLException ex) {
            showError("Failed to place remaining balance on customer account", ex);
        }
    }

    private Long promptForAcceptedQuotationDelivery(long invoiceId) throws SQLException {
        if (QuotationInvoiceViewService.listDeliverableLines(invoiceId).isEmpty()) {
            return null;
        }
        int choice = JOptionPane.showConfirmDialog(
                this,
                "Enter any quantities being delivered now?",
                "Invoice Delivery",
                JOptionPane.YES_NO_OPTION
        );
        if (choice != JOptionPane.YES_OPTION) {
            return null;
        }
        DeliveryDialog dialog = new DeliveryDialog(this, invoiceId);
        dialog.setVisible(true);
        return dialog.deliveryEventId();
    }

    private void previewQuotation() {
        Long quotationId = selectedId(quotationTable);
        if (quotationId == null) return;
        try {
            WindowHelper.showPosWindow(new QuotationInvoiceDocumentPreview("Quotation Preview", QuotationInvoiceDocumentBuilder.buildQuotation(quotationId), false, "QUOTATION", quotationId), this);
        } catch (SQLException ex) {
            showError("Failed to preview quotation", ex);
        }
    }

    private void openQuotationPrintDialog(long quotationId) throws SQLException {
        WindowHelper.showPosWindow(new QuotationInvoiceDocumentPreview(
                "Quotation Print",
                QuotationInvoiceDocumentBuilder.buildQuotation(quotationId),
                true,
                "QUOTATION",
                quotationId
        ), this);
    }

    private void openSalesInvoicePrintDialog(long invoiceId) throws SQLException {
        WindowHelper.showPosWindow(new QuotationInvoiceDocumentPreview(
                "Invoice Print",
                QuotationInvoiceDocumentBuilder.buildInvoice(invoiceId),
                true,
                "INVOICE",
                invoiceId
        ), this);
    }

    private void openDeliveryPrintDialog(long deliveryEventId) throws SQLException {
        WindowHelper.showPosWindow(new QuotationInvoiceDocumentPreview(
                "Delivery Bill Print",
                QuotationInvoiceDocumentBuilder.buildDelivery(deliveryEventId),
                true,
                "DELIVERY_BILL",
                deliveryEventId
        ), this);
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
