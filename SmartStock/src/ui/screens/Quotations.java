package ui.screens;

import managers.PermissionManager;
import services.ManagerApprovalService;
import services.QuotationInvoiceService;
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
    private static final Color INPUT_SELECTION = new Color(37, 99, 235);
    private static final Color BUTTON_PRIMARY = new Color(37, 99, 235);
    private static final Color BUTTON_SECONDARY = new Color(75, 85, 99);
    private final DefaultTableModel quotationModel = readOnlyModel("ID", "Quotation #", "Customer", "Status", "Valid Until", "Total");
    private final JTable quotationTable = new JTable(quotationModel);
    private final LoadingStatePanel loadingState = new LoadingStatePanel();

    public Quotations() {
        setTitle("Quotations");
        setSize(1120, 720);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setJMenuBar(AppMenuBar.create(this, "Quotations"));

        JPanel mainPanel = new JPanel(new BorderLayout(12, 12));
        mainPanel.setBorder(new EmptyBorder(14, 14, 14, 14));
        add(mainPanel, BorderLayout.CENTER);

        JLabel title = new JLabel("Quotations");
        title.setFont(new Font("SansSerif", Font.BOLD, 26));
        mainPanel.add(title, BorderLayout.NORTH);
        mainPanel.add(tablePanel(quotationTable, quotationButtons()), BorderLayout.CENTER);
        mainPanel.add(loadingState, BorderLayout.SOUTH);

        refreshQuotations();
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
        stylePrimaryButton(newQuotation);
        styleSecondaryButton(editDraft);
        styleSecondaryButton(issue);
        styleSecondaryButton(accept);
        styleSecondaryButton(preview);
        styleSecondaryButton(refresh);
        newQuotation.addActionListener(e -> openQuotationDialog());
        editDraft.addActionListener(e -> editSelectedDraftQuotation());
        issue.addActionListener(e -> issueSelectedQuotation());
        accept.addActionListener(e -> acceptSelectedQuotation());
        preview.addActionListener(e -> previewQuotation());
        refresh.addActionListener(e -> refreshQuotations());
        return panel;
    }

    private JPanel tablePanel(JTable table, JPanel buttons) {
        table.setAutoCreateRowSorter(true);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        styleTable(table);
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.add(buttons, BorderLayout.NORTH);
        panel.add(new JScrollPane(table), BorderLayout.CENTER);
        return panel;
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

    void editSelectedDraftQuotation() {
        Long quotationId = selectedId(quotationTable);
        if (quotationId == null) return;
        try {
            QuotationInvoiceViewService.QuotationEditData quotation = ResponsiveTask.await(this,
                    "Loading quotation...", () -> QuotationInvoiceViewService.loadQuotationForEdit(quotationId));
            if (quotation == null) return;
            if (!"DRAFT".equals(quotation.status())) {
                JOptionPane.showMessageDialog(this, "Only draft quotations can be edited.", "Quotations", JOptionPane.WARNING_MESSAGE);
                return;
            }
            QuotationEditor editor = new QuotationEditor(this, quotation);
            editor.setVisible(true);
            if (editor.created) {
                refreshQuotations();
            }
        } catch (Exception ex) {
            showError("Failed to load draft quotation", ex);
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
                CreditOverride creditOverride = creditOverride(this, updated, BigDecimal.ZERO);
                if (creditOverride == null) return;
                ResponsiveTask.await(this, "Charging remaining balance to account...", () -> {
                    QuotationInvoiceService.chargeInvoiceToAccount(invoiceId,
                            "Remaining balance from accepted quotation.",
                            creditOverride.approvalToken(), creditOverride.reason());
                    return Boolean.TRUE;
                });
            }
        } catch (Exception ex) {
            showError("Failed to place remaining balance on customer account", ex);
        }
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
        Object value = table.getValueAt(row, 0);
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(String.valueOf(value));
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
        styleButton(button, BUTTON_PRIMARY, Color.WHITE);
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
        table.setForeground(Color.WHITE);
        table.setBackground(new Color(31, 31, 31));
        table.setSelectionForeground(Color.WHITE);
        table.setSelectionBackground(INPUT_SELECTION);
        table.setGridColor(new Color(85, 85, 85));
        table.getTableHeader().setForeground(Color.WHITE);
        table.getTableHeader().setBackground(new Color(38, 38, 38));
        table.getTableHeader().setOpaque(true);
    }

    static BigDecimal parseMoney(String value) {
        String clean = value == null ? "" : value.replace("$", "").replace(",", "").trim();
        return clean.isBlank() || "null".equalsIgnoreCase(clean)
                ? BigDecimal.ZERO
                : utils.CurrencyFormatter.normalize(new BigDecimal(clean));
    }

    private static boolean canChangeSaleItemPrice() {
        return PermissionManager.hasPermission(CHANGE_SALE_ITEM_PRICE_PERMISSION);
    }

    private static void hideInternalLineColumns(JTable table) {
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
        private PaymentInput paymentInput = new PaymentInput(BigDecimal.ZERO, "CASH", "");

        PaymentPrompt(JFrame owner, QuotationInvoiceViewService.InvoiceFinancials financials) {
            super(owner, "Accepted Quotation Payment", true);
            setSize(520, 280);
            setLocationRelativeTo(owner);
            setLayout(new BorderLayout(8, 8));
            amountField.setText(financials.balanceDue().toPlainString());
            JPanel main = new JPanel(new BorderLayout(8, 8));
            main.setBorder(new EmptyBorder(12, 12, 12, 12));
            add(main, BorderLayout.CENTER);
            JLabel summary = new JLabel("<html>Invoice " + financials.invoiceNumber()
                    + "<br>Total: " + financials.totalAmount()
                    + "<br>Balance: " + financials.balanceDue()
                    + "<br>Any unpaid balance will be placed on the customer account.</html>");
            main.add(summary, BorderLayout.NORTH);
            main.add(formPanel(new String[]{"Amount Paid Now", "Method", "Reference"}, new JComponent[]{amountField, methodBox, referenceField}), BorderLayout.CENTER);
            JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
            JButton continueButton = new JButton("Continue");
            buttons.add(continueButton);
            stylePrimaryButton(continueButton);
            main.add(buttons, BorderLayout.SOUTH);
            continueButton.addActionListener(e -> save());
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
        private JButton createButton;
        private JButton cancelButton;
        private boolean updatingCustomerResults;
        private long customerSearchGeneration;
        private QuotationInvoiceViewService.CustomerOption rememberedCustomer;
        boolean created;

        QuotationEditor(JFrame owner) {
            this(owner, null);
        }

        QuotationEditor(JFrame owner, QuotationInvoiceViewService.QuotationEditData editData) {
            super(owner, editData == null ? "New Quotation" : "Edit Draft Quotation " + editData.quotationNumber(), true);
            this.editQuotationId = editData == null ? null : editData.quotationId();
            this.editQuotationNumber = editData == null ? null : editData.quotationNumber();
            this.customerSearchTimer = new Timer(300, e -> refreshCustomerResults(editorText(customerBox)));
            customerSearchTimer.setRepeats(false);
            this.catalogSearchTimer = new Timer(250, e -> refreshCatalogSearch());
            catalogSearchTimer.setRepeats(false);
            setSize(860, 620);
            setLocationRelativeTo(owner);
            setLayout(new BorderLayout(8, 8));
            JPanel main = new JPanel(new BorderLayout(8, 8));
            main.setBorder(new EmptyBorder(12, 12, 12, 12));
            add(main, BorderLayout.CENTER);
            customerBox.setEditable(true);
            customerBox.setMaximumRowCount(12);
            loadCustomers();
            installCustomerSearch();
            if (editData != null) {
                loadExistingQuotation(editData);
            }
            productionDueDateField.setToolTipText("Optional; use YYYY-MM-DD.");
            main.add(formPanel(new String[]{"Customer", "Valid Until", "Production Due Date", "Notes"}, new JComponent[]{customerBox, validUntilField, productionDueDateField, new JScrollPane(notesArea)}), BorderLayout.NORTH);
            JTable lineTable = new JTable(lineModel);
            styleTable(lineTable);
            hideInternalLineColumns(lineTable);
            JPanel lineArea = new JPanel(new BorderLayout(8, 8));
            lineArea.add(catalogSearchPanel(), BorderLayout.NORTH);
            lineArea.add(new JScrollPane(lineTable), BorderLayout.CENTER);
            main.add(lineArea, BorderLayout.CENTER);
            JPanel southPanel = new JPanel(new BorderLayout(8, 8));
            statusLabel.setForeground(new Color(248, 113, 113));
            statusLabel.setFont(statusLabel.getFont().deriveFont(Font.BOLD, 13f));
            southPanel.add(statusLabel, BorderLayout.WEST);
            JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
            JButton addLine = new JButton("Add Line");
            JButton addCustomLine = new JButton("Add Custom Item");
            JButton editLine = new JButton("Edit Line");
            JButton removeLine = new JButton("Remove Line");
            createButton = new JButton(editData == null ? "Create Quotation" : "Save Draft");
            cancelButton = new JButton("Cancel");
            buttons.add(addLine);
            buttons.add(addCustomLine);
            buttons.add(editLine);
            buttons.add(removeLine);
            buttons.add(createButton);
            buttons.add(cancelButton);
            styleSecondaryButton(addLine);
            styleSecondaryButton(addCustomLine);
            styleSecondaryButton(editLine);
            styleSecondaryButton(removeLine);
            stylePrimaryButton(createButton);
            styleSecondaryButton(cancelButton);
            southPanel.add(buttons, BorderLayout.EAST);
            main.add(southPanel, BorderLayout.SOUTH);
            addLine.addActionListener(e -> addLine());
            addCustomLine.addActionListener(e -> addCustomLine());
            editLine.addActionListener(e -> editSelectedLine(lineTable));
            removeLine.addActionListener(e -> {
                int row = lineTable.getSelectedRow();
                if (row >= 0) lineModel.removeRow(lineTable.convertRowIndexToModel(row));
            });
            createButton.addActionListener(e -> createQuotation());
            cancelButton.addActionListener(e -> dispose());
            ThemeManager.applyToWindow(this);
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
            if(existing.custom()!=null){CustomQuotationLineEditor customEditor=new CustomQuotationLineEditor(this,existing);customEditor.setVisible(true);if(customEditor.line!=null){LineInput changed=customEditor.line;Object[]values={null,changed.itemName(),changed.sku(),changed.quantity(),changed.unitPrice(),changed.discountPercent(),changed.deliveryMethod(),changed.notes(),changed.originalUnitPrice(),changed.priceOverrideReason(),changed.priceOverrideByUserId(),changed.priceOverrideByName(),changed.priceOverrideApprovalToken(),changed.custom()};for(int column=0;column<values.length;column++)lineModel.setValueAt(values[column],modelRow,column);}return;}
            LineEditor editor = new LineEditor(this, existing);
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
                    parseMoney(String.valueOf(lineModel.getValueAt(row, 5))),
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

        private void createQuotation() {
            QuotationInvoiceViewService.CustomerOption customer = selectedCustomer();
            if (customer == null) {
                JOptionPane.showMessageDialog(this, "Select a customer from the list.");
                return;
            }
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
                        parseMoney(String.valueOf(lineModel.getValueAt(i, 5))),
                        String.valueOf(lineModel.getValueAt(i, 6)),
                        String.valueOf(lineModel.getValueAt(i, 7)),
                        blankToNull(String.valueOf(lineModel.getValueAt(i, 9))),
                        nullableInteger(lineModel.getValueAt(i, 10)),
                        blankToNull(String.valueOf(lineModel.getValueAt(i, 11))),
                        blankToNull(String.valueOf(lineModel.getValueAt(i, 12))),
                        (QuotationInvoiceService.CustomLineInput)lineModel.getValueAt(i,13)
                ));
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
            statusLabel.setText(editQuotationId == null ? "Creating quotation..." : "Saving draft...");
            Thread createThread = new Thread(() -> {
                try {
                    QuotationInvoiceService.QuotationResult result = editQuotationId == null
                            ? QuotationInvoiceService.createQuotation(customer.customerId(), validUntil, dueDate, notes, lines)
                            : QuotationInvoiceService.updateDraftQuotation(editQuotationId, customer.customerId(), validUntil, dueDate, notes, lines);
                    SwingUtilities.invokeLater(() -> {
                        created = true;
                        setCreatingQuotation(false);
                        statusLabel.setForeground(new Color(74, 222, 128));
                        statusLabel.setText((editQuotationId == null ? "Created quotation " : "Saved draft ") + result.quotationNumber() + ".");
                        dispose();
                    });
                } catch (Exception ex) {
                    SwingUtilities.invokeLater(() -> {
                        setCreatingQuotation(false);
                        showQuotationError((editQuotationId == null ? "Failed to create quotation." : "Failed to save draft.") + "\n\n" + ex.getMessage());
                    });
                }
            }, "sales-quotation-create");
            createThread.setDaemon(true);
            createThread.start();
        }

        private void setCreatingQuotation(boolean creating) {
            createButton.setEnabled(!creating);
            cancelButton.setEnabled(!creating);
            createButton.setText(creating ? (editQuotationId == null ? "Creating..." : "Saving...") : (editQuotationId == null ? "Create Quotation" : "Save Draft"));
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
            if(selected!=null)rememberCustomer(selected);
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
            rememberedCustomer=customer;updatingCustomerResults=true;customerBox.setSelectedItem(customer);updatingCustomerResults=false;
        }

        private List<QuotationInvoiceViewService.CustomerOption> currentCustomers(){List<QuotationInvoiceViewService.CustomerOption> current=new ArrayList<>();for(int i=0;i<customerBox.getItemCount();i++)current.add(customerBox.getItemAt(i));return current;}

        private static QuotationInvoiceViewService.CustomerOption customerById(List<QuotationInvoiceViewService.CustomerOption> customers,QuotationInvoiceViewService.CustomerOption target){if(target==null)return null;for(var customer:customers)if(customer.customerId()==target.customerId())return customer;return null;}

        static QuotationInvoiceViewService.CustomerOption resolveCustomerSelection(Object selected,String editorText,List<QuotationInvoiceViewService.CustomerOption> customers,QuotationInvoiceViewService.CustomerOption remembered){
            if(selected instanceof QuotationInvoiceViewService.CustomerOption customer)return customer;
            if(remembered!=null&&exactCustomerMatch(remembered,editorText))return remembered;
            String search=editorText==null?"":editorText.trim();if(search.isBlank())return null;
            for(var customer:customers)if(exactCustomerMatch(customer,search))return customer;
            return customers.size()==1?customers.get(0):null;
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

    private static class LineEditor extends JDialog {
        private final JComboBox<QuotationInvoiceViewService.ProductOption> productBox = new JComboBox<>();
        private final JTextField itemField = new JTextField();
        private final JTextField skuField = new JTextField();
        private final JTextField qtyField = new JTextField("1");
        private final JTextField unitField = new JTextField("0");
        private final JTextField discountField = new JTextField("0");
        private final JComboBox<String> deliveryBox = new JComboBox<>(new String[]{"PICKUP", "LOCAL_DELIVERY", "SHIP", "INSTALLATION"});
        private final JTextField notesField = new JTextField();
        private final Timer productSearchTimer;
        private final boolean manualOnly;
        private boolean updatingProductResults;
        private LineInput line;

        LineEditor(JDialog owner) {
            this(owner, null, true);
        }

        LineEditor(JDialog owner, LineInput existingLine) {
            this(owner, existingLine, false);
        }

        private LineEditor(JDialog owner, LineInput existingLine, boolean manualOnly) {
            super(owner, "Add Quotation Line", true);
            this.manualOnly = manualOnly;
            productSearchTimer = new Timer(300, e -> refreshProductResults(editorText(productBox)));
            productSearchTimer.setRepeats(false);
            setSize(520, 360);
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
            productBox.addActionListener(e -> fillProduct());
            JPanel panel = manualOnly
                    ? formPanel(new String[]{"Item", "SKU", "Qty", "Unit Price", "Discount %", "Delivery", "Notes"},
                    new JComponent[]{itemField, skuField, qtyField, unitField, discountField, deliveryBox, notesField})
                    : formPanel(new String[]{"Product", "Item", "SKU", "Qty", "Unit Price", "Discount %", "Delivery", "Notes"},
                    new JComponent[]{productBox, itemField, skuField, qtyField, unitField, discountField, deliveryBox, notesField});
            panel.setBorder(new EmptyBorder(12, 12, 12, 12));
            add(panel, BorderLayout.CENTER);
            JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
            JButton add = new JButton("Add");
            JButton cancel = new JButton("Cancel");
            buttons.add(add);
            buttons.add(cancel);
            stylePrimaryButton(add);
            styleSecondaryButton(cancel);
            add(buttons, BorderLayout.SOUTH);
            add.addActionListener(e -> save());
            cancel.addActionListener(e -> dispose());
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
                        enteredPrice, originalUnitPrice, parseMoney(discountField.getText()),
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

    private static class CustomQuotationLineEditor extends JDialog{
        private final JComboBox<CustomOrderDataService.CustomItemOption>itemBox=new JComboBox<>();
        private final JComboBox<CustomOrderDataService.VariantOption>variantBox=new JComboBox<>();
        private final JTextField qty=new JTextField("1"),price=new JTextField("0"),width=new JTextField(),length=new JTextField(),discount=new JTextField("0"),design=new JTextField(),instructions=new JTextField();
        private final JComboBox<String>delivery=new JComboBox<>(new String[]{"PICKUP","LOCAL_DELIVERY","SHIP","INSTALLATION"});
        private final DefaultTableModel addons=new DefaultTableModel(new String[]{"Material ID","Material","Preset ID","Size","Mode","Description","Lines","Charge"},0);
        private LineInput line;
        CustomQuotationLineEditor(JDialog owner){this(owner,null);}
        CustomQuotationLineEditor(JDialog owner,LineInput existing){super(owner,existing==null?"Add Custom Item to Quotation":"Edit Quotation Custom Item",true);setSize(760,650);setLocationRelativeTo(owner);setLayout(new BorderLayout(8,8));
            JPanel form=formPanel(new String[]{"Custom Item","Variant","Quantity","Rate / Unit Price","Width","Length","Discount %","Delivery","Design / Placement","Instructions"},new JComponent[]{itemBox,variantBox,qty,price,width,length,discount,delivery,design,instructions});
            JTable addonTable=new JTable(addons);hideAddonIds(addonTable);JPanel center=new JPanel(new BorderLayout(8,8));center.setBorder(new EmptyBorder(12,12,12,12));center.add(form,BorderLayout.NORTH);center.add(new JScrollPane(addonTable),BorderLayout.CENTER);
            JButton addAddon=new JButton("Add Print Add-on"),remove=new JButton("Remove Add-on"),save=new JButton("Add Custom Item"),cancel=new JButton("Cancel");JPanel buttons=new JPanel(new FlowLayout(FlowLayout.RIGHT));buttons.add(addAddon);buttons.add(remove);buttons.add(save);buttons.add(cancel);center.add(buttons,BorderLayout.SOUTH);add(center);
            styleSecondaryButton(addAddon);styleSecondaryButton(remove);stylePrimaryButton(save);styleSecondaryButton(cancel);
            itemBox.addActionListener(e->loadVariantsAndPrice());addAddon.addActionListener(e->addAddon());remove.addActionListener(e->{int r=addonTable.getSelectedRow();if(r>=0)addons.removeRow(addonTable.convertRowIndexToModel(r));});save.addActionListener(e->save());cancel.addActionListener(e->dispose());
            ThemeManager.applyToWindow(this);
            try{for(var item:ResponsiveTask.await(this,"Loading custom items...",CustomOrderDataService::listActiveItems))itemBox.addItem(item);if(existing!=null)loadExisting(existing);}catch(Exception e){JOptionPane.showMessageDialog(this,e.getMessage(),"Custom Items",JOptionPane.ERROR_MESSAGE);dispose();}
        }
        private void loadExisting(LineInput existing){var c=existing.custom();for(int i=0;i<itemBox.getItemCount();i++)if(java.util.Objects.equals(itemBox.getItemAt(i).customItemId(),c.customItemId())){itemBox.setSelectedIndex(i);break;}loadVariantsAndPrice();for(int i=0;i<variantBox.getItemCount();i++)if(java.util.Objects.equals(variantBox.getItemAt(i).variantId(),c.customVariantId())){variantBox.setSelectedIndex(i);break;}qty.setText(String.valueOf(existing.quantity()));price.setText(c.areaPrice()==null?existing.unitPrice().toPlainString():c.areaPrice().toPlainString());width.setText(c.widthValue()==null?"":c.widthValue().toPlainString());length.setText(c.lengthValue()==null?"":c.lengthValue().toPlainString());discount.setText(existing.discountPercent().toPlainString());delivery.setSelectedItem(existing.deliveryMethod());design.setText(c.customizationDetails());instructions.setText(c.orderInstructions());if(c.printAddons()!=null)for(var a:c.printAddons())addons.addRow(new Object[]{a.printMaterialId(),a.materialName(),a.printSizePresetId(),a.printSizeName(),a.pricingMode(),a.description(),a.lineCount(),a.charge()});}
        private void loadVariantsAndPrice(){CustomOrderDataService.CustomItemOption item=(CustomOrderDataService.CustomItemOption)itemBox.getSelectedItem();variantBox.removeAllItems();if(item==null)return;BigDecimal p="AREA".equals(item.pricingType())?item.areaPrice():item.fixedPrice();if(p!=null)price.setText(p.toPlainString());if(item.hasVariants())try{for(var v:ResponsiveTask.await(this,"Loading variants...",()->CustomOrderDataService.listActiveVariants(item.customItemId())))variantBox.addItem(v);}catch(Exception e){JOptionPane.showMessageDialog(this,e.getMessage());}}
        private void addAddon(){try{List<CustomOrderDataService.PrintMaterialOption>materials=ResponsiveTask.await(this,"Loading print materials...",CustomOrderDataService::listActivePrintMaterials);if(materials.isEmpty())throw new IllegalArgumentException("No active print materials are configured.");CustomOrderDataService.PrintMaterialOption material=(CustomOrderDataService.PrintMaterialOption)JOptionPane.showInputDialog(this,"Material:","Print Add-on",JOptionPane.PLAIN_MESSAGE,null,materials.toArray(),materials.get(0));if(material==null)return;List<CustomOrderDataService.PrintSizePresetOption>presets=ResponsiveTask.await(this,"Loading print sizes...",()->CustomOrderDataService.listActivePrintSizePresets(material.printMaterialId()));CustomOrderDataService.PrintSizePresetOption preset=presets.isEmpty()?null:(CustomOrderDataService.PrintSizePresetOption)JOptionPane.showInputDialog(this,"Print size:","Print Add-on",JOptionPane.PLAIN_MESSAGE,null,presets.toArray(),presets.get(0));String description=JOptionPane.showInputDialog(this,"Print description:","");if(description==null)return;String chargeText=JOptionPane.showInputDialog(this,"Print charge:",preset==null||preset.fixedPrice()==null?"0":preset.fixedPrice().toPlainString());if(chargeText==null)return;addons.addRow(new Object[]{material.printMaterialId(),material.materialName(),preset==null?null:preset.printSizePresetId(),preset==null?"Custom":preset.presetName(),preset==null?"FIXED_PRESET":preset.pricingMode(),description,1,parseMoney(chargeText)});}catch(Exception e){JOptionPane.showMessageDialog(this,e.getMessage(),"Print Add-on",JOptionPane.ERROR_MESSAGE);}}
        private void save(){try{var item=(CustomOrderDataService.CustomItemOption)itemBox.getSelectedItem();if(item==null)throw new IllegalArgumentException("Select a custom item.");var variant=(CustomOrderDataService.VariantOption)variantBox.getSelectedItem();if(item.hasVariants()&&variant==null)throw new IllegalArgumentException("Select a variant.");int quantity=Integer.parseInt(qty.getText().trim());if(quantity<=0)throw new IllegalArgumentException("Quantity must be greater than zero.");BigDecimal entered=parseMoney(price.getText()),configured=variant!=null&&variant.fixedPrice()!=null?variant.fixedPrice():("AREA".equals(item.pricingType())?item.areaPrice():item.fixedPrice());BigDecimal w=width.getText().isBlank()?null:new BigDecimal(width.getText().trim()),l=length.getText().isBlank()?null:new BigDecimal(length.getText().trim()),area=null,base=entered;if("AREA".equals(item.pricingType())){if(w==null||l==null||w.signum()<=0||l.signum()<=0)throw new IllegalArgumentException("Enter valid width and length for area pricing.");area=w.multiply(l);base=area.multiply(entered).setScale(2,RoundingMode.HALF_UP);}List<QuotationInvoiceService.PrintAddonInput>print=new ArrayList<>();BigDecimal addonTotal=BigDecimal.ZERO;for(int r=0;r<addons.getRowCount();r++){BigDecimal charge=parseMoney(String.valueOf(addons.getValueAt(r,7)));addonTotal=addonTotal.add(charge);print.add(new QuotationInvoiceService.PrintAddonInput(((Number)addons.getValueAt(r,0)).longValue(),String.valueOf(addons.getValueAt(r,1)),addons.getValueAt(r,2)==null?null:((Number)addons.getValueAt(r,2)).longValue(),String.valueOf(addons.getValueAt(r,3)),String.valueOf(addons.getValueAt(r,4)),String.valueOf(addons.getValueAt(r,5)),Integer.parseInt(String.valueOf(addons.getValueAt(r,6))),charge));}BigDecimal unit=base.add(addonTotal),pct=parseMoney(discount.getText());if(pct.signum()<0||pct.compareTo(BigDecimal.valueOf(100))>0)throw new IllegalArgumentException("Discount must be between 0 and 100%.");String overrideReason=null,token=null;if(configured!=null&&entered.compareTo(configured)!=0){overrideReason=JOptionPane.showInputDialog(this,"Reason for custom-item price override:");if(overrideReason==null||overrideReason.isBlank())return;if(!PermissionManager.hasPermission("CUSTOM_ORDER_PRICE_OVERRIDE")&&!PermissionManager.hasPermission("CUSTOM_ORDER_OVERRIDES")){var approval=ManagerApprovalService.requestApproval(this,"CUSTOM_ORDER_PRICE_OVERRIDE","Custom Order Price Override","Reason for custom-item price override:");if(approval==null)return;token=approval.lanApprovalToken();}}var custom=new QuotationInvoiceService.CustomLineInput(item.customItemId(),variant==null?null:variant.variantId(),variant==null?null:variant.name(),item.pricingType(),w,l,item.dimensionUnit(),area,item.areaPriceUnit(),entered,design.getText().trim(),instructions.getText().trim(),print);String notesText=condensed(custom);line=new LineInput(null,item.name(),item.sku(),quantity,unit,unit,pct,String.valueOf(delivery.getSelectedItem()),notesText,overrideReason,null,null,token,custom);dispose();}catch(Exception e){JOptionPane.showMessageDialog(this,e.getMessage(),"Custom Item",JOptionPane.ERROR_MESSAGE);}}
        private static String condensed(QuotationInvoiceService.CustomLineInput c){List<String>parts=new ArrayList<>();if(c.variantName()!=null&&!c.variantName().isBlank())parts.add("Variant: "+c.variantName());if(c.widthValue()!=null&&c.lengthValue()!=null)parts.add("Size: "+c.widthValue()+" x "+c.lengthValue()+" "+c.dimensionUnit());if(c.customizationDetails()!=null&&!c.customizationDetails().isBlank())parts.add("Design: "+c.customizationDetails());if(c.orderInstructions()!=null&&!c.orderInstructions().isBlank())parts.add(c.orderInstructions());if(c.printAddons()!=null&&!c.printAddons().isEmpty())parts.add("Print: "+c.printAddons().stream().map(a->a.materialName()+" / "+a.printSizeName()).reduce((a,b)->a+", "+b).orElse(""));return String.join(" | ",parts);}
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
