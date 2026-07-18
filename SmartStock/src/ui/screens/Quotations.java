package ui.screens;

import managers.PermissionManager;
import services.ManagerApprovalService;
import services.QuotationInvoiceService;
import services.QuotationInvoiceViewService;
import ui.components.AppMenuBar;
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
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class Quotations extends JFrame {
    private static final String CHANGE_SALE_ITEM_PRICE_PERMISSION = "CHANGE_SALE_ITEM_PRICE";
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
    private static final Color INPUT_BACKGROUND = new Color(248, 250, 252);
    private static final Color INPUT_FOREGROUND = new Color(17, 24, 39);
    private static final Color INPUT_SELECTION = new Color(37, 99, 235);
    private static final Color BUTTON_PRIMARY = new Color(37, 99, 235);
    private static final Color BUTTON_SECONDARY = new Color(75, 85, 99);
    private final DefaultTableModel quotationModel = readOnlyModel("ID", "Quotation #", "Customer", "Status", "Valid Until", "Total");
    private final JTable quotationTable = new JTable(quotationModel);

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
            QuotationInvoiceViewService.QuotationEditData quotation = QuotationInvoiceViewService.loadQuotationForEdit(quotationId);
            if (!"DRAFT".equals(quotation.status())) {
                JOptionPane.showMessageDialog(this, "Only draft quotations can be edited.", "Quotations", JOptionPane.WARNING_MESSAGE);
                return;
            }
            QuotationEditor editor = new QuotationEditor(this, quotation);
            editor.setVisible(true);
            if (editor.created) {
                refreshQuotations();
            }
        } catch (SQLException ex) {
            showError("Failed to load draft quotation", ex);
        }
    }

    void issueSelectedQuotation() {
        Long quotationId = selectedId(quotationTable);
        if (quotationId == null) return;
        try {
            QuotationInvoiceService.issueQuotation(quotationId);
            refreshQuotations();
            openQuotationPrintDialog(quotationId);
        } catch (SQLException ex) {
            showError("Failed to issue quotation", ex);
        }
    }

    void acceptSelectedQuotation() {
        Long quotationId = selectedId(quotationTable);
        if (quotationId == null) return;
        try {
            QuotationInvoiceService.InvoiceResult result = QuotationInvoiceService.acceptQuotation(quotationId);
            promptForAcceptedQuotationPayment(result.invoiceId());
            Long deliveryEventId = promptForAcceptedQuotationDelivery(result.invoiceId());
            refreshQuotations();
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
            PaymentPrompt prompt = new PaymentPrompt(this, financials);
            prompt.setVisible(true);
            PaymentInput payment = prompt.paymentInput();
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

    static void stylePrimaryButton(JButton button) {
        styleButton(button, BUTTON_PRIMARY, Color.WHITE);
    }

    static void styleSecondaryButton(JButton button) {
        styleButton(button, BUTTON_SECONDARY, Color.WHITE);
    }

    private static void styleButton(JButton button, Color background, Color foreground) {
        button.setOpaque(true);
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
        for (int modelIndex = LINE_COL_ORIGINAL_UNIT; modelIndex <= LINE_COL_OVERRIDE_BY_NAME; modelIndex++) {
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
        private final JTextArea notesArea = new JTextArea(3, 40);
        private final DefaultTableModel lineModel = new DefaultTableModel(new String[]{
                "Product ID", "Item", "SKU", "Qty", "Unit", "Disc %", "Delivery", "Notes",
                "Original Unit", "Override Reason", "Override By User ID", "Override By"
        }, 0);
        private final JLabel statusLabel = new JLabel(" ");
        private final Long editQuotationId;
        private final String editQuotationNumber;
        private final Timer customerSearchTimer;
        private JButton createButton;
        private JButton cancelButton;
        private boolean updatingCustomerResults;
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
            main.add(formPanel(new String[]{"Customer", "Valid Until", "Notes"}, new JComponent[]{customerBox, validUntilField, new JScrollPane(notesArea)}), BorderLayout.NORTH);
            JTable lineTable = new JTable(lineModel);
            styleTable(lineTable);
            hideInternalLineColumns(lineTable);
            main.add(new JScrollPane(lineTable), BorderLayout.CENTER);
            JPanel southPanel = new JPanel(new BorderLayout(8, 8));
            statusLabel.setForeground(new Color(248, 113, 113));
            statusLabel.setFont(statusLabel.getFont().deriveFont(Font.BOLD, 13f));
            southPanel.add(statusLabel, BorderLayout.WEST);
            JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
            JButton addLine = new JButton("Add Line");
            JButton editLine = new JButton("Edit Line");
            JButton removeLine = new JButton("Remove Line");
            createButton = new JButton(editData == null ? "Create Quotation" : "Save Draft");
            cancelButton = new JButton("Cancel");
            buttons.add(addLine);
            buttons.add(editLine);
            buttons.add(removeLine);
            buttons.add(createButton);
            buttons.add(cancelButton);
            styleSecondaryButton(addLine);
            styleSecondaryButton(editLine);
            styleSecondaryButton(removeLine);
            stylePrimaryButton(createButton);
            styleSecondaryButton(cancelButton);
            southPanel.add(buttons, BorderLayout.EAST);
            main.add(southPanel, BorderLayout.SOUTH);
            addLine.addActionListener(e -> addLine());
            editLine.addActionListener(e -> editSelectedLine(lineTable));
            removeLine.addActionListener(e -> {
                int row = lineTable.getSelectedRow();
                if (row >= 0) lineModel.removeRow(lineTable.convertRowIndexToModel(row));
            });
            createButton.addActionListener(e -> createQuotation());
            cancelButton.addActionListener(e -> dispose());
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
                        editor.line.priceOverrideByName()
                });
            }
        }

        private void editSelectedLine(JTable lineTable) {
            int row = lineTable.getSelectedRow();
            if (row < 0) {
                JOptionPane.showMessageDialog(this, "Select a line first.", "Quotation", JOptionPane.WARNING_MESSAGE);
                return;
            }
            int modelRow = lineTable.convertRowIndexToModel(row);
            LineInput existing = lineInputFromRow(modelRow);
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
                    blankToNull(String.valueOf(lineModel.getValueAt(row, 11)))
            );
        }

        private void loadExistingQuotation(QuotationInvoiceViewService.QuotationEditData quotation) {
            selectCustomer(quotation.customerId());
            validUntilField.setText(quotation.validUntil() == null ? LocalDate.now().plusDays(30).toString() : quotation.validUntil().toLocalDate().toString());
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
                        line.priceOverrideByName()
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
                        blankToNull(String.valueOf(lineModel.getValueAt(i, 11)))
                ));
            }
            LocalDate validUntil;
            try {
                validUntil = LocalDate.parse(validUntilField.getText().trim());
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Valid Until must be a date like 2026-07-11.", "Quotation", JOptionPane.ERROR_MESSAGE);
                return;
            }
            String notes = notesArea.getText();
            setCreatingQuotation(true);
            statusLabel.setForeground(new Color(248, 113, 113));
            statusLabel.setText(editQuotationId == null ? "Creating quotation..." : "Saving draft...");
            Thread createThread = new Thread(() -> {
                try {
                    QuotationInvoiceService.QuotationResult result = editQuotationId == null
                            ? QuotationInvoiceService.createQuotation(customer.customerId(), validUntil, notes, lines)
                            : QuotationInvoiceService.updateDraftQuotation(editQuotationId, customer.customerId(), validUntil, notes, lines);
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
            UiTaskRunner.submit(this,"quotation.customers",()->QuotationInvoiceViewService.searchCustomers(query),customers->{
                updatingCustomerResults = true;
                customerBox.removeAllItems();
                for (QuotationInvoiceViewService.CustomerOption customer : customers) {
                    customerBox.addItem(customer);
                }
                customerBox.getEditor().setItem(query);
                updatingCustomerResults = false;
                selectExactCustomerMatch(query, customers);
                showCustomerPopupIfUseful();
            },ex->{updatingCustomerResults=false;});
        }

        private QuotationInvoiceViewService.CustomerOption selectedCustomer() {
            Object selected = customerBox.getSelectedItem();
            if (selected instanceof QuotationInvoiceViewService.CustomerOption customer) {
                return customer;
            }
            return selectBestCustomerMatch(editorText(customerBox));
        }

        private QuotationInvoiceViewService.CustomerOption selectBestCustomerMatch(String searchText) {
            List<QuotationInvoiceViewService.CustomerOption> current=new ArrayList<>();for(int i=0;i<customerBox.getItemCount();i++)current.add(customerBox.getItemAt(i));return selectBestCustomerMatch(searchText,current,true);
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
                updatingCustomerResults = true;
                customerBox.setSelectedItem(match);
                updatingCustomerResults = false;
            }
            return match;
        }

        private boolean exactCustomerMatch(QuotationInvoiceViewService.CustomerOption customer, String searchText) {
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
        private boolean updatingProductResults;
        private LineInput line;

        LineEditor(JDialog owner) {
            this(owner, null);
        }

        LineEditor(JDialog owner, LineInput existingLine) {
            super(owner, "Add Quotation Line", true);
            productSearchTimer = new Timer(300, e -> refreshProductResults(editorText(productBox)));
            productSearchTimer.setRepeats(false);
            setSize(520, 360);
            setLocationRelativeTo(owner);
            setLayout(new BorderLayout(8, 8));
            productBox.setEditable(true);
            productBox.setMaximumRowCount(12);
            loadProducts("");
            installProductSearch();
            if (existingLine != null) {
                loadExistingLine(existingLine);
            }
            productBox.addActionListener(e -> fillProduct());
            JPanel panel = formPanel(
                    new String[]{"Product", "Item", "SKU", "Qty", "Unit Price", "Discount %", "Delivery", "Notes"},
                    new JComponent[]{productBox, itemField, skuField, qtyField, unitField, discountField, deliveryBox, notesField}
            );
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
                Object selected = productBox.getSelectedItem();
                QuotationInvoiceViewService.ProductOption product = selected instanceof QuotationInvoiceViewService.ProductOption option ? option : null;
                String itemName = itemField.getText().trim();
                if (itemName.isBlank()) {
                    throw new IllegalArgumentException("Item name is required.");
                }
                BigDecimal enteredPrice = parseMoney(unitField.getText());
                BigDecimal originalUnitPrice = product == null || product.price() == null ? enteredPrice : product.price();
                String overrideReason = existingOverrideReason();
                Integer overrideByUserId = existingOverrideByUserId();
                String overrideByName = existingOverrideByName();
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
                    }
                } else if (!priceOverridden) {
                    overrideReason = null;
                    overrideByUserId = null;
                    overrideByName = null;
                }
                line = new LineInput(product, itemName, skuField.getText(), Integer.parseInt(qtyField.getText().trim()),
                        enteredPrice, originalUnitPrice, parseMoney(discountField.getText()),
                        String.valueOf(deliveryBox.getSelectedItem()), notesField.getText(),
                        overrideReason, overrideByUserId, overrideByName);
                dispose();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, ex.getMessage(), "Line", JOptionPane.ERROR_MESSAGE);
            }
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

    private record LineInput(QuotationInvoiceViewService.ProductOption product, String itemName, String sku, int quantity,
                             BigDecimal unitPrice, BigDecimal originalUnitPrice, BigDecimal discountPercent,
                             String deliveryMethod, String notes, String priceOverrideReason,
                             Integer priceOverrideByUserId, String priceOverrideByName) {
    }

    record PaymentInput(BigDecimal amount, String method, String reference) {
    }
}
