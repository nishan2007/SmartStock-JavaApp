package ui.screens;

import ui.design.DeckersPalette;

import managers.PermissionManager;
import services.ManagerApprovalService;
import services.QuotationInvoiceService;
import services.LanApiClient;
import services.QuotationInvoiceViewService;
import services.CustomOrderDataService;
import ui.components.AppMenuBar;
import ui.components.LoadingStatePanel;
import ui.helpers.CachedUiLoader;
import ui.helpers.ResponsiveTask;
import ui.helpers.SessionDataCache;
import ui.helpers.ThemeManager;
import ui.helpers.WindowHelper;
import ui.helpers.UiTaskRunner;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class Quotations extends JFrame {
    private static final String CHANGE_SALE_ITEM_PRICE_PERMISSION = "CHANGE_SALE_ITEM_PRICE";
    private static final String CREDIT_LIMIT_OVERRIDE_PERMISSION = "SET_CREDIT_LIMIT";
    private static final int LINE_COL_PRODUCT_ID = 0;
    private static final int LINE_COL_ITEM = 1;
    private static final int LINE_COL_SKU = 2;
    private static final int LINE_COL_QTY = 3;
    private static final int LINE_COL_UNIT = 4;
    private static final int LINE_COL_DISCOUNT = 5;
    private static final int LINE_COL_DELIVERY = 6;
    private static final int LINE_COL_NOTES = 7;
    private static final int LINE_COL_ORIGINAL_UNIT = 8;
    private static final int LINE_COL_OVERRIDE_REASON = 9;
    private static final int LINE_COL_OVERRIDE_BY_USER_ID = 10;
    private static final int LINE_COL_OVERRIDE_BY_NAME = 11;
    private static final int LINE_COL_OVERRIDE_TOKEN = 12;
    private static final int LINE_COL_CUSTOM = 13;
    private static final Color INPUT_BACKGROUND = new Color(248, 250, 252);
    private static final Color INPUT_FOREGROUND = new Color(17, 24, 39);
    private static final Color INPUT_SELECTION = DeckersPalette.PURPLE;
    private static final Color BUTTON_PRIMARY = DeckersPalette.ORANGE;
    private static final Color BUTTON_SECONDARY = new Color(75, 85, 99);
    private final DefaultTableModel quotationModel = readOnlyModel("ID", "Quotation #", "Customer", "Status", "Valid Until", "Total");
    private final JTable quotationTable = new JTable(quotationModel);
    private final LoadingStatePanel loadingState = new LoadingStatePanel();

    public Quotations() {
        setTitle("Quotations");
        getRootPane().putClientProperty("SmartStock.responsiveMinimumSize", new Dimension(800, 560));
        setSize(1120, 720);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setJMenuBar(AppMenuBar.create(this, "Quotations"));

        JPanel mainPanel = new JPanel(new BorderLayout(12, 12));
        mainPanel.setBorder(null);

        JLabel title = new JLabel("Quotations");
        title.setFont(new Font("SansSerif", Font.BOLD, 26));
        mainPanel.add(tablePanel(quotationTable, quotationButtons()), BorderLayout.CENTER);
        mainPanel.add(loadingState, BorderLayout.SOUTH);
        add(DocumentStudio.shell(mainPanel, 0, null, this::openQuotationDialog, null,
                () -> WindowHelper.showPosWindow(new Invoices(Invoices.InitialTab.INVOICES), this)), BorderLayout.CENTER);

        refreshQuotations();
        WindowHelper.configurePosWindow(this);
    }

    private JPanel quotationButtons() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        JButton newQuotation = new JButton("New Quotation");
        JButton editDraft = new JButton("Edit Quotation");
        JButton issue = new JButton("Issue");
        JButton cancelQuotation = new JButton("Cancel Quotation");
        cancelQuotation.setEnabled(PermissionManager.hasPermission("CANCEL_QUOTATIONS"));
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
        stylePrimaryButton(newQuotation);
        styleSecondaryButton(editDraft);
        styleSecondaryButton(issue);
        styleSecondaryButton(accept);
        styleSecondaryButton(cancelQuotation);
        styleSecondaryButton(preview);
        styleSecondaryButton(refresh);
        newQuotation.addActionListener(e -> openQuotationDialog());
        editDraft.addActionListener(e -> editSelectedDraftQuotation());
        issue.addActionListener(e -> issueSelectedQuotation());
        accept.addActionListener(e -> acceptSelectedQuotation());
        cancelQuotation.addActionListener(e -> cancelSelectedQuotation());
        preview.addActionListener(e -> previewQuotation());
        refresh.addActionListener(e -> refreshQuotations());
        return panel;
    }

    private JComponent tablePanel(JTable table, JPanel buttons) {
        table.setAutoCreateRowSorter(true);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        styleTable(table);
        return SalesDocumentWorkspace.create(this, table, buttons, "QUOTATION");
    }

    void refreshQuotations() {
        CachedUiLoader.load(this, "quotations.snapshot", QuotationRows.class,
                SessionDataCache.SCREEN_TTL, loadingState,
                () -> new QuotationRows(QuotationInvoiceViewService.listQuotations()), rows -> {
            quotationModel.setRowCount(0);
            for (QuotationInvoiceViewService.QuotationSummary row : rows.values()) {
                quotationModel.addRow(new Object[]{
                        row.quotationId(),
                        row.quotationNumber(),
                        row.customerName(),
                        row.status(),
                        row.validUntil(),
                        row.totalAmount()
                });
            }
        });
    }

    private record QuotationRows(List<QuotationInvoiceViewService.QuotationSummary> values) { }

    void openQuotationDialog() {
        QuotationEditor editor = new QuotationEditor(this);
        editor.setVisible(true);
        if (editor.created) {
            refreshQuotations();
        }
    }

    private void cancelSelectedQuotation() {
        if (!PermissionManager.hasPermission("CANCEL_QUOTATIONS")) return;
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
            if (Boolean.TRUE.equals(cancelled)) refreshQuotations();
        } catch (Exception ex) {
            showError("Failed to cancel quotation", ex);
        }
    }

    void editSelectedDraftQuotation() {
        Long quotationId = selectedId(quotationTable);
        if (quotationId == null) return;
        try {
            QuotationInvoiceViewService.QuotationEditData quotation = ResponsiveTask.await(this,
                    "Loading quotation...", () -> QuotationInvoiceViewService.loadQuotationForEdit(quotationId));
            if (quotation == null) return;
            if (!"DRAFT".equals(quotation.status()) && !"ISSUED".equals(quotation.status())) {
                JOptionPane.showMessageDialog(this, "Only draft or issued quotations can be edited.", "Quotations", JOptionPane.WARNING_MESSAGE);
                return;
            }
            QuotationEditor editor = new QuotationEditor(this, quotation);
            editor.setVisible(true);
            if (editor.created) {
                refreshQuotations();
            }
        } catch (Exception ex) {
            showError("Failed to load quotation", ex);
        }
    }

    void issueSelectedQuotation() {
        Long quotationId = selectedId(quotationTable);
        if (quotationId == null) return;
        try {
            Boolean issued = ResponsiveTask.await(this, "Issuing quotation...", () -> {
                QuotationInvoiceService.issueQuotation(quotationId);
                return Boolean.TRUE;
            });
            if (issued == null) return;
            refreshQuotations();
            openQuotationPrintDialog(quotationId);
        } catch (Exception ex) {
            showError("Failed to issue quotation", ex);
        }
    }

    void acceptSelectedQuotation() {
        Long quotationId = selectedId(quotationTable);
        if (quotationId == null) return;
        acceptQuotation(quotationId);
    }

    void acceptQuotation(long quotationId) {
        try {
            QuotationInvoiceService.InvoiceResult result = ResponsiveTask.await(this,
                    "Accepting quotation...", () -> QuotationInvoiceService.acceptQuotation(quotationId));
            if (result == null) return;
            promptForAcceptedQuotationPayment(result.invoiceId());
            Long deliveryEventId = promptForAcceptedQuotationDelivery(result.invoiceId());
            refreshQuotations();
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
            QuotationInvoiceViewService.InvoiceFinancials financials = ResponsiveTask.await(this,
                    "Loading invoice balance...", () -> QuotationInvoiceViewService.loadInvoiceFinancials(invoiceId));
            if (financials == null) return;
            PaymentPrompt prompt = new PaymentPrompt(this, financials);
            prompt.setVisible(true);
            PaymentInput payment = prompt.paymentInput();
            if (payment == null) return;
            boolean paymentRecorded = false;
            if (payment.amount().compareTo(BigDecimal.ZERO) > 0) {
                try {
                    CreditOverride creditOverride = creditOverride(this, financials, payment.amount());
                    if (creditOverride == null) return;
                    QuotationInvoiceService.PaymentReceiptRef receiptRef = ResponsiveTask.await(this,
                            "Recording invoice payment...", () -> QuotationInvoiceService.recordPayment(
                                    invoiceId, payment.amount(), payment.method(), payment.reference(),
                                    creditOverride.approvalToken(), creditOverride.reason()));
                    if (receiptRef == null) return;
                    paymentRecorded = true;
                    openPaymentReceipt(receiptRef);
                } catch (Exception ex) {
                    showError("Payment was not recorded; remaining balance will be placed on account if possible", ex);
                }
            }
            QuotationInvoiceViewService.InvoiceFinancials updated = ResponsiveTask.await(this,
                    "Refreshing invoice balance...", () -> QuotationInvoiceViewService.loadInvoiceFinancials(invoiceId));
            if (updated == null) return;
            if (!paymentRecorded && updated.balanceDue().compareTo(BigDecimal.ZERO) > 0) {
                if (!confirmAccountCharge(this, updated)) return;
                CreditOverride creditOverride = creditOverride(this, updated, BigDecimal.ZERO);
                if (creditOverride == null) return;
                LanApiClient.ChargeAuthorization authorization=updated.requireChargeAuthorization()?ui.helpers.CustomerChargeAuthorizationDialog.capture(this,updated.customerName()):null;
                if(updated.requireChargeAuthorization()&&authorization==null)return;
                ResponsiveTask.await(this, "Charging remaining balance to account...", () -> {
                    QuotationInvoiceService.chargeInvoiceToAccount(invoiceId,
                            "Remaining balance from accepted quotation.",
                            creditOverride.approvalToken(), creditOverride.reason(),authorization);
                    return Boolean.TRUE;
                });
            }
        } catch (Exception ex) {
            showError("Failed to place remaining balance on customer account", ex);
        }
    }

    static boolean confirmAccountCharge(Component parent, QuotationInvoiceViewService.InvoiceFinancials financials) {
        String message = "Invoice " + financials.invoiceNumber() + " has a remaining balance of "
                + financials.balanceDue() + ".\n\nPlace this balance on the customer account now?\n"
                + "Choose No to leave the invoice open and unpaid.";
        return JOptionPane.showConfirmDialog(parent, message, "Place Balance on Account",
                JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE) == JOptionPane.YES_OPTION;
    }

    static CreditOverride creditOverride(Component parent,
                                         QuotationInvoiceViewService.InvoiceFinancials financials,
                                         BigDecimal paymentAmount) {
        BigDecimal applied = paymentAmount == null ? BigDecimal.ZERO
                : paymentAmount.max(BigDecimal.ZERO).min(financials.balanceDue());
        BigDecimal remaining = financials.balanceDue().subtract(applied).max(BigDecimal.ZERO);
        BigDecimal available = financials.availableCredit() == null ? BigDecimal.ZERO : financials.availableCredit();
        if (remaining.compareTo(available) <= 0 || PermissionManager.hasPermission(CREDIT_LIMIT_OVERRIDE_PERMISSION)) {
            return new CreditOverride(null, null);
        }
        ManagerApprovalService.ApprovalResult approval = ManagerApprovalService.requestApproval(
                parent,
                CREDIT_LIMIT_OVERRIDE_PERMISSION,
                "Customer Credit Limit Override",
                "Reason for allowing " + remaining + " on account when available credit is " + available + ":"
        );
        return approval == null ? null : new CreditOverride(approval.lanApprovalToken(), approval.reason());
    }

    record CreditOverride(String approvalToken, String reason) { }

    private Long promptForAcceptedQuotationDelivery(long invoiceId) throws Exception {
        List<QuotationInvoiceViewService.DeliverableLine> lines = ResponsiveTask.await(this,
                "Loading deliverable lines...", () -> QuotationInvoiceViewService.listDeliverableLines(invoiceId));
        if (lines == null || lines.isEmpty()) {
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
        Invoices.DeliveryDialog dialog = new Invoices.DeliveryDialog(this, invoiceId);
        dialog.setVisible(true);
        return dialog.deliveryEventId();
    }

    void previewQuotation() {
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

    private void openPaymentReceipt(QuotationInvoiceService.PaymentReceiptRef receiptRef) {
        if (receiptRef == null) {
            return;
        }
        WindowHelper.showPosWindow(new AccountPaymentReceiptPreview(
                receiptRef.customerId(), receiptRef.transactionId()), this);
    }

    Long selectedId(JTable table) {
        int row = table.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "Select a row first.");
            return null;
        }
        return SalesDocumentWorkspace.selectedModelId(table);
    }

    static DefaultTableModel readOnlyModel(String... columns) {
        return new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
    }

    static JPanel formPanel(String[] labels, JComponent[] fields) {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        for (int i = 0; i < labels.length; i++) {
            gbc.gridx = 0;
            gbc.gridy = i;
            gbc.weightx = 0;
            panel.add(new JLabel(labels[i] + ":"), gbc);
            gbc.gridx = 1;
            gbc.weightx = 1;
            styleReadableControl(fields[i]);
            panel.add(fields[i], gbc);
        }
        return panel;
    }

    public static void stylePrimaryButton(JButton button) {
        styleButton(button, BUTTON_PRIMARY, new Color(15, 23, 42));
    }

    public static void styleSecondaryButton(JButton button) {
        styleButton(button, BUTTON_SECONDARY, Color.WHITE);
    }

    private static void styleButton(JButton button, Color background, Color foreground) {
        button.putClientProperty("SmartStock.ownedButtonBackground", background);
        button.putClientProperty("SmartStock.ownedButtonForeground", foreground);
        button.setOpaque(true);
        button.setContentAreaFilled(true);
        button.setBackground(background);
        button.setForeground(foreground);
        button.setFocusPainted(false);
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(background.darker()),
                new EmptyBorder(6, 14, 6, 14)
        ));
    }

    static void styleReadableControl(JComponent component) {
        if (component instanceof JScrollPane scrollPane) {
            scrollPane.getViewport().setBackground(new Color(17, 17, 17));
            Component view = scrollPane.getViewport().getView();
            if (view instanceof JComponent child) {
                styleReadableControl(child);
            }
            return;
        }
        component.setForeground(INPUT_FOREGROUND);
        component.setBackground(INPUT_BACKGROUND);
        component.setOpaque(true);
        if (component instanceof JTextComponent textComponent) {
            textComponent.setCaretColor(INPUT_FOREGROUND);
            textComponent.setSelectionColor(INPUT_SELECTION);
            textComponent.setSelectedTextColor(Color.WHITE);
            textComponent.setDisabledTextColor(new Color(107, 114, 128));
        } else if (component instanceof JComboBox<?> comboBox) {
            styleComboBox(comboBox);
        } else if (component instanceof JTable table) {
            styleTable(table);
        }
    }

    private static void styleComboBox(JComboBox<?> comboBox) {
        comboBox.setForeground(INPUT_FOREGROUND);
        comboBox.setBackground(INPUT_BACKGROUND);
        comboBox.setOpaque(true);
        comboBox.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                Component component = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                component.setForeground(isSelected ? Color.WHITE : INPUT_FOREGROUND);
                component.setBackground(isSelected ? INPUT_SELECTION : INPUT_BACKGROUND);
                return component;
            }
        });
    }

    static void styleTable(JTable table) {
        table.setForeground(DeckersPalette.text());
        table.setBackground(DeckersPalette.surface());
        table.setRowHeight(30);
        table.setShowVerticalLines(false);
        table.setSelectionForeground(Color.WHITE);
        table.setSelectionBackground(INPUT_SELECTION);
        table.setGridColor(DeckersPalette.border());
        table.getTableHeader().setForeground(DeckersPalette.text());
        table.getTableHeader().setBackground(DeckersPalette.background());
        table.getTableHeader().setOpaque(true);
    }

    static BigDecimal parseMoney(String value) {
        String clean = value == null ? "" : value.replace("$", "").replace(",", "").trim();
        return clean.isBlank() || "null".equalsIgnoreCase(clean)
                ? BigDecimal.ZERO
                : new BigDecimal(clean).setScale(2, RoundingMode.UNNECESSARY);
    }

    static BigDecimal parsePercent(String value) {
        String clean=value==null?"":value.trim();
        return clean.isBlank() || "null".equalsIgnoreCase(clean)?BigDecimal.ZERO:new BigDecimal(clean);
    }

    private static boolean canChangeSaleItemPrice() {
        return PermissionManager.hasPermission(CHANGE_SALE_ITEM_PRICE_PERMISSION);
    }

    private static void hideInternalLineColumns(JTable table) {
        int idColumn = table.convertColumnIndexToView(LINE_COL_PRODUCT_ID);
        if (idColumn >= 0) table.removeColumn(table.getColumnModel().getColumn(idColumn));
        for (int modelIndex = LINE_COL_ORIGINAL_UNIT; modelIndex <= LINE_COL_CUSTOM; modelIndex++) {
            int viewIndex = table.convertColumnIndexToView(modelIndex);
            if (viewIndex >= 0) {
                table.removeColumn(table.getColumnModel().getColumn(viewIndex));
            }
        }
    }

    private static Integer nullableInteger(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        if (text.isBlank() || "null".equalsIgnoreCase(text)) {
            return null;
        }
        return Integer.parseInt(text);
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String text = value.trim();
        return text.isBlank() || "null".equalsIgnoreCase(text) ? null : text;
    }

    void showError(String title, Exception ex) {
        JOptionPane.showMessageDialog(this, title + ".\n\n" + ex.getMessage(), "Quotations", JOptionPane.ERROR_MESSAGE);
    }

    static class PaymentPrompt extends JDialog {
        private final JTextField amountField = new JTextField("0");
        private final JComboBox<String> methodBox = new JComboBox<>(new String[]{"CASH", "CARD", "CHEQUE", "MMG"});
        private final JTextField referenceField = new JTextField();
        private PaymentInput paymentInput;

        PaymentPrompt(JFrame owner, QuotationInvoiceViewService.InvoiceFinancials financials) {
            super(owner, "Accepted Quotation Payment", true);
            ui.components.DocumentWorkspace.fitDialog(this, 1360, 800);
            setLocationRelativeTo(owner);
            setLayout(new BorderLayout(8, 8));
            amountField.setText(financials.balanceDue().toPlainString());
            JPanel main = DocumentStudio.surface(new BorderLayout(0, 18));
            main.setBorder(new EmptyBorder(22, 22, 22, 22));
            JLabel summary = new JLabel("<html>Invoice " + financials.invoiceNumber()
                    + "<br>Total: " + financials.totalAmount()
                    + "<br>Balance: " + financials.balanceDue()
                    + "<br>Review any remaining balance after payment.</html>");
            JPanel paymentFields = DocumentStudio.stack(DocumentStudio.workflow(2), summary,
                    DocumentStudio.section("Record payment", DocumentStudio.stack(
                            DocumentStudio.row(DocumentStudio.field("Amount paid now", amountField), DocumentStudio.field("Payment method", methodBox)),
                            DocumentStudio.field("Reference · optional", referenceField))));
            main.add(DocumentStudio.scroll(DocumentStudio.topAligned(paymentFields)), BorderLayout.CENTER);
            JPanel buttons = DocumentStudio.surface(new FlowLayout(FlowLayout.RIGHT, 8, 0));
            JButton continueButton = new JButton("Continue");
            buttons.add(continueButton);
            DocumentStudio.button(continueButton, true);
            main.add(buttons, BorderLayout.SOUTH);
            continueButton.addActionListener(e -> save());
            SalesDocumentPanel preview = new SalesDocumentPanel(this);
            preview.showDocument("INVOICE", financials.invoiceId(), "Invoice before this payment");
            add(DocumentStudio.shell(new ui.components.DocumentWorkspace(main, preview), 3, this::dispose, null, null, null), BorderLayout.CENTER);
            ThemeManager.applyToWindow(this);
        }

        private void save() {
            try {
                BigDecimal amount = parseMoney(amountField.getText());
                if (amount.compareTo(BigDecimal.ZERO) < 0) {
                    throw new IllegalArgumentException("Payment amount cannot be negative.");
                }
                paymentInput = new PaymentInput(amount, String.valueOf(methodBox.getSelectedItem()), referenceField.getText());
                dispose();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, ex.getMessage(), "Payment", JOptionPane.ERROR_MESSAGE);
            }
        }

        PaymentInput paymentInput() {
            return paymentInput;
        }
    }

    static class QuotationEditor extends JDialog {
        private final JComboBox<QuotationInvoiceViewService.CustomerOption> customerBox = new JComboBox<>();
        private final JTextField validUntilField = new JTextField(LocalDate.now().plusDays(30).toString());
        private final JTextField productionDueDateField = new JTextField();
        private final JTextArea notesArea = new JTextArea(3, 40);
        private final JCheckBox applyCustomerDiscountBox=new JCheckBox("Apply customer invoice discount");
        private final DefaultTableModel lineModel = new DefaultTableModel(new String[]{
                "Product ID", "Item", "SKU", "Qty", "Unit", "Disc %", "Delivery", "Notes",
                "Original Unit", "Override Reason", "Override By User ID", "Override By", "Override Token", "Custom Configuration"
        }, 0);
        private final JLabel statusLabel = new JLabel(" ");
        private final JTextField catalogSearchField = new JTextField();
        private final DefaultListModel<QuotationInvoiceViewService.ProductOption> catalogSearchModel = new DefaultListModel<>();
        private final JList<QuotationInvoiceViewService.ProductOption> catalogSearchList = new JList<>(catalogSearchModel);
        private final Long editQuotationId;
        private final String editQuotationNumber;
        private final Timer customerSearchTimer;
        private final Timer catalogSearchTimer;
        private final LetterDocumentView draftView = new LetterDocumentView();
        private final JLabel draftTotal = new JLabel("Total: choose a customer and add items");
        private final JComboBox<String> draftTemplate = new JComboBox<>(new String[]{"Grid", "Compact"});
        private final Timer draftTimer = new Timer(500, e -> refreshDraftPreview());
        private long draftGeneration;
        private boolean acceptAfterSave;
        private JButton reviewButton;
        private JButton createButton;
        private JButton cancelButton;
        private boolean updatingCustomerResults;
        private long customerSearchGeneration;
        private QuotationInvoiceViewService.CustomerOption rememberedCustomer;
        private JPanel quickCatalogPanel;
        boolean created;

        QuotationEditor(JFrame owner) {
            this(owner, null);
        }

        QuotationEditor(JFrame owner, QuotationInvoiceViewService.QuotationEditData editData) {
            super(owner, editData == null ? "New Quotation" : "Edit Quotation " + editData.quotationNumber(), true);
            this.editQuotationId = editData == null ? null : editData.quotationId();
            this.editQuotationNumber = editData == null ? null : editData.quotationNumber();
            this.customerSearchTimer = new Timer(300, e -> refreshCustomerResults(editorText(customerBox)));
            customerSearchTimer.setRepeats(false);
            this.catalogSearchTimer = new Timer(250, e -> refreshCatalogSearch());
            catalogSearchTimer.setRepeats(false);
            ui.components.DocumentWorkspace.fitDialog(this, 1440, 900);
            setLocationRelativeTo(owner);
            setLayout(new BorderLayout(8, 8));
            JPanel main = new JPanel(new BorderLayout(8, 8));
            main.setBorder(new EmptyBorder(12, 12, 12, 12));
            customerBox.setEditable(true);
            customerBox.setMaximumRowCount(6);
            customerBox.setRenderer(new DefaultListCellRenderer() {
                private String safe(String value) { return value == null ? "" : value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;"); }
                @Override public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected, boolean focus) {
                    Object display = value;
                    if (value instanceof QuotationInvoiceViewService.CustomerOption customer && index >= 0) {
                        display = "<html><b>" + safe(customer.name()) + "</b><br>" + safe(customer.accountNumber())
                                + (customer.phone() == null || customer.phone().isBlank() ? "" : " &nbsp; · &nbsp; " + safe(customer.phone())) + "</html>";
                    }
                    super.getListCellRendererComponent(list, display, index, selected, focus);
                    setBorder(new EmptyBorder(6, 10, 6, 10));
                    setBackground(selected ? DeckersPalette.tilePressed(DeckersPalette.PURPLE) : DeckersPalette.surface());
                    setForeground(DeckersPalette.text()); return this;
                }
            });
            loadCustomers();
            installCustomerSearch();
            if (editData != null) {
                loadExistingQuotation(editData);
            }
            productionDueDateField.setToolTipText("Optional; use YYYY-MM-DD.");
            notesArea.setLineWrap(true);
            notesArea.setWrapStyleWord(true);
            notesArea.setColumns(20);
            customerBox.addActionListener(e->{if(!updatingCustomerResults)refreshCustomerDiscount();});
            refreshCustomerDiscount();
            JTable lineTable = studioLineTable(lineModel);
            JButton addItem = new JButton("+ Add item"), editLine = new JButton("Edit"), removeLine = new JButton("Remove");
            JPanel itemActions = DocumentStudio.surface(new FlowLayout(FlowLayout.LEFT, 8, 0));
            for (JButton button : new JButton[]{addItem, editLine, removeLine}) { DocumentStudio.button(button, false); itemActions.add(button); }
            JPopupMenu addMenu = new JPopupMenu();
            JMenuItem stock = new JMenuItem("Stock catalogue…"), custom = new JMenuItem("Custom item…"), manual = new JMenuItem("Manual item…");
            addMenu.add(stock); addMenu.add(custom); addMenu.add(manual);
            addItem.addActionListener(e -> addMenu.show(addItem, 0, addItem.getHeight()));
            stock.addActionListener(e -> openCatalogPicker()); custom.addActionListener(e -> addCustomLine()); manual.addActionListener(e -> addLine());
            editLine.addActionListener(e -> editSelectedLine(lineTable));
            removeLine.addActionListener(e -> { int row = lineTable.getSelectedRow(); if (row >= 0) lineModel.removeRow(lineTable.convertRowIndexToModel(row)); });
            JPanel items = DocumentStudio.surface(new BorderLayout(0, 12));
            JScrollPane itemRows = new JScrollPane(lineTable) {
                @Override public Dimension getPreferredSize() {
                    Dimension size = super.getPreferredSize();
                    if (main.getHeight() > 0) size.height = Math.max(120, Math.min(220, main.getHeight() - 410));
                    return size;
                }
            };
            itemRows.setBorder(null);
            itemRows.setColumnHeaderView(lineTable.getTableHeader());
            itemRows.setPreferredSize(new Dimension(500, 220));
            items.add(itemRows, BorderLayout.CENTER); items.add(itemActions, BorderLayout.SOUTH);
            JButton newCustomer = new JButton("New Customer");
            DocumentStudio.button(newCustomer, false);
            newCustomer.addActionListener(e -> openNewCustomerDialog());
            JPanel customerPicker = DocumentStudio.surface(new BorderLayout(8, 0));
            customerPicker.add(customerBox, BorderLayout.CENTER);
            customerPicker.add(newCustomer, BorderLayout.EAST);
            JLabel customerLabel = DocumentStudio.label("Customer");
            customerLabel.setLabelFor(customerBox);
            JPanel customerField = DocumentStudio.surface(new BorderLayout(0, 7));
            customerField.add(customerLabel, BorderLayout.NORTH);
            customerField.add(customerPicker, BorderLayout.CENTER);
            JPanel customer = DocumentStudio.stack(customerField, applyCustomerDiscountBox);
            JPanel dates = DocumentStudio.stack(DocumentStudio.field("Valid until", validUntilField), DocumentStudio.field("Production due date", productionDueDateField));
            JPanel customerDates = DocumentStudio.surface(new GridBagLayout());
            GridBagConstraints group = new GridBagConstraints(); group.gridy = 0; group.fill = GridBagConstraints.HORIZONTAL;
            group.anchor = GridBagConstraints.NORTH; group.gridx = 0; group.weightx = 0.65; group.insets = new Insets(0, 0, 0, 18); customerDates.add(customer, group);
            group.gridx = 1; group.weightx = 0.35; group.insets = new Insets(0, 0, 0, 0); customerDates.add(dates, group);
            JPanel noteFields = DocumentStudio.field("Quotation notes", DocumentStudio.scroll(notesArea));
            noteFields.setVisible(!notesArea.getText().isBlank());
            JButton notesToggle = new JButton("Notes"); DocumentStudio.button(notesToggle, false);
            notesToggle.addActionListener(e -> { noteFields.setVisible(!noteFields.isVisible()); main.revalidate(); });
            itemActions.add(notesToggle);
            JPanel content = DocumentStudio.stack(DocumentStudio.workflow(0), customerDates, items, noteFields);
            main.setBackground(DeckersPalette.surface()); main.putClientProperty("SmartStock.preserveBackground", true);
            main.setBorder(new EmptyBorder(22, 22, 22, 22));
            main.add(DocumentStudio.scroll(DocumentStudio.topAligned(content)), BorderLayout.CENTER);
            JPanel footer = DocumentStudio.surface(new BorderLayout(0, 14));
            draftTotal.setHorizontalAlignment(SwingConstants.RIGHT); draftTotal.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 18));
            footer.add(draftTotal, BorderLayout.NORTH);
            JPanel buttons = DocumentStudio.surface(new ui.components.WrappingToolbarLayout());
            ((FlowLayout)buttons.getLayout()).setAlignment(FlowLayout.RIGHT);
            createButton = new JButton(editData == null ? "Save draft" : "Save quotation"); reviewButton = new JButton("Review & accept"); cancelButton = new JButton("Cancel");
            DocumentStudio.button(createButton, false); DocumentStudio.button(reviewButton, true); DocumentStudio.button(cancelButton, false);
            buttons.add(cancelButton); buttons.add(createButton); buttons.add(reviewButton); footer.add(buttons, BorderLayout.CENTER);
            statusLabel.setVisible(false); statusLabel.setForeground(DeckersPalette.muted());
            statusLabel.addPropertyChangeListener("text", e -> statusLabel.setVisible(!statusLabel.getText().isBlank()));
            footer.add(statusLabel, BorderLayout.SOUTH); main.add(footer, BorderLayout.SOUTH);
            createButton.addActionListener(e -> {
                if (lineTable.isEditing() && !lineTable.getCellEditor().stopCellEditing()) return;
                acceptAfterSave = false; createQuotation();
            });
            reviewButton.addActionListener(e -> {
                if (lineTable.isEditing() && !lineTable.getCellEditor().stopCellEditing()) return;
                if (JOptionPane.showConfirmDialog(this, "Accept this quotation and create its invoice?\n" + draftTotal.getText()
                        + "\n\nSaving will validate the customer, prices and any required approvals.", "Review quotation",
                        JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE) == JOptionPane.YES_OPTION) {
                    acceptAfterSave = true; createQuotation();
                }
            });
            cancelButton.addActionListener(e -> dispose());
            draftTimer.setRepeats(false);
            JPanel preview = DocumentStudio.surface(new BorderLayout(4, 4));
            preview.setBorder(new EmptyBorder(16, 16, 16, 16));
            JPanel previewTools = DocumentStudio.surface(new FlowLayout(FlowLayout.LEFT, 8, 0));
            previewTools.add(DocumentStudio.templateSwitch(draftTemplate));
            preview.add(previewTools, BorderLayout.NORTH); preview.add(draftView, BorderLayout.CENTER);
            JComponent workspace = new ui.components.DocumentWorkspace(main, preview);
            add(DocumentStudio.shell(workspace, 1, this::dispose, null, this::addCustomLine, null), BorderLayout.CENTER);
            lineModel.addTableModelListener(e -> scheduleDraftPreview());
            for (javax.swing.text.JTextComponent field : new javax.swing.text.JTextComponent[]{validUntilField, productionDueDateField, notesArea}) {
                field.getDocument().addDocumentListener(new DocumentListener() {
                    public void insertUpdate(DocumentEvent e) { scheduleDraftPreview(); }
                    public void removeUpdate(DocumentEvent e) { scheduleDraftPreview(); }
                    public void changedUpdate(DocumentEvent e) { scheduleDraftPreview(); }
                });
            }
            customerBox.addActionListener(e -> { if (!updatingCustomerResults) scheduleDraftPreview(); });
            draftTemplate.addActionListener(e -> scheduleDraftPreview());
            applyCustomerDiscountBox.addActionListener(e -> scheduleDraftPreview());
            addWindowListener(new java.awt.event.WindowAdapter() {
                @Override public void windowClosed(java.awt.event.WindowEvent e) {
                    draftTimer.stop(); customerSearchTimer.stop(); catalogSearchTimer.stop();
                }
            });
            ThemeManager.applyToWindow(this);
            scheduleDraftPreview();
        }

        static JTable studioLineTable(DefaultTableModel model) {
            JTable table = new JTable(model) {
                @Override public boolean isCellEditable(int row, int column) {
                    Object identifier = getColumnModel().getColumn(column).getIdentifier();
                    return !"amount".equals(identifier) && !"type".equals(identifier) && super.isCellEditable(row, column);
                }
            };
            configureStudioLineTable(table, model);
            table.putClientProperty("terminateEditOnFocusLost", Boolean.TRUE);
            return table;
        }

        private static void configureStudioLineTable(JTable table, DefaultTableModel lineModel) {
            hideInternalLineColumns(table);
            for (int modelColumn : new int[]{LINE_COL_SKU, LINE_COL_DELIVERY, LINE_COL_NOTES}) {
                int view = table.convertColumnIndexToView(modelColumn);
                if (view >= 0) table.removeColumn(table.getColumnModel().getColumn(view));
            }
            javax.swing.table.TableColumn type = new javax.swing.table.TableColumn(LINE_COL_CUSTOM);
            type.setIdentifier("type"); type.setHeaderValue("Type");
            type.setCellRenderer(new javax.swing.table.DefaultTableCellRenderer() {
                @Override public Component getTableCellRendererComponent(JTable t, Object value, boolean selected, boolean focus, int row, int col) {
                    int modelRow = t.convertRowIndexToModel(row);
                    String kind = value != null ? "Custom" : lineModel.getValueAt(modelRow, LINE_COL_PRODUCT_ID) != null ? "Stock" : "Manual";
                    super.getTableCellRendererComponent(t, kind, selected, false, row, col);
                    setForeground(DeckersPalette.muted()); setBorder(new EmptyBorder(4, 8, 4, 8)); return this;
                }
            });
            table.addColumn(type); table.moveColumn(table.getColumnCount() - 1, 1);
            javax.swing.table.TableColumn amount = new javax.swing.table.TableColumn(LINE_COL_UNIT);
            amount.setIdentifier("amount"); amount.setHeaderValue("Amount");
            amount.setCellRenderer(new javax.swing.table.DefaultTableCellRenderer() {
                @Override public Component getTableCellRendererComponent(JTable t, Object value, boolean selected, boolean focus, int row, int col) {
                    String text = "—";
                    try {
                        int r = t.convertRowIndexToModel(row);
                        BigDecimal gross = parseMoney(String.valueOf(value)).multiply(new BigDecimal(String.valueOf(lineModel.getValueAt(r, LINE_COL_QTY))));
                        BigDecimal discount = parsePercent(String.valueOf(lineModel.getValueAt(r, LINE_COL_DISCOUNT)));
                        text = utils.CurrencyFormatter.format(gross.multiply(BigDecimal.ONE.subtract(discount.movePointLeft(2))));
                    } catch (RuntimeException ignored) { }
                    super.getTableCellRendererComponent(t, text, selected, false, row, col);
                    setHorizontalAlignment(SwingConstants.RIGHT); setBorder(new EmptyBorder(4, 8, 4, 8)); return this;
                }
            });
            table.addColumn(amount);
            table.getColumnModel().getColumn(3).setHeaderValue("Rate");
            table.getColumnModel().getColumn(4).setHeaderValue("Discount %");
            int[] widths = {230, 76, 54, 90, 85, 100};
            int[] minimums = {155, 65, 45, 80, 90, 90};
            for (int i = 0; i < widths.length; i++) {
                table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
                table.getColumnModel().getColumn(i).setMinWidth(minimums[i]);
            }
            DocumentStudio.table(table);
            table.setToolTipText("Line amounts precede VAT and any customer discount. The store calculates the quotation total.");
        }

        private void openCatalogPicker() {
            if (quickCatalogPanel == null) quickCatalogPanel = catalogSearchPanel();
            JDialog picker = new JDialog(this, "Add stock item", true);
            picker.setDefaultCloseOperation(DISPOSE_ON_CLOSE);
            JPanel content = DocumentStudio.surface(new BorderLayout(12, 12)); content.setBorder(new EmptyBorder(20, 20, 20, 20));
            content.add(quickCatalogPanel, BorderLayout.CENTER);
            JPanel buttons = DocumentStudio.surface(new FlowLayout(FlowLayout.RIGHT, 8, 0));
            JButton add = new JButton("Add to quotation"), close = new JButton("Close");
            DocumentStudio.button(add, true); DocumentStudio.button(close, false); buttons.add(close); buttons.add(add);
            add.addActionListener(e -> {
                if (catalogSearchList.getSelectedValue() != null) { addSelectedCatalogProduct(); picker.dispose(); }
            });
            close.addActionListener(e -> picker.dispose()); content.add(buttons, BorderLayout.SOUTH);
            picker.add(content); ui.components.DocumentWorkspace.fitDialog(picker, 660, 420);
            DocumentStudio.finish(content); picker.setVisible(true);
        }

        private JPanel catalogSearchPanel() {
            JPanel panel = new JPanel(new BorderLayout(8, 4));
            panel.setBorder(BorderFactory.createTitledBorder("Quick Add Catalog Product"));
            styleReadableControl(catalogSearchField);
            catalogSearchField.setToolTipText("Search by product name, SKU, barcode, or description. Press Enter to add.");
            catalogSearchList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            catalogSearchList.setVisibleRowCount(3);
            catalogSearchList.setFixedCellHeight(24);
            panel.add(catalogSearchField, BorderLayout.NORTH);
            panel.add(new JScrollPane(catalogSearchList), BorderLayout.CENTER);
            catalogSearchField.getDocument().addDocumentListener(new DocumentListener() {
                @Override public void insertUpdate(DocumentEvent e) { catalogSearchTimer.restart(); }
                @Override public void removeUpdate(DocumentEvent e) { catalogSearchTimer.restart(); }
                @Override public void changedUpdate(DocumentEvent e) { catalogSearchTimer.restart(); }
            });
            catalogSearchField.addActionListener(e -> addSelectedCatalogProduct());
            catalogSearchList.addMouseListener(new java.awt.event.MouseAdapter() {
                @Override public void mouseClicked(java.awt.event.MouseEvent e) {
                    if (e.getClickCount() == 2) addSelectedCatalogProduct();
                }
            });
            catalogSearchList.getInputMap().put(KeyStroke.getKeyStroke("ENTER"), "add-product");
            catalogSearchList.getActionMap().put("add-product", new AbstractAction() {
                @Override public void actionPerformed(java.awt.event.ActionEvent e) { addSelectedCatalogProduct(); }
            });
            refreshCatalogSearch();
            return panel;
        }

        private void refreshCatalogSearch() {
            String search = catalogSearchField.getText().trim();
            if (search.isBlank()) {
                catalogSearchModel.clear();
                return;
            }
            UiTaskRunner.submit(this, "quotation.quick-products", () -> QuotationInvoiceViewService.searchProducts(search), products -> {
                if (!search.equals(catalogSearchField.getText().trim())) return;
                catalogSearchModel.clear();
                for (QuotationInvoiceViewService.ProductOption product : products) {
                    if (product.productId() != null) catalogSearchModel.addElement(product);
                }
                if (!catalogSearchModel.isEmpty()) catalogSearchList.setSelectedIndex(0);
            }, ex -> statusLabel.setText("Product search failed: " + ex.getMessage()));
        }

        private void addSelectedCatalogProduct() {
            QuotationInvoiceViewService.ProductOption product = catalogSearchList.getSelectedValue();
            if (product == null && catalogSearchModel.size() == 1) product = catalogSearchModel.get(0);
            if (product == null || product.productId() == null) {
                statusLabel.setText("Select a catalog product from the search results.");
                return;
            }
            for (int row = 0; row < lineModel.getRowCount(); row++) {
                if (product.productId().equals(nullableInteger(lineModel.getValueAt(row, LINE_COL_PRODUCT_ID)))) {
                    int quantity = Integer.parseInt(String.valueOf(lineModel.getValueAt(row, LINE_COL_QTY)));
                    lineModel.setValueAt(quantity + 1, row, LINE_COL_QTY);
                    finishQuickAdd(product.name());
                    return;
                }
            }
            lineModel.addRow(new Object[]{
                    product.productId(), product.name(), product.sku(), 1, product.price(), BigDecimal.ZERO,
                    "PICKUP", "", product.price(), null, null, null, null, null
            });
            finishQuickAdd(product.name());
        }

        private void finishQuickAdd(String productName) {
            statusLabel.setForeground(new Color(34, 197, 94));
            statusLabel.setText("Added " + productName + ".");
            catalogSearchField.setText("");
            catalogSearchModel.clear();
            catalogSearchField.requestFocusInWindow();
        }

        private void addLine() {
            LineEditor editor = new LineEditor(this);
            editor.setVisible(true);
            if (editor.line != null) {
                QuotationInvoiceViewService.ProductOption product = editor.line.product();
                lineModel.addRow(new Object[]{
                        product == null ? null : product.productId(),
                        editor.line.itemName(),
                        editor.line.sku(),
                        editor.line.quantity(),
                        editor.line.unitPrice(),
                        editor.line.discountPercent(),
                        editor.line.deliveryMethod(),
                        editor.line.notes(),
                        editor.line.originalUnitPrice(),
                        editor.line.priceOverrideReason(),
                        editor.line.priceOverrideByUserId(),
                        editor.line.priceOverrideByName(),
                        editor.line.priceOverrideApprovalToken(),editor.line.custom()
                });
            }
        }

        private void addCustomLine(){
            CustomQuotationLineEditor editor=new CustomQuotationLineEditor(this);
            editor.setVisible(true);
            if(editor.line==null)return;
            LineInput line=editor.line;
            lineModel.addRow(new Object[]{null,line.itemName(),line.sku(),line.quantity(),line.unitPrice(),line.discountPercent(),line.deliveryMethod(),line.notes(),line.originalUnitPrice(),line.priceOverrideReason(),line.priceOverrideByUserId(),line.priceOverrideByName(),line.priceOverrideApprovalToken(),line.custom()});
        }

        private void editSelectedLine(JTable lineTable) {
            int row = lineTable.getSelectedRow();
            if (row < 0) {
                JOptionPane.showMessageDialog(this, "Select a line first.", "Quotation", JOptionPane.WARNING_MESSAGE);
                return;
            }
            int modelRow = lineTable.convertRowIndexToModel(row);
            LineInput existing = lineInputFromRow(modelRow);
            if(existing.custom()!=null){CustomQuotationLineEditor customEditor=new CustomQuotationLineEditor(this,existing,modelRow);customEditor.setVisible(true);if(customEditor.line!=null){LineInput changed=customEditor.line;Object[]values={null,changed.itemName(),changed.sku(),changed.quantity(),changed.unitPrice(),changed.discountPercent(),changed.deliveryMethod(),changed.notes(),changed.originalUnitPrice(),changed.priceOverrideReason(),changed.priceOverrideByUserId(),changed.priceOverrideByName(),changed.priceOverrideApprovalToken(),changed.custom()};for(int column=0;column<values.length;column++)lineModel.setValueAt(values[column],modelRow,column);}return;}
            LineEditor editor = new LineEditor(this, existing, false, modelRow);
            editor.setVisible(true);
            if (editor.line != null) {
                QuotationInvoiceViewService.ProductOption product = editor.line.product();
                lineModel.setValueAt(product == null ? null : product.productId(), modelRow, 0);
                lineModel.setValueAt(editor.line.itemName(), modelRow, 1);
                lineModel.setValueAt(editor.line.sku(), modelRow, 2);
                lineModel.setValueAt(editor.line.quantity(), modelRow, 3);
                lineModel.setValueAt(editor.line.unitPrice(), modelRow, 4);
                lineModel.setValueAt(editor.line.discountPercent(), modelRow, 5);
                lineModel.setValueAt(editor.line.deliveryMethod(), modelRow, 6);
                lineModel.setValueAt(editor.line.notes(), modelRow, 7);
                lineModel.setValueAt(editor.line.originalUnitPrice(), modelRow, 8);
                lineModel.setValueAt(editor.line.priceOverrideReason(), modelRow, 9);
                lineModel.setValueAt(editor.line.priceOverrideByUserId(), modelRow, 10);
                lineModel.setValueAt(editor.line.priceOverrideByName(), modelRow, 11);
                lineModel.setValueAt(editor.line.priceOverrideApprovalToken(), modelRow, 12);
                lineModel.setValueAt(editor.line.custom(), modelRow, 13);
            }
        }

        private LineInput lineInputFromRow(int row) {
            Object productIdValue = lineModel.getValueAt(row, 0);
            Integer productId = productIdValue == null ? null : Integer.parseInt(productIdValue.toString());
            String item = String.valueOf(lineModel.getValueAt(row, 1));
            String sku = String.valueOf(lineModel.getValueAt(row, 2));
            BigDecimal unitPrice = parseMoney(String.valueOf(lineModel.getValueAt(row, 4)));
            BigDecimal originalUnitPrice = parseMoney(String.valueOf(lineModel.getValueAt(row, 8)));
            QuotationInvoiceViewService.ProductOption product = productId == null
                    ? null
                    : new QuotationInvoiceViewService.ProductOption(productId, item, sku, "", "", originalUnitPrice);
            return new LineInput(
                    product,
                    item,
                    sku,
                    Integer.parseInt(String.valueOf(lineModel.getValueAt(row, 3))),
                    unitPrice,
                    originalUnitPrice,
                    parsePercent(String.valueOf(lineModel.getValueAt(row, 5))),
                    String.valueOf(lineModel.getValueAt(row, 6)),
                    String.valueOf(lineModel.getValueAt(row, 7)),
                    blankToNull(String.valueOf(lineModel.getValueAt(row, 9))),
                    nullableInteger(lineModel.getValueAt(row, 10)),
                    blankToNull(String.valueOf(lineModel.getValueAt(row, 11))),
                    blankToNull(String.valueOf(lineModel.getValueAt(row, 12))),
                    (QuotationInvoiceService.CustomLineInput)lineModel.getValueAt(row,13)
            );
        }

        private void loadExistingQuotation(QuotationInvoiceViewService.QuotationEditData quotation) {
            selectCustomer(quotation.customerId());
            if (selectedCustomer() == null) {
                // The customer search loads asynchronously; retain the saved identity in the meantime.
                rememberCustomer(new QuotationInvoiceViewService.CustomerOption(
                        quotation.customerId(), "", quotation.customerName(), false));
                refreshCustomerResults(quotation.customerName());
            }
            validUntilField.setText(quotation.validUntil() == null ? LocalDate.now().plusDays(30).toString() : quotation.validUntil().toLocalDate().toString());
            productionDueDateField.setText(quotation.productionDueDate()==null?"":quotation.productionDueDate().toLocalDate().toString());
            notesArea.setText(quotation.notes() == null ? "" : quotation.notes());
            for (QuotationInvoiceViewService.QuotationEditLine line : quotation.lines()) {
                lineModel.addRow(new Object[]{
                        line.productId(),
                        line.itemName(),
                        line.sku(),
                        line.quantity(),
                        line.unitPrice(),
                        line.discountPercent(),
                        line.deliveryMethod(),
                        line.notes(),
                        line.originalUnitPrice() == null ? line.unitPrice() : line.originalUnitPrice(),
                        line.priceOverrideReason(),
                        line.priceOverrideByUserId(),
                        line.priceOverrideByName(),
                        null,line.custom()
                });
            }
        }

        private void selectCustomer(int customerId) {
            for (int i = 0; i < customerBox.getItemCount(); i++) {
                QuotationInvoiceViewService.CustomerOption option = customerBox.getItemAt(i);
                if (option.customerId() == customerId) {
                    updatingCustomerResults = true;
                    customerBox.setSelectedIndex(i);
                    updatingCustomerResults = false;
                    return;
                }
            }
        }

        private void scheduleDraftPreview() {
            draftGeneration++;
            draftTotal.setText("Total: updating…");
            draftView.showMessage("Updating draft preview…");
            draftTimer.restart();
        }

        private void refreshDraftPreview() {
            var customer = selectedCustomer();
            if (customer == null) {
                draftView.showMessage("Select a customer to preview the quotation");
                draftTotal.setText("Total: choose a customer"); return;
            }
            final long generation = draftGeneration;
            try {
                var lines = draftLines();
                LocalDate valid = validUntilField.getText().isBlank() ? null : LocalDate.parse(validUntilField.getText().trim());
                LocalDate due = productionDueDateField.getText().isBlank() ? null : LocalDate.parse(productionDueDateField.getText().trim());
                boolean compact = draftTemplate.getSelectedIndex() == 1;
                boolean discount = applyCustomerDiscountBox.isSelected();
                UiTaskRunner.submit(this, "quotation.draft-preview", () ->
                        QuotationInvoiceViewService.previewDraft(customer.customerId(), valid, due, lines, discount, compact),
                        preview -> { if (generation == draftGeneration) {
                            draftView.setDocument(preview.text());
                            draftTotal.setText("Quotation total: " + utils.CurrencyFormatter.format(preview.total()));
                        } },
                        error -> { if (generation == draftGeneration) {
                            draftView.showMessage("Preview unavailable. Check entries or reconnect to the store.");
                            draftTotal.setText("Total unavailable — check entries or connection");
                            draftView.setToolTipText(error.getMessage());
                        }});
            } catch (RuntimeException error) {
                draftView.showMessage("Complete valid dates, quantities and prices to preview");
                draftTotal.setText("Total: complete valid entries");
            }
        }

        private List<QuotationInvoiceService.QuotationLineInput> draftLines() {
            List<QuotationInvoiceService.QuotationLineInput> lines = new ArrayList<>();
            for (int i = 0; i < lineModel.getRowCount(); i++) {
                Object productIdValue = lineModel.getValueAt(i, 0);
                Integer productId = productIdValue == null ? null : Integer.parseInt(productIdValue.toString());
                lines.add(new QuotationInvoiceService.QuotationLineInput(
                        productId,
                        String.valueOf(lineModel.getValueAt(i, 1)),
                        String.valueOf(lineModel.getValueAt(i, 2)),
                        Integer.parseInt(String.valueOf(lineModel.getValueAt(i, 3))),
                        parseMoney(String.valueOf(lineModel.getValueAt(i, 4))),
                        parseMoney(String.valueOf(lineModel.getValueAt(i, 8))),
                        parsePercent(String.valueOf(lineModel.getValueAt(i, 5))),
                        String.valueOf(lineModel.getValueAt(i, 6)),
                        String.valueOf(lineModel.getValueAt(i, 7)),
                        blankToNull(String.valueOf(lineModel.getValueAt(i, 9))),
                        nullableInteger(lineModel.getValueAt(i, 10)),
                        blankToNull(String.valueOf(lineModel.getValueAt(i, 11))),
                        blankToNull(String.valueOf(lineModel.getValueAt(i, 12))),
                        (QuotationInvoiceService.CustomLineInput)lineModel.getValueAt(i,13)
                ));
            }
            return lines;
        }

        private void createQuotation() {
            QuotationInvoiceViewService.CustomerOption customer = selectedCustomer();
            if (customer == null) {
                JOptionPane.showMessageDialog(this, "Select an existing customer or use New Customer.");
                return;
            }
            List<QuotationInvoiceService.QuotationLineInput> lines;
            try { lines = draftLines(); }
            catch (RuntimeException error) {
                showQuotationError("Check item quantities, prices and discounts before saving."); return;
            }
            LocalDate validUntil;
            LocalDate productionDueDate=null;
            try {
                validUntil = LocalDate.parse(validUntilField.getText().trim());
                if(!productionDueDateField.getText().trim().isBlank())productionDueDate=LocalDate.parse(productionDueDateField.getText().trim());
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Valid Until must be a date like 2026-07-11.", "Quotation", JOptionPane.ERROR_MESSAGE);
                return;
            }
            String notes = notesArea.getText();
            LocalDate dueDate=productionDueDate;
            setCreatingQuotation(true);
            statusLabel.setForeground(new Color(248, 113, 113));
            statusLabel.setText(editQuotationId == null ? "Creating quotation..." : "Saving quotation...");
            Thread createThread = new Thread(() -> {
                try {
                    QuotationInvoiceService.QuotationResult result = editQuotationId == null
                            ? QuotationInvoiceService.createQuotation(customer.customerId(), validUntil, dueDate, notes, lines,applyCustomerDiscountBox.isSelected())
                            : QuotationInvoiceService.updateDraftQuotation(editQuotationId, customer.customerId(), validUntil, dueDate, notes, lines,applyCustomerDiscountBox.isSelected());
                    SwingUtilities.invokeLater(() -> {
                        created = true;
                        setCreatingQuotation(false);
                        statusLabel.setForeground(new Color(74, 222, 128));
                        statusLabel.setText((editQuotationId == null ? "Created quotation " : "Saved quotation ") + result.quotationNumber() + ".");
                        dispose();
                        if (acceptAfterSave) SwingUtilities.invokeLater(() -> {
                            if (getOwner() instanceof Invoices invoices) invoices.acceptQuotation(result.quotationId());
                            else if (getOwner() instanceof Quotations quotations) quotations.acceptQuotation(result.quotationId());
                        });
                    });
                } catch (Exception ex) {
                    SwingUtilities.invokeLater(() -> {
                        setCreatingQuotation(false);
                        showQuotationError((editQuotationId == null ? "Failed to create quotation." : "Failed to save quotation.") + "\n\n" + ex.getMessage());
                    });
                }
            }, "sales-quotation-create");
            createThread.setDaemon(true);
            createThread.start();
        }

        private void refreshCustomerDiscount(){QuotationInvoiceViewService.CustomerOption customer=selectedCustomer();boolean available=customer!=null&&customer.invoiceDiscountEnabled()&&customer.invoiceDiscountPercent()!=null&&customer.invoiceDiscountPercent().signum()>0;setCustomerDiscountVisibility(available);applyCustomerDiscountBox.setSelected(available);applyCustomerDiscountBox.setText(available?"Apply customer invoice discount ("+customer.invoiceDiscountPercent().stripTrailingZeros().toPlainString()+"%)":"Apply customer invoice discount");}

        private void setCustomerDiscountVisibility(boolean visible) {
            applyCustomerDiscountBox.setVisible(visible);
            Container parent = applyCustomerDiscountBox.getParent();
            if (parent != null && parent.getLayout() instanceof GridBagLayout layout) {
                int row = layout.getConstraints(applyCustomerDiscountBox).gridy;
                for (Component component : parent.getComponents())
                    if (component instanceof JLabel && layout.getConstraints(component).gridy == row)
                        component.setVisible(visible);
                parent.revalidate();
            }
        }

        private void setCreatingQuotation(boolean creating) {
            createButton.setEnabled(!creating);
            if (reviewButton != null) reviewButton.setEnabled(!creating);
            cancelButton.setEnabled(!creating);
            createButton.setText(creating ? "Saving…" : "Save quotation");
            setCursor(Cursor.getPredefinedCursor(creating ? Cursor.WAIT_CURSOR : Cursor.DEFAULT_CURSOR));
        }

        private void showQuotationError(String message) {
            statusLabel.setForeground(new Color(248, 113, 113));
            statusLabel.setText(message.replace('\n', ' '));
            showQuotationMessage("Quotation", message, JOptionPane.ERROR_MESSAGE);
        }

        private void showQuotationMessage(String title, String message, int messageType) {
            toFront();
            requestFocus();
            JOptionPane pane = new JOptionPane(message, messageType);
            JDialog dialog = pane.createDialog(this, title);
            dialog.setAlwaysOnTop(true);
            dialog.setVisible(true);
            dialog.dispose();
        }

        private void openNewCustomerDialog() {
            customerBox.hidePopup();
            JDialog dialog = new JDialog(this, "New Customer Account", true);
            dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
            JTextField nameField = new JTextField(26);
            JTextField phoneField = new JTextField(26);
            JPanel form = DocumentStudio.stack(
                    DocumentStudio.field("Customer name", nameField),
                    DocumentStudio.field("Phone number", phoneField));
            JButton cancel = new JButton("Cancel");
            JButton create = new JButton("Create Account");
            DocumentStudio.button(cancel, false);
            DocumentStudio.button(create, true);
            JLabel status = DocumentStudio.label(" ");
            JPanel actions = DocumentStudio.surface(new FlowLayout(FlowLayout.RIGHT, 8, 0));
            actions.add(cancel);
            actions.add(create);
            JPanel content = DocumentStudio.surface(new BorderLayout(0, 12));
            content.setBorder(new EmptyBorder(18, 18, 18, 18));
            content.add(form, BorderLayout.CENTER);
            content.add(DocumentStudio.stack(status, actions), BorderLayout.SOUTH);
            dialog.setContentPane(content);
            dialog.getRootPane().setDefaultButton(create);
            cancel.addActionListener(e -> dialog.dispose());
            create.addActionListener(new java.awt.event.ActionListener() {
                private String lastName;
                private String lastPhone;
                private String mutationKey;

                @Override public void actionPerformed(java.awt.event.ActionEvent event) {
                    String name = nameField.getText().strip();
                    String phone = phoneField.getText().strip();
                    if (name.isEmpty() || phone.isEmpty()) {
                        status.setText("Name and phone are required.");
                        (name.isEmpty() ? nameField : phoneField).requestFocusInWindow();
                        return;
                    }
                    if (!name.equals(lastName) || !phone.equals(lastPhone)) {
                        lastName = name;
                        lastPhone = phone;
                        mutationKey = UUID.randomUUID().toString();
                    }
                    String key = mutationKey;
                    setSaving(true);
                    UiTaskRunner.submit(dialog, "quotation.create-customer",
                            () -> QuotationInvoiceService.createCustomer(name, phone, key), saved -> {
                                customerSearchTimer.stop();
                                customerSearchGeneration++;
                                rememberCustomer(saved);
                                dialog.dispose();
                            }, error -> {
                                setSaving(false);
                                JOptionPane.showMessageDialog(dialog, error.getMessage(),
                                        "Could Not Create Customer", JOptionPane.ERROR_MESSAGE);
                            });
                }

                private void setSaving(boolean saving) {
                    nameField.setEditable(!saving);
                    phoneField.setEditable(!saving);
                    create.setEnabled(!saving);
                    cancel.setEnabled(!saving);
                    status.setText(saving ? "Creating customer account…" : " ");
                    dialog.setDefaultCloseOperation(saving
                            ? WindowConstants.DO_NOTHING_ON_CLOSE : WindowConstants.DISPOSE_ON_CLOSE);
                }
            });
            dialog.pack();
            dialog.setLocationRelativeTo(this);
            dialog.setVisible(true);
        }

        private void loadCustomers() {
            refreshCustomerResults("");
        }

        private void installCustomerSearch() {
            customerBox.addItemListener(e->{
                if(!updatingCustomerResults&&e.getStateChange()==java.awt.event.ItemEvent.SELECTED
                        &&e.getItem() instanceof QuotationInvoiceViewService.CustomerOption customer)rememberedCustomer=customer;
            });
            Component editor = customerBox.getEditor().getEditorComponent();
            if (editor instanceof JTextComponent textComponent) {
                styleReadableControl(textComponent);
                textComponent.getDocument().addDocumentListener(new DocumentListener() {
                    @Override
                    public void insertUpdate(DocumentEvent e) {
                        scheduleCustomerSearch();
                    }

                    @Override
                    public void removeUpdate(DocumentEvent e) {
                        scheduleCustomerSearch();
                    }

                    @Override
                    public void changedUpdate(DocumentEvent e) {
                        scheduleCustomerSearch();
                    }
                });
                if (textComponent instanceof JTextField textField) {
                    textField.addActionListener(e -> selectBestCustomerMatch(editorText(customerBox)));
                    textField.addFocusListener(new java.awt.event.FocusAdapter() {
                        @Override
                        public void focusGained(java.awt.event.FocusEvent e) {
                            showCustomerPopupIfUseful();
                        }
                    });
                }
            }
        }

        private void scheduleCustomerSearch() {
            if (!updatingCustomerResults) {
                customerSearchTimer.restart();
                scheduleDraftPreview();
            }
        }

        private void refreshCustomerResults(String searchText) {
            String query=searchText==null?"":searchText;
            long generation=++customerSearchGeneration;
            UiTaskRunner.submit(this,"quotation.customers",()->QuotationInvoiceViewService.searchCustomers(query),customers->{
                if(generation!=customerSearchGeneration||!query.equals(editorText(customerBox)))return;
                QuotationInvoiceViewService.CustomerOption previous=rememberedCustomer;
                updatingCustomerResults = true;
                customerBox.removeAllItems();
                for (QuotationInvoiceViewService.CustomerOption customer : customers) {
                    customerBox.addItem(customer);
                }
                // Keep search results uncommitted until the customer chooses one.
                customerBox.setSelectedIndex(-1);
                customerBox.getEditor().setItem(query);
                updatingCustomerResults = false;
                QuotationInvoiceViewService.CustomerOption preserved=customerById(customers,previous);
                if(preserved!=null&&exactCustomerMatch(preserved,query))rememberCustomer(preserved);
                else selectExactCustomerMatch(query, customers);
                showCustomerPopupIfUseful();
            },ex->{updatingCustomerResults=false;});
        }

        private QuotationInvoiceViewService.CustomerOption selectedCustomer() {
            QuotationInvoiceViewService.CustomerOption selected=resolveCustomerSelection(customerBox.getSelectedItem(),
                    editorText(customerBox),currentCustomers(),rememberedCustomer);

            return selected;
        }

        private QuotationInvoiceViewService.CustomerOption selectBestCustomerMatch(String searchText) {
            return selectBestCustomerMatch(searchText,currentCustomers(),true);
        }

        private void selectExactCustomerMatch(String searchText, List<QuotationInvoiceViewService.CustomerOption> customers) {
            selectBestCustomerMatch(searchText, customers, false);
        }

        private QuotationInvoiceViewService.CustomerOption selectBestCustomerMatch(String searchText,
                                                                                  List<QuotationInvoiceViewService.CustomerOption> customers,
                                                                                  boolean allowSingleMatch) {
            String search = searchText == null ? "" : searchText.trim();
            if (search.isBlank()) {
                return null;
            }
            QuotationInvoiceViewService.CustomerOption match = null;
            for (QuotationInvoiceViewService.CustomerOption customer : customers) {
                if (exactCustomerMatch(customer, search)) {
                    match = customer;
                    break;
                }
            }
            if (match == null && allowSingleMatch && customers.size() == 1) {
                match = customers.get(0);
            }
            if (match != null) {
                rememberCustomer(match);
            }
            return match;
        }

        private void rememberCustomer(QuotationInvoiceViewService.CustomerOption customer){
            rememberedCustomer=customer;updatingCustomerResults=true;
            try { customerBox.setSelectedItem(customer); }
            finally { updatingCustomerResults=false; }
            refreshCustomerDiscount();
            scheduleDraftPreview();
        }

        private List<QuotationInvoiceViewService.CustomerOption> currentCustomers(){List<QuotationInvoiceViewService.CustomerOption> current=new ArrayList<>();for(int i=0;i<customerBox.getItemCount();i++)current.add(customerBox.getItemAt(i));return current;}

        private static QuotationInvoiceViewService.CustomerOption customerById(List<QuotationInvoiceViewService.CustomerOption> customers,QuotationInvoiceViewService.CustomerOption target){if(target==null)return null;for(var customer:customers)if(customer.customerId()==target.customerId())return customer;return null;}

        static QuotationInvoiceViewService.CustomerOption resolveCustomerSelection(Object selected,String editorText,List<QuotationInvoiceViewService.CustomerOption> customers,QuotationInvoiceViewService.CustomerOption remembered){
            if(selected instanceof QuotationInvoiceViewService.CustomerOption customer&&exactCustomerMatch(customer,editorText))return customer;
            if(remembered!=null&&exactCustomerMatch(remembered,editorText))return remembered;
            String search=editorText==null?"":editorText.trim();if(search.isBlank())return null;
            for(var customer:customers)if(exactCustomerMatch(customer,search))return customer;
            return null;
        }

        private static boolean exactCustomerMatch(QuotationInvoiceViewService.CustomerOption customer, String searchText) {
            if(customer==null||searchText==null)return false;
            return searchText.equalsIgnoreCase(customer.name())
                    || searchText.equalsIgnoreCase(customer.accountNumber())
                    || searchText.equalsIgnoreCase(customer.toString());
        }

        private void showCustomerPopupIfUseful() {
            if (!customerBox.isShowing() || customerBox.getItemCount() <= 0) {
                return;
            }
            Component focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
            Component editor = customerBox.getEditor().getEditorComponent();
            if (focusOwner != customerBox && focusOwner != editor) {
                return;
            }
            SwingUtilities.invokeLater(() -> {
                if (customerBox.isShowing() && customerBox.getItemCount() > 0) {
                    customerBox.showPopup();
                }
            });
        }

        private String editorText(JComboBox<?> comboBox) {
            Object item = comboBox.getEditor().getItem();
            return item == null ? "" : String.valueOf(item);
        }
    }

    /** Item edits preview against a copy of the draft; cancel never changes its lines. */
    private static final class ItemDocumentPreview extends JPanel {
        private final LetterDocumentView view = new LetterDocumentView();
        private final JComboBox<String> template = new JComboBox<>(new String[]{"Grid", "Compact"});
        private final Timer timer;
        private long generation;
        ItemDocumentPreview(JDialog dialog, QuotationEditor source,
                java.util.function.Supplier<QuotationInvoiceService.QuotationLineInput> candidate, int row) {
            super(new BorderLayout(4, 4));
            setBorder(new EmptyBorder(16, 16, 16, 16));
            setBackground(DeckersPalette.surface()); putClientProperty("SmartStock.preserveBackground", true);
            JPanel toolbar = DocumentStudio.surface(new ui.components.WrappingToolbarLayout());
            toolbar.add(DocumentStudio.templateSwitch(template));
            add(toolbar, BorderLayout.NORTH); add(view, BorderLayout.CENTER);
            timer = new Timer(500, e -> {
                long request = generation;
                try {
                    var customer = source.selectedCustomer();
                    if (customer == null) { view.showMessage("Choose a customer in the quotation first"); return; }
                    List<QuotationInvoiceService.QuotationLineInput> lines = new ArrayList<>(source.draftLines());
                    var proposed = candidate.get();
                    if (row >= 0 && row < lines.size()) lines.set(row, proposed); else lines.add(proposed);
                    LocalDate valid = source.validUntilField.getText().isBlank() ? null : LocalDate.parse(source.validUntilField.getText().trim());
                    LocalDate due = source.productionDueDateField.getText().isBlank() ? null : LocalDate.parse(source.productionDueDateField.getText().trim());
                    boolean compact = template.getSelectedIndex() == 1;
                    boolean discount = source.applyCustomerDiscountBox.isSelected();
                    UiTaskRunner.submit(dialog, "quotation.item-preview", () -> QuotationInvoiceViewService.previewDraft(
                            customer.customerId(), valid, due, lines, discount, compact),
                            preview -> { if (request == generation) view.setDocument(preview.text()); },
                            error -> { if (request == generation) view.showMessage("Preview unavailable. Check entries or reconnect."); });
                } catch (RuntimeException error) { view.showMessage("Complete the item details to preview"); }
            });
            timer.setRepeats(false);
            template.addActionListener(e -> changed());
            dialog.addWindowListener(new java.awt.event.WindowAdapter() {
                @Override public void windowClosed(java.awt.event.WindowEvent e) { generation++; timer.stop(); }
            });
            changed();
        }
        void changed() { generation++; view.showMessage("Updating item preview…"); timer.restart(); }
        void watch(Component component) {
            if (component instanceof JTextComponent text) text.getDocument().addDocumentListener(new DocumentListener() {
                public void insertUpdate(DocumentEvent e) { changed(); }
                public void removeUpdate(DocumentEvent e) { changed(); }
                public void changedUpdate(DocumentEvent e) { changed(); }
            });
            if (component instanceof JComboBox<?> combo) combo.addActionListener(e -> changed());
            if (component instanceof Container container)
                for (Component child : container.getComponents()) watch(child);
        }
    }

    private static class LineEditor extends JDialog {
        private final JComboBox<QuotationInvoiceViewService.ProductOption> productBox = new JComboBox<>();
        private final JTextField itemField = new JTextField();
        private final JTextField skuField = new JTextField();
        private final JTextField qtyField = new JTextField("1");
        private final JTextField unitField = new JTextField("0");
        private final JTextField discountField = new JTextField("0");
        private final JTextField discountedPriceField = new JTextField("0");
        private ui.helpers.DiscountPriceFields discountPrices;
        private final JComboBox<String> deliveryBox = new JComboBox<>(new String[]{"PICKUP", "LOCAL_DELIVERY", "SHIP", "INSTALLATION"});
        private final JTextField notesField = new JTextField();
        private final Timer productSearchTimer;
        private final boolean manualOnly;
        private boolean updatingProductResults;
        private LineInput line;

        LineEditor(JDialog owner) {
            this(owner, null, true, -1);
        }

        LineEditor(JDialog owner, LineInput existingLine) {
            this(owner, existingLine, false, -1);
        }

        private LineEditor(JDialog owner, LineInput existingLine, boolean manualOnly, int row) {
            super(owner, "Add Quotation Line", true);
            this.manualOnly = manualOnly;
            productSearchTimer = new Timer(300, e -> refreshProductResults(editorText(productBox)));
            productSearchTimer.setRepeats(false);
            ui.components.DocumentWorkspace.fitDialog(this, 1440, 850);
            setLocationRelativeTo(owner);
            setLayout(new BorderLayout(8, 8));
            productBox.setEditable(true);
            productBox.setMaximumRowCount(12);
            if (!manualOnly) {
                loadProducts("");
                installProductSearch();
            }
            if (existingLine != null) {
                loadExistingLine(existingLine);
            }
            discountPrices=new ui.helpers.DiscountPriceFields(()->parseMoney(unitField.getText()),discountField,discountedPriceField);
            ui.helpers.DiscountPriceFields.listen(unitField,discountPrices::refresh);
            productBox.addActionListener(e -> fillProduct());
            JPanel itemFields = DocumentStudio.stack(
                    DocumentStudio.row(DocumentStudio.field("Item name", itemField), DocumentStudio.field("SKU", skuField)),
                    DocumentStudio.row(DocumentStudio.field("Quantity", qtyField), DocumentStudio.field("Rate / unit price", unitField), DocumentStudio.field("Discount %", discountField)),
                    DocumentStudio.row(DocumentStudio.field("Discounted unit price", discountedPriceField), DocumentStudio.field("Delivery", deliveryBox)),
                    DocumentStudio.field("Notes", notesField));
            JPanel panel = manualOnly ? DocumentStudio.stack(DocumentStudio.workflow(0), DocumentStudio.section("Manual item", itemFields))
                    : DocumentStudio.stack(DocumentStudio.workflow(0), DocumentStudio.field("Stock product", productBox), DocumentStudio.section("Item details", itemFields));
            panel.setBorder(new EmptyBorder(22, 22, 22, 22));
            add(panel, BorderLayout.CENTER);
            JPanel buttons = DocumentStudio.surface(new FlowLayout(FlowLayout.RIGHT, 10, 14));
            buttons.setBorder(new EmptyBorder(0, 22, 0, 22));
            JButton add = new JButton("Save item");
            JButton cancel = new JButton("Cancel");
            buttons.add(add);
            buttons.add(cancel);
            DocumentStudio.button(add, true);
            DocumentStudio.button(cancel, false);
            add(buttons, BorderLayout.SOUTH);
            add.addActionListener(e -> save());
            cancel.addActionListener(e -> dispose());
            if (owner instanceof QuotationEditor source) {
                ItemDocumentPreview preview = new ItemDocumentPreview(this, source, () -> {
                    var product = productBox.getSelectedItem() instanceof QuotationInvoiceViewService.ProductOption p ? p : null;
                    BigDecimal unit = parseMoney(unitField.getText());
                    return new QuotationInvoiceService.QuotationLineInput(product == null ? null : product.productId(),
                            itemField.getText(), skuField.getText(), Integer.parseInt(qtyField.getText().trim()),
                            unit, unit, parsePercent(discountField.getText()), String.valueOf(deliveryBox.getSelectedItem()),
                            notesField.getText(), null, null, null, null, null);
                }, row);
                preview.watch(panel);
                JPanel fields = DocumentStudio.surface(new BorderLayout());
                fields.add(DocumentStudio.scroll(DocumentStudio.topAligned(panel)), BorderLayout.CENTER); fields.add(buttons, BorderLayout.SOUTH);
                add(DocumentStudio.shell(new ui.components.DocumentWorkspace(fields, preview), 1, null, this::dispose, null, null), BorderLayout.CENTER);
                remove(buttons);
            }
            ThemeManager.applyToWindow(this);
        }

        private void loadExistingLine(LineInput existingLine) {
            line = existingLine;
            updatingProductResults = true;
            if (existingLine.product() != null) {
                productBox.addItem(existingLine.product());
                productBox.setSelectedItem(existingLine.product());
            } else {
                productBox.getEditor().setItem("Manual line");
            }
            itemField.setText(existingLine.itemName());
            skuField.setText(existingLine.sku());
            qtyField.setText(String.valueOf(existingLine.quantity()));
            unitField.setText(existingLine.unitPrice().toPlainString());
            discountField.setText(existingLine.discountPercent().stripTrailingZeros().toPlainString());
            deliveryBox.setSelectedItem(existingLine.deliveryMethod());
            notesField.setText(existingLine.notes());
            updatingProductResults = false;
        }

        private void save() {
            try {
                QuotationInvoiceViewService.ProductOption product = manualOnly ? null : selectedCatalogProduct();
                if (!manualOnly && product == null) {
                    throw new IllegalArgumentException("Select a catalog product from the search results. Use Add Line for a manual item.");
                }
                String itemName = itemField.getText().trim();
                if (itemName.isBlank()) {
                    throw new IllegalArgumentException("Item name is required.");
                }
                BigDecimal enteredPrice = parseMoney(unitField.getText());
                BigDecimal originalUnitPrice = product == null || product.price() == null ? enteredPrice : product.price();
                String overrideReason = existingOverrideReason();
                Integer overrideByUserId = existingOverrideByUserId();
                String overrideByName = existingOverrideByName();
                String overrideApprovalToken = existingOverrideApprovalToken();
                boolean priceOverridden = product != null && enteredPrice.compareTo(originalUnitPrice) != 0;
                if (priceOverridden && !canChangeSaleItemPrice()) {
                    boolean existingApprovalStillMatches = line != null
                            && line.priceOverrideReason() != null
                            && line.unitPrice().compareTo(enteredPrice) == 0
                            && line.originalUnitPrice().compareTo(originalUnitPrice) == 0;
                    if (!existingApprovalStillMatches) {
                        ManagerApprovalService.ApprovalResult approval = ManagerApprovalService.requestApproval(
                                this,
                                CHANGE_SALE_ITEM_PRICE_PERMISSION,
                                "Quotation Price Override",
                                "Reason for price change on " + itemName + ":"
                        );
                        if (approval == null) {
                            return;
                        }
                        overrideReason = approval.reason();
                        overrideByUserId = approval.approvedByUserId();
                        overrideByName = approval.approvedByName();
                        overrideApprovalToken = approval.lanApprovalToken();
                    }
                } else if (priceOverridden && canChangeSaleItemPrice()) {
                    boolean existingApprovalStillMatches = line != null
                            && line.priceOverrideReason() != null
                            && line.unitPrice().compareTo(enteredPrice) == 0
                            && line.originalUnitPrice().compareTo(originalUnitPrice) == 0;
                    if (!existingApprovalStillMatches) {
                        overrideReason = null;
                        overrideByUserId = null;
                        overrideByName = null;
                        overrideApprovalToken = null;
                    }
                } else if (!priceOverridden) {
                    overrideReason = null;
                    overrideByUserId = null;
                    overrideByName = null;
                    overrideApprovalToken = null;
                }
                QuotationInvoiceService.CustomLineInput existingCustom=line==null?null:line.custom();
                line = new LineInput(product, itemName, skuField.getText(), Integer.parseInt(qtyField.getText().trim()),
                        enteredPrice, originalUnitPrice, discountPrices.validatedPercent(),
                        String.valueOf(deliveryBox.getSelectedItem()), notesField.getText(),
                        overrideReason, overrideByUserId, overrideByName, overrideApprovalToken,existingCustom);
                dispose();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, ex.getMessage(), "Line", JOptionPane.ERROR_MESSAGE);
            }
        }

        private QuotationInvoiceViewService.ProductOption selectedCatalogProduct() {
            Object selected = productBox.getSelectedItem();
            if (selected instanceof QuotationInvoiceViewService.ProductOption option) return option;
            String entered = editorText(productBox).trim();
            for (int i = 0; i < productBox.getItemCount(); i++) {
                QuotationInvoiceViewService.ProductOption candidate = productBox.getItemAt(i);
                if (candidate.productId() != null && exactProductMatch(candidate, entered)) return candidate;
            }
            return null;
        }

        private String existingOverrideReason() {
            return line == null ? null : line.priceOverrideReason();
        }

        private Integer existingOverrideByUserId() {
            return line == null ? null : line.priceOverrideByUserId();
        }

        private String existingOverrideByName() {
            return line == null ? null : line.priceOverrideByName();
        }

        private String existingOverrideApprovalToken() {
            return line == null ? null : line.priceOverrideApprovalToken();
        }

        private void fillProduct() {
            if (updatingProductResults) {
                return;
            }
            Object selected = productBox.getSelectedItem();
            if (!(selected instanceof QuotationInvoiceViewService.ProductOption product)) {
                return;
            }
            if (product == null || product.productId() == null) return;
            itemField.setText(product.name());
            skuField.setText(product.sku());
            unitField.setText(product.price().toPlainString());
        }

        private void loadProducts(String searchText) {
            refreshProductResults(searchText);
        }

        private void installProductSearch() {
            Component editor = productBox.getEditor().getEditorComponent();
            if (editor instanceof JTextComponent textComponent) {
                styleReadableControl(textComponent);
                textComponent.getDocument().addDocumentListener(new DocumentListener() {
                    @Override
                    public void insertUpdate(DocumentEvent e) {
                        scheduleSearch();
                    }

                    @Override
                    public void removeUpdate(DocumentEvent e) {
                        scheduleSearch();
                    }

                    @Override
                    public void changedUpdate(DocumentEvent e) {
                        scheduleSearch();
                    }
                });
                if (textComponent instanceof JTextField textField) {
                    textField.addActionListener(e -> selectBestProductMatch(editorText(productBox)));
                    textField.addFocusListener(new java.awt.event.FocusAdapter() {
                        @Override
                        public void focusGained(java.awt.event.FocusEvent e) {
                            showProductPopupIfUseful();
                        }
                    });
                }
            }
        }

        private void scheduleSearch() {
            if (!updatingProductResults) {
                productSearchTimer.restart();
            }
        }

        private void refreshProductResults(String searchText) {
            String query=searchText==null?"":searchText;
            UiTaskRunner.submit(this,"quotation.products",()->QuotationInvoiceViewService.searchProducts(query),products->{
                updatingProductResults = true;
                productBox.removeAllItems();
                for (QuotationInvoiceViewService.ProductOption product : products) {
                    productBox.addItem(product);
                }
                productBox.getEditor().setItem(query);
                updatingProductResults = false;
                selectExactProductMatch(query, products);
                showProductPopupIfUseful();
            },ex->{updatingProductResults=false;});
        }

        private void selectBestProductMatch(String searchText) {
            List<QuotationInvoiceViewService.ProductOption> current=new ArrayList<>();for(int i=0;i<productBox.getItemCount();i++)current.add(productBox.getItemAt(i));selectBestProductMatch(searchText,current);
        }

        private void selectBestProductMatch(String searchText, List<QuotationInvoiceViewService.ProductOption> products) {
            selectBestProductMatch(searchText, products, true);
        }

        private void selectExactProductMatch(String searchText, List<QuotationInvoiceViewService.ProductOption> products) {
            selectBestProductMatch(searchText, products, false);
        }

        private void selectBestProductMatch(String searchText, List<QuotationInvoiceViewService.ProductOption> products, boolean allowSingleNumericMatch) {
            String search = searchText == null ? "" : searchText.trim();
            if (search.isBlank()) {
                return;
            }
            QuotationInvoiceViewService.ProductOption match = null;
            for (QuotationInvoiceViewService.ProductOption product : products) {
                if (product.productId() != null && exactProductMatch(product, search)) {
                    match = product;
                    break;
                }
            }
            if (match == null && allowSingleNumericMatch && search.matches("\\d{4,}") && products.size() == 2) {
                match = products.get(1);
            }
            if (match != null) {
                updatingProductResults = true;
                productBox.setSelectedItem(match);
                updatingProductResults = false;
                fillProduct();
            }
        }

        private boolean exactProductMatch(QuotationInvoiceViewService.ProductOption product, String searchText) {
            return searchText.equalsIgnoreCase(product.name())
                    || searchText.equalsIgnoreCase(product.sku())
                    || searchText.equalsIgnoreCase(product.barcode())
                    || searchText.equalsIgnoreCase(product.description());
        }

        private void showProductPopupIfUseful() {
            if (!productBox.isShowing() || productBox.getItemCount() <= 0) {
                return;
            }
            Component focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
            Component editor = productBox.getEditor().getEditorComponent();
            if (focusOwner != productBox && focusOwner != editor) {
                return;
            }
            SwingUtilities.invokeLater(() -> {
                if (productBox.isShowing() && productBox.getItemCount() > 0) {
                    productBox.showPopup();
                }
            });
        }

        private String editorText(JComboBox<?> comboBox) {
            Object item = comboBox.getEditor().getItem();
            return item == null ? "" : String.valueOf(item);
        }
    }

    static BigDecimal customQuotationCatalogPrice(CustomOrderDataService.CustomItemOption item,
                                                   CustomOrderDataService.VariantOption variant) {
        if (item == null) return null;
        if ("AREA".equals(item.pricingType())) return item.areaPrice();
        return variant != null && variant.fixedPrice() != null ? variant.fixedPrice() : item.fixedPrice();
    }
    private static class CustomQuotationLineEditor extends JDialog{
        private final JComboBox<CustomOrderDataService.CustomItemOption>itemBox=new JComboBox<>();
        private final JComboBox<CustomOrderDataService.VariantOption>variantBox=new JComboBox<>();
        private final JTextField qty=new JTextField("1"),price=new JTextField("0"),width=new JTextField(),length=new JTextField(),discount=new JTextField("0"),design=new JTextField();
        private final JTextArea instructions=new JTextArea(2,20);
        private final JTextField itemSearch=new JTextField();
        private final JTextField discountedPrice=new JTextField("0");
        private ui.helpers.DiscountPriceFields discountPrices;
        private final JComboBox<String>delivery=new JComboBox<>(new String[]{"PICKUP","LOCAL_DELIVERY","SHIP","INSTALLATION"});
        private final DefaultTableModel addons=new DefaultTableModel(new String[]{"Material ID","Material","Preset ID","Size","Mode","Description","Lines","Charge"},0);
        private LineInput line;
        CustomQuotationLineEditor(JDialog owner){this(owner,null);}
        CustomQuotationLineEditor(JDialog owner,LineInput existing){this(owner,existing,-1);}
        CustomQuotationLineEditor(JDialog owner,LineInput existing,int row){this(owner,existing,row,null);}
        CustomQuotationLineEditor(JDialog owner,LineInput existing,int row,List<CustomOrderDataService.CustomItemOption> catalog){super(owner,existing==null?"Add Custom Item to Quotation":"Edit Quotation Custom Item",true);ui.components.DocumentWorkspace.fitDialog(this,900,780);setLayout(new BorderLayout(8,8));
            JPanel areaFields = DocumentStudio.row(DocumentStudio.field("Width", width), DocumentStudio.field("Length", length));
            areaFields.setVisible(false);
            itemBox.addItemListener(e -> {
                var selected = itemBox.getSelectedItem();
                areaFields.setVisible(selected instanceof CustomOrderDataService.CustomItemOption item && "AREA".equals(item.pricingType()));
            });
            JPanel extraPricing = DocumentStudio.stack(DocumentStudio.row(DocumentStudio.field("Discounted unit price", discountedPrice), DocumentStudio.field("Delivery", delivery)), areaFields);
            extraPricing.setVisible(false);
            JButton priceOptions = new JButton("Pricing & delivery options"); DocumentStudio.button(priceOptions, false);
            priceOptions.addActionListener(e -> { extraPricing.setVisible(!extraPricing.isVisible()); revalidate(); });
            JButton findItem = new JButton("Find / scan item"); DocumentStudio.button(findItem, false);
            JButton manualItem = new JButton("Manual Item…"); DocumentStudio.button(manualItem, false);
            manualItem.addActionListener(e -> enterManualItem());
            JPanel scan = DocumentStudio.field("Name, SKU or barcode", itemSearch);
            scan.setVisible(false);
            findItem.addActionListener(e -> { scan.setVisible(!scan.isVisible()); revalidate(); if (scan.isVisible()) itemSearch.requestFocusInWindow(); });
            JPanel form = DocumentStudio.stack(DocumentStudio.workflow(0), scan,
                    DocumentStudio.section("Item", DocumentStudio.stack(
                            DocumentStudio.row(DocumentStudio.field("Item name", itemBox), DocumentStudio.field("Variant", variantBox)),
                            DocumentStudio.row(DocumentStudio.field("Quantity", qty), DocumentStudio.field("Rate / unit price", price), DocumentStudio.field("Discount %", discount)),
                            DocumentStudio.row(DocumentStudio.inline(findItem), DocumentStudio.inline(manualItem), DocumentStudio.inline(priceOptions)), extraPricing)));
            itemBox.addItemListener(e -> {
                if (itemBox.getSelectedItem() instanceof CustomOrderDataService.CustomItemOption item && "AREA".equals(item.pricingType())) extraPricing.setVisible(true);
            });
            JTable addonTable = new JTable(addons); hideAddonIds(addonTable);
            int mode = addonTable.convertColumnIndexToView(4); if (mode >= 0) addonTable.removeColumn(addonTable.getColumnModel().getColumn(mode));
            DocumentStudio.table(addonTable);
            JScrollPane addonRows = DocumentStudio.scroll(addonTable); addonRows.setColumnHeaderView(addonTable.getTableHeader());
            addonRows.setPreferredSize(new Dimension(500, 90));
            addonRows.setVisible(addons.getRowCount() > 0);
            addons.addTableModelListener(e -> { addonRows.setVisible(addons.getRowCount() > 0); revalidate(); });
            JButton addAddon = new JButton("Add print option"), remove = new JButton("Remove");
            DocumentStudio.button(addAddon, false); DocumentStudio.button(remove, false);
            JPanel addonActions = DocumentStudio.surface(new FlowLayout(FlowLayout.LEFT, 8, 0)); addonActions.add(addAddon); addonActions.add(remove);
            JButton printOptions = new JButton("Choose print option…"); DocumentStudio.button(printOptions, false);
            addons.addTableModelListener(e -> {
                printOptions.setText(addons.getRowCount() == 0 ? "Choose print option…" : addons.getRowCount() == 1
                        ? String.valueOf(addons.getValueAt(0, 1)) + " / " + addons.getValueAt(0, 3)
                        : addons.getRowCount() + " print options");
            });
            printOptions.addActionListener(e -> {
                JDialog options = new JDialog(this, "Print options", true);
                JPanel optionContent = DocumentStudio.surface(new BorderLayout(10, 14));
                optionContent.setBorder(new EmptyBorder(18, 18, 18, 18));
                addonRows.setVisible(true); optionContent.add(addonRows, BorderLayout.CENTER);
                JButton done = new JButton("Done"); DocumentStudio.button(done, true);
                JPanel optionButtons = DocumentStudio.surface(new BorderLayout());
                optionButtons.add(addonActions, BorderLayout.WEST); optionButtons.add(done, BorderLayout.EAST);
                done.addActionListener(event -> {
                    if (addonTable.isEditing() && !addonTable.getCellEditor().stopCellEditing()) return;
                    options.dispose();
                });
                optionContent.add(optionButtons, BorderLayout.SOUTH); options.add(optionContent);
                ui.components.DocumentWorkspace.fitDialog(options, 760, 380); DocumentStudio.finish(optionContent); options.setVisible(true);
            });
            JPanel printSection = DocumentStudio.section("Print add-on", DocumentStudio.row(
                    DocumentStudio.field("Print option", printOptions), DocumentStudio.field("Design / placement", design)));
            JTextField productionDate = new JTextField(owner instanceof QuotationEditor source
                    ? source.productionDueDateField.getText() : "");
            productionDate.setEditable(false);
            productionDate.setToolTipText("Set the production due date in the quotation.");
            JPanel center = DocumentStudio.stack(form, printSection, DocumentStudio.field("Instructions", DocumentStudio.scroll(instructions)),
                    DocumentStudio.field("Production due date · from quotation", productionDate));
            center.setBorder(new EmptyBorder(22, 22, 10, 22));
            JScrollPane formScroll = DocumentStudio.scroll(DocumentStudio.topAligned(center));
            JButton save = new JButton("Save item"), cancel = new JButton("Cancel");
            JPanel buttons = DocumentStudio.surface(new FlowLayout(FlowLayout.RIGHT, 10, 14));
            buttons.setBorder(new EmptyBorder(0, 22, 0, 22));
            DocumentStudio.button(save, true); DocumentStudio.button(cancel, false); buttons.add(cancel); buttons.add(save);
            JPanel standalone = DocumentStudio.surface(new BorderLayout()); standalone.add(formScroll, BorderLayout.CENTER); standalone.add(buttons, BorderLayout.SOUTH);
            add(standalone, BorderLayout.CENTER);
            itemBox.addActionListener(e->loadVariantsAndPrice());variantBox.addActionListener(e->loadSelectedPrice());addAddon.addActionListener(e->addAddon(addAddon));remove.addActionListener(e->{int r=addonTable.getSelectedRow();if(r>=0)addons.removeRow(addonTable.convertRowIndexToModel(r));});save.addActionListener(e->save());cancel.addActionListener(e->dispose());
            itemBox.setRenderer(new DefaultListCellRenderer() {
                @Override public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected, boolean focus) {
                    Object display = value instanceof CustomOrderDataService.CustomItemOption item && index < 0 ? item.name() : value;
                    super.getListCellRendererComponent(list, display, index, selected, focus);
                    setBorder(new EmptyBorder(4, 7, 4, 7)); return this;
                }
            });
            variantBox.setRenderer(new DefaultListCellRenderer() {
                @Override public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected, boolean focus) {
                    var item = (CustomOrderDataService.CustomItemOption)itemBox.getSelectedItem();
                    Object display = value;
                    if (value == null && item != null && !item.hasVariants())
                        display = java.util.stream.Stream.of(item.size(), item.color()).filter(v -> v != null && !v.isBlank()).collect(java.util.stream.Collectors.joining(" / "));
                    super.getListCellRendererComponent(list, display, index, selected, focus);
                    setBorder(new EmptyBorder(4, 7, 4, 7)); return this;
                }
            });
            ThemeManager.applyToWindow(this);
            if (owner instanceof QuotationEditor source) {
                ItemDocumentPreview preview = new ItemDocumentPreview(this, source, () -> {
                    var item = (CustomOrderDataService.CustomItemOption)itemBox.getSelectedItem();
                    if (item == null) throw new IllegalArgumentException("Choose an item");
                    BigDecimal unit = originalDiscountUnitPrice();
                    String detail = "Design: " + design.getText() + " | " + instructions.getText();
                    var variant = (CustomOrderDataService.VariantOption)variantBox.getSelectedItem();
                    if (variant != null) detail += " | Variant: " + variant.name();
                    for (int i = 0; i < addons.getRowCount(); i++)
                        detail += " | Print: " + addons.getValueAt(i, 1) + " / " + addons.getValueAt(i, 3);
                    return new QuotationInvoiceService.QuotationLineInput(null, item.name(), item.sku(),
                            Integer.parseInt(qty.getText().trim()), unit, unit, parsePercent(discount.getText()),
                            String.valueOf(delivery.getSelectedItem()), detail, null, null, null, null, null);
                }, row);
                preview.watch(form); addons.addTableModelListener(e -> preview.changed());
                JPanel fields = DocumentStudio.surface(new BorderLayout());
                fields.add(formScroll, BorderLayout.CENTER); fields.add(buttons, BorderLayout.SOUTH);
                add(DocumentStudio.shell(new ui.components.DocumentWorkspace(fields, preview), 2, null, this::dispose, null, null), BorderLayout.CENTER);
                ui.components.DocumentWorkspace.fitDialog(this, 1440, 900);
            }            itemSearch.setToolTipText("Type a name or SKU, or scan a barcode. Press Enter to select.");
            new ui.screens.customorders.CustomOrderItemSearch(itemSearch,
                    option -> selectSearchItem(option.customItemId(), option.customVariantId()), this::lookupSearchItem);
            addWindowListener(new java.awt.event.WindowAdapter() {
                @Override public void windowOpened(java.awt.event.WindowEvent e) { itemBox.requestFocusInWindow(); }
            });
            try{for(var item:catalog==null?ResponsiveTask.await(this,"Loading custom items...",CustomOrderDataService::listActiveItems):catalog)itemBox.addItem(item);if(existing!=null)loadExisting(existing);installDiscountPrices();}catch(Exception e){JOptionPane.showMessageDialog(this,e.getMessage(),"Custom Items",JOptionPane.ERROR_MESSAGE);dispose();}
        }
        private void enterManualItem() {
            var selected = (CustomOrderDataService.CustomItemOption)itemBox.getSelectedItem();
            boolean manual = selected != null && selected.customItemId() == null;
            JTextField name = new JTextField(manual ? selected.name() : "", 24);
            JTextField size = new JTextField(manual ? selected.size() : "", 16);
            JTextField color = new JTextField(manual ? selected.color() : "", 16);
            JPanel fields = DocumentStudio.stack(DocumentStudio.field("Item name", name),
                    DocumentStudio.row(DocumentStudio.field("Size", size), DocumentStudio.field("Colour", color)));
            while (JOptionPane.showConfirmDialog(this, fields, "Manual Item", JOptionPane.OK_CANCEL_OPTION,
                    JOptionPane.PLAIN_MESSAGE) == JOptionPane.OK_OPTION) {
                if (name.getText().isBlank() || name.getText().trim().length() > 200) {
                    JOptionPane.showMessageDialog(this, "Enter an item name of up to 200 characters.");
                    continue;
                }
                String previousPrice = manual ? price.getText() : "0";
                selectManualItem(name.getText().trim(), size.getText().trim(), color.getText().trim());
                price.setText(previousPrice);
                width.setText(""); length.setText("");
                price.requestFocusInWindow(); price.selectAll();
                return;
            }
        }
        private void selectManualItem(String name, String size, String color) {
            var manual = new CustomOrderDataService.CustomItemOption(null, name, size, color, null,
                    "SERVICE", "FIXED", null, false, null, null, null, null, null);
            itemBox.addItem(manual);
            itemBox.setSelectedItem(manual);
        }
        private void lookupSearchItem() {
            String query = itemSearch.getText().trim();
            if (query.isEmpty()) return;
            UiTaskRunner.submit(this, "quotation.custom-item-lookup",
                    () -> CustomOrderDataService.lookupCustomItem(query),
                    result -> {
                        if (!query.equals(itemSearch.getText().trim())) return;
                        if (result == null || result.customItemId() == null) {
                            JOptionPane.showMessageDialog(this, "No custom item or variant matched that search.");
                            return;
                        }
                        selectSearchItem(result.customItemId(), result.customVariantId());
                    }, failure -> JOptionPane.showMessageDialog(this, failure.getMessage(),
                            "Custom Item Search", JOptionPane.ERROR_MESSAGE));
        }

        private void selectSearchItem(Long itemId, Long variantId) {
            for (int i = 0; i < itemBox.getItemCount(); i++) {
                if (!java.util.Objects.equals(itemBox.getItemAt(i).customItemId(), itemId)) continue;
                itemBox.setSelectedIndex(i);
                if (variantId != null) {
                    for (int v = 0; v < variantBox.getItemCount(); v++) {
                        if (java.util.Objects.equals(variantBox.getItemAt(v).variantId(), variantId)) {
                            variantBox.setSelectedIndex(v);
                            qty.requestFocusInWindow();
                            qty.selectAll();
                            return;
                        }
                    }
                    variantBox.setSelectedIndex(-1);
                    JOptionPane.showMessageDialog(this, "That variant is no longer available. Select an active variant.");
                    return;
                }
                (itemBox.getItemAt(i).hasVariants() ? variantBox : qty).requestFocusInWindow();
                return;
            }
            JOptionPane.showMessageDialog(this, "That item is no longer available. Reopen the picker to refresh the catalog.");
        }

        private void loadExisting(LineInput existing){var c=existing.custom();if(c.customItemId()==null)selectManualItem(existing.itemName(),c.itemSize(),c.itemColor());for(int i=0;i<itemBox.getItemCount();i++)if(c.customItemId()!=null&&java.util.Objects.equals(itemBox.getItemAt(i).customItemId(),c.customItemId())){itemBox.setSelectedIndex(i);break;}loadVariantsAndPrice();for(int i=0;i<variantBox.getItemCount();i++)if(java.util.Objects.equals(variantBox.getItemAt(i).variantId(),c.customVariantId())){variantBox.setSelectedIndex(i);break;}qty.setText(String.valueOf(existing.quantity()));price.setText(c.areaPrice()==null?existing.unitPrice().toPlainString():c.areaPrice().toPlainString());width.setText(c.widthValue()==null?"":c.widthValue().toPlainString());length.setText(c.lengthValue()==null?"":c.lengthValue().toPlainString());discount.setText(existing.discountPercent().toPlainString());delivery.setSelectedItem(existing.deliveryMethod());design.setText(c.customizationDetails());instructions.setText(c.orderInstructions());if(c.printAddons()!=null)for(var a:c.printAddons())addons.addRow(new Object[]{a.printMaterialId(),a.materialName(),a.printSizePresetId(),a.printSizeName(),a.pricingMode(),a.description(),a.lineCount(),a.charge()});}
        private void loadVariantsAndPrice(){CustomOrderDataService.CustomItemOption item=(CustomOrderDataService.CustomItemOption)itemBox.getSelectedItem();variantBox.setEnabled(item!=null&&item.hasVariants());variantBox.removeAllItems();loadSelectedPrice();if(item==null)return;if(item.hasVariants())try{for(var v:ResponsiveTask.await(this,"Loading variants...",()->CustomOrderDataService.listActiveVariants(item.customItemId())))variantBox.addItem(v);}catch(Exception e){JOptionPane.showMessageDialog(this,e.getMessage());}}
        private void loadSelectedPrice(){
            BigDecimal configured=customQuotationCatalogPrice((CustomOrderDataService.CustomItemOption)itemBox.getSelectedItem(),(CustomOrderDataService.VariantOption)variantBox.getSelectedItem());
            price.setText((configured==null?BigDecimal.ZERO:configured).toPlainString());
        }
        private void addAddon(Component anchor){try{List<CustomOrderDataService.PrintMaterialOption>materials=ResponsiveTask.await(anchor,"Loading print materials...",CustomOrderDataService::listActivePrintMaterials);if(materials==null)return;if(materials.isEmpty())throw new IllegalArgumentException("No active print materials are configured.");CustomOrderDataService.PrintMaterialOption material=(CustomOrderDataService.PrintMaterialOption)JOptionPane.showInputDialog(anchor,"Material:","Print Add-on",JOptionPane.PLAIN_MESSAGE,null,materials.toArray(),materials.get(0));if(material==null)return;List<CustomOrderDataService.PrintSizePresetOption>presets=ResponsiveTask.await(anchor,"Loading print sizes...",()->CustomOrderDataService.listActivePrintSizePresets(material.printMaterialId()));if(presets==null)return;CustomOrderDataService.PrintSizePresetOption preset=presets.isEmpty()?null:(CustomOrderDataService.PrintSizePresetOption)JOptionPane.showInputDialog(anchor,"Print size:","Print Add-on",JOptionPane.PLAIN_MESSAGE,null,presets.toArray(),presets.get(0));String description=JOptionPane.showInputDialog(anchor,"Print description:","");if(description==null)return;String chargeText=JOptionPane.showInputDialog(anchor,"Print charge:",preset==null||preset.fixedPrice()==null?"0":preset.fixedPrice().toPlainString());if(chargeText==null)return;addons.addRow(new Object[]{material.printMaterialId(),material.materialName(),preset==null?null:preset.printSizePresetId(),preset==null?"Custom":preset.presetName(),preset==null?"FIXED_PRESET":preset.pricingMode(),description,1,parseMoney(chargeText)});}catch(Exception e){JOptionPane.showMessageDialog(anchor,e.getMessage(),"Print Add-on",JOptionPane.ERROR_MESSAGE);}}
        private BigDecimal originalDiscountUnitPrice() {
            var item=(CustomOrderDataService.CustomItemOption)itemBox.getSelectedItem();
            BigDecimal base=parseMoney(price.getText());
            if(item!=null && "AREA".equals(item.pricingType())) base=base.multiply(new BigDecimal(width.getText().trim())).multiply(new BigDecimal(length.getText().trim())).setScale(2,RoundingMode.HALF_UP);
            for(int row=0;row<addons.getRowCount();row++) base=base.add(parseMoney(String.valueOf(addons.getValueAt(row,7))));
            return base;
        }
        private void installDiscountPrices() {
            discountPrices=new ui.helpers.DiscountPriceFields(this::originalDiscountUnitPrice,discount,discountedPrice);
            for(JTextField field:new JTextField[]{price,width,length}) ui.helpers.DiscountPriceFields.listen(field,discountPrices::refresh);
            addons.addTableModelListener(e->discountPrices.refresh());
        }
        private void save(){try{var item=(CustomOrderDataService.CustomItemOption)itemBox.getSelectedItem();if(item==null)throw new IllegalArgumentException("Select a custom item.");var variant=(CustomOrderDataService.VariantOption)variantBox.getSelectedItem();if(item.hasVariants()&&variant==null)throw new IllegalArgumentException("Select a variant.");int quantity=Integer.parseInt(qty.getText().trim());if(quantity<=0)throw new IllegalArgumentException("Quantity must be greater than zero.");BigDecimal entered=parseMoney(price.getText()),configured=customQuotationCatalogPrice(item,variant);BigDecimal w=width.getText().isBlank()?null:new BigDecimal(width.getText().trim()),l=length.getText().isBlank()?null:new BigDecimal(length.getText().trim()),area=null,base=entered;if("AREA".equals(item.pricingType())){if(w==null||l==null||w.signum()<=0||l.signum()<=0)throw new IllegalArgumentException("Enter valid width and length for area pricing.");area=w.multiply(l);base=area.multiply(entered).setScale(2,RoundingMode.HALF_UP);}List<QuotationInvoiceService.PrintAddonInput>print=new ArrayList<>();BigDecimal addonTotal=BigDecimal.ZERO;for(int r=0;r<addons.getRowCount();r++){BigDecimal charge=parseMoney(String.valueOf(addons.getValueAt(r,7)));addonTotal=addonTotal.add(charge);print.add(new QuotationInvoiceService.PrintAddonInput(((Number)addons.getValueAt(r,0)).longValue(),String.valueOf(addons.getValueAt(r,1)),addons.getValueAt(r,2)==null?null:((Number)addons.getValueAt(r,2)).longValue(),String.valueOf(addons.getValueAt(r,3)),String.valueOf(addons.getValueAt(r,4)),String.valueOf(addons.getValueAt(r,5)),Integer.parseInt(String.valueOf(addons.getValueAt(r,6))),charge));}BigDecimal unit=base.add(addonTotal),pct=discountPrices.validatedPercent();if(pct.signum()<0||pct.compareTo(BigDecimal.valueOf(100))>0)throw new IllegalArgumentException("Discount must be between 0 and 100%.");String overrideReason=null,token=null;if(configured!=null&&entered.compareTo(configured)!=0){overrideReason=JOptionPane.showInputDialog(this,"Reason for custom-item price override:");if(overrideReason==null||overrideReason.isBlank())return;if(!PermissionManager.hasPermission("CUSTOM_ORDER_PRICE_OVERRIDE")&&!PermissionManager.hasPermission("CUSTOM_ORDER_OVERRIDES")){var approval=ManagerApprovalService.requestApproval(this,"CUSTOM_ORDER_PRICE_OVERRIDE","Custom Order Price Override","Reason for custom-item price override:");if(approval==null)return;token=approval.lanApprovalToken();}}var custom=new QuotationInvoiceService.CustomLineInput(item.customItemId(),variant==null?null:variant.variantId(),variant==null?null:variant.name(),item.pricingType(),w,l,item.dimensionUnit(),area,item.areaPriceUnit(),entered,design.getText().trim(),instructions.getText().trim(),print,variant==null?item.size():variant.effectiveSize(),variant==null?item.color():variant.effectiveColor());String notesText=condensed(custom);line=new LineInput(null,item.name(),item.sku(),quantity,unit,unit,pct,String.valueOf(delivery.getSelectedItem()),notesText,overrideReason,null,null,token,custom);dispose();}catch(Exception e){JOptionPane.showMessageDialog(this,e.getMessage(),"Custom Item",JOptionPane.ERROR_MESSAGE);}}
        private static String condensed(QuotationInvoiceService.CustomLineInput c){List<String>parts=new ArrayList<>();if(c.variantName()!=null&&!c.variantName().isBlank())parts.add("Variant: "+c.variantName());if(c.itemSize()!=null&&!c.itemSize().isBlank())parts.add("Item size: "+c.itemSize());if(c.itemColor()!=null&&!c.itemColor().isBlank())parts.add("Color: "+c.itemColor());if(c.widthValue()!=null&&c.lengthValue()!=null)parts.add("Dimensions: "+c.widthValue()+" x "+c.lengthValue()+" "+c.dimensionUnit());if(c.customizationDetails()!=null&&!c.customizationDetails().isBlank())parts.add("Design: "+c.customizationDetails());if(c.orderInstructions()!=null&&!c.orderInstructions().isBlank())parts.add(c.orderInstructions());if(c.printAddons()!=null&&!c.printAddons().isEmpty())parts.add("Print: "+c.printAddons().stream().map(a->a.materialName()+" / "+a.printSizeName()).reduce((a,b)->a+", "+b).orElse(""));return String.join(" | ",parts);}
        private static void hideAddonIds(JTable t){t.removeColumn(t.getColumnModel().getColumn(2));t.removeColumn(t.getColumnModel().getColumn(0));}
    }

    private record LineInput(QuotationInvoiceViewService.ProductOption product, String itemName, String sku, int quantity,
                             BigDecimal unitPrice, BigDecimal originalUnitPrice, BigDecimal discountPercent,
                             String deliveryMethod, String notes, String priceOverrideReason,
                             Integer priceOverrideByUserId, String priceOverrideByName,
                             String priceOverrideApprovalToken,QuotationInvoiceService.CustomLineInput custom) {
    }

    record PaymentInput(BigDecimal amount, String method, String reference) {
    }
}
