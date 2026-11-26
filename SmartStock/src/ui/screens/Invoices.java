package ui.screens;

import services.QuotationInvoiceService;
import services.LanApiClient;
import services.QuotationInvoiceViewService;
import ui.components.AppMenuBar;
import ui.components.LoadingStatePanel;
import ui.helpers.CachedUiLoader;
import ui.helpers.SessionDataCache;
import ui.helpers.UiTaskRunner;
import ui.helpers.ResponsiveTask;
import ui.helpers.ThemeManager;
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
    private final DefaultTableModel deliveryModel = Quotations.readOnlyModel("ID", "Delivery #", "Invoice #", "Customer", "Method", "Balance", "Created", "Status");
    private final DefaultTableModel paymentModel = Quotations.readOnlyModel("Receipt ID", "Customer ID", "Invoice #", "Customer", "Amount", "Method", "Reference", "Taken By", "Date", "Status");
    private final DefaultTableModel auditModel = Quotations.readOnlyModel("Time", "Document", "Action", "Field", "Old", "New", "User", "Reason");
    private final JTable quotationTable = new JTable(quotationModel);
    private final JTable invoiceTable = new JTable(invoiceModel);
    private final JTable deliveryTable = new JTable(deliveryModel);
    private final JTable paymentTable = new JTable(paymentModel);
    private final JTable auditTable = new JTable(auditModel);
    private final LoadingStatePanel loadingState = new LoadingStatePanel();

    public Invoices() {
        this(InitialTab.QUOTATIONS);
    }

    public Invoices(InitialTab initialTab) {
        setTitle("Quotations & Invoices");
        getRootPane().putClientProperty("SmartStock.responsiveMinimumSize", new Dimension(800, 560));
        setSize(1280, 760);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setJMenuBar(AppMenuBar.create(this, "Invoices"));

        JPanel mainPanel = new JPanel(new BorderLayout(12, 12));
        mainPanel.setBorder(null);

        JLabel title = new JLabel("Quotations & Invoices");
        title.setFont(new Font("SansSerif", Font.BOLD, 26));

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Quotations", tablePanel(quotationTable, quotationButtons()));
        tabs.addTab("Invoices", tablePanel(invoiceTable, invoiceButtons()));
        tabs.addTab("Deliveries", tablePanel(deliveryTable, deliveryButtons()));
        tabs.addTab("Payments", tablePanel(paymentTable, paymentButtons()));
        tabs.addTab("Audit", tablePanel(auditTable, auditButtons()));
        paymentTable.removeColumn(paymentTable.getColumnModel().getColumn(1));
        paymentTable.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseClicked(java.awt.event.MouseEvent event) {
                if (event.getClickCount() == 2) reprintPaymentReceipt();
            }
        });
        tabs.setSelectedIndex(initialTab == InitialTab.QUOTATIONS ? 0 : 1);
        mainPanel.add(tabs, BorderLayout.CENTER);
        mainPanel.add(loadingState, BorderLayout.SOUTH);
        JPanel studio = DocumentStudio.shell(mainPanel, initialTab == InitialTab.INVOICES ? 3 : 0,
                () -> tabs.setSelectedIndex(0), this::openQuotationDialog, null, () -> tabs.setSelectedIndex(1));
        add(studio, BorderLayout.CENTER);
        tabs.addChangeListener(e -> DocumentStudio.activate(studio, tabs.getSelectedIndex() == 1 ? 3 : 0));
        tabs.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));

        refreshAll();
        WindowHelper.configurePosWindow(this);
    }

    private JPanel quotationButtons() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        JButton newQuotation = new JButton("New Quotation");
        JButton editDraft = new JButton("Edit Quotation");
        JButton issue = new JButton("Issue");
        JButton cancelQuotation = new JButton("Cancel Quotation");
        cancelQuotation.setEnabled(managers.PermissionManager.hasPermission("CANCEL_QUOTATIONS"));
        JButton accept = new JButton("Accept Quotation");
        JButton preview = new JButton("Preview Quotation");
        JButton refresh = new JButton("Refresh");
        panel.add(newQuotation);
        panel.add(editDraft);
        panel.add(issue);
        panel.add(accept);
        panel.add(cancelQuotation);
        panel.add(preview);
        panel.add(refresh);
        Quotations.stylePrimaryButton(newQuotation);
        Quotations.styleSecondaryButton(editDraft);
        Quotations.styleSecondaryButton(issue);
        Quotations.styleSecondaryButton(accept);
        Quotations.styleSecondaryButton(cancelQuotation);
        Quotations.styleSecondaryButton(preview);
        Quotations.styleSecondaryButton(refresh);
        newQuotation.addActionListener(e -> openQuotationDialog());
        editDraft.addActionListener(e -> editSelectedDraftQuotation());
        issue.addActionListener(e -> issueSelectedQuotation());
        accept.addActionListener(e -> acceptSelectedQuotation());
        cancelQuotation.addActionListener(e -> cancelSelectedQuotation());
        preview.addActionListener(e -> previewQuotation());
        refresh.addActionListener(e -> refreshAll());
        return panel;
    }

    private void addCancellationButton(JPanel panel, String label, String permission, JTable table, String type) {
        JButton button = new JButton(label);
        Quotations.styleSecondaryButton(button);
        button.setEnabled(managers.PermissionManager.hasPermission(permission));
        panel.add(button);
        button.addActionListener(event -> {
            if (!managers.PermissionManager.hasPermission(permission)) return;
            Long id = selectedId(table);
            if (id == null) return;
            String explanation = switch (type) {
                case "PAYMENT" -> "Return/reverse the money using the original payment method. The invoice balance will be restored.";
                case "DELIVERY" -> "Confirm the goods have been returned. Delivered quantities and inventory will be restored.";
                default -> "Payments and deliveries must be cancelled first. Any undelivered linked production order will also be cancelled.";
            };
            JTextArea reason = new JTextArea(3, 32); reason.setLineWrap(true); reason.setWrapStyleWord(true);
            JTextField reference = new JTextField(24);
            JPanel fields = new JPanel(new BorderLayout(8, 8));
            fields.add(new JLabel("<html>" + explanation + "<br><br>Cancellation reason (required):</html>"), BorderLayout.NORTH);
            fields.add(new JScrollPane(reason), BorderLayout.CENTER);
            if ("PAYMENT".equals(type)) {
                JPanel ref = new JPanel(new BorderLayout(8, 8));
                ref.add(new JLabel("Reversal reference (required for non-cash):"), BorderLayout.NORTH);
                ref.add(reference, BorderLayout.CENTER); fields.add(ref, BorderLayout.SOUTH);
            }
            if (JOptionPane.showConfirmDialog(this, fields, label, JOptionPane.OK_CANCEL_OPTION,
                    JOptionPane.WARNING_MESSAGE) != JOptionPane.OK_OPTION) return;
            if (reason.getText().isBlank()) { JOptionPane.showMessageDialog(this, "Enter a cancellation reason."); return; }
            String cancellationReason = reason.getText().trim(), reversalReference = reference.getText().trim();
            UiTaskRunner.submit(this, "invoices.cancel." + type, () -> {
                switch (type) {
                    case "PAYMENT" -> QuotationInvoiceService.cancelInvoicePayment(id, cancellationReason, reversalReference);
                    case "DELIVERY" -> QuotationInvoiceService.cancelDelivery(id, cancellationReason);
                    default -> QuotationInvoiceService.cancelInvoice(id, cancellationReason);
                }
                return Boolean.TRUE;
            }, ignored -> refreshAll(), error -> showError("Cancellation failed", error instanceof Exception ex ? ex : new Exception(error)));
        });
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
        addCancellationButton(panel, "Cancel Invoice", "CANCEL_INVOICES", invoiceTable, "INVOICE");
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
        addCancellationButton(panel, "Cancel Delivery", "CANCEL_INVOICE_DELIVERIES", deliveryTable, "DELIVERY");
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

    private JPanel paymentButtons() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        JButton reprint = new JButton("Reprint Receipt");
        JButton refresh = new JButton("Refresh");
        panel.add(reprint);
        panel.add(refresh);
        Quotations.stylePrimaryButton(reprint);
        Quotations.styleSecondaryButton(refresh);
        addCancellationButton(panel, "Cancel Payment", "CANCEL_INVOICE_PAYMENTS", paymentTable, "PAYMENT");
        reprint.addActionListener(e -> reprintPaymentReceipt());
        refresh.addActionListener(e -> refreshAll());
        return panel;
    }

    private JComponent tablePanel(JTable table, JPanel buttons) {
        table.setAutoCreateRowSorter(true);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        Quotations.styleTable(table);
        String type = table == quotationTable ? "QUOTATION" : table == invoiceTable ? "INVOICE"
                : table == deliveryTable ? "DELIVERY_BILL" : null;
        return SalesDocumentWorkspace.create(this, table, buttons, type);
    }

    private void refreshAll() {
        CachedUiLoader.load(this,"invoices.snapshot",InvoiceSnapshot.class,SessionDataCache.SCREEN_TTL,
                loadingState,()->new InvoiceSnapshot(
                        UiTaskRunner.supplyAsync(QuotationInvoiceViewService::listQuotations),
                        UiTaskRunner.supplyAsync(QuotationInvoiceViewService::listInvoices),
                        UiTaskRunner.supplyAsync(QuotationInvoiceViewService::listDeliveries),
                        UiTaskRunner.supplyAsync(QuotationInvoiceViewService::listPayments),
                        UiTaskRunner.supplyAsync(QuotationInvoiceViewService::listAudit)
                ).joined(),this::applySnapshot);
    }

    private void applySnapshot(InvoiceSnapshot snapshot) {
        quotationModel.setRowCount(0);
        for (var row : snapshot.quotations()) quotationModel.addRow(new Object[]{row.quotationId(),row.quotationNumber(),row.customerName(),row.status(),row.validUntil(),row.totalAmount()});
        invoiceModel.setRowCount(0);
        for (var row : snapshot.invoices()) invoiceModel.addRow(new Object[]{row.invoiceId(),row.invoiceNumber(),row.customerName(),row.status(),row.paymentStatus(),row.balanceDue(),row.quotationNumber()});
        deliveryModel.setRowCount(0);
        for (var row : snapshot.deliveries()) deliveryModel.addRow(new Object[]{row.deliveryEventId(),row.deliveryNumber(),row.invoiceNumber(),row.customerName(),row.deliveryMethod(),row.balanceDue(),row.createdAt(),row.status()});
        paymentModel.setRowCount(0);
        for (var row : snapshot.payments()) paymentModel.addRow(new Object[]{row.transactionId(),row.customerId(),row.invoiceNumber(),row.customerName(),row.amount(),row.paymentMethod(),row.paymentReference(),row.takenBy(),row.createdAt(),row.status()});
        auditModel.setRowCount(0);
        for (var row : snapshot.audit()) auditModel.addRow(new Object[]{row.createdAt(),row.document(),row.actionType(),row.fieldName(),row.oldValue(),row.newValue(),row.userName(),row.reason()});
        if (quotationTable.getRowCount() > 0) quotationTable.setRowSelectionInterval(0, 0);
    }

    private record InvoiceSnapshot(List<QuotationInvoiceViewService.QuotationSummary> quotations,
                                   List<QuotationInvoiceViewService.InvoiceSummary> invoices,
                                   List<QuotationInvoiceViewService.DeliverySummary> deliveries,
                                   List<QuotationInvoiceViewService.PaymentSummary> payments,
                                   List<QuotationInvoiceViewService.AuditEntry> audit) {
        private InvoiceSnapshot(java.util.concurrent.CompletableFuture<List<QuotationInvoiceViewService.QuotationSummary>> quotations,
                                java.util.concurrent.CompletableFuture<List<QuotationInvoiceViewService.InvoiceSummary>> invoices,
                                java.util.concurrent.CompletableFuture<List<QuotationInvoiceViewService.DeliverySummary>> deliveries,
                                java.util.concurrent.CompletableFuture<List<QuotationInvoiceViewService.PaymentSummary>> payments,
                                java.util.concurrent.CompletableFuture<List<QuotationInvoiceViewService.AuditEntry>> audit) {
            this(quotations.join(),invoices.join(),deliveries.join(),payments.join(),audit.join());
        }
        private InvoiceSnapshot joined() { return this; }
    }

    private void addPayment() {
        Long invoiceId = selectedId(invoiceTable);
        if (invoiceId == null) return;
        try {
            QuotationInvoiceViewService.InvoiceFinancials financials = ResponsiveTask.await(this,"Loading invoice balance...",()->QuotationInvoiceViewService.loadInvoiceFinancials(invoiceId));
            if(financials==null)return;
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
            panel.add(DocumentStudio.section("Record payment", DocumentStudio.stack(
                    DocumentStudio.row(DocumentStudio.field("Amount", amount), DocumentStudio.field("Payment method", method)),
                    DocumentStudio.field("Reference · optional", reference))), BorderLayout.CENTER);
            if (!confirmPayment(panel, invoiceId)) {
                return;
            }
            BigDecimal paymentAmount = Quotations.parseMoney(amount.getText());
            if (paymentAmount.compareTo(BigDecimal.ZERO) <= 0) {
                JOptionPane.showMessageDialog(this, "Payment amount must be greater than zero.", "Invoices", JOptionPane.ERROR_MESSAGE);
                return;
            }
            String selectedMethod=(String)method.getSelectedItem(),paymentReference=reference.getText();
            Quotations.CreditOverride creditOverride = Quotations.creditOverride(this, financials, paymentAmount);
            if (creditOverride == null) return;
            QuotationInvoiceService.PaymentReceiptRef receiptRef = ResponsiveTask.await(this,"Recording invoice payment...",()->QuotationInvoiceService.recordPayment(invoiceId,paymentAmount,selectedMethod,paymentReference,creditOverride.approvalToken(),creditOverride.reason()));
            if(receiptRef==null)return;
            openPaymentReceipt(receiptRef);
            refreshAll();
        } catch (Exception ex) {
            showError("Failed to process payment", ex);
        }
    }

    private boolean confirmPayment(JPanel fields, long invoiceId) {
        JDialog dialog = new JDialog(this, "Record invoice payment", true);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        final boolean[] confirmed = {false};
        JPanel buttons = new JPanel(new ui.components.WrappingToolbarLayout());
        JButton record = new JButton("Continue"), cancel = new JButton("Cancel");
        DocumentStudio.button(record, true); DocumentStudio.button(cancel, false);
        buttons.add(record); buttons.add(cancel);
        record.addActionListener(e -> { confirmed[0] = true; dialog.dispose(); });
        cancel.addActionListener(e -> dialog.dispose());
        SalesDocumentPanel preview = new SalesDocumentPanel(dialog);
        preview.showDocument("INVOICE", invoiceId, "Invoice before this payment");
        JPanel form = DocumentStudio.surface(new BorderLayout(8, 18));
        form.setBorder(new EmptyBorder(22, 22, 22, 22));
        form.add(fields, BorderLayout.NORTH); form.add(buttons, BorderLayout.SOUTH);
        dialog.add(DocumentStudio.shell(new ui.components.DocumentWorkspace(form, preview), 3, dialog::dispose, null, null, null));
        ui.components.DocumentWorkspace.fitDialog(dialog, 1360, 800);
        ThemeManager.applyToWindow(dialog);
        dialog.setVisible(true);
        return confirmed[0];
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
        WindowHelper.showPosWindow(new AccountPaymentReceiptPreview(
                receiptRef.customerId(), receiptRef.transactionId()), this);
    }

    private void reprintPaymentReceipt() {
        int viewRow = paymentTable.getSelectedRow();
        if (viewRow < 0) {
            JOptionPane.showMessageDialog(this, "Select a payment to reprint.", "Payments", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        int row = paymentTable.convertRowIndexToModel(viewRow);
        long transactionId = ((Number) paymentModel.getValueAt(row, 0)).longValue();
        int customerId = ((Number) paymentModel.getValueAt(row, 1)).intValue();
        WindowHelper.showPosWindow(new AccountPaymentReceiptPreview(customerId, transactionId), this);
    }

    private void previewInvoice() {
        Long invoiceId = selectedId(invoiceTable);
        if (invoiceId == null) return;
        WindowHelper.showPosWindow(new QuotationInvoiceDocumentPreview(
                "Invoice Preview", false, "INVOICE", invoiceId), this);
    }

    private void previewDelivery() {
        Long deliveryId = selectedId(deliveryTable);
        if (deliveryId == null) return;
        WindowHelper.showPosWindow(new QuotationInvoiceDocumentPreview(
                "Delivery Bill Preview", false, "DELIVERY_BILL", deliveryId), this);
    }

    private void openQuotationDialog() {
        Quotations.QuotationEditor editor = new Quotations.QuotationEditor(this);
        editor.setVisible(true);
        if (editor.created) {
            refreshAll();
        }
    }

    private void cancelSelectedQuotation() {
        if (!managers.PermissionManager.hasPermission("CANCEL_QUOTATIONS")) return;
        Long quotationId = selectedId(quotationTable);
        if (quotationId == null) return;
        String reason = JOptionPane.showInputDialog(this,
                "Cancel this quotation? Enter an optional reason:", "Cancel Quotation", JOptionPane.WARNING_MESSAGE);
        if (reason == null) return;
        try {
            Boolean cancelled = ResponsiveTask.await(this, "Cancelling quotation...", () -> {
                QuotationInvoiceService.cancelQuotation(quotationId, reason.trim());
                return Boolean.TRUE;
            });
            if (Boolean.TRUE.equals(cancelled)) refreshAll();
        } catch (Exception ex) {
            showError("Failed to cancel quotation", ex);
        }
    }

    private void editSelectedDraftQuotation() {
        Long quotationId = selectedId(quotationTable);
        if (quotationId == null) return;
        try {
            QuotationInvoiceViewService.QuotationEditData quotation = ResponsiveTask.await(this,"Loading quotation...",()->QuotationInvoiceViewService.loadQuotationForEdit(quotationId));
            if(quotation==null)return;
            if (!"DRAFT".equals(quotation.status()) && !"ISSUED".equals(quotation.status())) {
                JOptionPane.showMessageDialog(this, "Only draft or issued quotations can be edited.", "Quotations", JOptionPane.WARNING_MESSAGE);
                return;
            }
            Quotations.QuotationEditor editor = new Quotations.QuotationEditor(this, quotation);
            editor.setVisible(true);
            if (editor.created) {
                refreshAll();
            }
        } catch (Exception ex) {
            showError("Failed to load quotation", ex);
        }
    }

    private void issueSelectedQuotation() {
        Long quotationId = selectedId(quotationTable);
        if (quotationId == null) return;
        try {
            Boolean issued = ResponsiveTask.await(this, "Issuing quotation...", () -> {
                QuotationInvoiceService.issueQuotation(quotationId);
                return Boolean.TRUE;
            });
            if (issued == null) return;
            refreshAll();
            openQuotationPrintDialog(quotationId);
        } catch (Exception ex) {
            showError("Failed to issue quotation", ex);
        }
    }

    private void acceptSelectedQuotation() {
        Long quotationId = selectedId(quotationTable);
        if (quotationId == null) return;
        acceptQuotation(quotationId);
    }

    void acceptQuotation(long quotationId) {
        try {
            QuotationInvoiceService.InvoiceResult result = ResponsiveTask.await(this,"Accepting quotation...",()->QuotationInvoiceService.acceptQuotation(quotationId));
            if(result==null)return;
            promptForAcceptedQuotationPayment(result.invoiceId());
            Long deliveryEventId = promptForAcceptedQuotationDelivery(result.invoiceId());
            refreshAll();
            openSalesInvoicePrintDialog(result.invoiceId());
            if (deliveryEventId != null) {
                openDeliveryPrintDialog(deliveryEventId);
            }
        } catch (Exception ex) {
            showError("Failed to accept quotation", ex);
        }
    }

    private void promptForAcceptedQuotationPayment(long invoiceId) {
        try {
            QuotationInvoiceViewService.InvoiceFinancials financials = ResponsiveTask.await(this,"Loading invoice balance...",()->QuotationInvoiceViewService.loadInvoiceFinancials(invoiceId));
            if(financials==null)return;
            Quotations.PaymentPrompt prompt = new Quotations.PaymentPrompt(this, financials);
            prompt.setVisible(true);
            Quotations.PaymentInput payment = prompt.paymentInput();
            if (payment == null) return;
            boolean paymentRecorded = false;
            if (payment.amount().compareTo(BigDecimal.ZERO) > 0) {
                try {
                    Quotations.CreditOverride creditOverride = Quotations.creditOverride(this, financials, payment.amount());
                    if (creditOverride == null) return;
                    QuotationInvoiceService.PaymentReceiptRef receiptRef = ResponsiveTask.await(this,"Recording invoice payment...",()->QuotationInvoiceService.recordPayment(invoiceId,payment.amount(),payment.method(),payment.reference(),creditOverride.approvalToken(),creditOverride.reason()));
                    if(receiptRef==null)return;
                    paymentRecorded = true;
                    openPaymentReceipt(receiptRef);
                } catch (Exception ex) {
                    showError("Payment was not recorded; remaining balance will be placed on account if possible", ex);
                }
            }
            QuotationInvoiceViewService.InvoiceFinancials updated = ResponsiveTask.await(this,"Refreshing invoice balance...",()->QuotationInvoiceViewService.loadInvoiceFinancials(invoiceId));
            if(updated==null)return;
            if (!paymentRecorded && updated.balanceDue().compareTo(BigDecimal.ZERO) > 0) {
                if (!Quotations.confirmAccountCharge(this, updated)) return;
                Quotations.CreditOverride creditOverride = Quotations.creditOverride(this, updated, BigDecimal.ZERO);
                if (creditOverride == null) return;
                LanApiClient.ChargeAuthorization authorization=updated.requireChargeAuthorization()?ui.helpers.CustomerChargeAuthorizationDialog.capture(this,updated.customerName()):null;
                if(updated.requireChargeAuthorization()&&authorization==null)return;
                ResponsiveTask.await(this, "Charging remaining balance to account...", () -> {
                    QuotationInvoiceService.chargeInvoiceToAccount(invoiceId,
                            "Remaining balance from accepted quotation.",creditOverride.approvalToken(),creditOverride.reason(),authorization);
                    return Boolean.TRUE;
                });
            }
        } catch (Exception ex) {
            showError("Failed to place remaining balance on customer account", ex);
        }
    }

    private Long promptForAcceptedQuotationDelivery(long invoiceId) throws Exception {
        List<QuotationInvoiceViewService.DeliverableLine> deliverable=ResponsiveTask.await(this,"Loading deliverable lines...",()->QuotationInvoiceViewService.listDeliverableLines(invoiceId));
        if(deliverable==null||deliverable.isEmpty()) {
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
        WindowHelper.showPosWindow(new QuotationInvoiceDocumentPreview(
                "Quotation Preview", false, "QUOTATION", quotationId), this);
    }

    private void openQuotationPrintDialog(long quotationId) throws SQLException {
        WindowHelper.showPosWindow(new QuotationInvoiceDocumentPreview(
                "Quotation Print",
                true,
                "QUOTATION",
                quotationId
        ), this);
    }

    private void openSalesInvoicePrintDialog(long invoiceId) throws SQLException {
        WindowHelper.showPosWindow(new QuotationInvoiceDocumentPreview(
                "Invoice Print",
                true,
                "INVOICE",
                invoiceId
        ), this);
    }

    private void openDeliveryPrintDialog(long deliveryEventId) throws SQLException {
        WindowHelper.showPosWindow(new QuotationInvoiceDocumentPreview(
                "Delivery Bill Print",
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
        return SalesDocumentWorkspace.selectedModelId(table);
    }

    private void showError(String title, Exception ex) {
        JOptionPane.showMessageDialog(this, title + ".\n\n" + ex.getMessage(), "Invoices", JOptionPane.ERROR_MESSAGE);
    }

    static class DeliveryDialog extends JDialog {
        private final long invoiceId;
        private final JComboBox<String> methodBox = new JComboBox<>(new String[]{"PICKUP", "LOCAL_DELIVERY", "SHIP", "INSTALLATION"});
        private final JTextField receiverField = new JTextField();
        private final JTextField notesField = new JTextField();
        private final DefaultTableModel lineModel = new DefaultTableModel(new String[]{"Line ID", "Item", "Invoiced", "Delivered", "Remaining", "Available", "Deliver Now"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return column == 6;
            }
        };
        private boolean created;
        private Long deliveryEventId;
        private JTable deliveryLinesTable;

        DeliveryDialog(JFrame owner, long invoiceId) {
            super(owner, "Post Invoice Delivery", true);
            this.invoiceId = invoiceId;
            ui.components.DocumentWorkspace.fitDialog(this, 1440, 850);
            setLocationRelativeTo(owner);
            setLayout(new BorderLayout(8, 8));
            JPanel main = DocumentStudio.surface(new BorderLayout(0, 18));
            main.setBorder(new EmptyBorder(22, 22, 22, 22));
            JPanel deliveryFields = DocumentStudio.stack(DocumentStudio.workflow(2),
                    DocumentStudio.row(DocumentStudio.field("Delivery method", methodBox), DocumentStudio.field("Receiver", receiverField)),
                    DocumentStudio.field("Delivery notes", notesField));
            JTable table = new JTable(lineModel);
            deliveryLinesTable = table;
            DocumentStudio.table(table);
            table.removeColumn(table.getColumnModel().getColumn(0));
            table.getColumnModel().getColumn(0).setPreferredWidth(260);
            table.putClientProperty("terminateEditOnFocusLost", Boolean.TRUE);
            for (int column = 1; column < table.getColumnCount(); column++) {
                table.getColumnModel().getColumn(column).setPreferredWidth(column == 5 ? 110 : 85);
            }
            JScrollPane lineRows = DocumentStudio.scroll(table); lineRows.setColumnHeaderView(table.getTableHeader());
            lineRows.setPreferredSize(new Dimension(500, 230));
            JPanel content = DocumentStudio.stack(deliveryFields, DocumentStudio.section("Delivery quantities", lineRows));
            main.add(DocumentStudio.scroll(DocumentStudio.topAligned(content)), BorderLayout.CENTER);
            JPanel buttons = DocumentStudio.surface(new FlowLayout(FlowLayout.RIGHT, 10, 0));
            JButton post = new JButton("Record delivery");
            JButton cancel = new JButton("Cancel");
            buttons.add(post);
            buttons.add(cancel);
            DocumentStudio.button(post, true);
            DocumentStudio.button(cancel, false);
            main.add(buttons, BorderLayout.SOUTH);
            post.addActionListener(e -> post());
            cancel.addActionListener(e -> dispose());
            SalesDocumentPanel invoicePreview = new SalesDocumentPanel(this);
            invoicePreview.showDocument("INVOICE", invoiceId, "Invoice before this delivery");
            add(DocumentStudio.shell(new ui.components.DocumentWorkspace(main, invoicePreview), 3, this::dispose, null, null, null), BorderLayout.CENTER);
            ThemeManager.applyToWindow(this);
            loadLines();
        }

        private void loadLines() {
            UiTaskRunner.submit(this,"invoice.delivery-lines",()->QuotationInvoiceViewService.listDeliverableLines(invoiceId),lines->{lineModel.setRowCount(0);
                for (QuotationInvoiceViewService.DeliverableLine line : lines) {
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
            },ex->JOptionPane.showMessageDialog(this,"Failed to load invoice lines.\n\n"+ex.getMessage(),"Delivery",JOptionPane.ERROR_MESSAGE));
        }

        private void post() {
            if (deliveryLinesTable.isEditing() && !deliveryLinesTable.getCellEditor().stopCellEditing()) return;
            List<QuotationInvoiceService.DeliveryLineInput> lines = new ArrayList<>();
            List<String> stockWarnings = new ArrayList<>();
            try {
                for (int i = 0; i < lineModel.getRowCount(); i++) {
                    int qty = parseQuantity(lineModel.getValueAt(i, 6));
                    int remaining = parseQuantity(lineModel.getValueAt(i, 4));
                    Object availableValue = lineModel.getValueAt(i, 5);
                    Integer available = availableQuantity(availableValue);
                    String item = String.valueOf(lineModel.getValueAt(i, 1));
                    if (qty < 0) {
                        throw new IllegalArgumentException("Delivery quantity for " + item + " cannot be negative.");
                    }
                    if (qty > remaining) {
                        throw new IllegalArgumentException("Delivery quantity for " + item + " cannot be more than the remaining " + remaining + ".");
                    }
                    if (qty > 0 && available != null && qty > available) {
                        stockWarnings.add(item + ": " + available + " in stock, delivering " + qty
                                + " will leave " + (available - qty) + ".");
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
            if (!stockWarnings.isEmpty()) {
                String warning = "This delivery will create negative inventory:\n\n"
                        + String.join("\n", stockWarnings)
                        + "\n\nContinue posting this delivery?";
                if (JOptionPane.showConfirmDialog(this, warning, "Negative Inventory Warning",
                        JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) != JOptionPane.YES_OPTION) {
                    return;
                }
            }
            try {
                String method=String.valueOf(methodBox.getSelectedItem()),receiver=receiverField.getText(),notes=notesField.getText();
                QuotationInvoiceService.DeliveryResult result = ResponsiveTask.await(this,"Posting delivery...",()->QuotationInvoiceService.postDelivery(invoiceId,method,receiver,notes,lines));
                if(result==null)return;
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

        private Integer availableQuantity(Object value) {
            if (value == null || "Manual".equals(String.valueOf(value))) {
                return null;
            }
            return parseQuantity(value);
        }

        Long deliveryEventId() {
            return deliveryEventId;
        }
    }
}
